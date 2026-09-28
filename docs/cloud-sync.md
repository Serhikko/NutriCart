# Cloud sync and the website

NutriCart is offline-first: Room on the phone is the primary copy, and nothing
the user does waits for the network. Cloud sync adds a **mirror** of the diary
in Supabase so the website can show it, to the user on any device and to a
partner who redeems a pairing code. Since milestone 2 the website is also a
**standalone tracker** for someone without an Android phone, and since
milestone 3 the phone user's own account opens on the website too, with edits
flowing both ways. This document is the design; the schema is
`supabase/migrations/` (one file per milestone).

## Parts

| Part | Where | Job |
| --- | --- | --- |
| `SyncOutboxEntity` / `SyncOutboxDao` | Room (schema v13) | Pending cloud writes, appended in the same flow as the Room write |
| `CloudMirror` | `cloud/` | The door repositories call after a write: builds the row, queues it, asks for a drain |
| `CloudRows` | `cloud/` | Entity → PostgREST row, one function per table; the column names in one place |
| `CloudSyncWorker` | WorkManager | Drains the outbox online, pulls the website's edits, then publishes `day_summaries` and the profile |
| `CloudPull` / `PullRules` | `cloud/` | The other direction: applies rows another client wrote, by the rules in `PullRules` (pure, tested) |
| `CloudAuth` | `cloud/` | Anonymous Supabase user; refreshes the JWT; session in `SecretsDataStore` |
| `CloudRepository` | `cloud/` | What Settings does: enable, backfill, pairing codes, partners, nudges |
| `SupabaseAuthApi` / `SupabaseRestApi` | Retrofit | GoTrue and PostgREST, no SDK |

## Write path

1. A repository writes to Room (`DiaryRepository`, `WaterRepository`,
   `ProfileRepository`, `ActivityRepository` for watch weights).
2. It calls `CloudMirror`. If sync is off, that returns at once. Otherwise the
   row goes into `sync_outbox` and `CloudSyncScheduling.requestSync()` enqueues
   the worker with a 10-second delay and `REPLACE`, so a basket of five items
   becomes one request.
3. `CloudSyncWorker` (network constraint) reads the outbox oldest-first in
   batches of 200, groups by table, keeps the newest row per id, adds
   `owner_id`, and upserts with `Prefer: resolution=merge-duplicates`. Drained
   rows are deleted. A 401 refreshes the token once. Network and 5xx errors
   retry with backoff; a 400 drops that batch and records `rejected`, so one
   bad row never blocks the queue.
4. The worker then recomputes today's and yesterday's numbers with
   `WidgetDataSource.forDay` (the same math as the dashboard) and upserts
   `day_summaries`, plus the profile name.

Deletes are upserts with `deleted_at` set, so there is one row shape. Row ids
are `"<device id>:<f|w>:<Room id>"`; the device id is minted once per install.

## Enabling

`CloudRepository.enable(name)` signs in anonymously if needed, stores the
name, flips the flag, and **backfills** the last 90 days of diary, water and
weight rows into the outbox in one insert. Disabling drops pending rows and
keeps the account and its server data; a later enable re-uses the session.

## Pairing

The phone generates a 6-character code (`PairingCode`: no 0/O/1/I), stores
its SHA-256 on the server with a 15-minute expiry, and shows the plain code.
The website calls `redeem_pairing_code(code)`, a `SECURITY DEFINER` function
that checks hash, expiry and single use, rate-limits to five failures per ten
minutes, and inserts a `partner_links` row. Row-level security then lets the
partner read the owner's rows and insert `nudges`. Either side can delete the
link.

## Nudges

The website inserts a `nudges` row. The phone's `PartnerInboxWorker` (the
same 15-minute cycle as the Telegram inbox, plus one run on every app open)
reads unseen nudges, marks them seen, and shows one notification per message.

## What never leaves the phone

Heart rate, sleep, per-sample steps, the AI key, the Telegram token. The
`day_summaries` row carries only the day's totals: target, eaten, active
kcal, steps, manual workout kcal.

## Milestone 2: the website as a tracker

A user with an iPhone keeps the questionnaire on the website. The pages under
`/me` (`Onboarding`, `MyDay`, `AddFood`, `MyWeek`) are the phone's screens in
React; the maths is a line-for-line port in `web/src/domain/` (calories,
macros, nutrient scaling, habit streaks) with the phone's own test vectors,
so both clients print the same target for the same person.

- `profile_details` (migration `0002`) holds the questionnaire. It is a
  separate table from `profiles` because partners may read the display name
  but never the birth date, height or goal. Its `primary_client` says who
  publishes `day_summaries`: the phone, or, for a web-only account, the
  website after every write (`web/src/lib/tracker.ts`).
- Web writes use the same row shapes and the same soft-delete convention as
  the phone; ids are `web:<f|w>:<uuid>`, so the two clients can never collide.
- Food comes from Open Food Facts (search and barcode) through a small
  same-origin Vercel function (`web/api/off.ts`) that sets the User-Agent
  OFF asks for and caches answers; the mapping uses the phone's barcode
  normalisation (UPC-E, EAN-8, UK codes with a leading 0) and the same
  per-100 g label maths. A typed barcode works when the camera does not.
- The barcode scanner is a camera view built on `@zxing/browser`, loaded only
  when someone taps Scan.

A partner sees a web-only account exactly like a phone account: same Day and
Week pages, same nudge button (the nudge then has nowhere to land until
milestone 3's web push).

## Milestone 3: one account everywhere, two-way sync

The phone user's account is anonymous, so nothing could open it elsewhere.
Now Settings → Cloud sync → **Account email** links an address: GoTrue mails
one confirmation, and from then on the website's Settings → **Sign in with
email** sends a magic link that opens the same account there. The phone's
session is untouched; a lost phone is recovered the same way (milestone 4's
"email on the account" moved here, since the web sign-in needs it).

For that to be useful the website must know the phone user's targets, so the
worker now also publishes `profile_details` with `primary_client = 'phone'`.
The web pages under `/me` then show the phone's own numbers: the target from
the phone's `day_summaries` row (it includes the day's activity, which the
website cannot know), the eaten total from the live rows.

**Pull.** After draining the outbox, `CloudPull` asks each of the three synced
tables for rows with `updated_at` past a per-table watermark, oldest first,
500 at a time, and applies them through the DAOs (never the repositories, so
nothing is mirrored back up or announced to the Telegram partner). The rules
in `PullRules`:

- A row this install minted (`<device>:f:<id>`) is only ever *deleted* by a
  pull, when the website set its `deleted_at`; the phone stays the author.
- A row another client minted (`web:f:<uuid>`) is inserted or updated, and
  keeps that id in a new `cloudId` column (Room v14), so the phone's own later
  delete sends a tombstone with the right id.
- A row whose id is still waiting in the outbox is skipped: the phone's newer
  write is on its way up and the server copy would undo it. Last writer wins
  per row, decided by the order of upload.
- Weight has no row id (one per day and source): the server value is applied
  when it differs and nothing newer is queued for that day.

The pull runs on every drain, on app open, and every 15 minutes in the
background (WorkManager's minimum), so a lunch logged on the website is on the
phone by the time it is opened. The website's writes to a phone account touch
only `eaten_kcal` in `day_summaries` (`web/src/domain/summary.ts`); the phone
republishes the full row, activity included, on its next sync.

## Not in milestones 1–3

- Realtime on the phone: the 15-minute pull is enough while the app is
  closed; a live channel while it is open would make web edits instant.
- Meal reminders and nudges for a web-only account need Web Push (a VAPID
  key, a service-worker handler and a scheduled Edge Function).
- The meal plan, shopping list and fridge on the website.
- Purging old tombstones on the server (a nightly job).
