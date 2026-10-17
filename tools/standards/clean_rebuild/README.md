# Clean-data rebuild of the rank standards (paused)

Goal: replace the rank standards (currently built on Strength Level / StrengthLog / ExRx tables) with numbers
from sources that are clearly allowed for commercial use, and see how much the ranks would change.
Nothing in the app has been changed yet; this folder is research only.

## Where it stands

- `Loadout_standards_change.txt` – every ranked exercise with its % change under the clean numbers
  (first version: 42 of 336 entries kept a rank). Since then coverage rose to **98 of 336** (see `compare.json`).
- `compare.json` – current vs new 5th/20th/50th/80th/95th percentile, per entry, at 180 lb (men) / 140 lb (women).
- `scripts/build.py` – builds the new tables and the comparison. `scripts/opl_full.py`, `scripts/owl.py` –
  turn the raw powerlifting / weightlifting data into each lifter's best lift.

### Method (as built)
1. **Shape across bodyweights:** OpenPowerlifting (public domain) – 4.05 M entries, raw only, each lifter's best
   squat/bench/deadlift (~1.48 M lifter-lifts). Median lift vs bodyweight fitted as a quadratic in log-log space.
   OpenWeightlifting (public domain) – ~66 k lifters, snatch and clean & jerk, same method.
2. **Where gym-goers sit:** gym-goer median = k × competitor median, with k set so the men's bench median at
   180 lb = 1.0 × bodyweight (confirmed by Piper et al. 2021: college men's 10RM 135 lb at 165–190 lb ≈ 180 lb 1RM)
   and women's at 132 lb = 0.63 × bodyweight. **The women's 0.63 anchor is weak** (it came from a study of national-team
   fencers) and should be replaced, ideally by the pooled-baselines work below.
3. **Spread:** log-normal, sigma 0.30 men / 0.35 women (Piper's homogeneous novice groups show ~0.27).
4. **Other lifts:** ratios to the lifts above, from research (all listed in `build.py` with their sources).

### Key clean sources found
| Source | Licence | Used for |
|---|---|---|
| OpenPowerlifting bulk CSV | Public domain | Squat, bench, deadlift shape |
| OpenWeightlifting (github.com/euanwm/OpenWeightlifting) | Public domain (data) | Snatch, clean & jerk |
| Piper et al. 2021 / 2022, Int J Strength Cond (IUSCA), college 10RM norms, 1,095 men + 371 women | CC BY 4.0 (checked on article page/PDF) | Lat pulldown, seated front press, preacher curl, leg press, bench — percentiles by bodyweight class, pre and post 12 weeks (`piper/piper_tables.json`) |
| Rodríguez-Ridao 2020, IJERPH | CC BY 4.0 (checked) | Incline : flat bench |
| Czaplicki 2019, PLOS ONE | CC BY 4.0 | Power snatch : snatch |
| Larsen 2021, Frontiers | CC BY 4.0 | Close-grip : flat bench |
| US Army report ADA274547 | Public domain | Push-up and sit-up percentiles by sex/age with body mass |
| Marine Corps pull-up studies, Chinese student fitness surveys, Yang 2019 JAMA Netw Open | CC BY / facts | Pull-ups, push-ups, sit-ups |
| Korean national fitness data (KSPO) | "No usage restriction" on data.go.kr, but our copy came via Kaggle – re-pull from the KSPO API (free key) for a clean chain | Sit-ups by bodyweight |
| Tomkinson et al. 2025 | Research | Grip (unchanged) |

Many other ratios are from paywalled papers; the numbers themselves are facts (usable), the tables/text are not
copied. Each source's licence evidence is recorded in the `research/*.json` files.

### Research files (`research/`)
- `studies.json`, `datasets.json`, `surveys.json` – first sweep (published studies, open datasets, community surveys).
- `lost_press_pull.json`, `lost_lower.json`, `lost_olympic_chest.json`, `lost_bodyweight.json` – per-exercise
  findings for the exercises that had no clean source.
- Gym-app statistics (Gravitus, Hardy, Liftoff) and Reddit surveys are cross-checks only (competitor/self-reported).

## Not finished
- **Arms / isolation** (curls, triceps, raises, flys, shrugs, crunch) – research not written yet.
- **Legs / hinges** – thin: only hip thrust, Bulgarian split squat, Smith squat have numbers.
- **Pooled baselines** – extracting baseline 1RM + body mass from 60+ open-access studies via the Europe PMC API,
  to fix the women's anchor and check the men's. Extractor was in progress.
- **Bodyweight moves** – data gathered (`lost_bodyweight.json`) but not yet turned into tables in `build.py`.
  Rule found: reps scale with body mass^(−1/3).
- Machine chest press, rows, and the remaining lost families.

## To pick up again
- Scripts use the old session's scratchpad paths; point them at a new work folder.
- Re-download big inputs (not committed):
  - OpenPowerlifting: https://openpowerlifting.gitlab.io/opl-csv/files/openpowerlifting-latest.zip
  - OpenWeightlifting: `git clone --filter=blob:none --sparse https://github.com/euanwm/OpenWeightlifting` then
    `git sparse-checkout set event_data`
- Full-text access needs the cloud environment's network access opened (Europe PMC REST API works best;
  pmc.ncbi.nlm.nih.gov HTML shows a captcha, mdpi.com blocks bots).
