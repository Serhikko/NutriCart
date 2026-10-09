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

What is where:

- `src/lib/dates.ts` — epoch-day arithmetic identical to the phone's `LocalDate.toEpochDay()`
- `src/lib/diary.ts` — row shapes and the small sums the screens need (the target itself comes from the phone)
- `src/lib/queries.ts` — the partner-side reads and writes, as React Query hooks, plus the Realtime subscription
- `src/lib/tracker.ts` — the own-tracker writes (profile, food, water, weight) and the day-summary refresh
- `src/lib/customProducts.ts` — the user's own products under their barcodes (`custom_products`, migration 0005): checked before Open Food Facts on a scan, listed first in a name search, never blocking either when the table is missing
- `src/lib/openFoodFacts.ts` — search and barcode lookup against Open Food Facts (found, incomplete with a prefill, or not found with the code's GS1 origin), through `api/off.ts` on the deployed site (a Vercel function that adds the User-Agent OFF asks for, caches answers and keeps VPN and corporate networks out of OFF's bot protection); the dev server calls OFF directly
- `src/domain/` — the phone's `domain/logic` in TypeScript: calories and macros, nutrient scaling, barcode normalisation and GS1 origin (482 Ukraine, 481 Belarus), product names by language, drink detection (Latin and Cyrillic units), habits
- `src/lib/session.tsx` — anonymous sign-in, kept by supabase-js
- `src/pages/` — Welcome, Day, Week, Settings (partner view); Onboarding (also the profile and manual-targets editor), MyDay, AddFood, MyWeek (own tracker)
