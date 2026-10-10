-- Fix: redeem_pairing_code() failed on every right code with "column reference
-- "owner_id" is ambiguous" (42702). The function returns a table whose first
-- column is named owner_id, and in PL/pgSQL those output columns are variables
-- too; the link insert's `on conflict (owner_id, partner_id)` names the
-- partner_links column of the same name, and PL/pgSQL refuses to guess which
-- one is meant. A wrong, used or expired code returned before that line, so
-- only a right one met the error. Re-creates the function (same body) with
-- `#variable_conflict use_column`, so a bare name in a query is the table's
-- column; the output columns are never read by name in the body. The result
-- shape (owner_id, display_name) stays as the website reads it. Safe to run
-- more than once.

-- Redeems a pairing code for the calling user. Runs as the table owner so it
-- can read pairing_codes (which no client may select) and insert the link.
-- Returns the owner's id and display name, or no row when the code is wrong,
-- expired or used. Five failures in ten minutes lock the caller out.
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
