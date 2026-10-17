import json
OUT="/tmp/claude-0/-home-user-Phone-App-Test/38839be0-c3cc-5779-aa49-91384a3b0295/scratchpad/research/lost_bodyweight.json"

# ---------- licences (reused) ----------
S_ARMY={"citation":"Knapik J, Banderet L, Bahrke M, O'Connor J, Jones B, Vogel J. Army Physical Fitness Test (APFT): Normative Data on 6022 Soldiers. USARIEM Technical Report T94-7, Jan 1994. DTIC ADA274547.",
 "url":"https://apps.dtic.mil/sti/tr/pdf/ADA274547.pdf (DTIC was in maintenance; full PDF read from https://archive.org/download/DTIC_ADA274547/DTIC_ADA274547.pdf)",
 "licence":"US federal government work (public domain, 17 USC 105); DTIC Distribution Statement A",
 "licence_evidence":"Report documentation page (SF298) of the PDF: 'Approved for Public Release; Distribution unlimited'. Authors are US Army (USARIEM / US Army Physical Fitness School) personnel.",
 "usable_commercially":"yes","reasoning":"Work of US Government employees in official duties is not copyrightable in the US; cleared for unlimited public release."}
S_YANG={"citation":"Yang J, Christophi CA, Farioli A, et al. Association Between Push-up Exercise Capacity and Future Cardiovascular Events Among Active Adult Men. JAMA Netw Open. 2019;2(2):e188341. doi:10.1001/jamanetworkopen.2018.8341 (PMC6484614)",
 "url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC6484614/","licence":"CC BY (JAMA Network Open)",
 "licence_evidence":"Article permissions block (Europe PMC full-text XML): 'Copyright 2019 Yang J et al. JAMA Network Open. This is an open access article distributed under the terms of the CC-BY License.'",
 "usable_commercially":"yes","reasoning":"CC BY permits commercial reuse with attribution."}
S_KEEFER_M={"citation":"Keefer M, DeBeliso M. A Comparison of United States Marine Corps Physical Fitness Test and Combat Fitness Test Results. Int J Exerc Sci. 2020;13(4):1741-1755. doi:10.70252/HCZX4482 (PMC7745917)",
 "url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC7745917/","licence":"CC BY-ND 4.0 (journal-wide)",
 "licence_evidence":"IJES author page https://ijes.scholasticahq.com/for-authors : 'All published work by IJES is licensed under a Creative Commons Attribution-NoDerivatives 4.0 International License. Copyright for all works published by IJES is retained by the authors.' (PMC page itself shows no licence block.)",
 "usable_commercially":"yes","reasoning":"CC BY-ND allows commercial use with attribution; we only use the reported facts (mean/SD/n), which are not copyrightable anyway, and do not redistribute an adapted version of the article."}
S_KEEFER_F={"citation":"Keefer M, Adams K, Climstein M, DeBeliso M. The Relationship between Physical Fitness and Combat Fitness Among Female United States Marines: A Brief Report. International Journal of Science and Engineering Investigations. 2021;10(116):9-12.",
 "url":"https://works.bepress.com/michael_climstein/170/ ; https://researchportal.scu.edu.au/esploro/outputs/journalArticle/The-relationship-between-physical-fitness-and/991012965000402368",
 "licence":"unclear (described as open access; no licence text seen - both hosting sites blocked from this environment)",
 "licence_evidence":"Only abstract text seen via search snippet; no licence statement retrieved.",
 "usable_commercially":"facts-only","reasoning":"Numeric facts (mean, SD, n, body mass) are not copyrightable; do not copy text/tables."}
S_300={"citation":"Looney DP, Potter AW, Schafer EA, Chapman CL, Friedl KE. The 300 Marines: characterizing the US Marines with perfect scores on their physical and combat fitness tests. Front Physiol. 2024;15:1406749. doi:10.3389/fphys.2024.1406749 (PMC11217602)",
 "url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC11217602/","licence":"CC BY 4.0",
 "licence_evidence":"Article permissions: 'This is an open-access article distributed under the terms of the Creative Commons Attribution License (CC BY). The use, distribution or reproduction in other forums is permitted, provided the original author(s) and the copyright owner(s) are credited...' https://creativecommons.org/licenses/by/4.0/",
 "usable_commercially":"yes","reasoning":"CC BY 4.0."}
S_YIN={"citation":"Yin et al. Physical Fitness Index and body mass index: A cross-sectional study based on 1.3 million college students. PLoS One. 2025 (PMC12574861)",
 "url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC12574861/","licence":"CC BY 4.0",
 "licence_evidence":"Article permissions: '(c) 2025 Yin et al This is an open access article distributed under the terms of the Creative Commons Attribution License, which permits unrestricted use, distribution, and reproduction in any medium' (https://creativecommons.org/licenses/by/4.0/)",
 "usable_commercially":"yes","reasoning":"CC BY 4.0."}
S_PAOLI={"citation":"Paoli A, Grimaldi K, D'Agostino D, et al. Ketogenic diet does not affect strength performance in elite artistic gymnasts. J Int Soc Sports Nutr. 2012;9:34 (PMC3411406)",
 "url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC3411406/","licence":"CC BY 2.0",
 "licence_evidence":"Article permissions: 'This is an Open Access article distributed under the terms of the Creative Commons Attribution License (http://creativecommons.org/licenses/by/2.0), which permits unrestricted use, distribution, and reproduction in any medium'",
 "usable_commercially":"yes","reasoning":"CC BY 2.0."}
S_MARKOVIC={"citation":"Markovic G, Jaric S. Movement performance and body size: the relationship for different groups of tests. Eur J Appl Physiol. 2004;92(1-2):139-149. PMID 15024662",
 "url":"https://pubmed.ncbi.nlm.nih.gov/15024662/","licence":"Springer subscription article (all rights reserved)",
 "licence_evidence":"Europe PMC record: isOpenAccess=N, no licence. Only abstract read.",
 "usable_commercially":"facts-only","reasoning":"We use only the reported scaling exponent (a scientific fact/finding), not text or tables."}
S_VANDERBURGH={"citation":"Vanderburgh PM, Crowder TA. Body mass penalties in the physical fitness tests of the Army, Air Force, and Navy. Mil Med. 2006;171(8):753-756 (PMID 16933817); and Vanderburgh PM. Correction factors for body mass bias in military physical fitness tests. Mil Med. 2007;172(7):738-742 (PMID 17691687)",
 "url":"https://pubmed.ncbi.nlm.nih.gov/17691687/","licence":"Military Medicine (Oxford Univ. Press / AMSUS) subscription; all rights reserved",
 "licence_evidence":"Europe PMC records: isOpenAccess=N. Only abstracts read.",
 "usable_commercially":"facts-only","reasoning":"Use only the published finding (exponent -1/3; 15-20% penalty 60 vs 90 kg men)."}
S_KSPO={"citation":"Korea Sports Promotion Foundation (KSPO), National Fitness Award 100 (국민체력100) measurement records, as redistributed in 'Body performance Data' (kukuroo3, Kaggle; 13,393 rows, filtered/post-processed by uploader). Copy used: https://raw.githubusercontent.com/pedrogs97/sprint4/main/data/bodyPerformance.csv + bodyPerformanceTest.csv (13,298 + 94 rows = 13,392 of 13,393).",
 "url":"https://www.kaggle.com/datasets/kukuroo3/body-performance-data ; original: https://www.data.go.kr/data/15108938/openapi.do (KSPO 국민체력100 체력인증센터 측정결과 정보, OpenAPI) and https://www.data.go.kr/data/96238/linkedData.do (체력측정 항목별 측정 데이터)",
 "licence":"Source data: data.go.kr '이용허락범위: 제한 없음' (no usage restriction) for KSPO 국민체력100 measurement-result OpenAPI. Kaggle redistribution licence: not verified (Kaggle blocked by captcha).",
 "licence_evidence":"data.go.kr/data/15108938/openapi.do: '이용 조건 및 라이선스 정보 비용부과유무 무료 ... 이용허락범위 제한 없음'; description lists 신장, 체중(kg), 체지방률(%), 악력, 윗몸말아올리기(회) etc. The linked dataset 96238 (bigdata-culture.kr) lists 신장, 체중, 윗몸일으키기, BMI but its licence field is blank.",
 "usable_commercially":"unclear","reasoning":"KSPO's own API data is marked unrestricted on the Korean public-data portal, but the CSV we analysed is a third-party Kaggle redistribution with an unverified licence. For a clean chain, re-pull the same records from the KSPO OpenAPI (free key) - the computed percentiles are facts either way."}
S_CSEP={"citation":"Canadian Society for Exercise Physiology (CSEP) push-up norms (CPAFLA / CSEP-PATH manual; widely reproduced e.g. ACSM Guidelines). Origin usually given as the 1981 Canada Fitness Survey.",
 "url":"https://csep.ca (manual, not online); secondary reprints e.g. https://www.acefitness.org/images/webcontent/assets/certification/ace-answers/forms/pt/36_Push-up_Assessment_Protocol.pdf",
 "licence":"CSEP copyright (manual sold commercially)",
 "licence_evidence":"No open licence found; norms reproduced in many third-party sites under attribution to CSEP.",
 "usable_commercially":"facts-only","reasoning":"Band cut-offs are facts; do not copy the table layout/labels. Percentile mapping of the bands is an assumption."}

entries=[]

# ---------- PUSH-UPS ----------
army_m={ # age: (p5,p20,p50,p80,p95, mean, sd, n, body mass mean, sd)
 "17-21":(35,44,52,62,74,53.1,11.6,1157,75.3,9.3),
 "22-26":(35,43,51,63,77,53.2,12.4,1688,76.7,10.3),
 "27-31":(33,40,49,61,76,50.7,12.7,1017,78.2,10.8),
 "32-36":(30,36,46,58,73,47.4,13.1,824,79.2,10.3),
 "37-41":(30,34,41,53,70,44.2,12.4,382,79.3,10.4),
 "42-46":(26,30,36,46,61,38.4,11.5,217,80.6,11.3),
 "47-51":(22,25,32,47,64,35.8,12.8,54,81.2,8.4)}
army_f={
 "17-21":(16,20,30,40,50,30.0,10.7,111,59.0,7.4),
 "22-26":(17,20,29,40,54,31.0,11.1,228,59.8,6.8),
 "27-31":(15,18,25,35,49,27.5,10.6,164,61.6,7.8),
 "32-36":(14,18,24,32,52,25.6,10.0,119,63.1,8.5),
 "37-41":(14,14,19,32,56,22.9,13.0,34,61.7,7.3),
 "42-46":(None,14,17,23,None,18.8,5.6,18,60.6,6.5)}
def pk(t): return {"p5":t[0],"p20":t[1],"p50":t[2],"p80":t[3],"p95":t[4],"mean":t[5],"sd":t[6],"n":t[7],"body_mass_kg_mean":t[8],"body_mass_kg_sd":t[9]}
entries.append({
 "exercise":"push-ups","approach":"direct",
 "population":"US Army soldiers, 1988 Active Army Physical Fitness Survey (14 installations, ~1% sample stratified by age), 5,346 men / 676 women; 609 judged non-maximal efforts excluded",
 "protocol":"Max push-ups in 2 minutes (FM 21-20 1984 standard: full push-up for BOTH sexes, upper arm at least parallel to ground, full elbow extension; pausing allowed in front-leaning rest)",
 "numbers":{"men_by_age":{k:pk(v) for k,v in army_m.items()},"women_by_age":{k:pk(v) for k,v in army_f.items()},
   "men_all_ages":{"mean":50.4,"sd":13.0,"body_mass_kg":"77.5 +/- 10.3","height_cm":"176.5 +/- 7.3"},
   "women_all_ages":{"mean":28.3,"sd":11.1,"body_mass_kg":"60.8 +/- 7.6","height_cm":"163.6 +/- 7.2"},
   "note":"Values are raw-score percentiles exactly as in Tables 3-17 (reps), body mass from Table 2. 47-51 women (n=2) and >=52 men (n=6) omitted (individual values only). Women 42-46: P5/P95 not tabulated (n=18). 99th pct of men 42-46 printed as 70 (OCR '/0')."},
 "derived_percentiles_if_possible":"Already percentiles. Population is fitter than ordinary adults (soldiers who train for this test) and a 2-min timed set; treat as an upper-reference / 'trained' anchor. For bodyweight scaling see the 'bodyweight-scaling' entry.",
 "body_mass_recorded":True,
 "sources":[S_ARMY],"confidence":"high","verification":"full text (PDF read; tables OCR-checked, Table 2 read from page image)"})

entries.append({
 "exercise":"push-ups","approach":"direct",
 "population":"1,104 career male firefighters, 10 Indiana fire departments, baseline exams 2000-2007; mean age 39.6 (SD 9.2), BMI 28.7 (4.3)",
 "protocol":"Metronome-paced push-ups at 80 bpm (i.e., 40 reps/min) until 80 reps reached, 3+ missed beats, or exhaustion",
 "numbers":{"bins":[
   {"push_ups":"0-10","n":75,"age_mean":48.4,"bmi_mean":33.1,"bmi_sd":5.8},
   {"push_ups":"11-20","n":200,"age_mean":45.1,"bmi_mean":30.3,"bmi_sd":4.9},
   {"push_ups":"21-30","n":389,"age_mean":39.0,"bmi_mean":28.7,"bmi_sd":3.9},
   {"push_ups":"31-40","n":285,"age_mean":36.6,"bmi_mean":27.4,"bmi_sd":3.1},
   {"push_ups":">=41","n":155,"age_mean":35.1,"bmi_mean":26.8,"bmi_sd":2.9}]},
 "derived_percentiles_if_possible":{"method":"linear interpolation within bins (bin edges -0.5/10.5/20.5/30.5/40.5)","men_mean_age_40":{"p5":7.7,"p20":17.8,"p50":27.6,"p80":38.2,"p95":"> 40 (open bin; ~45-55 if bin spans 41-60)"},
   "bmi_gradient":"Mean BMI falls monotonically from 33.1 (0-10 reps) to 26.8 (>=41 reps) - direct evidence of reps vs body size/fatness in ordinary working men (confounded with age)."},
 "body_mass_recorded":True,"sources":[S_YANG],"confidence":"high","verification":"full text (Table 1 from Europe PMC XML)"})

csep_m={"15-19":(17,22,28,38),"20-29":(16,21,28,35),"30-39":(11,16,21,29),"40-49":(9,12,16,24),"50-59":(6,9,12,20),"60-69":(4,7,10,17)}
csep_f={"15-19":(11,17,24,32),"20-29":(9,14,20,29),"30-39":(7,12,19,26),"40-49":(4,10,14,23),"50-59":(1,6,10,20),"60-69":(1,4,11,16)}
def csep_pcts(t):
    p20=t[0]+.5; p40=t[1]+.5; p60=t[2]+.5; p80=t[3]+.5; p50=(p40+p60)/2; sd=(p80-p20)/1.6832
    return {"p5":round(max(0,p50-1.645*sd),1),"p20":p20,"p50":p50,"p80":p80,"p95":round(p50+1.645*sd,1)}
entries.append({
 "exercise":"push-ups","approach":"direct",
 "population":"Canadian general population (CSEP norms, reported origin: 1981 Canada Fitness Survey); men standard push-ups, women KNEE push-ups",
 "protocol":"Consecutive push-ups to fatigue, no time limit (CSEP)",
 "numbers":{"bands_format":"upper bound of Needs-improvement / Fair / Good / Very-good bands; Excellent = above last value",
   "men_standard":{k:{"NI_max":v[0],"Fair_max":v[1],"Good_max":v[2],"VeryGood_max":v[3],"Excellent_min":v[3]+1} for k,v in csep_m.items()},
   "women_knee":{k:{"NI_max":v[0],"Fair_max":v[1],"Good_max":v[2],"VeryGood_max":v[3],"Excellent_min":v[3]+1} for k,v in csep_f.items()},
   "verification_detail":"Men 20-29 (<=16/17-21/22-28/29-35/>=36), women 20-29 (<=9/10-14/15-20/21-29/>=30), men 30-39 Good 17-21, women 40-49 (5-10/11-14/15-23) confirmed in search snippets; other rows from the widely reproduced CSEP table (recall), not re-verified."},
 "derived_percentiles_if_possible":{"assumption":"band edges = 20th/40th/60th/80th percentiles (common CSEP interpretation, NOT verified); p50 = midpoint of P40/P60; p5/p95 = normal extrapolation from P20/P80",
   "men_standard":{k:csep_pcts(v) for k,v in csep_m.items()},"women_knee":{k:csep_pcts(v) for k,v in csep_f.items()}},
 "body_mass_recorded":False,"sources":[S_CSEP],"confidence":"medium","verification":"search snippet (partial rows) + recall"})

# ---------- PULL-UPS ----------
entries.append({
 "exercise":"pull-ups","approach":"direct",
 "population":"Active-duty enlisted US Marines, PFT records 2017-01-01 to 2018-08-12",
 "protocol":"USMC PFT pull-ups (dead hang, chin over bar, no time limit; overhand or underhand grip permitted per USMC rules - grip rule from recall, not verified here). Many Marines stop at the max-points count (men 20-23) -> ceiling effect",
 "numbers":{"men":{"n":19678,"age":"22.5 +/- 1.3","height_m":"1.77 +/- 0.07","body_mass_kg":"79.4 +/- 10.3","pull_ups":"18.0 +/- 5.0","abdominal_crunches_2min":"111.2 +/- 9.0"},
            "women":{"n":554,"age":"22.7 +/- 1.4","height_cm":"163 +/- 7","body_mass_kg":"62.8 +/- 7.3","pull_ups":"7.1 +/- 4.3","note":"women could choose push-ups instead (selection toward able pull-uppers)"}},
 "derived_percentiles_if_possible":{"method":"normal approximation, floored at 0 (ceiling at max-score counts makes upper tail too low)",
   "men":{"p5":9.8,"p20":13.8,"p50":18.0,"p80":22.2,"p95":26.2},"women":{"p5":0.0,"p20":3.5,"p50":7.1,"p80":10.7,"p95":14.2}},
 "body_mass_recorded":True,"sources":[S_KEEFER_M,S_KEEFER_F],"confidence":"medium",
 "verification":"men: full text (PMC page, Tables 1-2); women: search snippet of abstract"})

entries.append({
 "exercise":"pull-ups","approach":"direct",
 "population":"US Marines with DXA body composition (2024 study): 1,224 men (30 +/- 8 y), 497 women (29 +/- 7 y), split into '300 Marines' (perfect PFT+CFT, 22 men / 21 women) vs 'Other'",
 "protocol":"USMC PFT pull-ups",
 "numbers":{"men_other":{"pull_ups":"20 +/- 5","body_mass_kg":"86.2 +/- 11.1","height_cm":"177 +/- 7","bmi":"27.6 +/- 3.0","body_fat_pct":"21.9 +/- 6.1","lean_mass_kg":"63.7 +/- 7.9"},
            "men_300":{"pull_ups":"23 +/- 2","body_mass_kg":"80.8 +/- 7.1","bmi":"25.2 +/- 1.8","body_fat_pct":"15.9 +/- 4.8","lean_mass_kg":"64.6 +/- 5.9"},
            "women_other":{"pull_ups":"9 +/- 3","body_mass_kg":"67.5 +/- 8.5","height_cm":"163 +/- 7","bmi":"25.4 +/- 2.6","body_fat_pct":"29.0 +/- 5.6","lean_mass_kg":"45.2 +/- 5.6"},
            "women_300":{"pull_ups":"13 +/- 4","body_mass_kg":"64.3 +/- 5.9","bmi":"23.4 +/- 1.8","body_fat_pct":"21.5 +/- 4.8","lean_mass_kg":"47.7 +/- 3.8"},
            "max_points_pull_ups":"men 20 (17-20 y), 23 (21-35 y) ... 18 (51+); women 7 (17-20), 11-12 (21-35) ... 4 (51+)"},
 "derived_percentiles_if_possible":"Gives pull-ups at known body mass/fat: same lean mass, ~5 kg more fat mass -> ~3 fewer pull-ups (men) / ~4 fewer (women). Use to calibrate body-mass/fat scaling.",
 "body_mass_recorded":True,"sources":[S_300],"confidence":"high","verification":"full text (Tables 1-3 from Europe PMC XML)"})

entries.append({
 "exercise":"pull-ups","approach":"direct",
 "population":"706,858 male undergraduates (18-24 y), Jiangsu province colleges, China; BMI 22.96 +/- 4.05",
 "protocol":"Chinese National Student Physical Health Standard pull-up: pronated grip, chin above bar, lower until elbow angle >150 deg, no swinging",
 "numbers":{"men_total":{"n":706858,"age":"19.43 +/- 1.23","pull_ups":"5.88 +/- 5.43","bmi":"22.96 +/- 4.05"},
   "by_grade":[{"grade":1,"n":211463,"pull_ups":"5.39 +/- 5.11","bmi":"23.07 +/- 4.20"},{"grade":2,"n":208673,"pull_ups":"5.93 +/- 5.40","bmi":"22.93 +/- 4.12"},
               {"grade":3,"n":188148,"pull_ups":"6.38 +/- 5.69","bmi":"22.83 +/- 3.96"},{"grade":4,"n":98574,"pull_ups":"5.89 +/- 5.58","bmi":"23.05 +/- 3.71"}]},
 "derived_percentiles_if_possible":{"note":"Distribution is strongly right-skewed with a mass at 0 (SD ~ mean); normal approx is poor. Rough gamma-like guess: p5=0, p20=1, p50=5, p80=10, p95=16 (ESTIMATE, not published).",
   "body_mass_estimate":"BMI 23 at ~172 cm => ~68 kg (height not reported)"},
 "body_mass_recorded":True,"sources":[S_YIN],"confidence":"medium","verification":"full text (Table 3 from Europe PMC XML)"})

entries.append({
 "exercise":"pull-ups","approach":"direct",
 "population":"Female Marine recruits at MCRD Parris Island, late 2013 (end of recruit training)",
 "protocol":"Pull-ups (USMC)",
 "numbers":{"women":"55% could NOT perform 3 pull-ups (i.e., 45% could do >=3)","body_mass":"not reported"},
 "derived_percentiles_if_possible":{"women_trained_recruits":"P55 ~ 3 reps => median ~2-3 pull-ups after ~13 weeks training"},
 "body_mass_recorded":False,
 "sources":[{"citation":"Associated Press / Military.com, 'Marine Corps Delays Pull-Up Requirement for Women', 3 Jan 2014 (reporting USMC data)","url":"https://mst.military.com/daily-news/2014/01/03/marine-corps-delays-pull-up-requirement-for-women.html",
   "licence":"news article, all rights reserved; underlying figure is US Government (USMC) data","licence_evidence":"Military.com copyright notice; USMC data is a federal work","usable_commercially":"facts-only","reasoning":"Single statistic (a fact)."}],
 "confidence":"medium","verification":"search snippet"})

# ---------- CHIN-UPS ----------
entries.append({
 "exercise":"chin-ups","approach":"ratio to pull-ups (and push-ups/dips)",
 "population":"8 elite male artistic gymnasts, age 20.9 +/- 5.5, body mass 69.6 +/- 7.3 kg (7.6% fat)",
 "protocol":"Reverse-grip (supinated) chins, shoulder width, chest to bar, to exhaustion; push-ups chest to floor; parallel-bar dips to shoulder-limited depth",
 "numbers":{"baseline_trial1":{"chin_ups":"17 +/- 4.2","push_ups":"36 +/- 6.3","parallel_bar_dips":"25.8 +/- 8.35","body_mass_kg":"69.6 +/- 7.3"},
            "baseline_trial2":{"chin_ups":"15.2 +/- 3.4","push_ups":"37 +/- 11.8","parallel_bar_dips":"23 +/- 12.19","body_mass_kg":"70.1 +/- 6.2"}},
 "derived_percentiles_if_possible":{"ratios_gymnasts":{"chin_ups/push_ups":"0.41-0.47","dips/push_ups":"0.62-0.72","dips/chin_ups":"1.51-1.52"},
   "chin_vs_pull":"No study found that measured max chin-ups and max pull-ups in the same people. USMC counts either grip as a pull-up. Suggested working ratio chin-ups = 1.0-1.15 x pull-ups (low confidence; EMG studies show similar lat activity, more biceps with supinated grip)."},
 "body_mass_recorded":True,"sources":[S_PAOLI],"confidence":"low","verification":"full text (Table 4) for gymnast numbers; chin:pull ratio has no source"})

# ---------- DIPS ----------
entries.append({
 "exercise":"dips","approach":"ratio to push-ups",
 "population":"(a) 8 elite male gymnasts 69.6 kg (Paoli 2012); (b) biomechanical load model for ordinary adults",
 "protocol":"Parallel-bar dips to exhaustion",
 "numbers":{"gymnasts":"dips 25.8 +/- 8.35 vs push-ups 36 +/- 6.3 (and 23 vs 37) at 69.6-70.1 kg",
   "load_model":"push-up support load ~0.64 x body weight (Ebben et al. 2011 JSCR, recall - not re-verified), dip load ~0.95 x BW (body minus forearms/hands; segment-mass estimate); Epley-type conversion reps_dip = 30*(0.64*(1+P/30)/0.95 - 1)"},
 "derived_percentiles_if_possible":{"gymnast_ratio":"dips ~ 0.62-0.72 x push-ups (highly trained, dips practiced)",
   "model_for_ordinary_men_from_CSEP_percentiles":{"20-29":{"p20":1.3,"p50":7.1,"p80":14.1,"p95":19.6},"30-39":{"p50":3.0,"p80":10.1,"p95":14.9},"40-49":{"p80":6.7,"p95":9.9}},
   "recommendation":"Blend: dips ~ 0.3 x push-ups for untrained (model) rising to ~0.65 x for trained (gymnasts); low confidence."},
 "body_mass_recorded":True,"sources":[S_PAOLI],"confidence":"low","verification":"full text (gymnast data); model assumptions not verified"})

# ---------- SIT-UPS / CRUNCHES ----------
kr={"M":{"20-29":(3657,50.3,9.6,33,43,51,58,64),"30-39":(1945,47.5,9.7,31,40,48,55,62),"40-49":(1172,40.6,10.3,23,32,42,49,56),"50-59":(897,35.6,9.7,20,28,36,44,52),"60-64":(796,31.0,8.8,17,24,31,38,45),"20-64":(8467,44.9,11.7,24,35,46,55,62)},
    "F":{"20-29":(2134,39.0,11.0,19,30,40,48,55),"30-39":(717,32.7,11.4,13,24,33,42,51),"40-49":(694,28.3,11.5,7,20,29,37,47),"50-59":(845,20.5,10.5,1,12,21,29,37),"60-64":(536,15.9,10.2,0,6,17,24,33),"20-64":(4926,30.9,13.9,6,19,32,43,52)}}
def kk(t): return {"n":t[0],"mean":t[1],"sd":t[2],"p5":t[3],"p20":t[4],"p50":t[5],"p80":t[6],"p95":t[7]}
entries.append({
 "exercise":"sit-ups","approach":"direct",
 "population":"Korean adults 21-64 tested at KSPO National Fitness 100 centres (13,393 records; 8,467 men mean 73.6 +/- 9.5 kg, 4,926 women 56.9 +/- 7.6 kg)",
 "protocol":"KSPO adult sit-up test (column 'sit-ups counts'; adult protocol is believed to be crossed-arm sit-ups in 60 s - not verified from the dataset)",
 "numbers":{"computed_from_individual_records":{"men":{k:kk(v) for k,v in kr["M"].items()},"women":{k:kk(v) for k,v in kr["F"].items()}},
   "by_body_mass_men_20_39":[{"kg":"<60","n":220,"p20":39,"p50":47,"p80":55},{"kg":"60-70","n":1620,"p20":44,"p50":51,"p80":58},{"kg":"70-80","n":2331,"p20":43,"p50":51,"p80":58},{"kg":"80-90","n":1082,"p20":41,"p50":50,"p80":57},{"kg":">=90","n":349,"p20":33,"p50":44,"p80":52}],
   "by_body_mass_men_40_64":[{"kg":"<60","n":244,"p50":34},{"kg":"60-70","n":997,"p50":36},{"kg":"70-80","n":1174,"p50":37},{"kg":"80-90","n":357,"p50":36},{"kg":">=90","n":93,"p50":31}],
   "by_body_mass_women_20_39":[{"kg":"<50","n":516,"p20":26,"p50":37,"p80":45},{"kg":"50-55","n":806,"p20":29,"p50":39,"p80":47},{"kg":"55-60","n":726,"p20":30,"p50":39,"p80":48},{"kg":"60-70","n":643,"p20":28,"p50":40,"p80":47},{"kg":">=70","n":160,"p20":17,"p50":33,"p80":44}],
   "by_body_mass_women_40_64":[{"kg":"<50","n":282,"p50":22},{"kg":"50-55","n":558,"p50":24},{"kg":"55-60","n":575,"p50":23},{"kg":"60-70","n":547,"p50":20},{"kg":">=70","n":113,"p50":13}],
   "regressions_ln_situps":{"men":"ln(s) = 4.944 - 0.0132*age - 0.164*ln(mass_kg) (n=8461, s>0)","women":"ln(s) = 5.379 - 0.0226*age - 0.300*ln(mass_kg) (n=4807)",
     "with_body_fat":"men: +0.335*ln(mass) - 0.0227*BF%; women: +0.640*ln(mass) - 0.0381*BF% (heavier-but-lean is NOT penalised; fat is)"}},
 "derived_percentiles_if_possible":"Computed directly (linear-interpolated percentiles). Relationship with mass is an inverted U: flat 60-90 kg (men), drops >=90 kg; overall mass exponent about -0.16 (men) / -0.30 (women) at fixed age.",
 "body_mass_recorded":True,"sources":[S_KSPO],"confidence":"high (numbers) / licence unclear","verification":"full dataset (individual records analysed locally)"})

entries.append({
 "exercise":"sit-ups","approach":"direct",
 "population":"US Army soldiers 1988 (same survey as push-ups); body mass per age group as in the push-up entry",
 "protocol":"Max sit-ups in 2 minutes, knees 90 deg, hands behind head, ankles held, up to vertical",
 "numbers":{"men_by_age":{"17-21":{"p5":47,"p20":54,"p50":61,"p80":70,"p95":80,"mean":62.1,"sd":10.2,"n":1157,"body_mass_kg":75.3},
    "22-26":{"p5":46,"p20":52,"p50":60,"p80":70,"p95":83,"mean":61.5,"sd":11.3,"n":1688,"body_mass_kg":76.7},
    "27-31":{"p5":42,"p20":50,"p50":59,"p80":70,"p95":82,"mean":59.6,"sd":12.3,"n":1017,"body_mass_kg":78.2},
    "32-36":{"p5":39,"p20":45,"p50":55,"p80":66,"p95":80,"mean":55.8,"sd":12.7,"n":825,"body_mass_kg":79.2},
    "37-41":{"p5":34,"p20":40,"p50":49,"p80":61,"p95":76,"mean":50.5,"sd":13.2,"n":382,"body_mass_kg":79.3},
    "42-46":{"p5":25,"p20":32,"p50":44,"p80":55,"p95":70,"mean":44.6,"sd":13.3,"n":217,"body_mass_kg":80.6},
    "47-51":{"p5":26,"p20":29,"p50":35,"p80":55,"p95":68,"mean":40.1,"sd":13.6,"n":54,"body_mass_kg":81.2}},
  "women_by_age":{"17-21":{"p5":39,"p20":52,"p50":60,"p80":70,"p95":80,"mean":60.3,"sd":11.8,"n":111,"body_mass_kg":59.0},
    "22-26":{"p5":46,"p20":52,"p50":62,"p80":73,"p95":88,"mean":63.2,"sd":12.3,"n":228,"body_mass_kg":59.8},
    "27-31":{"p5":41,"p20":48,"p50":60,"p80":70,"p95":81,"mean":59.5,"sd":12.5,"n":164,"body_mass_kg":61.6},
    "32-36":{"p5":36,"p20":40,"p50":50,"p80":67,"p95":77,"mean":53.1,"sd":13.9,"n":119,"body_mass_kg":63.1},
    "37-41":{"p5":22,"p20":35,"p50":48,"p80":66,"p95":81,"mean":50.4,"sd":16.4,"n":34,"body_mass_kg":61.7},
    "42-46":{"p20":34,"p50":50,"p80":58,"mean":48.4,"sd":14.3,"n":18,"body_mass_kg":60.6}},
  "all":{"men":"58.7 +/- 12.6","women":"58.9 +/- 13.3"}},
 "derived_percentiles_if_possible":"Already percentiles (2-min test, trained population).",
 "body_mass_recorded":True,"sources":[S_ARMY],"confidence":"high","verification":"full text (PDF tables)"})

entries.append({
 "exercise":"sit-ups","approach":"direct",
 "population":"600,999 female undergraduates (18-24 y), Jiangsu, China; BMI 21.06 +/- 3.31",
 "protocol":"Bent-knee sit-ups, hands behind head, elbows to knees (Chinese National Student Physical Health Standard; standard is 1 min - duration not stated in paper)",
 "numbers":{"women_total":{"n":600999,"age":"19.39 +/- 1.20","sit_ups":"37.29 +/- 8.62","bmi":"21.06 +/- 3.31"},
   "by_grade":[{"grade":1,"n":172302,"sit_ups":"35.99 +/- 8.41","bmi":"21.32 +/- 3.49"},{"grade":2,"n":178294,"sit_ups":"37.04 +/- 8.51","bmi":"21.09 +/- 3.37"},{"grade":3,"n":158309,"sit_ups":"37.76 +/- 8.66","bmi":"20.89 +/- 3.23"},{"grade":4,"n":92094,"sit_ups":"39.38 +/- 8.68","bmi":"20.82 +/- 2.94"}]},
 "derived_percentiles_if_possible":{"women_normal_approx":{"p5":23.1,"p20":30.0,"p50":37.3,"p80":44.5,"p95":51.5}},
 "body_mass_recorded":True,"sources":[S_YIN],"confidence":"high","verification":"full text (Table 1)"})

entries.append({
 "exercise":"crunches","approach":"direct (ceiling-limited) / use sit-up data",
 "population":"19,678 male US Marines (79.4 +/- 10.3 kg); 1,224 men / 497 women Marines (2024)",
 "protocol":"USMC abdominal crunch, 2 minutes (discontinued event; max points at 100-115 reps -> strong ceiling)",
 "numbers":{"men_2017_2018":"111.2 +/- 9.0 (n=19,678)","men_other_2024":"113 +/- 9","women_other_2024":"106 +/- 10","men_300":"114 +/- 9","women_300":"115 +/- 15"},
 "derived_percentiles_if_possible":"Not usable for percentiles (Marines stop at the max-score count). For crunch-type ranking use the KSPO sit-up distribution with a protocol factor, or Canada CHMS partial curl-ups (StatCan Open Licence; capped at 25, adult tables not retrieved).",
 "body_mass_recorded":True,"sources":[S_KEEFER_M,S_300],"confidence":"low","verification":"full text"})

# ---------- INVERTED ROWS ----------
entries.append({
 "exercise":"inverted rows","approach":"ratio to pull-ups (biomechanical load model; no direct data)",
 "population":"model applied to Marine pull-up distributions",
 "protocol":"Feet on floor, body near horizontal, bar at ~hip height",
 "numbers":{"assumptions":"inverted-row load ~0.65 x BW at near-horizontal body (published suspension-row studies report load rising with inclination; exact % not verified), pull-up load ~0.95 x BW; reps_row = 30*(0.95*(1+P/30)/0.65 - 1)"},
 "derived_percentiles_if_possible":{"men_from_USMC_men":{"p5":28.2,"p20":34.0,"p50":40.2,"p80":46.3,"p95":52.1},"women_from_USMC_women":{"p5":13.8,"p20":19.0,"p50":24.2,"p80":29.5,"p95":34.6},
   "zero_pull_up_people":"can still do ~0-14 rows; model gives 13.8 rows at exactly 1 pull-up"},
 "body_mass_recorded":False,
 "sources":[{"citation":"Giancotti GF et al. J Strength Cond Res 2021 (strap load vs body inclination) and PLoS One 2023 suspension-exercise load study (PMC10516423) - load/angle relationship only","url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC10516423/","licence":"PLoS One CC BY 4.0 (journal default); JSCR all rights reserved","licence_evidence":"PLOS ONE journal-wide CC BY policy (not opened for this article)","usable_commercially":"facts-only","reasoning":"Only the general load-angle finding is used."}],
 "confidence":"low","verification":"search snippet (model inputs); no direct inverted-row rep data found. NHANES NNYFS 2012 'modified pull-up' (an inverted-row-style test, public domain CDC data, ages 6-15 only) is the only open individual-level dataset - not processed."})

# ---------- HSPU ----------
entries.append({
 "exercise":"handstand push-ups","approach":"direct (anchor points) + ratio to push-ups (model)",
 "population":"(a) survey of exercisers (likely CrossFit; source not identified); (b) 30 male collegiate gymnasts 66-67 kg (2026 RCT)",
 "protocol":"(a) strict HSPU, >=1 rep; (b) max HSPU in 40 s on an inversion stand, head to 15 cm above floor",
 "numbers":{"survey":"39.5% of respondents could not do 1 strict HSPU; men 20.3% unable, women 54.7% unable","gymnasts":"baseline values only in figures (not extracted); body mass 66.1-67.0 kg"},
 "derived_percentiles_if_possible":{"trained_population_anchors":"men P20 ~ 0-1 rep; women P50 ~ 0-1 rep",
   "ordinary_adults_model":"With push-up load 0.64 BW, HSPU moved load ~0.92 BW and overhead-press strength ~0.63 x horizontal press, first HSPU needs ~38+ push-ups => for ordinary men only ~P90+ can do any; women ~P98+. Low confidence."},
 "body_mass_recorded":True,
 "sources":[{"citation":"Unidentified strict-HSPU survey study (seen via ResearchGate search snippet)","url":"https://www.researchgate.net/publication/328361562_Exercise_Technique_Handstand_Push-Up (search context only)","licence":"unknown","licence_evidence":"not retrieved","usable_commercially":"unclear","reasoning":"Source not identified; treat as anecdotal."},
   {"citation":"Effects of low-load blood flow restriction vs. high-load resistance training on upper-body strength in male collegiate gymnasts: RCT. J Exerc Sci Fit 2026 (PMC12925195)","url":"https://pmc.ncbi.nlm.nih.gov/articles/PMC12925195/","licence":"CC BY 4.0","licence_evidence":"'This is an open access article under the CC BY license (http://creativecommons.org/licenses/by/4.0/).'","usable_commercially":"yes","reasoning":"CC BY; but baseline HSPU/dip/pull-up reps are only in figures."}],
 "confidence":"low","verification":"search snippet (survey); full text (gymnast study, no baseline numbers in text)"})

# ---------- MUSCLE-UPS ----------
entries.append({
 "exercise":"muscle-ups","approach":"ratio to pull-ups + dips (heuristic; no data)",
 "population":"none","protocol":"bar muscle-up",
 "numbers":{"found":"No study or open dataset with max muscle-up reps or % of people able to perform one (only EMG studies, e.g. IJES 2023 ring vs bar muscle-up, PMC10824315, CC BY-ND)."},
 "derived_percentiles_if_possible":{"heuristic_unsourced":"first muscle-up typically once strict pull-ups >= ~10-12 and dips >= ~10-15; muscle-ups ~ max(0,(pull-ups - 10)/2.5). Treat as placeholder."},
 "body_mass_recorded":False,"sources":[],"confidence":"low","verification":"none"})

# ---------- BODYWEIGHT SCALING ----------
entries.append({
 "exercise":"ALL bodyweight moves (body-mass scaling rule)","approach":"scaling model",
 "population":"77 male PE students (Markovic & Jaric 2004); US military test data (Vanderburgh)",
 "protocol":"Allometric P = a*M^b",
 "numbers":{"markovic_jaric_2004":"tests of supporting body weight (chin-ups, squats) followed b = -0.33 for body mass (b = -1 for height); external-force tests b = 0.67; rapid movements b = 0",
   "vanderburgh_2007":"push-ups, sit-ups, crunches, curl-ups and run scores scale with body mass^(-1/3); 15-20% penalty for 90 kg vs 60 kg men (75 vs 45 kg women)",
   "empirical_checks_here":{"marines_pullups":"men 86.2 kg -> 20 reps vs 80.8 kg (leaner) -> 23 reps; women 67.5 kg -> 9 vs 64.3 kg -> 13 (fat mass drives most of the difference)",
     "firefighters_pushups":"mean BMI 33.1 at 0-10 reps vs 26.8 at >=41 reps",
     "korean_situps":"exponent -0.16 (men) / -0.30 (women) at fixed age; positive with lean mass, negative with fat%"}},
 "derived_percentiles_if_possible":{"recommended_rule":"reps_at_mass(M) = reps_ref * (M / M_ref)^(-1/3) when extra mass is lean; use a steeper penalty (or BF%-based term) when the extra mass is fat. Reference masses: Army men 77.5 kg / women 60.8 kg; USMC men 79.4 kg / women 62.8 kg; Chinese students ~68 kg; Korean men 73.6 / women 56.9 kg.",
   "example":"A 100 kg man vs 77.5 kg reference: x(100/77.5)^(-1/3) = 0.918 -> 8% fewer reps at equal relative fitness."},
 "body_mass_recorded":True,"sources":[S_MARKOVIC,S_VANDERBURGH],"confidence":"medium","verification":"abstracts (Europe PMC) + full-text datasets above"})

json.dump(entries,open(OUT,"w"),indent=1,ensure_ascii=False)
print("wrote",len(entries),"entries")
