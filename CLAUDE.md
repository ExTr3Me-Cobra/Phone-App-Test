# Working notes for Claude

Loadout (package `com.ironlog.app`): Kotlin + Jetpack Compose. No local SDK; CI builds every push to
`claude/workout-log-app` and publishes release `loadout-N` with `Loadout.apk`.

## Standing rules
- After every push, give the direct APK link
  `https://github.com/ExTr3Me-Cobra/Phone-App-Test/releases/download/loadout-N/Loadout.apk` and check it returns 302.
- No AI model IDs anywhere in the repo. No pull requests unless asked.
- Never compare our data with Strength Level, for any reason, not even as a reference point or sanity check.
- Nothing copyrighted, trademarked or not free for commercial use (Strength Level, ExRx, StrengthLog,
  Bodybuilding.com text, "Olympian" and similar). Cite studies by reference only; never copy their text or figures.
- Personal data stays out of the repo: Hevy exports, workout history, body weight or body fat, the OpenAI key, email.
- Version is `0.x.build`; bump the minor version for notable features. Ask the owner before going to 1.0.

## Waiting on the owner
- **Standards data fixes** (not applied). The owner is logging 1 Rep Max tests on the anchor and Research mode
  exercises first; do these after that, using their results:
  1. Light men (120–150 lb): standards rise faster than body weight, so light lifters are ranked too high.
     Rep standards at 120 lb also come out below 180 lb. Flatten the light end of the curve.
  2. Women's bodyweight moves (pull-ups, chin-ups, dips, handstand push-ups) are too close to men's; real data puts
     women at roughly 30–50% of men.
  3. Women's leg press is too high (the Piper women's table disagrees with that study's own averages).
  4. Front Squats With Two Kettlebells is a both-bell total but logged per bell; Tate Press is treated as one
     dumbbell but uses two.
  5. Variants that should roughly match: clean-grip vs front squat, full vs back squat, power clean below hang clean,
     powerlifting bench 15% above bench (should be ~5%), seated dumbbell press vs dumbbell shoulder press.
  6. Bottom levels bunched together: widen the spread back to 0.30 (men) / 0.35 (women).
  7. Count at most ~12 reps toward the estimated max on machine and isolation lifts.
  Fix from our own sources and build code; don't copy a reviewer's remembered numbers.
- The 72 medium-confidence exercise removal candidates.
- A link for the tip button.
- Weighted-vest calf raises (two- and one-leg) need new standards.
- The "Clean Search Data" doc is out of date (old 1.00× bench, old spread).
