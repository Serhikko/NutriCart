-- Products a user added under their barcode, for the website.
--
-- Ukrainian (GS1 482) and Belarusian (GS1 481) products are often missing
-- from Open Food Facts, or there without all of their nutrition. The website
-- then lets the user add the product once, from the label, and keeps it here
-- under its barcode, so the next scan of that code finds it before Open Food
-- Facts is asked, and a name search lists it first. The phone keeps the same
-- thing locally in Room as "local:barcode:<digits>"; the website maps these
-- rows to that same id. Nothing here is synced to the phone (yet).
--
-- Private to the owner: unlike the diary, a partner never reads these rows.
-- The website treats a missing table as "nothing saved", so scanning keeps
-- working on a project that has not run this file; only remembering fails.
-- Safe to run more than once.

create table if not exists custom_products (
    owner_id                uuid not null references auth.users (id) on delete cascade,
    -- The scanned digits (or Open Food Facts' own form of the code), EAN-8 to GTIN-14.
    barcode                 text not null check (barcode ~ '^[0-9]{8,14}$'),
    name                    text not null check (char_length(btrim(name)) between 1 and 500),
    brand                   text,
    -- Per 100 g, or per 100 ml when liquid (1 ml of a drink ~ 1 g): the
    -- same ranges as the phone's custom-food dialog.
    kcal_per_100g           double precision not null check (kcal_per_100g between 0 and 900),
    protein_per_100g        double precision not null check (protein_per_100g between 0 and 100),
    fat_per_100g            double precision not null check (fat_per_100g between 0 and 100),
    carbs_per_100g          double precision not null check (carbs_per_100g between 0 and 100),
    serving_size_g          double precision check (serving_size_g is null or serving_size_g between 1 and 5000),
    liquid                  boolean not null default false,
    fiber_per_100g          double precision check (fiber_per_100g is null or fiber_per_100g between 0 and 100),
    sugars_per_100g         double precision check (sugars_per_100g is null or sugars_per_100g between 0 and 100),
    salt_per_100g           double precision check (salt_per_100g is null or salt_per_100g between 0 and 100),
    saturated_fat_per_100g  double precision check (saturated_fat_per_100g is null or saturated_fat_per_100g between 0 and 100),
    updated_at              timestamptz not null default now(),
    primary key (owner_id, barcode)
);

-- Name search: the website asks for name ILIKE '%text%' within one owner;
-- the primary key already serves the barcode lookup.
create index if not exists custom_products_owner_updated_idx on custom_products (owner_id, updated_at);

drop trigger if exists custom_products_updated_at on custom_products;
create trigger custom_products_updated_at before update on custom_products
    for each row execute function set_updated_at();

-- Supabase's default privileges already give the API roles access to new
-- tables in public; stated here so the table works on a project where they
-- were narrowed. Anonymous users sign in as `authenticated` too. Row-level
-- security below still decides which rows each user touches.
grant select, insert, update, delete on custom_products to authenticated;

alter table custom_products enable row level security;

-- Owner only, for every operation.
drop policy if exists custom_products_select on custom_products;
create policy custom_products_select on custom_products for select to authenticated
    using (owner_id = auth.uid());
drop policy if exists custom_products_insert on custom_products;
create policy custom_products_insert on custom_products for insert to authenticated
    with check (owner_id = auth.uid());
drop policy if exists custom_products_update on custom_products;
create policy custom_products_update on custom_products for update to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists custom_products_delete on custom_products;
create policy custom_products_delete on custom_products for delete to authenticated
    using (owner_id = auth.uid());
