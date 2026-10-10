-- Fix: what broke in the pairing flow once a right code got through (0006).
--
-- 1. The phone could not make a pairing code at all. It stores the code with
--    an upsert (Prefer: resolution=merge-duplicates, so PostgREST sends
--    INSERT ... ON CONFLICT (id) DO UPDATE), and naming a conflict target makes
--    Postgres check the new row against the table's SELECT policies as well.
--    pairing_codes had none, so every phone code failed with 42501 "new row
--    violates row-level security policy" (HTTP 403, which the phone reads as a
--    dead session). For the same reason the "discard my older codes" DELETE
--    that both clients send before a new code (WHERE owner_id = me) matched no
--    row and deleted nothing, silently: a superseded code kept working until
--    it expired. Adds a SELECT policy for the owner's own rows. The owner
--    sees only hashes of codes they made; nobody else sees any row, and
--    redeem_pairing_code() reads the table as its owner, as before.
--
-- 2. A code's expiry was whatever the client sent, measured by its own clock:
--    a phone or browser running 20 minutes slow made codes that had already
--    expired on the server (the partner was told the right code was wrong),
--    and a hand-made request could make one that lasts for years. A trigger
--    now sets created_at and expires_at from the server's clock on insert;
--    the clients keep sending expires_at, which is ignored, and their
--    countdowns (15 minutes on their own clock) stay right. Unused codes
--    already in the table are cut to at most 15 more minutes.
--
-- 3. The limit of five failed redeems in ten minutes did not hold: a
--    successful redeem deleted the caller's failures (so guesses between two
--    of your own codes were never counted), and parallel calls all passed
--    the count before any of them recorded a failure. redeem_pairing_code()
--    now takes a per-caller advisory lock before counting, and counts every
--    call, a successful one too: with only failures counted, a caller with a
--    second account holding each guess as its own code learnt from whether
--    the redeem linked to it whether a third account held the guess as
--    well, without a single failure. A partner redeems once or twice; five
--    calls in ten minutes is the limit. Calls older than ten minutes are
--    purged on every call. The caller's own codes no longer answer a redeem:
--    a code matching only one's own counts as a wrong code (it raised "that
--    is your own code" without counting, so inserting each guess as one's
--    own code first, then redeeming it, tested guesses without limit); the
--    website says "your own code" from the code it is showing. code_hash is
--    no longer unique (see 6): a hash held by live codes of two accounts
--    answers nobody. Otherwise it is 0006's.
--
-- 4. Nudges: the API roles could write every column. A partner could set
--    from_name to anything of any length and send a nudge already marked
--    seen, and the owner could rewrite the text or the sender of a received
--    one. Clients may now insert only owner_id, from_id, from_name and text,
--    and update only seen_at; from_name is filled from the sender's own
--    profile name on insert (the name the owner sees in their follower list),
--    and is at most 40 characters like the name itself.
--
-- 5. A partner could read the owner's deleted diary and water lines (the
--    tombstones keep the name and nutrients, and are never purged); the
--    website even downloaded them with every day it showed. A partner now
--    reads live rows only; the owner still reads their own tombstones, which
--    the phone's pull needs. A soft delete then reaches a watching partner as
--    the day_summaries change that follows it rather than as the row's own
--    update (or with the Day page's one-minute refresh).
--
-- 6. code_hash was unique across everybody's codes, so inserting a hash told
--    any signed-in user (an anonymous sign-in is enough) whether someone
--    else's live code had it, without a redeem and so outside the limit in 3:
--    a bulk insert of candidate hashes failed as a whole (23505, HTTP 409)
--    when one was taken, and with 1 the same insert with
--    ON CONFLICT (code_hash) DO NOTHING followed by a read of one's own rows
--    named the taken one in a single request. The constraint is dropped (a
--    plain index stays for redeem's lookup), so an insert says nothing about
--    other people's codes; neither client names code_hash as a conflict
--    target. Each account keeps one code: inserting one deletes the
--    account's other codes, as both clients' "New code" already does, and
--    one account's inserts run one at a time, so parallel ones cannot leave
--    two. When live codes of two accounts share a hash, by chance (about one
--    in a billion per code) or because someone copied a code they saw, a
--    redeem of it links nobody and counts as a wrong code (3): nobody is
--    linked to the wrong person, and the owner makes a new code.
--
-- Safe to run more than once.

-- ---------------------------------------------------------------------------
-- 1. pairing_codes: the owner reads their own rows
-- ---------------------------------------------------------------------------

drop policy if exists pairing_codes_select on pairing_codes;
create policy pairing_codes_select on pairing_codes for select to authenticated
    using (owner_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 2. pairing_codes: the server sets the expiry
-- ---------------------------------------------------------------------------

create or replace function pairing_codes_server_expiry() returns trigger
language plpgsql set search_path = public as $$
begin
    new.created_at := now();
    new.expires_at := now() + interval '15 minutes';
    return new;
end $$;

drop trigger if exists pairing_codes_server_expiry on pairing_codes;
create trigger pairing_codes_server_expiry before insert on pairing_codes
    for each row execute function pairing_codes_server_expiry();

-- Unused codes made before this file keep at most 15 more minutes (never
-- extended: a code with less left keeps what it has).
update pairing_codes set expires_at = now() + interval '15 minutes'
where used_at is null and expires_at > now() + interval '15 minutes';

-- ---------------------------------------------------------------------------
-- 3. redeem_pairing_code: a rate limit that holds
-- ---------------------------------------------------------------------------

-- Redeems a pairing code for the calling user. Runs as the table owner so it
-- can read every pairing code (a client reads only its own) and insert the
-- link. Returns the owner's id and display name, or no row when the code is
-- wrong, expired, used, the caller's own, or held by two accounts at once
-- (see 6). Five calls in ten minutes, right or wrong, lock the caller out;
-- calls of one caller run one at a time, so parallel guesses count too.
-- search_path includes `extensions`: on Supabase pgcrypto (digest) lives there.
create or replace function redeem_pairing_code(p_code text)
returns table (owner_id uuid, display_name text)
language plpgsql security definer set search_path = public, extensions as $$
#variable_conflict use_column
declare
    v_hash   text;
    v_code   pairing_codes%rowtype;
    v_me     uuid := auth.uid();
begin
    if v_me is null then
        raise exception 'not signed in' using errcode = '28000';
    end if;

    -- Held until the call's transaction ends: the count below then sees the
    -- caller's previous call, recorded below.
    perform pg_advisory_xact_lock(hashtext('redeem_pairing_code'), hashtext(v_me::text));

    delete from pairing_attempts a
    where a.user_id = v_me and a.attempted_at < now() - interval '10 minutes';

    if (select count(*) from pairing_attempts a
        where a.user_id = v_me and a.attempted_at > now() - interval '10 minutes') >= 5 then
        raise exception 'too many attempts, try again later' using errcode = '54000';
    end if;

    -- Every call counts, a successful one too: whether a redeem links or not
    -- would otherwise tell a caller with a second account (holding the guess
    -- as its own code) whether someone else holds it as well, for free.
    insert into pairing_attempts (user_id) values (v_me);

    v_hash := encode(digest(upper(trim(p_code)), 'sha256'), 'hex');

    -- The caller's own codes never answer: a guess that matches only one's
    -- own code is a wrong code.
    select * into v_code from pairing_codes c
    where c.code_hash = v_hash and c.used_at is null and c.expires_at > now()
      and c.owner_id <> v_me
    order by c.created_at, c.id
    limit 1
    for update;

    if not found then
        return;
    end if;

    -- Live codes of two accounts with this hash (a chance collision, or a
    -- copy of a code someone saw) answer nobody, so nobody is linked to the
    -- wrong person; to the caller it is a wrong code.
    if exists (select 1 from pairing_codes c
               where c.code_hash = v_hash and c.used_at is null and c.expires_at > now()
                 and c.owner_id <> v_me and c.owner_id <> v_code.owner_id) then
        return;
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

    return query
        select p.user_id, p.display_name from profiles p where p.user_id = v_code.owner_id;
end $$;

grant execute on function redeem_pairing_code(text) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. nudges: only the columns each side may set
-- ---------------------------------------------------------------------------

revoke insert, update on nudges from anon, authenticated;
grant insert (owner_id, from_id, from_name, text) on nudges to authenticated;
grant update (seen_at) on nudges to authenticated;

-- The sender's own profile name, read under the sender's own rights (a
-- from_id that is not the caller finds nothing, and the insert policy then
-- refuses the row anyway).
create or replace function nudges_from_name() returns trigger
language plpgsql set search_path = public as $$
begin
    new.from_name := coalesce((select p.display_name from profiles p where p.user_id = new.from_id), '');
    return new;
end $$;

drop trigger if exists nudges_from_name on nudges;
create trigger nudges_from_name before insert on nudges
    for each row execute function nudges_from_name();

update nudges set from_name = left(from_name, 40) where char_length(from_name) > 40;
alter table nudges drop constraint if exists nudges_from_name_len;
alter table nudges add constraint nudges_from_name_len check (char_length(from_name) <= 40);

-- ---------------------------------------------------------------------------
-- 5. A partner reads live diary and water rows only
-- ---------------------------------------------------------------------------

drop policy if exists food_log_entries_select on food_log_entries;
create policy food_log_entries_select on food_log_entries for select to authenticated
    using (owner_id = auth.uid() or (deleted_at is null and is_partner_of(owner_id)));

drop policy if exists water_entries_select on water_entries;
create policy water_entries_select on water_entries for select to authenticated
    using (owner_id = auth.uid() or (deleted_at is null and is_partner_of(owner_id)));

-- ---------------------------------------------------------------------------
-- 6. pairing_codes: a hash is not unique, and an account keeps one code
-- ---------------------------------------------------------------------------

-- The constraint 0001 made for `code_hash text not null unique`.
alter table pairing_codes drop constraint if exists pairing_codes_code_hash_key;
create index if not exists pairing_codes_hash_idx on pairing_codes (code_hash);

-- Deletes the inserting user's other codes. One user's inserts run one at a
-- time (a per-owner lock held until the transaction ends), so of parallel
-- inserts each sees the code the one before it committed, and an account
-- never holds two. Runs as the table owner, so the lock needs no grant to
-- the API roles; it touches only the caller's own codes, and an insert
-- naming someone else deletes nothing here and is refused by the insert
-- policy after it.
create or replace function pairing_codes_one_per_owner() returns trigger
language plpgsql security definer set search_path = public as $$
begin
    if new.owner_id is distinct from auth.uid() then
        return new;
    end if;
    perform pg_advisory_xact_lock(hashtext('pairing_codes_one_per_owner'), hashtext(new.owner_id::text));
    delete from pairing_codes c where c.owner_id = new.owner_id;
    return new;
end $$;

drop trigger if exists pairing_codes_one_per_owner on pairing_codes;
create trigger pairing_codes_one_per_owner before insert on pairing_codes
    for each row execute function pairing_codes_one_per_owner();
