# LiftLog

A native Android workout tracker: build programs, log weight/reps, auto-start a rest timer, and see your full lifting history and progress.

Kotlin · Jetpack Compose (Material 3) · Room · no account, no backend. All data stays on the phone.

## Features

**Program builder**
- Programs → training days → exercises with target **sets × reps**, **starting weight**, and **rest time**
- Reorder days and exercises, rename, delete
- Two starter templates (Full Body A/B, Push/Pull/Legs) you can add with one tap and edit
- 34 common exercises preloaded, plus create your own from the picker (long-press one to delete it if it isn't used anywhere)

**Workout logging**
- Start any program day (or a quick workout with no program). Home suggests **Up next**: the day after the last one you trained
- Sets pre-fill with **what you lifted last time** for that set; the program's starting weight is only used the first time
- Tap the "Prev" value to copy last time's set into the current one
- Check off a set → **rest timer auto-starts** with that exercise's rest time
- Add/remove sets and exercises mid-workout, change rest per exercise
- Workouts survive the app being killed. Every keystroke is saved to the database

**Rest timer**
- Animated countdown ring in-app with −15 / +15 / Skip
- Runs as a foreground service with a live countdown notification, so it keeps going with the screen off or the app in the background
- Vibrates + plays a notification sound when rest is over

**Historian**
- **History**: every finished workout grouped by month, with duration, sets, and volume. Open one to see each set's weight × reps and the time since the previous set
- **Last time** shown inline on every exercise while you log
- **Progress** per exercise: chart of estimated 1RM (Epley), top weight, or volume over time; personal records (heaviest set, best e1RM, best-volume workout, most reps); every past workout for that lift
- **Backup & export**: full JSON backup you can restore (e.g. on a new phone), and a CSV of every completed set for Excel/Sheets

**Settings**: lb/kg (lb by default; display-only conversion, data stored in kg), default rest, auto-start timer on/off.

## Screenshots

These are real renders of the app's Compose UI with sample data (Robolectric + Roborazzi), not mockups.

| Home | Logging a workout | Program day |
|---|---|---|
| ![](docs/screenshots/01_home.png) | ![](docs/screenshots/02_active_workout.png) | ![](docs/screenshots/03_program_day.png) |

| History | Workout detail | Exercise progress | Settings |
|---|---|---|---|
| ![](docs/screenshots/04_history.png) | ![](docs/screenshots/05_workout_detail.png) | ![](docs/screenshots/06_exercise_progress.png) | ![](docs/screenshots/07_settings.png) |

## Build & install

Requirements: JDK 17+ and the Android SDK (Android Studio installs both).

```bash
# Debug build
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Install on a connected phone (USB debugging on)
./gradlew :app:installDebug
```

Or open the folder in Android Studio and press Run.

Min Android version: 8.0 (API 26). Target: API 36.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

JVM tests (Robolectric, in-memory Room) cover: workout start/prefill logic, rest tracking, finishing/discarding, history queries, reordering, exercise de-duplication, backup round-trip, bad-file rejection, CSV output, unit conversion and PR math. `ScreenshotTest` regenerates the screenshots into `app/build/outputs/roborazzi/`.

## Known limitations / honest notes

- **"Rest before" in history is the time between completing consecutive sets.** It includes the time spent doing the set itself, so it reads ~20–60 s higher than pure rest. Measuring true rest would need a "start set" tap, which adds friction.
- **The rest timer uses a `specialUse` foreground service.** That's fine for sideloading. Publishing on Google Play requires justifying that service type in the Play Console.
- **The release build is signed with the debug key** so `assembleRelease` gives an installable APK. Set up your own signing key before publishing.
- **No cloud sync.** Use Settings → Back up everything regularly. Android's automatic device backup also includes the database if you have it enabled.
- **The e1RM is an estimate** (Epley formula) and gets unreliable above ~10 reps.
- **The UI is dark-only by design**, and ignores Material You wallpaper colors to keep the brand look.

## Project layout

```
app/src/main/java/com/codyphil/liftlog/
  data/       Room entities, DAOs, repository, backup/export, templates, settings
  domain/     Units (lb/kg), stats (e1RM, PRs), formatting
  timer/      RestTimer state + foreground RestTimerService
  ui/         Compose screens: train, workout, programs, history, progress, settings, shared components, theme
```
