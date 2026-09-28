# NutriCart website

NutriCart in the browser, two ways:

- **Partner view** — enter a 6-character pairing code from the phone's
  Settings and see that person's day as they log it, plus a nudge button that
  becomes a notification on their phone.
- **Own tracker** — for someone without an Android phone: the same
  questionnaire, targets, food search, barcode scanning, water, weight and
  week statistics, under `/me`. The calorie maths is a port of the phone's,
  checked against the same test vectors. A phone user opens the same account
  here by linking an email on the phone and choosing "Sign in with email" in
  Settings; what they log here comes back to the phone.

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
- `src/lib/openFoodFacts.ts` — search and barcode lookup against Open Food Facts
- `src/domain/` — the phone's `domain/logic` in TypeScript: calories and macros, nutrient scaling, barcode normalisation, habits
- `src/lib/session.tsx` — anonymous sign-in, kept by supabase-js
- `src/pages/` — Welcome, Day, Week, Settings (partner view); Onboarding, MyDay, AddFood, MyWeek (own tracker)
