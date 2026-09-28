# Supabase project for NutriCart

The cloud side of the app: one Postgres schema, row-level security, one RPC
for pairing, and Realtime on four tables. Nothing here runs on the phone; the
phone talks to it over PostgREST and GoTrue (see `docs/cloud-sync.md`).

## Setup (once, about 10 minutes)

1. Create a free project at supabase.com — region **EU Central (Frankfurt)**.
2. Authentication → Providers → enable **Anonymous sign-ins**.
3. SQL Editor → run `migrations/0001_init.sql`.
4. Project Settings → API → copy the **Project URL** and the **anon public key**.
5. Put them in `local.properties` (not committed):

   ```
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=eyJ...
   ```

   The same two values go into the web app's `web/.env.local` and into the
   repository's GitHub Actions secrets for CI builds.

The anon key is safe in a client: it grants only what the policies in the
migration allow. The service-role key is never used by this project.
