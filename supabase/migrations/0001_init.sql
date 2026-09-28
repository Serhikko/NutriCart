-- NutriCart cloud schema, milestone 1: accounts, pairing, one-way sync from
-- the phone, and nudges back. Run once in the Supabase SQL editor (or with the
-- Supabase CLI). Everything is protected by row-level security: a row is
-- visible to its owner and to anyone linked to that owner through
-- partner_links; only the owner writes it; a partner may insert nudges.
--
-- Design notes live in docs/cloud-sync.md.

create extension if not exists pgcrypto;

-- Enum names match the Kotlin enums exactly, so both clients share the words.
create type meal_slot as enum ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK');
create type weight_source as enum ('MANUAL', 'HEALTH_CONNECT');

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table profiles (
    user_id       uuid primary key references auth.users (id) on delete cascade,
    display_name  text not null check (char_length(display_name) between 1 and 40),
    updated_at    timestamptz not null default now()
);

-- The code itself is never stored: only its SHA-256 (hex, upper-cased input).
create table pairing_codes (
    id          uuid primary key default gen_random_uuid(),
    code_hash   text not null unique,
    owner_id    uuid not null references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    expires_at  timestamptz not null,
    used_at     timestamptz
);
create index pairing_codes_owner_idx on pairing_codes (owner_id);

-- One row per redeemed code. scope is 'read' in milestone 1; later milestones
-- may add 'write' for a partner who logs on the owner's behalf.
create table partner_links (
    id          uuid primary key default gen_random_uuid(),
    owner_id    uuid not null references auth.users (id) on delete cascade,
    partner_id  uuid not null references auth.users (id) on delete cascade,
    scope       text not null default 'read' check (scope in ('read')),
    created_at  timestamptz not null default now(),
    constraint partner_links_unique unique (owner_id, partner_id),
    constraint partner_links_not_self check (owner_id <> partner_id),
    -- Both sides also point at profiles, under explicit names, so PostgREST
    -- can embed either display name: profiles!partner_links_owner_profile_fkey
    -- and profiles!partner_links_partner_id_fkey.
    constraint partner_links_owner_profile_fkey foreign key (owner_id) references profiles (user_id) on delete cascade,
    constraint partner_links_partner_id_fkey foreign key (partner_id) references profiles (user_id) on delete cascade
);
create index partner_links_partner_idx on partner_links (partner_id);

-- Failed redeem attempts, for the rate limit inside redeem_pairing_code.
create table pairing_attempts (
    user_id       uuid not null,
    attempted_at  timestamptz not null default now()
);
create index pairing_attempts_idx on pairing_attempts (user_id, attempted_at);

-- The diary. id is minted on the phone ("<device>:f:<local id>"), so a
-- retried upload is an idempotent upsert. Nutrition values are SNAPSHOTS,
-- exactly as in the Room table: history never changes when a product does.
create table food_log_entries (
    id                 text primary key,
    owner_id           uuid not null references auth.users (id) on delete cascade,
    epoch_day          integer not null,
    meal               meal_slot not null,
    name               text not null,
    grams              double precision,
    servings           double precision,
    kcal               double precision not null,
    protein_g          double precision not null,
    fat_g              double precision not null,
    carbs_g            double precision not null,
    fiber_g            double precision,
    sugars_g           double precision,
    salt_g             double precision,
    saturated_fat_g    double precision,
    logged_at          timestamptz not null,
    deleted_at         timestamptz,
    updated_at         timestamptz not null default now()
);
create index food_log_entries_day_idx on food_log_entries (owner_id, epoch_day);

create table water_entries (
    id          text primary key,
    owner_id    uuid not null references auth.users (id) on delete cascade,
    epoch_day   integer not null,
    ml          integer not null,
    logged_at   timestamptz not null,
    deleted_at  timestamptz,
    updated_at  timestamptz not null default now()
);
create index water_entries_day_idx on water_entries (owner_id, epoch_day);

-- At most one row per day and source, like the Room unique index.
create table weight_entries (
    owner_id    uuid not null references auth.users (id) on delete cascade,
    epoch_day   integer not null,
    source      weight_source not null,
    weight_kg   double precision not null,
    updated_at  timestamptz not null default now(),
    primary key (owner_id, epoch_day, source)
);

-- Computed on the phone with the same math as the dashboard and the widget.
-- The website shows these numbers and never re-derives the target.
create table day_summaries (
    owner_id      uuid not null references auth.users (id) on delete cascade,
    epoch_day     integer not null,
    target_kcal   integer not null,
    eaten_kcal    integer not null,
    active_kcal   double precision,
    steps         integer,
    workout_kcal  double precision not null default 0,
    updated_at    timestamptz not null default now(),
    primary key (owner_id, epoch_day)
);

-- A message from a partner to the owner's phone. from_name is copied in at
-- insert time so the phone can show it without a second query.
create table nudges (
    id          uuid primary key default gen_random_uuid(),
    owner_id    uuid not null references auth.users (id) on delete cascade,
    from_id     uuid not null references auth.users (id) on delete cascade,
    from_name   text not null default '',
    text        text not null check (char_length(text) between 1 and 500),
    created_at  timestamptz not null default now(),
    seen_at     timestamptz
);
create index nudges_owner_unseen_idx on nudges (owner_id, seen_at, created_at);

-- ---------------------------------------------------------------------------
-- updated_at maintenance
-- ---------------------------------------------------------------------------

create or replace function set_updated_at() returns trigger
language plpgsql as $$
begin
    new.updated_at := now();
    return new;
end $$;

create trigger profiles_updated_at before update on profiles
    for each row execute function set_updated_at();
create trigger food_log_entries_updated_at before update on food_log_entries
    for each row execute function set_updated_at();
create trigger water_entries_updated_at before update on water_entries
    for each row execute function set_updated_at();
create trigger weight_entries_updated_at before update on weight_entries
    for each row execute function set_updated_at();
create trigger day_summaries_updated_at before update on day_summaries
    for each row execute function set_updated_at();

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

-- True when the calling user has been linked to this owner by a pairing code.
create or replace function is_partner_of(p_owner uuid) returns boolean
language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from partner_links
        where owner_id = p_owner and partner_id = auth.uid()
    );
$$;

-- Redeems a pairing code for the calling user. Runs as the table owner so it
-- can read pairing_codes (which no client may select) and insert the link.
-- Returns the owner's id and display name, or no row when the code is wrong,
-- expired or used. Five failures in ten minutes lock the caller out.
create or replace function redeem_pairing_code(p_code text)
returns table (owner_id uuid, display_name text)
language plpgsql security definer set search_path = public as $$
declare
    v_hash   text;
    v_code   pairing_codes%rowtype;
    v_me     uuid := auth.uid();
begin
    if v_me is null then
        raise exception 'not signed in' using errcode = '28000';
    end if;

    if (select count(*) from pairing_attempts a
        where a.user_id = v_me and a.attempted_at > now() - interval '10 minutes') >= 5 then
        raise exception 'too many attempts, try again later' using errcode = '54000';
    end if;

    v_hash := encode(digest(upper(trim(p_code)), 'sha256'), 'hex');

    select * into v_code from pairing_codes c
    where c.code_hash = v_hash and c.used_at is null and c.expires_at > now()
    for update;

    if not found then
        insert into pairing_attempts (user_id) values (v_me);
        return;
    end if;

    if v_code.owner_id = v_me then
        raise exception 'that is your own code' using errcode = '22023';
    end if;

    -- Both sides need a profile row (the link points at profiles). The phone
    -- publishes the owner's real name on its next sync and the website asks
    -- the partner for one on first visit; these are the safety nets.
    insert into profiles (user_id, display_name)
    values (v_me, 'Partner') on conflict (user_id) do nothing;
    insert into profiles (user_id, display_name)
    values (v_code.owner_id, 'NutriCart') on conflict (user_id) do nothing;

    insert into partner_links (owner_id, partner_id)
    values (v_code.owner_id, v_me) on conflict (owner_id, partner_id) do nothing;

    update pairing_codes set used_at = now() where id = v_code.id;
    delete from pairing_attempts a where a.user_id = v_me;

    return query
        select p.user_id, p.display_name from profiles p where p.user_id = v_code.owner_id;
end $$;

grant execute on function redeem_pairing_code(text) to authenticated;
grant execute on function is_partner_of(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------

alter table profiles enable row level security;
alter table pairing_codes enable row level security;
alter table partner_links enable row level security;
alter table pairing_attempts enable row level security;
alter table food_log_entries enable row level security;
alter table water_entries enable row level security;
alter table weight_entries enable row level security;
alter table day_summaries enable row level security;
alter table nudges enable row level security;

-- profiles: mine, plus the people I am linked with in either direction.
create policy profiles_select on profiles for select to authenticated
    using (user_id = auth.uid()
        or is_partner_of(user_id)
        or exists (select 1 from partner_links l where l.owner_id = auth.uid() and l.partner_id = profiles.user_id));
create policy profiles_insert on profiles for insert to authenticated
    with check (user_id = auth.uid());
create policy profiles_update on profiles for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- pairing_codes: the owner creates and discards codes; nobody reads them
-- (the RPC does, as the table owner).
create policy pairing_codes_insert on pairing_codes for insert to authenticated
    with check (owner_id = auth.uid());
create policy pairing_codes_delete on pairing_codes for delete to authenticated
    using (owner_id = auth.uid());

-- partner_links: both sides see the link and either side may end it.
create policy partner_links_select on partner_links for select to authenticated
    using (owner_id = auth.uid() or partner_id = auth.uid());
create policy partner_links_delete on partner_links for delete to authenticated
    using (owner_id = auth.uid() or partner_id = auth.uid());
-- No insert policy: links are created only by redeem_pairing_code().

-- pairing_attempts: written by the RPC only.

-- Diary, water, weight, summaries: owner writes, owner and partner read.
create policy food_log_entries_select on food_log_entries for select to authenticated
    using (owner_id = auth.uid() or is_partner_of(owner_id));
create policy food_log_entries_write on food_log_entries for all to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create policy water_entries_select on water_entries for select to authenticated
    using (owner_id = auth.uid() or is_partner_of(owner_id));
create policy water_entries_write on water_entries for all to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create policy weight_entries_select on weight_entries for select to authenticated
    using (owner_id = auth.uid() or is_partner_of(owner_id));
create policy weight_entries_write on weight_entries for all to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create policy day_summaries_select on day_summaries for select to authenticated
    using (owner_id = auth.uid() or is_partner_of(owner_id));
create policy day_summaries_write on day_summaries for all to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- nudges: a linked partner writes to the owner; the owner marks them seen.
create policy nudges_select on nudges for select to authenticated
    using (owner_id = auth.uid() or from_id = auth.uid());
create policy nudges_insert on nudges for insert to authenticated
    with check (from_id = auth.uid() and is_partner_of(owner_id));
create policy nudges_update_seen on nudges for update to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Realtime: the website subscribes to these; RLS still filters what it gets.
-- ---------------------------------------------------------------------------

alter publication supabase_realtime add table food_log_entries;
alter publication supabase_realtime add table water_entries;
alter publication supabase_realtime add table day_summaries;
alter publication supabase_realtime add table nudges;
