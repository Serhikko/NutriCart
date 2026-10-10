-- Fix: redeem_pairing_code() failed on every code with "function digest(text,
-- unknown) does not exist". On Supabase the pgcrypto extension is installed in
-- the `extensions` schema, and the function had pinned search_path to
-- `public` alone, so the hash step could not find digest(). Re-creates the
-- function (same body) with `extensions` on the path; is_partner_of() gets the
-- same for consistency. Safe to run more than once.

create or replace function is_partner_of(p_owner uuid) returns boolean
language sql stable security definer set search_path = public, extensions as $$
    select exists (
        select 1 from partner_links
        where owner_id = p_owner and partner_id = auth.uid()
    );
$$;

-- Redeems a pairing code for the calling user. Runs as the table owner so it
-- can read pairing_codes (which no client may select) and insert the link.
-- Returns the owner's id and display name, or no row when the code is wrong,
-- expired or used. Five failures in ten minutes lock the caller out.
-- search_path includes `extensions`: on Supabase pgcrypto (digest) lives there.
create or replace function redeem_pairing_code(p_code text)
returns table (owner_id uuid, display_name text)
language plpgsql security definer set search_path = public, extensions as $$
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

