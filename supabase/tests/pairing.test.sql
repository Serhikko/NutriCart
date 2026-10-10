-- The "follow someone's day" flow against the real schema: pairing codes,
-- redeem_pairing_code(), what a partner may read and send, and how either
-- side ends the link. Each test is one DO block run as the user it names,
-- under row-level security, with the SQL PostgREST runs for the clients'
-- requests (supabase-js on the website, Retrofit on the phone); it raises on
-- the first thing that is not as it should be, and prints "ok N - ..." when
-- it passed. Tests 17 and 22 also start psql sessions in the background (\!),
-- which connect with the libpq variables of the environment. run.sh applies
-- supabase_stub.sql and every migration first, on an empty database.
--
-- Made-up people: an owner who shares their day, a partner who follows it,
-- a second partner, a stranger with no link, and two users who only guess.

set client_min_messages = warning;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create schema nutricart_test;
grant usage on schema nutricart_test to anon, authenticated;

-- The made-up users.
create function nutricart_test.owner() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000a1'::uuid $$;
create function nutricart_test.partner() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000b2'::uuid $$;
create function nutricart_test.partner2() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000b3'::uuid $$;
create function nutricart_test.stranger() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000c4'::uuid $$;
create function nutricart_test.guesser() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000d5'::uuid $$;
create function nutricart_test.guesser2() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000d6'::uuid $$;
create function nutricart_test.racer() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000e7'::uuid $$;

-- Acts as a signed-in user, as PostgREST does for a request with that
-- user's JWT, until the current transaction (the test's DO block) ends.
-- Null acts signed out: a request with only the anon key.
create function nutricart_test.login(p_user uuid) returns void language plpgsql as $$
begin
    if p_user is null then
        perform set_config('request.jwt.claims', '{"role":"anon"}', true);
        set local role anon;
    else
        perform set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
        set local role authenticated;
    end if;
end $$;

-- Back to the superuser (the test's own view of every row).
create function nutricart_test.logout() returns void language plpgsql as $$
begin
    perform set_config('request.jwt.claims', '', true);
    set local role none;
end $$;

-- What a client stores for a code: lower-case hex SHA-256 of the upper-cased code.
create function nutricart_test.hash(p_code text) returns text language sql immutable as $$
    select encode(extensions.digest(upper(p_code), 'sha256'), 'hex')
$$;

create function nutricart_test.check(p_ok boolean, p_what text) returns void language plpgsql as $$
begin
    if p_ok is not true then
        raise exception 'FAILED: %', p_what;
    end if;
end $$;

-- Runs p_sql and requires it to fail with SQLSTATE p_state (its effects are rolled back).
create function nutricart_test.expect_error(p_sql text, p_state text) returns void language plpgsql as $$
begin
    begin
        execute p_sql;
    exception when others then
        if sqlstate <> p_state then
            raise exception 'FAILED: expected SQLSTATE %, got % (%) from: %', p_state, sqlstate, sqlerrm, p_sql;
        end if;
        return;
    end;
    raise exception 'FAILED: expected SQLSTATE % from: %', p_state, p_sql;
end $$;

-- The website's "New code" (useNewPairingCode): discard my codes, then insert
-- the new hash with a plain insert (return=minimal). Returns how many rows
-- the discard removed. p_owner: a hand-made request naming someone else.
create function nutricart_test.web_new_code(p_code text, p_expires timestamptz default now() + interval '15 minutes', p_owner uuid default null)
returns integer language plpgsql as $$
declare
    v_deleted integer;
    v_inserted integer;
begin
    with pgrst_source as (delete from pairing_codes where owner_id = auth.uid() returning 1)
    select count(*) into v_deleted from pgrst_source;
    with pgrst_source as (
        insert into pairing_codes (code_hash, owner_id, expires_at)
        values (nutricart_test.hash(p_code), coalesce(p_owner, auth.uid()), p_expires) returning 1)
    select count(*) into v_inserted from pgrst_source;
    return v_deleted;
end $$;

-- The phone's "New code" (CloudRepository.newPairingCode): DELETE
-- ?owner_id=eq.<me>, then POST with Prefer: resolution=merge-duplicates and
-- no on_conflict, which PostgREST sends as an upsert on the primary key.
create function nutricart_test.phone_new_code(p_code text, p_owner uuid default null)
returns integer language plpgsql as $$
declare
    v_deleted integer;
    v_inserted integer;
begin
    with pgrst_source as (delete from pairing_codes where owner_id = auth.uid() returning 1)
    select count(*) into v_deleted from pgrst_source;
    with pgrst_source as (
        insert into pairing_codes (code_hash, expires_at, owner_id)
        select b.code_hash, b.expires_at, b.owner_id
        from json_to_recordset(json_build_array(json_build_object(
            'code_hash', nutricart_test.hash(p_code),
            'expires_at', now() + interval '15 minutes',
            'owner_id', coalesce(p_owner, auth.uid()))))
            as b (code_hash text, expires_at timestamptz, owner_id uuid)
        on conflict (id) do update
            set code_hash = excluded.code_hash, expires_at = excluded.expires_at, owner_id = excluded.owner_id
        returning 1)
    select count(*) into v_inserted from pgrst_source;
    return v_deleted;
end $$;

-- rpc('redeem_pairing_code', { p_code }): the owner it returns, or null for no row.
create function nutricart_test.redeem(p_code text) returns uuid language sql as $$
    select r.owner_id from redeem_pairing_code(p_code) r
$$;

-- The website's useFollowed / useMyPartners: the links with the other side's
-- name embedded (profiles!partner_links_owner_profile_fkey and
-- !partner_links_partner_profile_fkey). An embedded row RLS hides is null.
create function nutricart_test.followed_names() returns text language sql as $$
    select coalesce(string_agg(coalesce(p.display_name, '?'), ',' order by l.created_at, p.display_name), '')
    from partner_links l
    left join lateral (select display_name from profiles where profiles.user_id = l.owner_id) p on true
    where l.partner_id = auth.uid()
$$;
create function nutricart_test.partner_names() returns text language sql as $$
    select coalesce(string_agg(coalesce(p.display_name, '?'), ',' order by l.created_at, p.display_name), '')
    from partner_links l
    left join lateral (select display_name from profiles where profiles.user_id = l.partner_id) p on true
    where l.owner_id = auth.uid()
$$;

-- One of test 17's parallel callers, each its own psql session started by the
-- test: waits at the start gate the test holds closed, then redeems a wrong
-- code as the racer and keeps its transaction (and any lock the redeem took)
-- open for 0.2 s, like a slow request.
create function nutricart_test.race_call(p_i integer) returns text language plpgsql as $$
begin
    perform pg_advisory_xact_lock_shared(hashtext('nutricart_test'), 17);
    perform nutricart_test.login(nutricart_test.racer());
    begin
        perform * from redeem_pairing_code('RACE' || lpad(p_i::text, 2, '0'));
    exception when sqlstate '54000' then
        return 'locked out';
    end;
    perform pg_sleep(0.2);
    return 'wrong code';
end $$;

-- Ten minutes pass for the redeem limit: every recorded call ages past it.
-- Between tests that redeem only to set something up, so the users they
-- share stay under five calls; the tests of the limit itself use users of
-- their own.
create function nutricart_test.time_passes() returns void language sql as $$
    update pairing_attempts set attempted_at = attempted_at - interval '11 minutes'
$$;

-- What a user reads of the owner's day (the website's useDay / useWeek reads).
create function nutricart_test.visible(p_table text) returns integer language plpgsql as $$
declare
    v integer;
begin
    execute format('select count(*) from %I where owner_id = %L', p_table, nutricart_test.owner()) into v;
    return v;
end $$;

-- ---------------------------------------------------------------------------
-- The people, and the owner's day as the phone uploads it
-- ---------------------------------------------------------------------------

insert into auth.users (id, is_anonymous) values
    (nutricart_test.owner(), true),
    (nutricart_test.partner(), true),
    (nutricart_test.partner2(), true),
    (nutricart_test.stranger(), true),
    (nutricart_test.guesser(), true),
    (nutricart_test.guesser2(), true);

do $t$
begin
    perform nutricart_test.login(nutricart_test.owner());
    -- The phone's worker: profile, diary (a live row and a tombstone), water, weight, summary,
    -- each an upsert on the primary key (Prefer: resolution=merge-duplicates).
    insert into profiles (user_id, display_name) values (auth.uid(), 'Test Owner')
        on conflict (user_id) do update set display_name = excluded.display_name;
    insert into food_log_entries (id, owner_id, epoch_day, meal, name, grams, kcal, protein_g, fat_g, carbs_g, logged_at, deleted_at)
    values ('dev1:f:1', auth.uid(), 20736, 'LUNCH', 'Test soup', 300, 240, 9, 8, 30, now(), null),
           ('dev1:f:2', auth.uid(), 20736, 'SNACK', 'Test cake', 120, 480, 6, 24, 60, now(), now())
    on conflict (id) do update set name = excluded.name, kcal = excluded.kcal, deleted_at = excluded.deleted_at;
    insert into water_entries (id, owner_id, epoch_day, ml, logged_at, deleted_at)
    values ('dev1:w:1', auth.uid(), 20736, 250, now(), null),
           ('dev1:w:2', auth.uid(), 20736, 500, now(), now())
    on conflict (id) do update set ml = excluded.ml, deleted_at = excluded.deleted_at;
    insert into weight_entries (owner_id, epoch_day, source, weight_kg) values (auth.uid(), 20736, 'MANUAL', 70.5)
        on conflict (owner_id, epoch_day, source) do update set weight_kg = excluded.weight_kg;
    insert into day_summaries (owner_id, epoch_day, target_kcal, eaten_kcal, active_kcal, steps)
    values (auth.uid(), 20736, 2000, 240, 120.5, 4000)
        on conflict (owner_id, epoch_day) do update set eaten_kcal = excluded.eaten_kcal;
    insert into profile_details (user_id, sex, birth_date, height_cm, activity_level, goal, primary_client)
    values (auth.uid(), 'FEMALE', '1990-01-01', 170, 'LIGHT', 'MAINTAIN', 'phone');
end $t$;

-- ---------------------------------------------------------------------------
-- Redeeming a code
-- ---------------------------------------------------------------------------

do $t$
declare
    v_row record;
    v_n integer;
begin
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('H46MCW');

    -- The partner saves a name on Welcome (profiles upsert), then types the code in lower case with spaces.
    perform nutricart_test.login(nutricart_test.partner());
    insert into profiles (user_id, display_name) values (auth.uid(), 'Test Partner')
        on conflict (user_id) do update set display_name = excluded.display_name;
    select * into v_row from redeem_pairing_code('  h46mcw ');
    perform nutricart_test.check(v_row.owner_id = nutricart_test.owner(), 'redeem returns the owner''s id');
    perform nutricart_test.check(v_row.display_name = 'Test Owner', 'redeem returns the owner''s name');
    perform nutricart_test.check(is_partner_of(nutricart_test.owner()), 'the partner is linked to the owner');
    select count(*) into v_n from partner_links where owner_id = nutricart_test.owner() and partner_id = auth.uid() and scope = 'read';
    perform nutricart_test.check(v_n = 1, 'one read link');

    perform nutricart_test.logout();
    perform nutricart_test.check((select used_at is not null from pairing_codes where code_hash = nutricart_test.hash('H46MCW')), 'the code is marked used');
end $t$;
select 'ok 1 - a right code (lower case, padded) links the accounts and returns the owner';

do $t$
begin
    -- A partner with no profile yet: the RPC's safety net names them.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('K7MQ2X');
    perform nutricart_test.login(nutricart_test.partner2());
    perform nutricart_test.check(nutricart_test.redeem('K7MQ2X') = nutricart_test.owner(), 'the second partner links');
    perform nutricart_test.check((select display_name from profiles where user_id = auth.uid()) = 'Partner', 'a partner without a name gets the placeholder');
end $t$;
select 'ok 2 - a partner who saved no name is linked with a placeholder profile';

do $t$
begin
    perform nutricart_test.logout();
    perform nutricart_test.check((select used_at is not null from pairing_codes where code_hash = nutricart_test.hash('K7MQ2X')), 'test 2''s code is still there, used');
    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.check(nutricart_test.redeem('K7MQ2X') is null, 'a used code returns no row');
    perform nutricart_test.check(nutricart_test.redeem('ZZZZZZ') is null, 'a wrong code returns no row');

    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('EXP234');
    perform nutricart_test.logout();
    update pairing_codes set expires_at = now() - interval '1 minute' where code_hash = nutricart_test.hash('EXP234');
    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.check(nutricart_test.redeem('EXP234') is null, 'an expired code returns no row');
    perform nutricart_test.check(not is_partner_of(nutricart_test.owner()), 'the stranger is not linked');
end $t$;
select 'ok 3 - a used, wrong or expired code returns no row and links nobody';

do $t$
declare
    v_before integer;
begin
    select count(*) into v_before from pairing_attempts where user_id = nutricart_test.owner();
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('OWN234');
    perform nutricart_test.check(nutricart_test.redeem('OWN234') is null, 'your own code returns no row');
    perform nutricart_test.check((select used_at is null from pairing_codes where owner_id = auth.uid()), 'your own code stays unused');
    perform nutricart_test.logout();
    perform nutricart_test.check((select count(*) from pairing_attempts where user_id = nutricart_test.owner()) = v_before + 1, 'and counts as a wrong code');
end $t$;
select 'ok 4 - your own code answers nobody, and counts as a wrong code';

do $t$
begin
    perform nutricart_test.login(null);
    perform nutricart_test.expect_error($$select * from redeem_pairing_code('OWN234')$$, '28000');
end $t$;
select 'ok 5 - redeeming signed out raises 28000';

do $t$
begin
    perform nutricart_test.login(nutricart_test.guesser());
    for i in 1..5 loop
        perform nutricart_test.check(nutricart_test.redeem('WRONG' || i) is null, 'a wrong guess returns no row');
    end loop;
    perform nutricart_test.expect_error($$select * from redeem_pairing_code('WRONG6')$$, '54000');
end $t$;
select 'ok 6 - the 6th wrong code in ten minutes raises 54000';

do $t$
declare
    v_link uuid;
begin
    -- The partner, already linked, redeems a fresh code of the same owner.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('AGN234');
    perform nutricart_test.login(nutricart_test.partner());
    select id into v_link from partner_links where partner_id = auth.uid();
    perform nutricart_test.check(nutricart_test.redeem('AGN234') = nutricart_test.owner(), 'the second redeem returns the owner');
    perform nutricart_test.check((select count(*) from partner_links where partner_id = auth.uid()) = 1, 'still one link');
    perform nutricart_test.check((select id from partner_links where partner_id = auth.uid()) = v_link, 'the same link');
end $t$;
select 'ok 7 - a second code redeemed by a linked partner does not duplicate the link';

-- ---------------------------------------------------------------------------
-- What each side reads once linked
-- ---------------------------------------------------------------------------

do $t$
begin
    perform nutricart_test.login(nutricart_test.partner());
    perform nutricart_test.check((select display_name from profiles where user_id = nutricart_test.owner()) = 'Test Owner', 'the partner reads the owner''s name');
    perform nutricart_test.check(nutricart_test.visible('food_log_entries') >= 1, 'the partner reads the owner''s diary');
    perform nutricart_test.check(nutricart_test.visible('water_entries') >= 1, 'the partner reads the owner''s water');
    perform nutricart_test.check(nutricart_test.visible('weight_entries') = 1, 'the partner reads the owner''s weight');
    perform nutricart_test.check(nutricart_test.visible('day_summaries') = 1, 'the partner reads the owner''s day summary');
    perform nutricart_test.check((select count(*) from profile_details) = 0, 'the partner never reads the questionnaire');
    perform nutricart_test.check((select count(*) from pairing_codes) = 0, 'the partner reads no pairing code');

    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.check((select count(*) from profiles where user_id = nutricart_test.owner()) = 0, 'a stranger reads no name');
    perform nutricart_test.check(nutricart_test.visible('food_log_entries') = 0, 'a stranger reads no diary');
    perform nutricart_test.check(nutricart_test.visible('water_entries') = 0, 'a stranger reads no water');
    perform nutricart_test.check(nutricart_test.visible('weight_entries') = 0, 'a stranger reads no weight');
    perform nutricart_test.check(nutricart_test.visible('day_summaries') = 0, 'a stranger reads no summary');
    perform nutricart_test.check((select count(*) from partner_links) = 0, 'a stranger reads no link');
end $t$;
select 'ok 8 - the partner reads the owner''s name, diary, water, weight and summaries; a stranger reads none';

do $t$
begin
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.check(nutricart_test.partner_names() = 'Test Partner,Partner', 'the owner lists both partners by name, got ' || nutricart_test.partner_names());
    perform nutricart_test.login(nutricart_test.partner());
    perform nutricart_test.check(nutricart_test.followed_names() = 'Test Owner', 'the partner lists the owner by name, got ' || nutricart_test.followed_names());
end $t$;
select 'ok 9 - the owner lists the partners'' names and the partner the owner''s, as the embeds read them';

-- ---------------------------------------------------------------------------
-- Nudges
-- ---------------------------------------------------------------------------

do $t$
declare
    v_name text;
begin
    -- The website's useSendNudge: four columns, return=minimal.
    perform nutricart_test.login(nutricart_test.partner());
    insert into nudges (owner_id, from_id, from_name, text) values (nutricart_test.owner(), auth.uid(), 'Test Partner', 'Time to eat');
    perform nutricart_test.check((select count(*) from nudges where owner_id = nutricart_test.owner() and from_id = auth.uid()) = 1, 'the partner sees their sent nudge');

    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.expect_error(format($$insert into nudges (owner_id, from_id, from_name, text) values (%L, auth.uid(), 'x', 'hi')$$, nutricart_test.owner()), '42501');

    -- The phone: GET unseen, then PATCH seen_at.
    perform nutricart_test.login(nutricart_test.owner());
    select from_name into v_name from nudges where owner_id = auth.uid() and seen_at is null order by created_at;
    perform nutricart_test.check(v_name = 'Test Partner', 'the owner reads the unseen nudge with its sender''s name');
    update nudges set seen_at = now() where owner_id = auth.uid() and seen_at is null;
    perform nutricart_test.check((select count(*) from nudges where owner_id = auth.uid() and seen_at is null) = 0, 'marked seen');

    perform nutricart_test.login(nutricart_test.partner());
    perform nutricart_test.check((select seen_at is not null from nudges where from_id = auth.uid()), 'the partner sees it was seen');
end $t$;
select 'ok 10 - a partner sends a nudge, a stranger cannot, and the owner marks it seen';

-- ---------------------------------------------------------------------------
-- Ending the link
-- ---------------------------------------------------------------------------

do $t$
declare
    v_n integer;
begin
    perform nutricart_test.login(nutricart_test.stranger());
    delete from partner_links;
    get diagnostics v_n = row_count;
    perform nutricart_test.check(v_n = 0, 'a stranger removes no link');

    -- The partner stops following (useUnfollow: DELETE partner_links?id=eq.<link>).
    perform nutricart_test.login(nutricart_test.partner2());
    delete from partner_links where id = (select id from partner_links where partner_id = auth.uid());
    get diagnostics v_n = row_count;
    perform nutricart_test.check(v_n = 1, 'the partner unfollows');
    perform nutricart_test.check(not is_partner_of(nutricart_test.owner()), 'no longer a partner');
    perform nutricart_test.check(nutricart_test.visible('food_log_entries') = 0, 'reads no diary after unfollowing');
    perform nutricart_test.check(nutricart_test.followed_names() = '', 'follows nobody');
end $t$;
select 'ok 11 - the partner unfollows and reads nothing more; a stranger ends no link';

do $t$ begin perform nutricart_test.time_passes(); end $t$;

-- ---------------------------------------------------------------------------
-- Regressions fixed in 0007_fix_pairing_codes_and_partner_reads.sql
-- ---------------------------------------------------------------------------

do $t$
declare
    v_deleted integer;
begin
    perform nutricart_test.login(nutricart_test.owner());
    v_deleted := nutricart_test.phone_new_code('PHN567');
    perform nutricart_test.check(v_deleted >= 1, 'the phone''s "New code" discards the older codes, deleted ' || v_deleted);
    perform nutricart_test.check((select count(*) from pairing_codes) = 1, 'the owner keeps exactly one code');
    perform nutricart_test.expect_error(format($$select nutricart_test.phone_new_code('FORGE2', %L)$$, nutricart_test.stranger()), '42501');
    perform nutricart_test.phone_new_code('PHN234');
    perform nutricart_test.login(nutricart_test.partner2());
    perform nutricart_test.check(nutricart_test.redeem('phn234') = nutricart_test.owner(), 'a phone-made code redeems');
end $t$;
select 'ok 12 - the phone makes a code with its upsert (ON CONFLICT (id)), and not one for someone else';

do $t$ begin perform nutricart_test.time_passes(); end $t$;

do $t$
declare
    v_deleted integer;
begin
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('OLD234');
    v_deleted := nutricart_test.web_new_code('NEW234');
    perform nutricart_test.check(v_deleted = 1, '"New code" deletes the older code, deleted ' || v_deleted);

    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.check((select count(*) from pairing_codes) = 0, 'a stranger reads no code');
    -- The website's plain insert, hand-edited to name the owner: the insert policy refuses it.
    perform nutricart_test.expect_error(format($$select nutricart_test.web_new_code('FORGE3', now() + interval '15 minutes', %L)$$, nutricart_test.owner()), '42501');
    delete from pairing_codes where owner_id = nutricart_test.owner();
    perform nutricart_test.logout();
    perform nutricart_test.check((select count(*) from pairing_codes where owner_id = nutricart_test.owner()) = 1, 'a stranger deletes none of the owner''s codes');

    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.check(nutricart_test.redeem('OLD234') is null, 'the discarded code no longer works');
    perform nutricart_test.check(nutricart_test.redeem('NEW234') = nutricart_test.owner(), 'the new one does');
    delete from partner_links where partner_id = auth.uid();
end $t$;
select 'ok 13 - "New code" really discards the older code, and nobody makes a code for someone else';

do $t$ begin perform nutricart_test.time_passes(); end $t$;

do $t$
declare
    v_left interval;
begin
    -- A browser 20 minutes slow sends an expiry already past on the server's clock.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('SLW234', now() - interval '5 minutes');
    perform nutricart_test.logout();
    select expires_at - now() into v_left from pairing_codes where code_hash = nutricart_test.hash('SLW234');
    perform nutricart_test.check(v_left between interval '14 minutes' and interval '15 minutes', 'the server sets 15 minutes, got ' || v_left);
    perform nutricart_test.login(nutricart_test.stranger());
    perform nutricart_test.check(nutricart_test.redeem('SLW234') = nutricart_test.owner(), 'the slow clock''s code redeems');
    delete from partner_links where partner_id = auth.uid();

    -- A hand-made request asks for a code that lasts for years.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('LNG234', '2099-01-01');
    perform nutricart_test.logout();
    select expires_at - now() into v_left from pairing_codes where code_hash = nutricart_test.hash('LNG234');
    perform nutricart_test.check(v_left between interval '14 minutes' and interval '15 minutes', 'a far expiry is cut to 15 minutes, got ' || v_left);
end $t$;
select 'ok 14 - the server, not the client''s clock, sets the code''s 15 minutes';

do $t$
begin
    -- Four wrong guesses, then a right code: the failures still count, and
    -- so does the success, the fifth call.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('RGT234');
    perform nutricart_test.login(nutricart_test.guesser2());
    for i in 1..4 loop
        perform nutricart_test.redeem('BAD' || i || 'XY');
    end loop;
    perform nutricart_test.check(nutricart_test.redeem('RGT234') = nutricart_test.owner(), 'the right code still works');
    perform nutricart_test.expect_error($$select * from redeem_pairing_code('BAD5XY')$$, '54000');
end $t$;
select 'ok 15 - a successful redeem counts toward the limit and wipes nothing before it';

do $t$
begin
    -- The guesser's failures are older than ten minutes now: they no longer count and are purged.
    update pairing_attempts set attempted_at = attempted_at - interval '11 minutes' where user_id = nutricart_test.guesser();
    perform nutricart_test.login(nutricart_test.guesser());
    perform nutricart_test.check(nutricart_test.redeem('WRONG7') is null, 'a guess after ten minutes is allowed again');
    perform nutricart_test.logout();
    perform nutricart_test.check((select count(*) from pairing_attempts where user_id = nutricart_test.guesser()) = 1, 'old failures are purged');
end $t$;
select 'ok 16 - failures older than ten minutes stop counting and are purged';

do $t$
begin
    -- The call holds an exclusive lock on its caller until commit.
    perform nutricart_test.login(nutricart_test.guesser());
    perform nutricart_test.redeem('WRONG8');
    perform nutricart_test.logout();
    perform nutricart_test.check(exists (
        select 1 from pg_locks
        where locktype = 'advisory' and pid = pg_backend_pid() and granted and mode = 'ExclusiveLock'
          and classid = hashtext('redeem_pairing_code')::oid
          and objid = hashtext(nutricart_test.guesser()::text)::oid and objsubid = 2), 'redeem holds the caller''s exclusive advisory lock');
end $t$;

-- 20 wrong codes from one user at the same moment, each in its own session
-- (psql in the background, with this one's connection settings): the gate
-- (an advisory lock this session holds) keeps them waiting until all 20 are
-- connected, then lets them go together. Exactly 5 may be recorded. pg_locks
-- lists every database's locks: the waits count only this database's, so
-- runs on other databases of the same server do not disturb this one.
insert into auth.users (id, is_anonymous) values (nutricart_test.racer(), true);
do $t$ begin perform pg_advisory_lock(hashtext('nutricart_test'), 17); end $t$;
\! for i in $(seq 1 20); do psql -X -q -At -c "select nutricart_test.race_call($i)" >/dev/null & done
do $t$
declare
    v_n integer;
    v_deadline timestamptz := clock_timestamp() + interval '60 seconds';
begin
    loop
        select count(*) into v_n from pg_locks
        where locktype = 'advisory' and not granted
          and database = (select oid from pg_database where datname = current_database())
          and classid = hashtext('nutricart_test')::oid and objid = 17 and objsubid = 2;
        exit when v_n = 20;
        if clock_timestamp() > v_deadline then
            perform pg_advisory_unlock(hashtext('nutricart_test'), 17);
            raise exception 'FAILED: only % of 20 parallel callers reached the gate in 60 s (psql errors, if any, are above)', v_n;
        end if;
        perform pg_sleep(0.05);
    end loop;
    perform pg_advisory_unlock(hashtext('nutricart_test'), 17);

    -- Each caller holds the gate (shared) until its transaction ends.
    loop
        exit when not exists (select 1 from pg_locks
            where locktype = 'advisory'
              and database = (select oid from pg_database where datname = current_database())
              and classid = hashtext('nutricart_test')::oid and objid = 17 and objsubid = 2);
        if clock_timestamp() > v_deadline then
            raise exception 'FAILED: the parallel callers did not finish in 60 s';
        end if;
        perform pg_sleep(0.05);
    end loop;

    select count(*) into v_n from pairing_attempts where user_id = nutricart_test.racer();
    perform nutricart_test.check(v_n = 5, '20 parallel wrong codes record exactly 5 failures, recorded ' || v_n);
end $t$;
select 'ok 17 - one user''s redeem calls run one at a time: 20 parallel wrong codes record 5 failures';

do $t$
begin
    perform nutricart_test.login(nutricart_test.partner());
    -- Pre-seen, back-dated or posing as someone else: refused, or the name comes from the profile.
    perform nutricart_test.expect_error(format($$insert into nudges (owner_id, from_id, from_name, text, seen_at) values (%L, auth.uid(), 'x', 'hidden', now())$$, nutricart_test.owner()), '42501');
    perform nutricart_test.expect_error(format($$insert into nudges (owner_id, from_id, from_name, text, created_at) values (%L, auth.uid(), 'x', 'later', now() + interval '1 year')$$, nutricart_test.owner()), '42501');
    insert into nudges (owner_id, from_id, from_name, text) values (nutricart_test.owner(), auth.uid(), repeat('Someone else ', 400), 'Drink water');
    perform nutricart_test.check((select from_name from nudges where text = 'Drink water') = 'Test Partner', 'from_name is the sender''s profile name');

    -- The owner may mark it seen, nothing else.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.expect_error($$update nudges set text = 'rewritten' where text = 'Drink water'$$, '42501');
    perform nutricart_test.expect_error(format($$update nudges set from_id = %L where text = 'Drink water'$$, nutricart_test.stranger()), '42501');
    update nudges set seen_at = now() where text = 'Drink water';
    perform nutricart_test.check((select seen_at is not null from nudges where text = 'Drink water'), 'the owner marks it seen');

    perform nutricart_test.logout();
    perform nutricart_test.expect_error($$update nudges set from_name = repeat('x', 41)$$, '23514');
end $t$;
select 'ok 18 - a nudge''s sender, name and times are not the client''s to set, and the owner only marks it seen';

do $t$
declare
    v_n integer;
begin
    perform nutricart_test.login(nutricart_test.partner());
    perform nutricart_test.check((select count(*) from food_log_entries where owner_id = nutricart_test.owner() and epoch_day = 20736) = 1, 'the partner reads only the live diary row');
    perform nutricart_test.check((select count(*) from food_log_entries where owner_id = nutricart_test.owner() and deleted_at is not null) = 0, 'no deleted diary row');
    perform nutricart_test.check((select count(*) from water_entries where owner_id = nutricart_test.owner() and deleted_at is not null) = 0, 'no deleted water row');

    -- The owner: the phone's pull still gets the tombstones, and both ways of deleting still work.
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.check((select count(*) from food_log_entries where owner_id = auth.uid() and updated_at > '1970-01-01' and deleted_at is not null) = 1, 'the owner pulls their tombstones');
    with pgrst_source as (
        update food_log_entries set deleted_at = now() where id = 'dev1:f:1' returning food_log_entries.*)
    select count(*) into v_n from pgrst_source;
    perform nutricart_test.check(v_n = 1, 'the website soft-deletes with return=representation');
    insert into water_entries (id, owner_id, epoch_day, ml, logged_at, deleted_at) values ('dev1:w:1', auth.uid(), 20736, 250, now(), now())
        on conflict (id) do update set deleted_at = excluded.deleted_at;
    perform nutricart_test.check((select count(*) from water_entries where owner_id = auth.uid() and deleted_at is null) = 0, 'the phone''s tombstone upsert lands');

    perform nutricart_test.login(nutricart_test.partner());
    perform nutricart_test.check(nutricart_test.visible('food_log_entries') = 0 and nutricart_test.visible('water_entries') = 0, 'deleted rows disappear for the partner');
end $t$;
select 'ok 19 - a partner never reads the owner''s deleted diary or water lines';

do $t$
declare
    v_n integer;
begin
    -- The owner removes the partner (useRemovePartner / the phone's Remove).
    perform nutricart_test.login(nutricart_test.owner());
    delete from partner_links where partner_id = nutricart_test.partner();
    get diagnostics v_n = row_count;
    perform nutricart_test.check(v_n = 1, 'the owner removes the partner');

    perform nutricart_test.login(nutricart_test.partner());
    perform nutricart_test.check(not is_partner_of(nutricart_test.owner()), 'no longer a partner');
    perform nutricart_test.check(nutricart_test.visible('day_summaries') + nutricart_test.visible('weight_entries') = 0, 'reads nothing more');
    perform nutricart_test.check(nutricart_test.followed_names() = '', 'follows nobody');
    perform nutricart_test.expect_error(format($$insert into nudges (owner_id, from_id, from_name, text) values (%L, auth.uid(), '', 'hi')$$, nutricart_test.owner()), '42501');
end $t$;
select 'ok 20 - the owner removes a partner, who then reads and sends nothing';

-- A stranger who inserts hashes to find out which codes are live learns
-- nothing: code_hash is not unique, so the insert succeeds whatever other
-- people's codes are, and the stranger reads back only their own one code.
-- Separate transactions, as separate requests are: the owner's code is the
-- older one, and the oldest live code with a hash wins a redeem.
do $t$
begin
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('DUP234');
end $t$;

do $t$
declare
    v_n integer;
begin
    perform nutricart_test.login(nutricart_test.stranger());
    -- POST ?on_conflict=code_hash with Prefer: resolution=ignore-duplicates.
    perform nutricart_test.expect_error(format($$insert into pairing_codes (code_hash, owner_id, expires_at) values (%L, auth.uid(), now()) on conflict (code_hash) do nothing$$, nutricart_test.hash('DUP234')), '42P10');
    -- A plain bulk insert of candidates, one of them the owner's live code.
    insert into pairing_codes (code_hash, owner_id, expires_at)
    select nutricart_test.hash(c), auth.uid(), now() from unnest(array['DUP233', 'DUP234', 'DUP235']) c;
    get diagnostics v_n = row_count;
    perform nutricart_test.check(v_n = 3, 'the stranger''s insert of a taken hash succeeds, inserted ' || v_n);
    perform nutricart_test.check((select count(*) from pairing_codes) = 1, 'the stranger keeps one code and reads only it');
    -- The owner's hash alone, back-dated by hand to look like the oldest code.
    insert into pairing_codes (code_hash, owner_id, expires_at, created_at) values (nutricart_test.hash('DUP234'), auth.uid(), now(), '2000-01-01');
    get diagnostics v_n = row_count;
    perform nutricart_test.check(v_n = 1, 'the stranger''s insert of the owner''s hash succeeds');
    perform nutricart_test.check((select count(*) from pairing_codes where owner_id = auth.uid() and code_hash = nutricart_test.hash('DUP234')) = 1
        and (select count(*) from pairing_codes) = 1, 'the stranger reads their own row and no other');
end $t$;

do $t$
declare
    v_links integer;
begin
    perform nutricart_test.logout();
    perform nutricart_test.check((select created_at > now() - interval '1 minute' from pairing_codes where owner_id = nutricart_test.stranger()),
        'the server, not the client, sets created_at: the back-dated row is not from 2000');
    select count(*) into v_links from partner_links;
    perform nutricart_test.login(nutricart_test.partner2());
    perform nutricart_test.check(nutricart_test.redeem('DUP234') is null, 'a code two accounts hold answers nobody');
    perform nutricart_test.logout();
    perform nutricart_test.check((select count(*) from partner_links) = v_links, 'nobody is linked');
    perform nutricart_test.check((select count(*) from pairing_codes where code_hash = nutricart_test.hash('DUP234') and used_at is null) = 2, 'both copies stay unused');
    perform nutricart_test.check((select count(*) from pairing_attempts where user_id = nutricart_test.partner2() and attempted_at > now() - interval '1 minute') = 1, 'it counts');
end $t$;
select 'ok 21 - inserting a hash tells nobody whether someone else''s code has it, and a hash two accounts hold links nobody';

-- One of test 22's parallel callers, each its own psql session: waits at the
-- gate, then makes a code as the code racer the way the phone does (a plain
-- insert, no delete first) and keeps its transaction open for 0.2 s.
create function nutricart_test.code_race_call(p_i integer) returns text language plpgsql as $$
begin
    perform pg_advisory_xact_lock_shared(hashtext('nutricart_test'), 22);
    perform nutricart_test.login(nutricart_test.code_racer());
    insert into pairing_codes (code_hash, owner_id, expires_at)
    values (nutricart_test.hash('PAR' || lpad(p_i::text, 3, '0')), auth.uid(), now() + interval '15 minutes');
    perform pg_sleep(0.2);
    return 'inserted';
end $$;
create function nutricart_test.code_racer() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000f8'::uuid $$;
create function nutricart_test.prober() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000f9'::uuid $$;
create function nutricart_test.helper() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000fa'::uuid $$;
create function nutricart_test.asker() returns uuid language sql immutable as $$ select '00000000-0000-4000-8000-0000000000fb'::uuid $$;

-- 10 codes made by one user at the same moment, each in its own session,
-- released together by the same kind of gate as test 17's: one account's
-- inserts take turns, so exactly one code is left.
insert into auth.users (id, is_anonymous) values (nutricart_test.code_racer(), true);
do $t$ begin perform pg_advisory_lock(hashtext('nutricart_test'), 22); end $t$;
\! for i in $(seq 1 10); do psql -X -q -At -c "select nutricart_test.code_race_call($i)" >/dev/null & done
do $t$
declare
    v_n integer;
    v_deadline timestamptz := clock_timestamp() + interval '60 seconds';
begin
    loop
        select count(*) into v_n from pg_locks
        where locktype = 'advisory' and not granted
          and database = (select oid from pg_database where datname = current_database())
          and classid = hashtext('nutricart_test')::oid and objid = 22 and objsubid = 2;
        exit when v_n = 10;
        if clock_timestamp() > v_deadline then
            perform pg_advisory_unlock(hashtext('nutricart_test'), 22);
            raise exception 'FAILED: only % of 10 parallel callers reached the gate in 60 s (psql errors, if any, are above)', v_n;
        end if;
        perform pg_sleep(0.05);
    end loop;
    perform pg_advisory_unlock(hashtext('nutricart_test'), 22);

    loop
        exit when not exists (select 1 from pg_locks
            where locktype = 'advisory'
              and database = (select oid from pg_database where datname = current_database())
              and classid = hashtext('nutricart_test')::oid and objid = 22 and objsubid = 2);
        if clock_timestamp() > v_deadline then
            raise exception 'FAILED: the parallel callers did not finish in 60 s';
        end if;
        perform pg_sleep(0.05);
    end loop;

    select count(*) into v_n from pairing_codes where owner_id = nutricart_test.code_racer();
    perform nutricart_test.check(v_n = 1, '10 parallel codes of one account leave exactly 1, left ' || v_n);
end $t$;
select 'ok 22 - one account''s codes made at the same moment still leave one code';

-- Guessing by first inserting each guess as one's own code, then redeeming
-- it: before, a guess matching only the guesser's own code raised "your own
-- code" without counting, so the limit never held and a guesser could try
-- codes until one was someone else's. Now the own code answers nothing and
-- counts, and the 6th try in ten minutes is refused, the right code too.
insert into auth.users (id, is_anonymous) values (nutricart_test.prober(), true);
do $t$
begin
    perform nutricart_test.login(nutricart_test.owner());
    perform nutricart_test.web_new_code('VIC234');
end $t$;

do $t$
declare
    v_guess text;
begin
    perform nutricart_test.login(nutricart_test.prober());
    foreach v_guess in array array['PRB222', 'PRB223', 'PRB224', 'PRB225', 'PRB226'] loop
        perform nutricart_test.web_new_code(v_guess);
        perform nutricart_test.check(nutricart_test.redeem(v_guess) is null, 'a guess only the prober holds answers nothing: ' || v_guess);
    end loop;
    -- Locked out now, even with the owner's right code held as their own too.
    perform nutricart_test.web_new_code('VIC234');
    perform nutricart_test.expect_error($$select * from redeem_pairing_code('VIC234')$$, '54000');
    perform nutricart_test.check(nutricart_test.visible('food_log_entries') = 0, 'the prober reads none of the owner''s diary');
    perform nutricart_test.logout();
    perform nutricart_test.check((select count(*) from pairing_attempts where user_id = nutricart_test.prober()) = 5, 'every try counted');
    perform nutricart_test.check((select count(*) from partner_links where partner_id = nutricart_test.prober()) = 0, 'the prober is linked to nobody');
    perform nutricart_test.check((select used_at is null from pairing_codes where owner_id = nutricart_test.owner()), 'the owner''s code stays unused');
end $t$;
select 'ok 23 - guessing through one''s own codes is counted and refused like any guessing';

-- Guessing with a second account: the helper holds each guess as its own
-- code, and the asker redeems it. A link to the helper meant nobody else
-- held the guess; only a guess someone else held failed. Every call counts
-- now, the ones that link too, so the 6th call in ten minutes is refused.
insert into auth.users (id, is_anonymous) values (nutricart_test.helper(), true), (nutricart_test.asker(), true);
do $t$
declare
    v_guess text;
begin
    foreach v_guess in array array['HLP222', 'HLP223', 'HLP224', 'HLP225', 'HLP226'] loop
        perform nutricart_test.login(nutricart_test.helper());
        perform nutricart_test.web_new_code(v_guess);
        perform nutricart_test.login(nutricart_test.asker());
        perform nutricart_test.check(nutricart_test.redeem(v_guess) = nutricart_test.helper(), 'the asker links to the helper: ' || v_guess);
    end loop;
    perform nutricart_test.login(nutricart_test.helper());
    perform nutricart_test.web_new_code('HLP227');
    perform nutricart_test.login(nutricart_test.asker());
    perform nutricart_test.expect_error($$select * from redeem_pairing_code('HLP227')$$, '54000');
    perform nutricart_test.logout();
    perform nutricart_test.check((select count(*) from pairing_attempts where user_id = nutricart_test.asker()) = 5, 'five calls counted, all of them links');
end $t$;
select 'ok 24 - redeems that link count too, so a second account holding the guesses tests no more of them';
