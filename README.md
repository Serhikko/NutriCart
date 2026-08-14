# NutriCart

[![Tests](https://github.com/Serhikko/NutriCart/actions/workflows/tests.yml/badge.svg)](https://github.com/Serhikko/NutriCart/actions/workflows/tests.yml)

Fitness & nutrition tracker for Android where the weekly meal plan turns into a
ready-to-shop grocery list. Offline-first, ad-free, in English and Ukrainian.

## Features

- **Personalized targets** — Mifflin-St Jeor calorie math with safety floors,
  WHO-based daily limits for sugar, salt and saturated fat, and a manual
  override for custom (e.g. nutritionist-prescribed) targets.
- **Food diary** — Open Food Facts search with an offline cache, barcode
  scanning, custom foods, favorites, frequency-ranked search, one-tap saved
  meals and a multi-add basket.
- **Full nutrition label** — fiber, sugars, salt, saturated fat and E-number
  additives per product; "green numbers" feedback on the dashboard.
- **7-day meal plan generator** — hits the calorie target within ±5% by scaling
  portions; meal locking, swapping, and a batch-cooking mode (cook 3–4 times a
  week, eat all 7 days).
- **Grocery list** — built from the plan, merged by ingredient, sorted in
  store-walk order; checked-off items survive plan regeneration; shareable as
  plain text.
- **Watch & Health Connect sync** — steps, active calories, exercise sessions,
  sleep and heart rate; measured activity adjusts the daily target with no
  double counting.
- **Workouts** — manual logging with MET-based calorie math, automatic watch
  session import, and recurring activities that log themselves.
- **Statistics** — calories-by-day chart with per-day verdicts, an adherence
  calendar, honest averages, and diet analysis with your top food sources per
  nutrient.
- **Habits** — day notes, smart meal reminders (silent when the meal is already
  logged), and a home-screen widget.
- **32 built-in recipes** (Ukrainian cuisine) with step-by-step instructions.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Room · Hilt · WorkManager ·
Retrofit + kotlinx.serialization · Health Connect · Glance · JUnit

## Architecture

- MVVM + Repository, single-activity Compose navigation.
- All calorie, meal-plan and statistics math is pure Kotlin in `domain/logic`,
  covered by **100 unit tests**.
- Offline-first: every looked-up product is cached, and the whole app keeps
  working without internet.
- The Room schema evolved through 11 versions with hand-written migrations —
  no data loss across updates.

## Getting started

```
git clone https://github.com/Serhikko/NutriCart.git
cd NutriCart
./gradlew :app:assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 37, minSdk 28). Or just grab
the latest APK from [Releases](https://github.com/Serhikko/NutriCart/releases).

## License

[MIT](LICENSE)
