-- Milestone 2: the website as a standalone tracker. A user with no phone
-- keeps the questionnaire here and the website computes the day target with
-- the same math as the phone (web/src/domain/calories.ts).
--
-- profile_details is a SEPARATE table from profiles on purpose: profiles
-- (the display name) is readable by linked partners, these details are not.

create type activity_level_kind as enum ('SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE', 'VERY_ACTIVE');
create type goal_kind as enum ('LOSE', 'MAINTAIN', 'GAIN');

create table profile_details (
    user_id             uuid primary key references auth.users (id) on delete cascade,
    sex                 text not null check (sex in ('MALE', 'FEMALE')),
    birth_date          date not null,
    height_cm           integer not null check (height_cm between 100 and 250),
    activity_level      activity_level_kind not null,
    goal                goal_kind not null,
    target_kg_per_week  double precision not null default 0.5,
    -- Manual overrides: all four or none, like the phone's Settings section.
    custom_kcal_target  integer,
    custom_protein_g    integer,
    custom_fat_g        integer,
    custom_carbs_g      integer,
    -- Which client owns this account's numbers: the phone publishes
    -- day_summaries itself; a web-only user has the website do it.
    primary_client      text not null default 'web' check (primary_client in ('phone', 'web')),
    updated_at          timestamptz not null default now()
);

create trigger profile_details_updated_at before update on profile_details
    for each row execute function set_updated_at();

alter table profile_details enable row level security;

create policy profile_details_owner on profile_details for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());
