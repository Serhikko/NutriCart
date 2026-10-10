# Supabase project for NutriCart

The cloud side of the app: one Postgres schema, row-level security, one RPC
for pairing, and Realtime on four tables. Nothing here runs on the phone; the
phone talks to it over PostgREST and GoTrue (see `docs/cloud-sync.md`).

## Setup (once, about 10 minutes)

1. Create a free project at supabase.com — region **EU Central (Frankfurt)**.
2. Authentication → Providers → enable **Anonymous sign-ins**.
3. SQL Editor → run `migrations/0001_init.sql`, then `0002_web_tracker.sql`,
   then `0003_two_way_sync.sql`, then `0004_fix_redeem_search_path.sql`, then
   `0005_custom_products.sql`, then `0006_fix_redeem_ambiguous_owner.sql`,
   then `0007_fix_pairing_codes_and_partner_reads.sql` (in order; a project
   that already has the earlier ones needs only the files after them, and
   each of 0004 to 0007 is safe to run again). Or let GitHub run them: see
   [Applying migrations automatically](#applying-migrations-automatically).

   **Run 0006** on an existing project: without it every right pairing code
   fails with `42702 column reference "owner_id" is ambiguous`, so nobody can
   follow anybody's day.

   **Run 0007** after it: without it the phone cannot make a pairing code at
   all (`42501 new row violates row-level security policy`, which the phone
   shows as "The cloud rejected this phone's session"), "New code" on either
   client leaves the older code working, a code made on a device whose clock
   is off can be expired before anyone types it, and a partner can read the
   diary lines the owner deleted. It also makes the limit on code redeems
   hold: five calls in ten minutes, right or wrong, including
   parallel ones, a guess made your own code first, and one held by a second
   account of your own; inserting a code's hash no longer tells anyone
   whether someone else's live code has it. And it stops nudges carrying a
   made-up sender name or times.

   **Run 0005** (`custom_products`) on an existing project too: it holds the
   products a user adds under their barcode on the website when Open Food
   Facts lacks them (often Ukrainian `482…` and Belarusian `481…` codes).
   Without it the website still scans and logs, but cannot remember an added
   product for the next scan, and says so.
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

## Applying migrations automatically

`.github/workflows/supabase-migrate.yml` applies new files in `migrations/`
to the project whenever a push to `master` or `claude/dreamy-cerf-wa8d9u`
changes them, so nobody pastes SQL by hand. It first rehearses on a
throwaway Postgres (every migration plus the database tests below, then a
database set up by hand through 0006, like this project), and only then
runs `migrate.sh` against the project. `migrate.sh` keeps what it applied in
`nutricart_migrations.applied` (a schema the API does not serve); on its
first run it checks that 0001-0006, run by hand, are really there and
records them. Each file is applied in one transaction with its record, so a
failure leaves nothing half done.

Setup, once:

1. Supabase → **Connect** → **Direct** (connection string) → method
   **Session pooler** (port 5432, user `postgres.<project ref>`). Not the
   plain direct connection: it is IPv6 only, and GitHub's runners are not.
2. Put the **database password** in place of `[YOUR-PASSWORD]`. If it is
   lost, Project Settings → Database → **Reset database password** (the apps
   and the website use the API keys, not this password). A generated
   password is letters and digits; any `%` `/` `@` `?` `#` in a password must
   be percent-encoded in the string.
3. GitHub → the repository's Settings → Secrets and variables → Actions →
   **New repository secret**, named `SUPABASE_DB_URL`. Safer still: Settings
   → Environments → `supabase-production` → add it there as an environment
   secret, limit the environment's deployment branches to the two above, and
   optionally add yourself as a required reviewer to approve each apply.
4. The next push that changes `migrations/` applies what is new. To apply
   now, open Actions → **Supabase migrations** → the latest run → **Re-run
   all jobs** (it reads the secret afresh).

The repository is public, and so are its Actions logs: `migrate.sh` never
prints the connection string or a query's rows, and in Actions a failing
statement prints only its SQLSTATE (file and line too), since even an error
message can quote a value. To see the message, run the same file in the SQL
editor, or `DATABASE_URL=... supabase/migrate.sh` from a laptop (bash 4
or later: on macOS, `brew install bash`). A failed
file leaves nothing behind and is not recorded, so the next run tries it
again. Without the secret the job only rehearses.

## Database tests

`tests/run.sh` applies a Supabase stand-in (`tests/supabase_stub.sql`: the API
roles, `auth.uid()` from the JWT claims, pgcrypto in `extensions`), every
migration in order, then `tests/*.test.sql`, to an empty Postgres database
named by the usual libpq variables, and prints one line per test passed:

```
PGHOST=localhost PGUSER=postgres PGPASSWORD=postgres PGDATABASE=scratch supabase/tests/run.sh
```

The tests act as the owner, a partner and a stranger under row-level
security and run the SQL that PostgREST runs for the clients' requests:
pairing codes made by the phone and by the website, redeeming (right, used,
expired, wrong, own code, signed out, the rate limit, a stranger inserting
hashes to find live codes), what a partner reads and sends, and removing or
unfollowing. Test 17 starts 20 `psql` sessions in the background (with the
same libpq variables) that redeem wrong codes at the same moment, so `psql`
must be on the PATH; it counts only its own database's locks, so runs on
other databases of the same server can go at the same time. CI runs them on pushes to master
and on pull requests (the `supabase` job in `.github/workflows/tests.yml`).
`--through 0005` applies only the migrations up to that one, to watch a test
catch the bug a later file fixed.
