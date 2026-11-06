# LiftLog features

Everything LiftLog does, grouped by area. For how to use each feature, see the **[user manual](docs/MANUAL.md)** ([PDF](docs/LiftLog-Manual.pdf)).

LiftLog is a free Android app (8.0 or newer). It has no account, no servers and no ads, and works fully offline. It doesn't even have permission to use the internet.

---

## Programs

- **Programs → training days → exercises**, each with target sets, reps, starting weight and rest time
- **Program library** with 7 ready-made programs you can copy and edit:
  - Full Body A/B · 5×5 Strength · Upper / Lower · Push / Pull / Legs · Full Body Hypertrophy · Dumbbell Only · Training Max Strength
- **Auto-progression**, chosen per exercise:
  - **Linear**: hit every rep on every set → more weight next time. Two misses in a row at the same weight → 10% deload.
  - **Double**: work up a rep range (e.g. 8–12). Every set at the top → add weight and start the range again.
  - **% of training max (% TM)**: each set is a percentage of your training max. An AMRAP ("as many reps as possible") set moves the training max up or down.
  - **None**: just repeat what you did last time.
- **Sensible defaults**: big barbell lifts start on linear progression (+5 lb upper body / +10 lb lower body), everything else on double progression.
- **Uses your RPE**: if you log effort, an all-out (RPE 10) day holds the weight, and an easy day (every set RPE 7 or less) doubles the jump.
- **Automatic warm-up sets**, per exercise: the empty bar, then about 40%, 60% and 80% of your first working set, rounded to weights you can load.
- **Supersets**: link two or more exercises in a day.
- **Edit anything**: rename, reorder (move up/down), delete days and exercises.

## Logging a workout

- **Start** from "Up next" (the day after the one you trained last), any program day, or a **quick workout** with no program.
- **Pre-filled sets** from your progression rule, or from what you lifted last time.
- **"Last time" line and a "Prev" column** on every exercise. Tap a Prev value to copy it into the set.
- **One tap ✓ per set.** Your data is saved as you type, so a crash or closed app loses nothing.
- **Set types**: Warm-up (W), To failure (F), Drop set (D). Warm-ups are kept out of history, records, volume and progression.
- **AMRAP sets** (shown as "3+").
- **Optional RPE column** (6–10) to rate how hard each set felt.
- **Plate calculator**: which plates go on each side, with your bar weight.
- **Warm-up button** to add a warm-up ramp to any exercise.
- **Supersets**: checking off a set jumps straight to the next exercise in the group. Rest starts after the round.
- **Mid-workout changes**: add or remove sets and exercises, change rest times, reorder, set a training max.
- **Readiness check-in** (optional): four quick questions before a program day. A low score offers a lighter day (−10%), which never counts against your progression.

## Rest timer

- **Starts automatically** when you check off a set, using that exercise's rest time.
- **Big countdown ring** in the app, with −15 s / +15 s / Skip.
- **Keeps running with the screen off**, with a live countdown in the notification shade.
- **Vibrates and notifies** when rest is over.

## History ("the Historian")

- **Every finished workout**, grouped by month, with duration, sets and volume.
- **Workout detail**: every set's weight × reps, the time since the previous set, warm-up markers, RPE, readiness score, lighter-day tag and training-max changes.
- **Monthly recap**:
  - workouts, hours, sets and total weight lifted
  - new estimated 1-rep-max records
  - top lift and muscle, favourite day and week streak
  - shareable as an image

## Progress

- **Per-exercise charts** of estimated 1-rep max, top weight or volume over time.
- **Personal records**: heaviest set, best estimated 1RM, best-volume workout, most reps.
- **Weekly working sets per muscle**, with a 10–20 set target band.
- **Recovery map**: a front and back body figure coloured Recovering / Ready / Fresh from your recent hard sets.
- **Body tracking**: body weight, body fat, waist, chest, arms, thighs and hips, each with a chart.
- **Muscle group per exercise** (set automatically for built-in exercises, editable).

## Settings & data

- **Units**: pounds or kilograms. Switching changes the display only; your logged data isn't changed. Body measurements follow (inches or cm).
- **Rest and warm-ups**: auto-start rest timer, default rest time, empty bar weight (for plates and warm-ups).
- **Optional extras**: RPE tracking, readiness check-in.
- **Health Connect sync** (optional, write-only): finished workouts appear in Android's Health Connect and apps that read it (e.g. Samsung Health), with each set's reps.
- **Backup & restore**: a full backup as one file. Restore it on a new phone; older backup files still restore.
- **CSV export** of every set for Excel or Google Sheets.
- **Safe updates**: every update keeps your data. Releases are signed with the same key, so new versions install over old ones.

## Exercise library

- **41 built-in exercises** with muscle groups.
- **Add your own** by typing a name in the exercise picker.
- **Filter by muscle**, rename exercises, and delete any exercise that isn't used anywhere.
