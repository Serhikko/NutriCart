# NutriCart website

NutriCart in the browser, two ways:

- **Partner view** — enter a 6-character pairing code from the phone's
  Settings and see that person's day as they log it, plus a nudge button that
  becomes a notification on their phone.
- **Own tracker** — for someone without an Android phone: the same
  questionnaire, targets, food search, barcode scanning, water, weight and
  week statistics, under `/me`. The calorie maths is a port of the phone's,
  checked against the same test vectors. A phone user opens the same account
  here by linking an email and a password on the phone and signing in with
  them under Settings; what they log here comes back to the phone.

React 19, TypeScript, Vite, Supabase, installable as a PWA. Design and
milestones: `docs/cloud-sync.md`.

```
cd web
cp .env.example .env.local   # paste the Supabase URL and anon key
npm install
npm run dev                  # http://localhost:5173
npm test                     # vitest
npm run build                # dist/
```

Deploy: import the repository in Vercel, set the root directory to `web/`,
add `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` as environment variables.
Every pull request then gets a preview URL.

The functions in `api/` read the same two variables at run time (Vercel
gives every environment variable to functions as well as to the build), or
`SUPABASE_URL` and `SUPABASE_ANON_KEY` when those are set; nothing else
needs configuring. They are compiled one file at a time, so each is
self-contained and imports nothing from `src/`.

| Function | What it does | What it sends on |
| --- | --- | --- |
| `api/off.ts` | Proxies the two Open Food Facts endpoints the site uses, with the User-Agent OFF asks for. Vercel caches a product for a day, a search for ten minutes, an unknown barcode (404) for ten minutes, and never a rate limit or an error, so a busy moment at OFF is not served as "not found" for a day. OFF's `Retry-After` is passed on. | The barcode or search text, to OFF. |
| `api/zakaz.ts` | `GET /api/zakaz?code=<8–14 digits>`: asks Ukrainian supermarkets (Auchan, Novus, METRO, EKO Market and other chains, through zakaz.ua's shop API) whether they list the code, and answers with their product cards cut down to name, brand, pack size and the four label values. Needs `Authorization: Bearer <the visitor's Supabase access token>`, checked against Supabase's `/auth/v1/user`; on a deployment without the Supabase variables the check is skipped. The store list is fetched at most once a day per warm instance (a built-in list of five stores stands in when it cannot be had); then up to six stores are asked in parallel, 6 s each, under 8 s in all. A full answer (every store asked answered) is kept by the browser for an hour (`private`); one with a store failing or timing out is not kept at all, so a store that is back gets asked again. | Only the scanned barcode, as a GTIN-14, to zakaz.ua: no token, no user, no visitor IP. |

The site asks `api/zakaz.ts` only after Open Food Facts had no usable
product, and never for Belarusian (481) or UK (500–509) numbers, which the
shops practically never list. One request per scan; nothing is stored or
crawled, and nothing from the shops goes to Open Food Facts. The dev server
has no functions, so there the shops are not asked at all.

What is where:

- `src/lib/dates.ts` — epoch-day arithmetic identical to the phone's `LocalDate.toEpochDay()`
- `src/lib/diary.ts` — row shapes and the small sums the screens need (the target itself comes from the phone)
- `src/lib/queries.ts` — the partner-side reads and writes, as React Query hooks, plus the Realtime subscription
- `src/lib/tracker.ts` — the own-tracker writes (profile, food, water, weight) and the day-summary refresh
- `src/lib/customProducts.ts` — the user's own products under their barcodes (`custom_products`, migration 0005): checked before Open Food Facts on a scan, listed first in a name search, never blocking either when the table is missing
- `src/lib/openFoodFacts.ts` — search, and the barcode lookup in the phone's order: the user's own product, then Open Food Facts (once per code form that differs by more than leading zeros; a busy OFF is asked again once after a short pause), then the Ukrainian shops. The result is found, incomplete with a prefill (from OFF or a shop), or not found with the code's GS1 origin and which sources did not answer. OFF is reached through `api/off.ts` on the deployed site (adds the User-Agent OFF asks for, caches answers, keeps VPN and corporate networks out of OFF's bot protection), directly as the fallback and on the dev server
- `src/lib/zakaz.ts` and `src/domain/zakaz.ts` — the Ukrainian shops: asking `api/zakaz.ts`, and reading a shop's card (numbers out of label text such as "9,95г", kJ converted, the four values checked against each other, allowing for the alcohol a drink's title states) into a product (`zakaz:<code>`) or a prefill; `lookupNotice` picks the not-found message so a source that did not answer is never said to lack the product
- `src/domain/` — the phone's `domain/logic` in TypeScript: calories and macros, nutrient scaling, barcode normalisation and GS1 origin (482 Ukraine, 481 Belarus), product names by language, drink detection (Latin and Cyrillic units), habits, the shops' card rules; shared test vectors with the phone
- `src/lib/session.tsx` — anonymous sign-in, kept by supabase-js
- `src/pages/` — Welcome, Day, Week, Settings (partner view); Onboarding (also the profile and manual-targets editor), MyDay, AddFood, MyWeek (own tracker)
