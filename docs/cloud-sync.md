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

The owner makes a 6-character code (`PairingCode`: no 0/O/1/I), on the phone
(Settings → Cloud sync & website → New code) or on the website (Settings →
Share your day). Either client first deletes the owner's older codes, then
inserts the new code's SHA-256 and shows the plain code; only the hash is
stored. An owner may select, insert and delete their own `pairing_codes`
rows and nobody else's (migration `0007`: the delete-before-insert needs the
owner's SELECT policy, and so did the phone's former upsert; the phone now
sends a plain insert, after publishing the owner's name so a partner who
redeems at once sees it). The server sets
the 15-minute expiry from its own clock on insert, whatever the client sent,
and keeps one code per account (an insert deletes the account's other
codes). `code_hash` is not unique (`0007` drops 0001's constraint): with it,
inserting candidate hashes told anyone which of them was someone else's
live code without a single redeem. A redeem never matches the caller's own
code (it counts as a wrong one, so a guess cannot be tested by making it
one's own code first; the website says "your own code" from the code it is
showing), and a hash held by live codes of two accounts links nobody.

The partner types the code on the website (Home → Follow someone's day;
Cyrillic look-alike letters are read as the Latin ones). It calls
`redeem_pairing_code(code)`, a `SECURITY DEFINER` function that checks hash,
expiry and single use and inserts a `partner_links` row. Five calls in ten
minutes, right or wrong, lock the caller's account out: one caller's calls
run one at a time, a success counts like a failure (so a second account
holding the guesses learns nothing for free), and the redeem is the only way
to learn whether a code is live. Row-level security then
lets the partner read the owner's profile name and live rows (not the
tombstones of deleted lines) and insert `nudges`. Either side can delete the
link. `supabase/tests/pairing.test.sql` runs this whole flow against the
schema in CI.

## Nudges

The website inserts a `nudges` row (clients may set only `owner_id`,
`from_id`, `from_name` and `text`; the server fills `from_name` from the
sender's profile name). The phone's `PartnerInboxWorker` (the same 15-minute
cycle as the Telegram inbox, plus one run when the app starts, from
`MainActivity.onCreate`; nothing polls while the app stays open) reads unseen
nudges, marks them seen (the only column an owner may change), and shows one
notification per message. Nothing else shows nudges to the owner, so an
owner without the Android app gets none; the website tells the partner so.

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
  OFF asks for and caches answers (a scanned code OFF lacks may then come
  from Ukrainian shops, below); the mapping uses the phone's barcode
  normalisation (UPC-E, EAN-8, UK codes with a leading 0) and the same
  per-100 g label maths. A typed barcode works when the camera does not.
- The barcode scanner is a camera view built on `@zxing/browser`, loaded only
  when someone taps Scan.

A partner sees a web-only account exactly like a phone account: same Day and
Week pages, same nudge button (the nudge then has nowhere to land until Web
Push, which is not in milestones 1–3; see the end of this document).

### Products added under their barcode (`custom_products`, migration `0005`)

Ukrainian (GS1 `482`) and Belarusian (GS1 `481`) products are often missing
from Open Food Facts, or there with a name only in `product_name_uk` / `_ru`
/ `_be` and without all of their nutrition. Both clients now read those name
fields (language order by the barcode's GS1 prefix, never the UI language),
read Cyrillic pack sizes ("500 мл", "0,5 л") as drinks, and turn a scan into
one of three outcomes: found, incomplete (the form opens prefilled with what
OFF knows) or not found (the form opens empty, with the barcode attached).

- On the phone the added product is a local Room product with id
  `local:barcode:<digits>`; no schema change, nothing synced.
- On the website it is a row in `custom_products`, keyed by
  `(owner_id, barcode)`, readable and writable by its owner only (no partner
  access). The site maps it to the same `local:barcode:<digits>` id. A
  barcode lookup checks it first, under every normalised form of the code;
  a name search lists matching rows before the OFF results. A project that
  has not run `0005` keeps scanning: reads treat the missing table as empty,
  and a failed save still lets the food be logged, with a note.
- A GS1 prefix tells which national office issued the number, not where the
  food was made, so the screens say "Ukrainian product", never "made in".

### Where a scanned product comes from, and what each request reveals

Open Food Facts has only a few thousand usable Ukrainian products and fewer
Belarusian ones, and a busy OFF used to look like "not found". Both
clients now run the same lookup (`BarcodeLookup.kt` on the phone,
`web/src/lib/openFoodFacts.ts` on the website), with the same test vectors
for every pure rule:

1. The user's own product under any normalised form of the code.
2. On the phone, a cached OFF row with its details, then a cached shop row
   (`zakaz:<code>`, never refreshed: the shops have no detail nutrients).
3. **Open Food Facts**, once per code form that differs by more than
   leading zeros (OFF strips them itself, and allows 15 product reads a
   minute per address). A 429 or 503 is "busy": asked again once per scan
   after `Retry-After` (1–5 s, else not at all). Busy again, or any other
   failure, ends the OFF part; the scan carries on and remembers it.
   OFF's `nutriments_estimated` (values guessed from the ingredients) only
   ever prefill the form, never make a product by themselves. On the phone
   a cached OFF row without details answers next, if there is one.
4. **Ukrainian shops** (the zakaz.ua stores API behind Auchan, Novus, METRO,
   EKO Market and other chains), only when OFF had no usable product and
   the code is not Belarusian (481) or British (500–509), which the shops
   practically never list. Up to six stores are asked in parallel for the
   one code. A card is logged as is only with a name and four label values
   that agree with each other (energy within 20 kcal + 35 % of the 4/9/4
   sum, or of that sum plus the alcohol a drink's title states, so a wine
   or a vodka passes); anything less prefills the form for the user to
   check. When a store list cannot be had, both clients ask the same five
   built-in stores.
5. A partial product (OFF's first, else a shop's): the form, prefilled. An
   OFF record with neither a name nor a core value is a bare stub and does
   not count, so a shop's partial product is used instead. The line above
   the form names the source; with all four core values filled in (a
   shop's values that don't add up, or OFF's estimates) it asks the user to
   check them against the label rather than fill in what is missing.
6. Otherwise "not found", and the message says which source did not answer
   (`lookupNotice`): a source that did not answer is never said to lack the
   product, and the message says to try again later. On both clients the
   one action is to add the product (the form opens with the barcode); a
   new scan is the retry. OFF out of reach with no shop answering either is
   still the "you are offline" message.

What leaves the device for each source:

| Request | Who receives it | What it carries |
| --- | --- | --- |
| OFF product or search (phone) | Open Food Facts | The barcode or search text, the phone's IP, the app's User-Agent |
| OFF product or search (website) | `web/api/off.ts` on Vercel, which asks OFF; directly from the browser only when that fails | The barcode or search text; OFF sees Vercel's address, or the visitor's on the direct fallback |
| Shops (phone) | zakaz.ua (`stores-api.zakaz.ua`), straight from the phone | The barcode as a GTIN-14, the phone's IP, the app's User-Agent; the store list once a day |
| Shops (website) | `web/api/zakaz.ts` on Vercel, which asks zakaz.ua | To Vercel: the barcode and the visitor's Supabase access token, which is checked with this project's Supabase (`/auth/v1/user`) and goes nowhere else. To zakaz.ua: the barcode only, from Vercel's address |

zakaz.ua's API is unofficial and undocumented. It is used for point lookups
only, one scan at a time and only after OFF came up empty, with an honest
User-Agent and nothing posing as a shop's own web page. Nothing is crawled,
no dataset is built or shipped (the phone keeps only the products its user
scanned, as it does OFF's), and nothing from the shops is sent to Open
Food Facts. A product from the shops says so where its nutrition is shown
("Source: Ukrainian shop catalogue (zakaz.ua)"), and a form prefilled from
a shop says a shop lists it, not Open Food Facts.

## Milestone 3: one account everywhere, two-way sync

The phone user's account is anonymous, so nothing could open it elsewhere.
Now Settings → Cloud sync → **Account email** links an address and a
password (typed, or made up by the app: 14 symbols without look-alikes,
`PasswordGenerator`): GoTrue mails one confirmation, and from then on the
website's Settings signs in with that email and password from any browser.
A password rather than only a magic link because the link opens in whatever
browser the mail app hands it to, not the one the person wants to use; the
confirmation link still arrives, but it only confirms the address. A
sign-in link by email stays as the fallback for a forgotten password. The
phone's session is untouched; a lost phone is recovered the same way
(milestone 4's "email on the account" moved here, since the web sign-in
needs it). The password is never stored on the phone.

For that to be useful the website must know the phone user's targets, so the
worker now also publishes `profile_details` with `primary_client = 'phone'`.
The web pages under `/me` then show the phone's own numbers: the target from
the phone's `day_summaries` row (it includes the day's activity, which the
website cannot know), the eaten total from the live rows.

**Pull.** After draining the outbox, `CloudPull` asks each of the three synced
tables for rows with `updated_at` past a per-table watermark, less a minute
(`updated_at` is the writing transaction's start, so a row that commits late
could land behind a watermark already moved past it; a row pulled twice
changes nothing), oldest first, 500 at a time; for food and water it leaves
out this install's own live rows, which only a delete can change. It applies
them through the DAOs (never the repositories, so
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
phone by the time it is opened. The website's writes to a phone account move
only `eaten_kcal` in `day_summaries` (`web/src/domain/summary.ts`; the row's
own `target_kcal` goes back unchanged, since the upsert's insert half must
satisfy `NOT NULL`); the phone republishes the full row, activity included,
on its next sync.

## Not in milestones 1–3

- Realtime on the phone: the 15-minute pull is enough while the app is
  closed; a live channel while it is open would make web edits instant.
- Meal reminders and nudges for a web-only account need Web Push (a VAPID
  key, a service-worker handler and a scheduled Edge Function).
- The meal plan, shopping list and fridge on the website.
- Purging old tombstones on the server (a nightly job).
