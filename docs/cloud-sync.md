# Cloud sync and the website

NutriCart is offline-first: Room on the phone is the primary copy, and nothing
the user does waits for the network. Cloud sync adds a **mirror** of the diary
in Supabase so the website can show it, to the user on any device and to a
partner who redeems a pairing code. This document is the design; the schema is
`supabase/migrations/0001_init.sql`.

## Parts

| Part | Where | Job |
| --- | --- | --- |
| `SyncOutboxEntity` / `SyncOutboxDao` | Room (schema v13) | Pending cloud writes, appended in the same flow as the Room write |
| `CloudMirror` | `cloud/` | The door repositories call after a write: builds the row, queues it, asks for a drain |
| `CloudRows` | `cloud/` | Entity → PostgREST row, one function per table; the column names in one place |
| `CloudSyncWorker` | WorkManager | Drains the outbox online, then publishes `day_summaries` and the profile |
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

## Not in milestone 1

- Pull: the phone never reads diary rows back. Web edits (milestone 3) will
  arrive through a watermark on `updated_at` with last-writer-wins per row.
- Email on the account: an anonymous user dies with the phone (milestone 4).
- Realtime on the phone: polling is enough while the app is closed; a live
  channel while it is open comes with the two-way sync.
