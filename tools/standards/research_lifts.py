"""Real-data lifts: Intermediate (50th percentile) 1RM for people who lift, from published research only.

Level: gym-goer median bench press = 1.10 x bodyweight for men at 180 lb and 0.68 x bodyweight for women at 132 lb: halfway
between Isenmann et al. 2026's intermediate group (1.20 / 0.73, possibly more trained than most gym-goers) and the
smaller studies' 1.00 / 0.63 (Tiggemann et al. 2011; Rodriguez-Ridao et al. 2020). Every other lift is a ratio measured in the same people, from the studies cited per lift.
Bodyweight shape: OpenPowerlifting / OpenWeightlifting median curves (inputs/openpowerlifting_curves.json).
gold = a 1RM ratio measured in that sex in a published study; 10-rep-max ratios, ratios measured only in the other sex,
assumptions and low-confidence studies are not gold.
Output: inputs/research_lifts.json, the median at 180 lb (men) and 140 lb (women) for each lift.
"""
import json, math, os
HERE = os.path.dirname(os.path.abspath(__file__))

C = json.load(open(os.path.join(HERE, 'inputs', 'openpowerlifting_curves.json')))['bodyweight']
def F(key):
    a, b, c, lo, hi = C[key]
    def f(bw):
        x = math.log(min(max(bw, lo), hi))
        return math.exp(a * x * x + b * x + c) * ((bw / hi) ** 0.15 if bw > hi else 1) * ((bw / lo) ** 0.9 if bw < lo else 1)
    return f

K = {'M': 180 * 1.10 / F('MB')(180), 'F': 132 * 0.68 / F('FB')(132)}
# Squat and deadlift relative to bench in gym-goers (Isenmann et al. 2026, Sci Rep, n = 393, CC BY 4.0; mean of the
# untrained-to-intermediate levels). Competitors squat and pull more relative to bench than gym-goers do.
GYM_RATIO = {'M': {'S': 1.24, 'D': 1.53}, 'F': {'S': 1.67, 'D': 2.05}}
ANCHOR_BW = {'M': 180, 'F': 132}
def base(lift): return lambda sex: (lambda bw: K[sex] * F(sex + lift)(bw))
def gym(lift): return lambda sex: (lambda bw: K[sex] * F(sex + lift)(bw) * GYM_RATIO[sex][lift] * F(sex + 'B')(ANCHOR_BW[sex]) / F(sex + lift)(ANCHOR_BW[sex]))
def scaled(f, r): return lambda sex: (lambda bw: f(sex)(bw) * (r[sex] if isinstance(r, dict) else r))
B, SN, CJ, Sq, D = base('B'), base('SN'), base('CJ'), gym('S'), gym('D')
Y, N = True, False

# exercise in the app, formula, gold (men, women), base lift for the bodyweight curve, source
LIFTS = [
 ('Barbell Bench Press - Medium Grip', B, (Y, Y), 'B', 'gym-goer median bench, halfway between Isenmann et al. 2026 (CC BY) and Tiggemann et al. 2011 / Rodriguez-Ridao et al. 2020'),
 ('Barbell Squat', Sq, (Y, Y), 'S', 'squat = 1.24 x bench men / 1.67 women in gym-goers (Isenmann et al. 2026, CC BY)'),
 ('Barbell Deadlift', D, (Y, Y), 'D', 'deadlift = 1.53 x bench men / 2.05 women in gym-goers (Isenmann et al. 2026, CC BY)'),
 ('Sumo Deadlift', D, (N, N), 'D', 'assumed equal to the conventional deadlift'),
 ('Trap Bar Deadlift', scaled(D, 1.04), (Y, N), 'D', 'deadlift x 1.04, the mean of 1.08 (Swinton et al. 2011) and 1.00 (Camara et al. 2016), both in men'),
 ('Front Barbell Squat', scaled(Sq, 0.785), (N, N), 'S', 'squat x 0.785, a commonly cited ratio not confirmed in a 1RM study'),
 ('Barbell Incline Bench Press - Medium Grip', scaled(B, 0.78), (Y, N), 'B', 'bench x 0.78 at 30 degrees (Rodriguez-Ridao et al. 2020, CC BY; participants mostly men)'),
 ('Dumbbell Bench Press', scaled(B, 0.83 / 2), (Y, N), 'B', 'bench x 0.83 for both dumbbells, per dumbbell (Saeterbakken et al. 2011; Jackson 2012 thesis; men)'),
 ('Incline Dumbbell Press', scaled(B, 0.78 * 0.83 / 2), (N, N), 'B', 'incline ratio x dumbbell ratio, from two different studies'),
 ('Smith Machine Bench Press', scaled(B, {'M': 0.92, 'F': 0.80}), (N, N), 'B', 'bench x 0.92 men (between Cotterman et al. 2005 and Saeterbakken et al. 2011) / 0.80 women (Cotterman et al. 2005)'),
 ('Leg Press', scaled(B, {'M': 2.65, 'F': 4.5}), (N, N), 'B', 'bench x 2.65 men / 4.5 women, a 10-rep-max ratio (Piper et al. 2021/2022, CC BY)'),
 ('Wide-Grip Lat Pulldown', scaled(B, {'M': 0.93, 'F': 1.34}), (N, N), 'B', 'bench x 0.93 men / 1.34 women, a 10-rep-max ratio (Piper et al. 2021/2022, CC BY)'),
 ('Seated Barbell Military Press', scaled(B, {'M': 0.665, 'F': 0.79}), (N, N), 'B', 'bench x 0.665 men / 0.79 women, a 10-rep-max ratio (Piper et al. 2021/2022, CC BY)'),
 ('Standing Military Press', scaled(B, {'M': 0.665 * 0.92, 'F': 0.79 * 0.92}), (N, N), 'B', 'seated press x 0.92 for standing (assumed from Saeterbakken & Fimland 2013)'),
 ('Seated Dumbbell Press', scaled(B, {'M': 0.30, 'F': 0.35}), (N, N), 'B', 'per dumbbell about 0.30 men / 0.35 women x bench - low confidence'),
 ('Preacher Curl', scaled(B, {'M': 0.47, 'F': 0.57}), (N, N), 'B', 'bench x 0.47 men / 0.57 women, a 10-rep-max ratio (Piper et al. 2021/2022, CC BY)'),
 ('Barbell Curl', scaled(B, {'M': 0.55, 'F': 0.60}), (Y, N), 'B', 'bench x 0.55 men (Massini et al. 2022: 0.54; Ribeiro et al. 2024: 0.58); women 0.60, between Seo et al. 2012 (0.46) and Ribeiro et al. 2024 (0.74)'),
 ('EZ-Bar Curl', scaled(B, {'M': 0.55, 'F': 0.60}), (N, N), 'B', 'assumed equal to the barbell curl'),
 ('Close-Grip Barbell Bench Press', scaled(B, 0.95), (Y, N), 'B', 'bench x 0.95 (Larsen et al. 2021, CC BY; men)'),
 ('Decline Barbell Bench Press', scaled(B, 1.0), (N, N), 'B', 'assumed equal to the flat bench - low confidence'),
 ('Decline Dumbbell Bench Press', scaled(B, 0.83 / 2), (N, N), 'B', 'assumed equal to the flat dumbbell bench'),
 ('Seated Cable Rows', scaled(B, {'M': 0.97, 'F': 1.07}), (Y, Y), 'B', 'bench x 0.97 men / 1.07 women (Seo et al. 2012, J Sports Sci Med, machine row and bench)'),
 ('Bent Over Barbell Row', scaled(B, {'M': 0.93, 'F': 1.03}), (N, N), 'B', 'assumed about 0.96 x the seated row - low confidence'),
 ('Barbell Hip Thrust', scaled(D, {'M': 1.22, 'F': 1.29}), (Y, Y), 'D', 'deadlift x 1.22 men / 1.29 women (Nieto-Acevedo et al. 2023, IJERPH, CC BY)'),
 ('Romanian Deadlift', scaled(Sq, 1.0), (N, N), 'D', 'squat x 1.00 (one rugby 3RM study) - low confidence'),
 ('Snatch', SN, (N, N), 'SN', 'OpenWeightlifting shape; gym-goers assumed the same fraction of competitors as on the bench'),
 ('Clean and Jerk', CJ, (N, N), 'CJ', 'OpenWeightlifting shape; gym-goers assumed the same fraction of competitors as on the bench'),
 ('Power Clean', scaled(CJ, 0.89), (N, N), 'CJ', 'clean x 0.89 (Szyszka & Czaplicki 2021), the clean taken as equal to the clean & jerk'),
 ('Hang Clean', scaled(CJ, 0.96), (N, N), 'CJ', 'clean x 0.96 (Szyszka & Czaplicki 2021), the clean taken as equal to the clean & jerk'),
 ('Power Snatch', scaled(SN, 0.88), (N, N), 'SN', 'snatch x 0.88 (Czaplicki et al. 2019, CC BY)'),
 ('Push Press', scaled(CJ, 0.87), (N, N), 'CJ', 'clean & jerk x 0.87 - low confidence'),
]

if __name__ == '__main__':
    out = []
    for n, f, gold, bl, src in LIFTS:
        out.append({'exercise': n, 'male_median_lb_at_180': round(f('M')(180), 1), 'female_median_lb_at_140': round(f('F')(140), 1),
                    'gold': {'male': gold[0], 'female': gold[1]}, 'curve': bl, 'source': src})
    json.dump(out, open(os.path.join(HERE, 'inputs', 'research_lifts.json'), 'w'), indent=1)
    for o in out: print(f"{o['exercise']:44} {o['male_median_lb_at_180']:7} {o['female_median_lb_at_140']:7}")
