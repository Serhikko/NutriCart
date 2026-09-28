# Supabase project for NutriCart

The cloud side of the app: one Postgres schema, row-level security, one RPC
for pairing, and Realtime on four tables. Nothing here runs on the phone; the
phone talks to it over PostgREST and GoTrue (see `docs/cloud-sync.md`).

## Setup (once, about 10 minutes)

1. Create a free project at supabase.com — region **EU Central (Frankfurt)**.
2. Authentication → Providers → enable **Anonymous sign-ins**.
3. SQL Editor → run `migrations/0001_init.sql`, then `0002_web_tracker.sql`,
   then `0003_two_way_sync.sql`, then `0004_fix_redeem_search_path.sql` (in
   order; a project set up before 0004 existed needs only that last file).
4. Authentication → Providers → enable **Email** (email and password, with
   a sign-in link as the fallback; the default 6-character minimum is fine,
   the apps require 8). Authentication → URL Configuration → set the **Site URL** to
   the website (the Vercel URL) and add it to the redirect list, so the
   confirmation and sign-in emails land back on the site.
5. Project Settings → API → copy the **Project URL** and the **anon public key**.
6. Put them in `local.properties` (not committed):

   ```
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=eyJ...
   ```

   The same two values go into the web app's `web/.env.local` and into the
   repository's GitHub Actions secrets for CI builds.

The anon key is safe in a client: it grants only what the policies in the
migration allow. The service-role key is never used by this project.
