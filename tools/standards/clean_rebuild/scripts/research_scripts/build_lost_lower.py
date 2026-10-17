import json, sys
OUT = sys.argv[1]

LEGAL = ("General legal position (US/EU): individual measured values (means, SDs, ratios) are facts and not "
         "copyrightable even when the article is paywalled; the table layout, prose and figures are protected. "
         "EU sui generis database right does not attach to a handful of numbers taken from one paper. "
         "So 'facts-only' = re-key the numbers, cite the paper, do not copy tables/text.")

# --- licence evidence blocks reused across entries ---
JSCR = {
  "licence": "Subscription journal, copyright National Strength and Conditioning Association (published by Wolters Kluwer). Only individual hybrid-OA articles carry CC BY-NC-ND 4.0.",
  "licence_evidence": "Search-result text for recent JSCR OA articles: 'published by Wolters Kluwer on behalf of the NSCA under a CC BY-NC-ND 4.0 license ... can't be altered or used commercially without the journal's permission' (https://pmc.ncbi.nlm.nih.gov/articles/PMC5441887/ ; https://pmc.ncbi.nlm.nih.gov/articles/PMC11042517/). The 2005-2019 articles cited here are not in PMC OA and sit behind the LWW paywall (journals.lww.com/nsca-jscr).",
  "usable_commercially": "facts-only",
  "reasoning": "Paywalled/NC article: text and tables cannot be reproduced commercially, but the reported numbers (1RMs, regression coefficients) are facts. " + LEGAL,
}
FRONTIERS = {
  "licence": "CC BY 4.0 (Frontiers publishes all articles under CC BY).",
  "usable_commercially": "yes",
  "reasoning": "CC BY permits commercial reuse with attribution; numbers are facts anyway.",
}
ISBS = {
  "licence": "ISBS Proceedings Archive (Northern Michigan University Digital Commons) - described by ISBS as an 'open access' archive; authors keep copyright; no Creative Commons licence found.",
  "licence_evidence": "commons.nmu.edu/isbs/policies.html (via search snippet): authors 'initially own the copyright' and must own it or be authorised to submit; isbs.org/conferences/proceedings-archive describes the proceedings as archived in two 'open access' journals (Konstanz 1983-2016, NMU 2017-). No CC licence statement surfaced.",
  "usable_commercially": "facts-only",
  "reasoning": "Free to read but all rights reserved by authors; reuse the numbers as facts with citation, do not copy text/tables. " + LEGAL,
}
PEERJ = {
  "licence": "CC BY 4.0 (PeerJ's standard licence for all articles).",
  "licence_evidence": "Not quoted from the article page in this session (peerj.com unreachable; snippets did not include the licence line). PeerJ's published policy is that every article is CC BY 4.0 - confirm on the article page footer.",
  "usable_commercially": "yes",
  "reasoning": "CC BY allows commercial reuse with attribution; numbers are facts regardless.",
}
JSSM = {
  "licence": "Open access; JSSM articles are CC BY 4.0 or CC BY-NC-ND 4.0 depending on article (licence for 2004-era articles not confirmed).",
  "licence_evidence": "scienceverse/papers JSSM corpus README (GitHub): 'License: CC-BY 4.0 or CC-BY-NC-ND 4.0, depending on the article -- check each paper's own license before reuse' (https://github.com/scienceverse/papers/tree/main/jssm). Covers 2014-2026; the 2004 article was not checked.",
  "usable_commercially": "facts-only",
  "reasoning": "If the article is CC BY-NC-ND, the text/tables are not commercially reusable, but the numbers are facts. " + LEGAL,
}

entries = []

def E(**k): entries.append(k)

# ---------------- HIP THRUST ----------------
E(exercise="hip thrust (barbell)",
  approach="ratio to back squat AND ratio to conventional deadlift",
  numbers=[
    {"source":"ISBS hip-joint-kinetics paper (Univ. of Bath)","population":"resistance-trained men","sex":"male","n":6,
     "values":"1RM hip thrust 180 +/- 46 kg; 1RM deadlift 174 +/- 35 kg; 1RM back squat 145 +/- 36 kg (mean +/- SD)",
     "note":"Same 6 men performed all three lifts (tested at 90% 1RM for kinetics). Values quoted identically in two copies (ResearchGate + NMU ISBS archive) per search snippets."},
    {"source":"Hammond et al. 2019 PeerJ 7:e7337","population":"trained men, 22.1 +/- 0.6 y, 79.8 +/- 13.8 kg","sex":"male","n":14,
     "values":"Back-squat group (n=7): squat 1RM +11.8 kg (= +10.2%); hip-thrust group (n=7): hip thrust 1RM +27.5 kg (= +24.8%) after 4 wk at 80% 1RM",
     "note":"Baselines back-calculated: squat ~= 11.8/0.102 = 115.7 kg; hip thrust ~= 27.5/0.248 = 110.9 kg. DIFFERENT GROUPS, so cross-ratio is rough. Paper text is inconsistent about mean+/-SD vs median(IQR)."},
    {"source":"Plotkin et al. 2023 Front Physiol 14:1279170","population":"untrained college-aged men and women","sex":"both","n":34,
     "values":"3RM (not 1RM) squat, hip thrust and deadlift measured pre/post 9 wk. Between-group differences in change only were retrieved: squat 3RM favoured squat group by 14 +/- 2 kg [CI95 9,18]; hip thrust 3RM favoured hip-thrust group by 26 +/- 5 kg [CI95 16,34]; deadlift 3RM no difference (0 +/- 2 kg).",
     "note":"BASELINE 3RM TABLE NOT RETRIEVED - this is the best source for ordinary (untrained) men AND women; open the full text (PMC10593473 / frontiersin.org) to get baseline squat, hip-thrust and deadlift 3RMs by group (and possibly by sex)."},
  ],
  ratio_value_if_derivable={"hip_thrust_to_back_squat":"1.24 (180/145; trained men, ISBS n=6); ~0.96 (110.9/115.7; separate groups of trained men, Hammond 2019 - weak)",
                            "hip_thrust_to_deadlift":"1.03 (180/174; ISBS n=6)",
                            "comment":"Hip thrust strength depends heavily on familiarity: novices start near or below their squat and gain fast (+24.8% in 4 wk in Hammond). For ordinary gym-goers a ratio of ~1.0-1.25 x back squat looks plausible but needs Plotkin's baseline data to confirm for untrained men and women."},
  sources=[
    dict(citation="(Authors not confirmed - University of Bath group) 'A comparison of hip joint kinetics during the barbell hip thrust, deadlift and back squat.' ISBS Proceedings Archive (International Society of Biomechanics in Sports conference).",
         url="https://commons.nmu.edu/cgi/viewcontent.cgi?article=1419&context=isbs ; https://researchportal.bath.ac.uk/en/publications/a-comparison-of-hip-joint-kinetics-during-the-barbell-hip-thrust-/", **ISBS),
    dict(citation="Hammond A, et al. (2019). The effects of a 4-week mesocycle of barbell back squat or barbell hip thrust strength training upon isolated lumbar extension strength. PeerJ 7:e7337.",
         url="https://peerj.com/articles/7337/ ; https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6662562/", **PEERJ),
    dict(citation="Plotkin DL, et al. (2023). Hip thrust and back squat training elicit similar gluteus muscle hypertrophy and transfer similarly to the deadlift. Front Physiol 14:1279170. doi:10.3389/fphys.2023.1279170",
         url="https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2023.1279170/full ; https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10593473/",
         licence_evidence="DOAJ lists the article (https://doaj.org/article/e0f865b8c13d4086895c8762e72a6988); Frontiers' standard licence is CC BY 4.0 (the Frontiers CC BY line was seen verbatim for a sister Frontiers article in this session). Not quoted from this article's page.", **FRONTIERS),
    dict(citation="Barbalho M, et al. (2020). Back squat vs. hip thrust resistance-training programs in well-trained women. Int J Sports Med 41(5):306-310. (squat 1RM 91.9 -> 124.9 kg reported, BM 69.5 kg)",
         url="https://www.strongerbyscience.com/barbalho/",
         licence="Paywalled (Thieme).", licence_evidence="n/a", usable_commercially="no",
         reasoning="DO NOT USE: Vigotsky, Nuckols & Heathers documented improbable data patterns across Barbalho et al.'s work and several Barbalho papers have been retracted. Excluded on data-integrity grounds, not licence."),
  ],
  confidence="medium-low (one tiny trained-male sample for the direct ratio; untrained data not yet extracted)",
  verification="ISBS numbers seen in two search snippets (not opened). Hammond baselines derived by arithmetic from reported kg and % gains. Plotkin baselines NOT retrieved - open the full text (top priority for this exercise).")

# ---------------- GLUTE BRIDGE ----------------
E(exercise="glute bridge (barbell, floor)",
  approach="ratio to hip thrust (no source found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="No study found reporting barbell glute-bridge 1RM. Still missing; any ratio to hip thrust would be an assumption.")

# ---------------- RDL / SLDL / GOOD MORNING ----------------
E(exercise="Romanian deadlift",
  approach="ratio to conventional deadlift (no published ratio found)",
  numbers=[{"source":"Lee et al. 2018","population":"men","sex":"male","n":21,"values":"RDL 1RM was measured (loads set at 70% RDL 1RM); kg values not retrieved; snippet does not show whether conventional deadlift 1RM was also tested."},
           {"source":"Coratella et al. 2022","population":"bodybuilders","sex":"male (reported as bodybuilders)","n":10,"values":"Each variant (RDL, step-RDL, stiff-leg DL) lifted at 80% of its own 1RM, so per-variant 1RMs were measured; kg values not retrieved."}],
  ratio_value_if_derivable=None,
  sources=[
    dict(citation="Lee S, Schultz J, Timgren J, Staelgraeven K, Miller M, Liu Y (2018). An electromyographic and kinetic comparison of conventional and Romanian deadlifts. J Exerc Sci Fit 16(3):87-93. doi:10.1016/j.jesf.2018.08.001",
         url="https://www.sciencedirect.com/science/article/pii/S1728869X18301291 ; https://doaj.org/article/cb967057d3314b8cbda698d30b84ae6c",
         licence="Gold OA (Elsevier for the Society of Chinese Scholars on Exercise Physiology and Fitness); JESF articles are normally CC BY-NC-ND 4.0.",
         licence_evidence="DOAJ-listed (doaj.org/article/cb967057d3314b8cbda698d30b84ae6c); the exact licence line was not quoted in snippets - confirm on the article page.",
         usable_commercially="facts-only", reasoning="NC-ND blocks commercial reuse of text/tables; numbers are facts. " + LEGAL),
    dict(citation="Coratella G, Tornatore G, Longo S, Esposito F, Ce E (2022). An electromyographic analysis of Romanian, step-Romanian, and stiff-leg deadlift: implication for resistance training. IJERPH 19(3):1903. doi:10.3390/ijerph19031903",
         url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8835508/",
         licence="CC BY 4.0 (MDPI standard).", licence_evidence="MDPI publishes all IJERPH articles under CC BY 4.0 (journal policy); not quoted from the page this session.",
         usable_commercially="yes", reasoning="CC BY; but it only gives RDL vs SLDL vs step-RDL 1RMs, not conventional deadlift."),
  ],
  confidence="none for a ratio", verification="No RDL:deadlift ratio found in any snippet. Still missing. Coratella's table would at least give RDL : stiff-leg DL.")

E(exercise="stiff-leg deadlift",
  approach="ratio to Romanian deadlift / conventional deadlift (not found)",
  numbers=[{"source":"Coratella et al. 2022","n":10,"sex":"male","population":"bodybuilders","values":"stiff-leg DL 1RM measured alongside RDL and step-RDL 1RM; kg not retrieved"}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="Coratella G, et al. (2022). IJERPH 19(3):1903 (see Romanian deadlift entry).", url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8835508/",
                licence="CC BY 4.0 (MDPI).", licence_evidence="MDPI journal-wide CC BY 4.0 policy; not quoted this session.", usable_commercially="yes", reasoning="CC BY.")],
  confidence="none", verification="Numbers not retrieved. Still missing.")

E(exercise="good morning",
  approach="ratio to back squat (not found)",
  numbers=[{"source":"Vigotsky et al. 2015","values":"Good-morning 1RM was measured (sets at 50-90% 1RM); n, sex and kg not retrieved; no squat 1RM reported per snippets."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="Vigotsky AD, Harper EN, Ryan DR, Contreras B (2015). Effects of load on good morning kinematics and EMG activity. PeerJ 3:e708. PMID 25653899",
                url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4304869/ ; https://peerj.com/articles/708/", **PEERJ)],
  confidence="none", verification="Still missing. PeerJ requires raw data - the supplementary data file would give individual GM 1RMs (no squat comparison).")

# ---------------- RACK PULL / DEFICIT ----------------
E(exercise="rack pull (above/below knee)",
  approach="ratio to conventional deadlift (proxy only)",
  numbers=[{"source":"Beckham et al. 2012 (via secondary summary)","population":"competitive powerlifters","sex":"not stated","n":14,
            "values":"Isometric pull force ~21% higher with bar just above the knee than with bar at floor position (ISOMETRIC, not 1RM)."},
           {"source":"coaching / 2022 wrestler pilot (secondary)","values":"Partial-ROM pulls prescribed at 105-120% of full deadlift 1RM (a prescription, not a measured 1RM)."}],
  ratio_value_if_derivable="~1.05-1.20 x deadlift (very low confidence; isometric proxy + training prescriptions; depends on pin height)",
  sources=[dict(citation="Beckham G, Lamont H, Sato K, Ramsey M, Haff GG, Stone M (2012). Isometric strength of powerlifters in key positions of the conventional deadlift. Journal of Trainology 1:32-35. (identified from a blog summary - verify)",
                url="(original not opened; seen via secondary summary in search results)",
                licence="Journal of Trainology is free-to-read; licence not checked.", licence_evidence="none retrieved", usable_commercially="unclear",
                reasoning="Licence not researched due to search budget; the 21% figure is a fact if verified, but it is isometric force, not a dynamic 1RM.")],
  confidence="very low", verification="Secondary summary only. Treat as placeholder.")

E(exercise="deficit deadlift",
  approach="ratio to conventional deadlift (not found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="No study with deficit-deadlift 1RM found. Still missing.")

# ---------------- SQUAT VARIANTS ----------------
E(exercise="Smith machine squat",
  approach="ratio to (free-weight) back squat",
  numbers=[{"source":"Cotterman et al. 2005","population":"young adults","sex":"16 men, 16 women","n":32,
            "values":"Women: SM squat 1RM (kg) = 28.3 + 0.73 x FW squat 1RM (abstract text renders '=' as '5'; sign of 28.3 should be checked). Squat 1RM was greater on the Smith machine than with free weights (abstract: for women; a secondary summary says for both sexes). Men's squat equation not retrieved. (Bench: SM = -6.76 + 0.95 x FW, already in studies.json.)"}],
  ratio_value_if_derivable={"women_SM_over_FW":"1.20 at FW 60 kg; 1.08 at FW 80 kg; 1.01 at FW 100 kg (from the equation)",
                            "men":"direction SM >= FW; magnitude not retrieved"},
  sources=[dict(citation="Cotterman ML, Darby LA, Skelly WA (2005). Comparison of muscle force production using the Smith machine and free weights for bench press and squat exercises. J Strength Cond Res 19(1):169-176. PMID 15705030",
                url="https://pubmed.ncbi.nlm.nih.gov/15705030/", **JSCR)],
  confidence="medium for women (one equation, n=16); low for men",
  verification="Equation from PubMed abstract text via search snippet (garbled symbols). Open the abstract to confirm sign and whether a men's equation exists. Note Smith machines differ (angled vs vertical, counterbalanced bars).")

E(exercise="hack squat (machine)",
  approach="ratio to back squat",
  numbers=[{"source":"Clark, Lambert & Hunter 2019","population":"resistance-trained males","sex":"male","n":10,
            "values":"Abstract: mean hack-squat 1RM and submaximal loads were significantly higher than back squat (p < 0.0001). kg values not retrieved. Test loads were set from 'system mass max' = 1RM + 0.886 x body mass."}],
  ratio_value_if_derivable="direction only: hack squat 1RM > back squat 1RM (magnitude not retrieved; machine-specific because sled angle and counterweight vary)",
  sources=[dict(citation="Clark DR, Lambert MI, Hunter AM (2019). Trunk muscle activation in the back and hack squat at the same relative loads. J Strength Cond Res 33(Suppl 1):S60-S69. doi:10.1519/JSC.0000000000002144",
                url="https://dspace.stir.ac.uk/handle/1893/26150 ; https://researchonline.ljmu.ac.uk/id/eprint/9010/1/Trunk_muscle_activation_in_the_back_and_hack_squat.95870.pdf",
                licence=JSCR["licence"] + " Author accepted manuscripts are free in the Stirling (STORRE) and LJMU repositories (repository licence not checked).",
                licence_evidence=JSCR["licence_evidence"], usable_commercially="facts-only", reasoning=JSCR["reasoning"])],
  confidence="low (direction only)",
  verification="Open the free accepted manuscript (Stirling/LJMU PDF) to read the 1RM table - quick win.")

E(exercise="box squat",
  approach="ratio to back squat (not found)",
  numbers=[{"source":"McBride et al. 2010","population":"resistance-trained men (described elsewhere as powerlifters)","sex":"male","n":8,
            "values":"Body mass 107.65 +/- 29.79 kg; back squat 1RM 200.11 +/- 58.91 kg. Box squat done at 60/70/80% of SQUAT 1RM; no box-squat 1RM. Peak power at 80%: squat 2050 +/- 486 W vs box squat 2197 +/- 544 W; 'minimal differences' in kinetics/EMG."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="McBride JM, Skinner JW, Schafer PC, Haines TL, Kirby TJ (2010). Comparison of kinetic variables and muscle activity during a squat vs. a box squat. J Strength Cond Res 24(12):3195-3199. doi:10.1519/JSC.0b013e3181f6399a",
                url="https://www.researchgate.net/publication/49659495", **JSCR)],
  confidence="none for a ratio", verification="Still missing. Similar kinetics at matched loads suggest ~1.0 x back squat, but that is an inference, not a measurement.")

E(exercise="Bulgarian split squat (rear-foot-elevated split squat)",
  approach="ratio to back squat",
  numbers=[{"source":"Han et al. 2025","population":"trained male boxers, 19.7 +/- 1.0 y; both lifts on a Smith machine","sex":"male","n":20,
            "values":"Back squat 1RM 135.25 +/- 30.21 kg; BSS 1RM dominant leg 95.25 +/- 25.36 kg; non-dominant 90.25 +/- 24.89 kg (external load).",
            "note":"One search snippet said the BSS cell was blank and that loads were % of 'predicted 1RM'; a second snippet gave the BSS values. Verify in the table."},
           {"source":"Speirs et al. 2016","population":"academy rugby players","sex":"male","n":18,
            "values":"3RM -> predicted 1RM for RFESS and back squat; only % changes retrieved (split-squat group: RFESS +9.2%, squat +5.7%; squat group: RFESS +10.5%, squat +5.0%)."},
           {"source":"McCurdy et al. 2004","population":"30 untrained (22 W, 8 M) + 22 trained (12 W, 10 M)","sex":"both","n":52,
            "values":"Modified unilateral (rear-foot-elevated) squat 1RM and 3RM measured by sex/training status; reliability r = 0.97-0.99; kg values not retrieved."}],
  ratio_value_if_derivable="BSS per leg / back squat = 0.70 (dominant) and 0.67 (non-dominant) in trained men on a Smith machine (Han 2025)",
  sources=[dict(citation="Han, Xie, Niu, Jia, Zhang (2025). Unilateral and bilateral load-velocity relationships in athletes: evidence from a study in boxers. Front Sports Act Living 7:1598396. doi:10.3389/fspor.2025.1598396",
                url="https://www.frontiersin.org/journals/sports-and-active-living/articles/10.3389/fspor.2025.1598396/full ; https://pmc.ncbi.nlm.nih.gov/articles/PMC12597917/",
                licence_evidence="Search result quoting the article: 'open-access article distributed under the terms of the Creative Commons Attribution License (CC BY)'.", **FRONTIERS),
           dict(citation="Speirs DE, Bennett MA, Finn CV, Turner AP (2016). Unilateral vs. bilateral squat training for strength, sprints, and agility in academy rugby players. J Strength Cond Res 30(2):386-392. PMID 26200193",
                url="https://pubmed.ncbi.nlm.nih.gov/26200193/", **JSCR),
           dict(citation="McCurdy K, Langford GA, Cline AL, Doscher M, Hoff R (2004). The reliability of 1- and 3RM tests of unilateral strength in trained and untrained men and women. J Sports Sci Med 3(3):190-196.",
                url="https://www.jssm.org/jssm-03-190.xml-Fulltext", **JSSM)],
  confidence="medium-low (one study, trained men, Smith machine)",
  verification="Han values from search snippet - verify table. McCurdy 2004 (free on jssm.org) is the best lead for men AND women, trained AND untrained.")

E(exercise="barbell lunge",
  approach="ratio to back squat (not found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="No lunge 1RM study surfaced. Still missing; the BSS ratio (~0.67-0.70 x back squat per leg) is the nearest proxy.")
E(exercise="dumbbell lunge",
  approach="ratio to barbell lunge / back squat (not found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="Still missing.")
E(exercise="goblet squat",
  approach="ratio to back/front squat (not found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="No goblet-squat 1RM research found. Still missing (practically capped by the heaviest dumbbell/kettlebell people can hold).")
E(exercise="dumbbell squat",
  approach="ratio to back squat (not found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="Still missing.")
E(exercise="Zercher squat",
  approach="ratio to front squat (not found)",
  numbers=None, ratio_value_if_derivable=None, sources=[],
  confidence="none", verification="No research found. Still missing.")
E(exercise="overhead squat",
  approach="ratio to snatch / back squat (not found)",
  numbers=[{"source":"review-type snippets","values":"Only correlations retrieved: front/back squat 1RM vs snatch r = 0.73-0.95; overhead press vs split jerk r = 0.90. No overhead-squat 1RM data."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="Search results incl. 'Relationship of maximum strength to weightlifting performance' and Predictive validity of the snatch pull force-velocity profile (PMC8167790).",
                url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8167790/", licence="not checked", licence_evidence="none", usable_commercially="unclear",
                reasoning="Correlations only; nothing usable for a ratio.")],
  confidence="none", verification="Still missing.")

# ---------------- MACHINES ----------------
E(exercise="leg extension (machine)",
  approach="direct norms by sex, and ratio to leg press",
  numbers=[{"source":"Massini et al. 2022","population":"well-trained young men","sex":"male","n":30,"values":"1RM leg press 45 deg, knee extension, leg curl (plus arm curl, bench, seated row) measured; kg not retrieved."},
           {"source":"Hoeger et al. 1990","population":"untrained men 38, untrained women 40, trained men 25, trained women 26","sex":"both","n":129,
            "values":"1RM on 7 Universal-type machine lifts incl. knee extension, leg curl, leg press, by sex x training status; kg not retrieved (only reps at 40/60/80% seen)."},
           {"source":"Levinger et al. 2009","population":"untrained middle-aged adults (~51 y)","sex":"25 men, 28 women","n":53,
            "values":"1RM on 7 machine exercises (leg press confirmed; leg extension/leg curl likely); ICC > 0.99; kg not retrieved."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="Massini DA, et al. (2022). Single- and multi-joint maximum weight lifting relationship to free-fat mass in different exercises for upper- and lower-limbs in well-trained male young adults. IJERPH 19(7):4020. doi:10.3390/ijerph19074020",
                url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8997733/", licence="CC BY 4.0 (MDPI).",
                licence_evidence="MDPI journal-wide CC BY 4.0 policy; not quoted from page this session.", usable_commercially="yes",
                reasoning="CC BY. Would give leg extension : leg press and leg curl : leg extension ratios in trained men once the table is read."),
           dict(citation="Hoeger WWK, Hopkins DR, Barette SL, Hale DF (1990). Relationship between repetitions and selected percentages of one repetition maximum: a comparison between untrained and trained males and females. J Appl Sport Sci Res 4(2):47-54.",
                url="https://journals.lww.com/nsca-jscr/abstract/1990/05000/relationship_between_repetitions_and_selected.4.aspx", **JSCR),
           dict(citation="Levinger I, Goodman C, Hare DL, Jerums G, Toia D, Selig S (2009). The reliability of the 1RM strength test for untrained middle-aged individuals. J Sci Med Sport 12(2):310-316. doi:10.1016/j.jsams.2007.10.007",
                url="https://vuir.vu.edu.au/4408/", licence="Paywalled (Elsevier); VU repository record.",
                licence_evidence="ScienceDirect subscription article; no OA licence found (search snippets).", usable_commercially="facts-only", reasoning="Numbers are facts. " + LEGAL),
           dict(citation="Strength Level leg-extension standards (average 1RM 99 kg men / 61 kg women; seen in search results).",
                url="https://strengthlevel.com/strength-standards/leg-extension/kg", licence="Proprietary website; all rights reserved.",
                licence_evidence="Project already lists Strength Level as an excluded source (surveys.json).", usable_commercially="no",
                reasoning="Excluded source; numbers recorded here only so nobody re-adds them by accident.")],
  confidence="none (no numbers extracted from usable sources)",
  verification="Still missing. Best quick wins: Massini 2022 (CC BY, PMC) table; Hoeger 1990 table (covers untrained men AND women).")

for nm in ["lying leg curl (machine)", "seated leg curl (machine)"]:
  E(exercise=nm,
    approach="ratio to leg extension (H:Q 1RM ratio) / direct norms",
    numbers=[{"source":"Massini 2022 / Hoeger 1990 / Levinger 2009 (see leg extension)","values":"leg curl 1RM measured in all three; kg not retrieved. Hoeger does not say lying vs seated (Universal-type machine, likely prone/lying)."},
             {"source":"coaching rules of thumb (not research)","values":"leg curl >= 60-75% of leg extension; isokinetic H:Q ~0.6 with no sex difference (Sex-Based Comparisons of H:Q Across the Velocity Spectrum, PubMed 39953723, n=27) - isokinetic torque, not machine 1RM."}],
    ratio_value_if_derivable="not from 1RM research; isokinetic H:Q ~0.6 (proxy only)",
    sources=[dict(citation="Sex-Based Comparisons of Hamstrings-to-Quadriceps Ratio Across the Velocity Spectrum (PubMed 39953723).", url="https://pubmed.ncbi.nlm.nih.gov/39953723/",
                  licence="not checked", licence_evidence="none", usable_commercially="unclear",
                  reasoning="Isokinetic data: only supports 'no sex difference in H:Q', not a machine 1RM ratio.")],
    confidence="none", verification="Still missing. Seated vs lying difference not found either.")

E(exercise="hip abduction machine",
  approach="direct norms / sex ratio (not found)",
  numbers=[{"source":"isokinetic study (PMC4164157)","n":36,"population":"collegiate athletes","sex":"both",
            "values":"Hip abductor isokinetic peak torque men 1.29 vs women 1.13 Nm/kg (significant); adductor torque no sex difference. NOT machine 1RM."}],
  ratio_value_if_derivable="female/male abductor torque per kg ~0.88 (isokinetic proxy)",
  sources=[dict(citation="Comparison of isokinetic hip abduction and adduction peak torques and ratio between sexes (PMC4164157).", url="https://pmc.ncbi.nlm.nih.gov/articles/PMC4164157/",
                licence="not checked", licence_evidence="none", usable_commercially="unclear", reasoning="Proxy only; licence not researched."),
           dict(citation="Strength Level hip abduction/adduction standards (seen in search results).", url="https://strengthlevel.com/strength-standards/hip-abduction/kg",
                licence="Proprietary.", licence_evidence="Project excludes Strength Level (surveys.json).", usable_commercially="no", reasoning="Excluded source.")],
  confidence="none", verification="No peer-reviewed machine 1RM norms found. Still missing.")
E(exercise="hip adduction machine",
  approach="direct norms / sex ratio (not found)",
  numbers=[{"source":"PMC4164157","values":"isokinetic adductor peak torque: no significant sex difference (per kg). NOT machine 1RM."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="PMC4164157 (see hip abduction).", url="https://pmc.ncbi.nlm.nih.gov/articles/PMC4164157/", licence="not checked", licence_evidence="none", usable_commercially="unclear", reasoning="Proxy only.")],
  confidence="none", verification="Still missing.")

E(exercise="calf raise (standing machine / seated / barbell / dumbbell / leg-press calf raise)",
  approach="ratio to back squat (standing) and standing vs seated",
  numbers=[{"source":"Moeck, Hartmann & Wirth 2023","population":"physical-education students (per snippets; sample size reported as 56 in one snippet)","sex":"not retrieved",
            "values":"Standing calf raise 1RM and squat 1RM both measured; only correlations retrieved (SCR 1RM vs SJ r ~0.57-0.66, vs CMJ r ~0.57-0.71)."},
           {"source":"elite youth soccer (PMC11054242)","values":"standing calf raise 1RM and squat 1RM measured; kg not retrieved."},
           {"source":"standing vs seated calf-raise training (PMC10753835)","n":14,"population":"untrained adults","values":"each leg trained at 70% of its standing or seated 1RM; baseline 1RMs not retrieved."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="Moeck S, Hartmann R, Wirth K (2023). Vertical jumping performance relates to the one-repetition maximum in the standing calf raise and in the squat. German Journal of Exercise and Sport Research 53(2):139-147.",
                url="https://fis.fhwn.ac.at/en/publications/vertical-jumping-performance-relates-to-the-one-repetition-maximu/",
                licence="Springer journal; OA status not checked.", licence_evidence="none retrieved", usable_commercially="facts-only",
                reasoning="Even if paywalled, the 1RM means are facts. " + LEGAL),
           dict(citation="Association between sprint and jump performance and maximum strength in standing calf raise or squat in elite youth soccer players (PMC11054242).",
                url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11054242/", licence="in PMC; licence not checked", licence_evidence="none", usable_commercially="unclear", reasoning="Not researched (budget)."),
           dict(citation="Triceps surae muscle hypertrophy is greater after standing versus seated calf-raise training. Front Physiol 2023, doi:10.3389/fphys.2023.1272106 (PMC10753835).",
                url="https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10753835/", licence_evidence="Frontiers journal (CC BY standard); not quoted this session.", **FRONTIERS)],
  confidence="none (no numbers)", verification="Still missing numbers. Three studies hold standing calf raise (and one seated) 1RM - open their tables. Barbell, dumbbell and leg-press calf raise: nothing found.")

E(exercise="(general) other leads and excluded sources",
  approach="n/a",
  numbers=[{"source":"Marzagao (Fitbod) 2026 arXiv 2603.17495 / SportRxiv 768","values":"Fitbod app logs: 37.7M sets, ~66,000 users, 505 exercises; analysis set 303,494 near-failure sets, 14,966 users, 388 exercises. Gives an exercise-dependent e1RM formula, not per-exercise norms; data not public."}],
  ratio_value_if_derivable=None,
  sources=[dict(citation="Marzagao T (2026). A weight-dependent 1RM prediction equation optimized on 303,494 near-failure sets across 388 exercises. arXiv:2603.17495; SportRxiv doi:10.51224/SportRxiv.768",
                url="https://sportrxiv.org/index.php/server/preprint/view/768", licence="not found in snippets", licence_evidence="none", usable_commercially="unclear",
                reasoning="Possible later source of ordinary-gym-goer per-exercise ratios if Fitbod publishes summaries; nothing usable now.")],
  confidence="n/a",
  verification="Access notes: peerj.com, frontiersin.org, ncbi/europepmc, arxiv, dspace.stir.ac.uk, ljmu, commons.nmu.edu, unm.edu all blocked from this sandbox (WebFetch DNS fail / proxy 403). Only search snippets were used.")

json.dump(entries, open(OUT, "w"), indent=1, ensure_ascii=False)
print(len(entries), "entries written")
