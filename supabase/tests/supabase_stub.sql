-- A stand-in for what a Supabase project has before any NutriCart migration,
-- for the database tests (run.sh) on a plain Postgres 16, such as the
-- official postgres:16 image in CI: the API roles, auth.users, auth.uid()
-- from the request's JWT claims as PostgREST sets them, pgcrypto in the
-- `extensions` schema, the default grants Supabase gives the API roles on
-- new objects in `public`, the realtime publication and Supabase's
-- search_path. It is not Supabase: PostgREST, GoTrue and Realtime are not
-- here, so the tests call the SQL those would run.
--
-- Works in any database (the name is not assumed) and is safe to run more
-- than once; the roles are cluster-wide, so a second database reuses them.

do $r$ begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin noinherit; end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin noinherit; end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role nologin noinherit bypassrls; end if;
end $r$;

create schema if not exists auth;
create table if not exists auth.users (
    id            uuid primary key,
    email         text,
    is_anonymous  boolean not null default false,
    created_at    timestamptz not null default now()
);

-- PostgREST puts the verified JWT's claims in request.jwt.claims (older
-- versions: one setting per claim, request.jwt.claim.sub).
create or replace function auth.uid() returns uuid language sql stable as $$
    select nullif(coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'), '')::uuid
$$;
create or replace function auth.role() returns text language sql stable as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.role', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role')
$$;
grant usage on schema auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;
grant execute on function auth.role() to anon, authenticated, service_role;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
grant usage on schema extensions to anon, authenticated, service_role;

grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;

do $p$ begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        create publication supabase_realtime;
    end if;
end $p$;

-- Supabase's database search_path, for the sessions that follow.
do $d$ begin
    execute format('alter database %I set search_path = "$user", public, extensions', current_database());
end $d$;
