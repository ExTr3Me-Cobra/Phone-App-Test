"""Splits each muscle group into individual muscles / heads, with research behind every number.

How the numbers work
- weights.py says how hard each muscle GROUP works compared with the main group (main = 1.0).
- HEADS (below) says how that group's work is shared between its heads for a movement pattern,
  as activity relative to the busiest head (busiest = 1.0). A head's final % = group % x head share.
- ADD gives muscles extra work the group rules can't express (e.g. iliopsoas in leg raises).

The numbers are estimates rounded to 0.05. They come from surface / fine-wire EMG (% of maximum
voluntary contraction) and, where available, training studies measuring which heads grew. EMG is
not a direct measure of force, so treat them as "which parts work hardest", not exact percentages.
Each rule lists its sources (REFS keys); the app shows them on the exercise page.
"""

REFS = {
    'Andersen 2014': 'Andersen V, Fimland MS, Wiik E, Skoglund A, Saeterbakken AH. Effects of grip width on muscle strength and activation in the lat pull-down. J Strength Cond Res. 2014;28(4):1135-1142.',
    'Andersson 1997': 'Andersson EA, Nilsson J, Ma Z, Thorstensson A. Abdominal and hip flexor muscle activation during various training exercises. Eur J Appl Physiol. 1997;75:115-123.',
    'Barnett 1995': 'Barnett C, Kippers V, Turner P. Effects of variations of the bench press exercise on the EMG activity of five shoulder muscles. J Strength Cond Res. 1995;9(4):222-227.',
    'Boland 2008': 'Boland MR, Spigelman T, Uhl TL. The function of brachioradialis. J Hand Surg Am. 2008;33(10):1853-1859.',
    'Boren 2011': 'Boren K, Conrey C, Le Coguic J, et al. Electromyographic analysis of gluteus medius and gluteus maximus during rehabilitation exercises. Int J Sports Phys Ther. 2011;6(3):206-223.',
    'Botton 2013': 'Botton CE, Wilhelm EN, Ughini CC, Pinto RS, Lima CS. Electromyographical analysis of the deltoid between different strength training exercises. Medicina Sportiva. 2013;17(2):67-71.',
    'Bourne 2017': 'Bourne MN, Williams MD, Opar DA, et al. Impact of exercise selection on hamstring muscle activation. Br J Sports Med. 2017;51(13):1021-1028.',
    'Broome 1971': 'Broome HL, Basmajian JV. The function of the teres major muscle: an electromyographic study. Anat Rec. 1971;170(3):309-310.',
    'Calatayud 2015': 'Calatayud J, Borreani S, Colado JC, et al. Bench press and push-up at comparable levels of muscle activity results in similar strength gains. J Strength Cond Res. 2015;29(1):246-253.',
    'Campos 2020': 'Campos YAC, Vianna JM, Guimaraes MP, et al. Different shoulder exercises affect the activation of deltoid portions in resistance-trained individuals. J Hum Kinet. 2020;75:5-14.',
    'Caterisano 2002': 'Caterisano A, Moss RF, Pellinger TK, et al. The effect of back squat depth on the EMG activity of 4 superficial hip and thigh muscles. J Strength Cond Res. 2002;16(3):428-432.',
    'Contreras 2015': 'Contreras B, Vigotsky AD, Schoenfeld BJ, Beardsley C, Cronin J. A comparison of gluteus maximus, biceps femoris, and vastus lateralis EMG activity in the back squat and barbell hip thrust exercises. J Appl Biomech. 2015;31(6):452-458.',
    'Coratella 2020': 'Coratella G, Tornatore G, Longo S, Esposito F, Ce E. An electromyographic analysis of lateral raise variations and frontal raise in competitive bodybuilders. Int J Environ Res Public Health. 2020;17(17):6015.',
    'Decker 1999': 'Decker MJ, Hintermeister RA, Faber KJ, Hawkins RJ. Serratus anterior muscle activity during selected rehabilitation exercises. Am J Sports Med. 1999;27(6):784-791.',
    'Decker 2003': 'Decker MJ, Tokish JM, Ellis HB, Torry MR, Hawkins RJ. Subscapularis muscle activity during selected rehabilitation exercises. Am J Sports Med. 2003;31(1):126-134.',
    'Distefano 2009': 'Distefano LJ, Blackburn JT, Marshall SW, Padua DA. Gluteal muscle activation during common therapeutic exercises. J Orthop Sports Phys Ther. 2009;39(7):532-540.',
    'Ekstrom 2003': 'Ekstrom RA, Donatelli RA, Soderberg GL. Surface electromyographic analysis of exercises for the trapezius and serratus anterior muscles. J Orthop Sports Phys Ther. 2003;33(5):247-258.',
    'Ema 2013': 'Ema R, Wakahara T, Miyamoto N, Kanehisa H, Kawakami Y. Inhomogeneous architectural changes of the quadriceps femoris induced by resistance training. Eur J Appl Physiol. 2013;113(11):2691-2703.',
    'Escamilla 1998': 'Escamilla RF, Fleisig GS, Zheng N, et al. Biomechanics of the knee during closed kinetic chain and open kinetic chain exercises. Med Sci Sports Exerc. 1998;30(4):556-569.',
    'Escamilla 2001': 'Escamilla RF. Knee biomechanics of the dynamic squat exercise. Med Sci Sports Exerc. 2001;33(1):127-141.',
    'Escamilla 2002': 'Escamilla RF, Francisco AC, Kayes AV, Speer KP, Moorman CT. An electromyographic analysis of sumo and conventional style deadlifts. Med Sci Sports Exerc. 2002;34(4):682-688.',
    'Escamilla 2006': 'Escamilla RF, Babb E, DeWitt R, et al. Electromyographic analysis of traditional and nontraditional abdominal exercises: implications for rehabilitation and training. Phys Ther. 2006;86(5):656-671.',
    'Fenwick 2009': 'Fenwick CM, Brown SH, McGill SM. Comparison of different rowing exercises: trunk muscle activation and lumbar spine motion, load, and stiffness. J Strength Cond Res. 2009;23(5):1408-1417.',
    'Glass 1997': 'Glass SC, Armstrong T. Electromyographical activity of the pectoralis muscle during incline and decline bench presses. J Strength Cond Res. 1997;11(3):163-167.',
    'Hamlyn 2007': 'Hamlyn N, Behm DG, Young WB. Trunk muscle activation during dynamic weight-training exercises and isometric instability activities. J Strength Cond Res. 2007;21(4):1108-1112.',
    'Hebert-Losier 2012': 'Hebert-Losier K, Schneiders AG, Garcia JA, Sullivan SJ, Simoneau GG. Influence of knee flexion angle and age on triceps surae muscle activity during heel raises. J Strength Cond Res. 2012;26(11):3124-3133.',
    'Juker 1998': 'Juker D, McGill S, Kropf P, Steffen T. Quantitative intramuscular myoelectric activity of lumbar portions of psoas and the abdominal wall during a wide variety of tasks. Med Sci Sports Exerc. 1998;30(2):301-310.',
    'Kelly 1996': 'Kelly BT, Kadrmas WR, Speer KP. The manual muscle examination for rotator cuff strength: an electromyographic investigation. Am J Sports Med. 1996;24(5):581-588.',
    'Kholinne 2018': 'Kholinne E, Zulkarnain RF, Sun YC, et al. The different role of each head of the triceps brachii muscle in elbow extension. Acta Orthop Traumatol Turc. 2018;52(3):201-205.',
    'Kleiber 2015': 'Kleiber T, Kunz L, Disselhorst-Klug C. Muscular coordination of biceps brachii and brachioradialis in elbow flexion with respect to hand position. Front Physiol. 2015;6:215.',
    'Kubo 2019': 'Kubo K, Ikebukuro T, Yata H. Effects of squat training with different depths on lower limb muscle volumes. Eur J Appl Physiol. 2019;119(9):1933-1942.',
    'Lauver 2016': 'Lauver JD, Cayot TE, Scheuermann BW. Influence of bench angle on upper extremity muscular activation during bench press exercise. Eur J Sport Sci. 2016;16(3):309-316.',
    'Ludewig 2004': 'Ludewig PM, Hoff MS, Osowski EE, Meschke SA, Rundquist PJ. Relative balance of serratus anterior and upper trapezius muscle activity during push-up exercises. Am J Sports Med. 2004;32(2):484-493.',
    'Lusk 2010': 'Lusk SJ, Hale BD, Russell DM. Grip width and forearm orientation effects on muscle activity during the lat pull-down. J Strength Cond Res. 2010;24(7):1895-1900.',
    'Maeo 2021': 'Maeo S, Huang M, Wu Y, et al. Greater hamstrings muscle hypertrophy but similar damage protection after training at long versus short muscle lengths. Med Sci Sports Exerc. 2021;53(4):825-837.',
    'Maeo 2023': 'Maeo S, Wu Y, Huang M, et al. Triceps brachii hypertrophy is substantially greater after elbow extension training performed in the overhead versus neutral arm position. Eur J Sport Sci. 2023;23(7):1240-1250.',
    'Marchetti 2011': 'Marchetti PH, Uchida MC. Effects of the pullover exercise on the pectoralis major and latissimus dorsi muscles as evaluated by EMG. J Appl Biomech. 2011;27(4):380-384.',
    'McAllister 2013': 'McAllister MJ, Schilling BK, Hammond KG, Weiss LW, Farney TM. Effect of grip width on electromyographic activity during the upright row. J Strength Cond Res. 2013;27(1):181-187.',
    'McAllister 2014': 'McAllister MJ, Hammond KG, Schilling BK, et al. Muscle activation during various hamstring exercises. J Strength Cond Res. 2014;28(6):1573-1580.',
    'McGill 2009': 'McGill SM, McDermott A, Fenwick CM. Comparison of different strongman events: trunk muscle activation and lumbar spine motion, load, and stiffness. J Strength Cond Res. 2009;23(4):1148-1161.',
    'Mogk 2003': 'Mogk JPM, Keir PJ. The effects of posture on forearm muscle loading during gripping. Ergonomics. 2003;46(9):956-975.',
    'Neumann 2017': 'Neumann DA. Kinesiology of the Musculoskeletal System, 3rd ed. Elsevier; 2017. (muscle actions where no EMG study fits)',
    'Oliveira 2009': 'Oliveira LF, Matta TT, Alves DS, Garcia MA, Vieira TM. Effect of the shoulder position on the biceps brachii EMG in different dumbbell curls. J Sports Sci Med. 2009;8(1):24-29.',
    'Ono 2011': 'Ono T, Higashihara A, Fukubayashi T. Hamstring functions during hip-extension exercise assessed with electromyography and magnetic resonance imaging. Res Sports Med. 2011;19(1):42-52.',
    'Reinold 2004': 'Reinold MM, Wilk KE, Fleisig GS, et al. Electromyographic analysis of the rotator cuff and deltoid musculature during common shoulder external rotation exercises. J Orthop Sports Phys Ther. 2004;34(7):385-394.',
    'Reinold 2009': 'Reinold MM, Escamilla RF, Wilk KE. Current concepts in the scientific and clinical rationale behind exercises for glenohumeral and scapulothoracic musculature. J Orthop Sports Phys Ther. 2009;39(2):105-117.',
    'Rodriguez-Ridao 2020': 'Rodriguez-Ridao D, Antequera-Vique JA, Martin-Fuentes I, Muyor JM. Effect of five bench inclinations on the electromyographic activity of the pectoralis major, anterior deltoid, and triceps brachii during the bench press exercise. Int J Environ Res Public Health. 2020;17(19):7339.',
    'Sarti 1996': 'Sarti MA, Monfort M, Fuster MA, Villaplana LA. Muscle activity in upper and lower rectus abdominus during abdominal exercises. Arch Phys Med Rehabil. 1996;77(12):1293-1297.',
    'Lehman 2001': 'Lehman GJ, McGill SM. Quantification of the differences in electromyographic activity magnitude between the upper and lower portions of the rectus abdominis muscle during selected trunk exercises. Phys Ther. 2001;81(5):1096-1101.',
    'Saeterbakken 2013': 'Saeterbakken AH, Fimland MS. Effects of body position and loading modality on muscle activity and strength in shoulder presses. J Strength Cond Res. 2013;27(7):1824-1831.',
    'Selkowitz 2013': 'Selkowitz DM, Beneck GJ, Powers CM. Which exercises target the gluteal muscles while minimizing activation of the tensor fascia lata? J Orthop Sports Phys Ther. 2013;43(2):54-64.',
    'Serner 2014': 'Serner A, Jakobsen MD, Andersen LL, et al. EMG evaluation of hip adduction exercises for soccer players: implications for exercise selection in prevention and treatment of groin injuries. Br J Sports Med. 2014;48(14):1108-1114.',
    'Trebs 2010': 'Trebs AA, Brandenburg JP, Pitney WA. An electromyography analysis of 3 muscles surrounding the shoulder joint during the performance of a chest press exercise at several angles. J Strength Cond Res. 2010;24(7):1925-1930.',
    'Stastny 2017': 'Stastny P, Golas A, Blazek D, et al. A systematic review of surface electromyography analyses of the bench press movement task. PLoS One. 2017;12(2):e0171632.',
    'Youdas 2010': 'Youdas JW, Amundson CL, Cicero KS, et al. Surface electromyographic activation patterns and elbow joint motion during a pull-up, chin-up, or perfect-pullup rotational exercise. J Strength Cond Res. 2010;24(12):3404-3414.',
}

# Each group's heads, in display order.
GROUPS = {
    'chest': ['chest (upper)', 'chest (middle)', 'chest (lower)'],
    'shoulders': ['shoulders (front delt)', 'shoulders (side delt)', 'shoulders (rear delt)'],
    'biceps': ['biceps (long head)', 'biceps (short head)', 'brachialis'],
    'triceps': ['triceps (long head)', 'triceps (lateral head)', 'triceps (medial head)'],
    # Grip is part of the forearms: the finger flexors that do the gripping are forearm muscles.
    'forearms': ['forearms (brachioradialis)', 'forearms (wrist flexors)', 'forearms (wrist extensors)', 'forearms (finger flexors)'],
    'abdominals': ['abs (upper)', 'abs (lower)', 'obliques'],
    'serratus': ['serratus (serratus anterior)'],
    'hip flexors': ['hip flexors (iliopsoas)'],
    'lats': ['lats', 'teres major'],
    'middle back': ['middle back (rhomboids)'],
    'lower back': ['lower back (erector spinae)'],
    'traps': ['traps (upper)', 'traps (middle)', 'traps (lower)'],
    'neck': ['neck'],
    'rotator cuff': ['rotator cuff (supraspinatus)', 'rotator cuff (infraspinatus)', 'rotator cuff (teres minor)', 'rotator cuff (subscapularis)'],
    'glutes': ['glutes (maximus)'],
    'abductors': ['abductors (glute medius)', 'abductors (TFL)'],
    'adductors': ['adductors (longus & brevis)', 'adductors (magnus)'],
    'quadriceps': ['quadriceps (rectus femoris)', 'quadriceps (vastus lateralis)', 'quadriceps (vastus medialis)'],
    'hamstrings': ['hamstrings (biceps femoris)', 'hamstrings (semitendinosus & semimembranosus)'],
    'calves': ['calves (gastrocnemius)', 'calves (soleus)'],
    'shins': ['shins (tibialis anterior)'],
}
GROUP_OF = {h: g for g, hs in GROUPS.items() for h in hs}

# Short names used below.
UP, MID, LOW = 'chest (upper)', 'chest (middle)', 'chest (lower)'
FD, SD, RD = 'shoulders (front delt)', 'shoulders (side delt)', 'shoulders (rear delt)'
BL, BS, BR = 'biceps (long head)', 'biceps (short head)', 'brachialis'
TLO, TLA, TME = 'triceps (long head)', 'triceps (lateral head)', 'triceps (medial head)'
FBR, FFL, FEX = 'forearms (brachioradialis)', 'forearms (wrist flexors)', 'forearms (wrist extensors)'
# RA stands for the whole rectus abdominis in the rules; ABS_SPLIT shares it between upper and lower abs.
RA, OB = 'rectus abdominis', 'obliques'
UA, LA = 'abs (upper)', 'abs (lower)'
LAT, TMJ = 'lats', 'teres major'
RHO = 'middle back (rhomboids)'
TU, TM, TL = 'traps (upper)', 'traps (middle)', 'traps (lower)'
SUP, INF, TMI, SUB = ('rotator cuff (supraspinatus)', 'rotator cuff (infraspinatus)',
                      'rotator cuff (teres minor)', 'rotator cuff (subscapularis)')
GMED, TFL = 'abductors (glute medius)', 'abductors (TFL)'
ALB, AMG = 'adductors (longus & brevis)', 'adductors (magnus)'
RF, VL, VM = 'quadriceps (rectus femoris)', 'quadriceps (vastus lateralis)', 'quadriceps (vastus medialis)'
BF, SEMI = 'hamstrings (biceps femoris)', 'hamstrings (semitendinosus & semimembranosus)'
GAS, SOL = 'calves (gastrocnemius)', 'calves (soleus)'
IL = 'hip flexors (iliopsoas)'
TIB = 'shins (tibialis anterior)'

# (name regex, group, {head: share}, sources). First match for the group wins; DEFAULT_HEADS otherwise.
HEADS = [
    # ---- Chest: incline shifts work to the clavicular (upper) fibres, decline / dips to the lower ones.
    (r'incline|low cable crossover|low.?to.?high|landmine press', 'chest', {UP: 1, MID: .8, LOW: .5},
     ['Trebs 2010', 'Lauver 2016', 'Rodriguez-Ridao 2020']),
    (r'decline|dips?\b|high.?to.?low', 'chest', {UP: .45, MID: .85, LOW: 1}, ['Glass 1997', 'Lauver 2016', 'Barnett 1995']),
    (r'pullover', 'chest', {UP: .5, MID: .8, LOW: 1}, ['Marchetti 2011']),
    (r'shoulder press|military press|overhead press|push press|arnold|jerk|front .*raise', 'chest', {UP: 1, MID: .3, LOW: .1},
     ['Saeterbakken 2013', 'Campos 2020']),
    (r'bench|chest press|floor press|push-?ups?\b|push up|fl(y|ye|yes)\b|crossover|pec deck|butterfly', 'chest',
     {UP: .8, MID: 1, LOW: .85}, ['Barnett 1995', 'Calatayud 2015', 'Stastny 2017']),
    # ---- Deltoid heads.
    (r'rear delt|reverse fl|face pull|band pull apart|y-t-w|back fl', 'shoulders', {FD: .1, SD: .55, RD: 1},
     ['Botton 2013', 'Campos 2020']),
    (r'lateral raise|side laterals|deltoid raise|power partials|lu raise', 'shoulders', {FD: .5, SD: 1, RD: .45},
     ['Coratella 2020', 'Botton 2013', 'Campos 2020']),
    (r'upright row|high pull', 'shoulders', {FD: .7, SD: 1, RD: .45}, ['McAllister 2013']),
    (r'front .*raise|raise over head|raise above head|scaption', 'shoulders', {FD: 1, SD: .5, RD: .1},
     ['Coratella 2020', 'Campos 2020']),
    (r'shoulder press|military press|overhead press|push press|arnold|jerk|bradford|landmine|handstand|pike', 'shoulders',
     {FD: 1, SD: .75, RD: .25}, ['Saeterbakken 2013', 'Campos 2020']),
    (r'bench|chest press|floor press|push-?ups?\b|push up|dips?\b|fl(y|ye|yes)\b|crossover|pec deck|butterfly', 'shoulders',
     {FD: 1, SD: .25, RD: .1}, ['Barnett 1995', 'Trebs 2010']),
    (r'row|pull-?ups?|chin-?ups?|pulldown|pullover|muscle up|swim', 'shoulders', {FD: .1, SD: .35, RD: 1},
     ['Fenwick 2009', 'Lusk 2010']),
    # ---- Biceps: shoulder behind the body stretches / favours the long head; arm in front favours the short head.
    (r'hammer|reverse.*curl|zottman|reverse.*preacher|reverse plate|cross.?body', 'biceps', {BL: .65, BS: .5, BR: 1},
     ['Kleiber 2015', 'Boland 2008']),
    (r'incline.*curl|drag curl|bayesian|behind.?the.?back curl', 'biceps', {BL: 1, BS: .8, BR: .75}, ['Oliveira 2009']),
    (r'preacher|spider|concentration|scott', 'biceps', {BL: .75, BS: 1, BR: .85}, ['Oliveira 2009']),
    (r'neutral', 'biceps', {BL: .75, BS: .6, BR: 1}, ['Kleiber 2015', 'Boland 2008']),
    (r'chin-?ups?|pull-?ups?|row|pulldown', 'biceps', {BL: .85, BS: .85, BR: 1}, ['Youdas 2010', 'Lusk 2010']),
    (r'curl', 'biceps', {BL: 1, BS: .95, BR: .8}, ['Oliveira 2009', 'Kleiber 2015']),
    # ---- Triceps: overhead = long head stretched; pressing (arm in front) relies on lateral and medial heads.
    (r'overhead|french|halo|tate|skull|lying triceps|lying tricep|triceps press|press to chin|jm press', 'triceps',
     {TLO: 1, TLA: .75, TME: .8}, ['Maeo 2023', 'Kholinne 2018']),
    (r'kickback', 'triceps', {TLO: .9, TLA: 1, TME: .9}, ['Kholinne 2018']),
    (r'pushdown|push-?down|pressdown', 'triceps', {TLO: .7, TLA: 1, TME: 1}, ['Maeo 2023', 'Kholinne 2018']),
    (r'bench|press|push-?ups?\b|push up|dips?\b', 'triceps', {TLO: .55, TLA: 1, TME: 1}, ['Kholinne 2018', 'Barnett 1995']),
    # ---- Forearms.
    (r'hammer|reverse.*curl|zottman|reverse.*preacher|reverse plate', 'forearms', {FBR: 1, FFL: .3, FEX: .6},
     ['Boland 2008', 'Kleiber 2015']),
    # Opening the fingers against a band: the finger extensors (in the wrist-extensor group), not the grip flexors.
    (r'finger extension', 'forearms', {FBR: .1, FFL: .1, FEX: 1}, ['Neumann 2017']),
    (r'reverse wrist|wrist extension|wrist roller', 'forearms', {FBR: .2, FFL: .5, FEX: 1}, ['Neumann 2017']),
    (r'wrist curl|wrist flexion|finger curl', 'forearms', {FBR: .1, FFL: 1, FEX: .25}, ['Neumann 2017']),
    (r'pronation|supination|wrist rotation', 'forearms', {FBR: .7, FFL: .7, FEX: .7}, ['Neumann 2017']),
    (r'neutral', 'forearms', {FBR: 1, FFL: .6, FEX: .4}, ['Boland 2008', 'Mogk 2003']),
    (r'curl', 'forearms', {FBR: 1, FFL: .5, FEX: .3}, ['Boland 2008', 'Kleiber 2015']),
    # ---- Abdominals: rotation / side bending / anti-rotation favour the obliques.
    (r'twist|side bend|oblique|judo flip|heel touch|wood ?chop|cable lift|pallof|side plank|suitcase|windmill|russian|landmine 180|rotation',
     'abdominals', {RA: .5, OB: 1}, ['Escamilla 2006', 'McGill 2009']),
    (r'air bike|bicycle|elbow to knee|cross.?body crunch', 'abdominals', {RA: 1, OB: .9}, ['Escamilla 2006']),
    (r'farmer|carry|walk with', 'abdominals', {RA: .6, OB: 1}, ['McGill 2009']),
    (r'plank|rollout|ab wheel|fallout|bear crawl|dead bug|hollow', 'abdominals', {RA: 1, OB: .75}, ['Escamilla 2006']),
    (r'crunch|sit-?up|leg raise|knee raise|v-up|jackknife|tuck|pull-in|hip raise|butt-ups|cocoons|toes to bar', 'abdominals',
     {RA: 1, OB: .6}, ['Escamilla 2006', 'Andersson 1997']),
    (r'squat|deadlift|lunge|press|row|clean|snatch', 'abdominals', {RA: .7, OB: 1}, ['Hamlyn 2007']),
    # ---- Lats: teres major works with the lats only against resistance.
    (r'.', 'lats', {LAT: 1, TMJ: .7}, ['Broome 1971', 'Youdas 2010']),
    # ---- Traps.
    (r'shrug', 'traps', {TU: 1, TM: .45, TL: .2}, ['Ekstrom 2003']),
    (r'y-t-w|prone.*y|y raise|lower trap|scaption|overhead.*shrug', 'traps', {TU: .45, TM: .8, TL: 1}, ['Ekstrom 2003']),
    (r'pull-?ups?|chin-?ups?|pulldown|muscle up', 'traps', {TU: .25, TM: .7, TL: 1}, ['Youdas 2010', 'Lusk 2010']),
    (r'row|rear delt|reverse fl|face pull|band pull apart|back fl', 'traps', {TU: .55, TM: 1, TL: .65}, ['Ekstrom 2003', 'Fenwick 2009']),
    (r'shoulder press|military press|overhead press|push press|arnold|jerk|lateral raise|side laterals|front .*raise|upright row|landmine',
     'traps', {TU: 1, TM: .35, TL: .6}, ['Ekstrom 2003']),
    (r'deadlift|rack pull|farmer|carry|walk with|clean|snatch', 'traps', {TU: 1, TM: .6, TL: .45}, ['Escamilla 2002', 'McGill 2009']),
    # ---- Rotator cuff.
    (r'internal rotation', 'rotator cuff', {SUP: .25, INF: .15, TMI: .15, SUB: 1}, ['Decker 2003', 'Reinold 2009']),
    (r'external rotation|cuban|face pull|no.?money|w raise', 'rotator cuff', {SUP: .45, INF: 1, TMI: .9, SUB: .2},
     ['Reinold 2004', 'Reinold 2009']),
    (r'scaption|full can|empty can|lateral raise|side laterals|y-t-w', 'rotator cuff', {SUP: 1, INF: .55, TMI: .45, SUB: .35},
     ['Kelly 1996', 'Reinold 2009']),
    (r'rear delt|reverse fl|band pull apart|row|back fl', 'rotator cuff', {SUP: .5, INF: 1, TMI: .85, SUB: .25},
     ['Reinold 2004', 'Reinold 2009']),
    (r'push-?up|press|dips?\b|punch|hug|plank', 'rotator cuff', {SUP: .6, INF: .45, TMI: .35, SUB: 1}, ['Decker 2003']),
    # ---- Glute medius vs TFL.
    (r'clamshell|side-?lying.*abduction|band walk|monster walk|lateral walk|side step', 'abductors', {GMED: 1, TFL: .35},
     ['Selkowitz 2013', 'Distefano 2009']),
    (r'abduction|fire hydrant|side leg raise', 'abductors', {GMED: 1, TFL: .7}, ['Selkowitz 2013', 'Boren 2011']),
    # ---- Adductors: in squats / hinges the magnus works as a hip extender; adduction drills hit longus most.
    (r'squat|lunge|deadlift|step-?up|good morning|romanian|stiff|leg press|hip thrust|swing', 'adductors', {ALB: .45, AMG: 1},
     ['Kubo 2019']),
    (r'adduction|copenhagen|adductor|groin', 'adductors', {ALB: 1, AMG: .75}, ['Serner 2014']),
    # ---- Quadriceps: rectus femoris crosses the hip, so it does little in squats and a lot in leg extensions / hip flexion.
    (r'leg extension|sissy', 'quadriceps', {RF: 1, VL: 1, VM: .95}, ['Ema 2013', 'Escamilla 1998']),
    (r'leg raise|knee raise|hip flexion|kick|march|sit-?up|tuck|v-up|jackknife', 'quadriceps', {RF: 1, VL: .1, VM: .1},
     ['Andersson 1997', 'Neumann 2017']),
    (r'squat|lunge|step|leg press|jump|wall sit|pistol|deadlift|clean|snatch|thrust|bridge', 'quadriceps', {RF: .55, VL: 1, VM: .95},
     ['Escamilla 2001', 'Kubo 2019', 'Escamilla 1998']),
    # ---- Hamstrings: knee-flexion work favours semitendinosus; hip-hinge work favours biceps femoris long head.
    (r'glute.?ham|nordic|razor', 'hamstrings', {BF: .75, SEMI: 1}, ['Bourne 2017']),
    (r'leg curl|hamstring curl|ball leg curl|slider curl', 'hamstrings', {BF: .8, SEMI: 1}, ['Maeo 2021', 'Ono 2011']),
    (r'romanian|stiff|good morning|deadlift|hyperextension|back extension|swing|rack pull|superman', 'hamstrings', {BF: 1, SEMI: .85},
     ['Ono 2011', 'McAllister 2014', 'Bourne 2017']),
    # ---- Calves: a bent knee slackens the gastrocnemius, leaving the soleus to do the work.
    (r'seated calf|bent.?knee calf', 'calves', {GAS: .4, SOL: 1}, ['Hebert-Losier 2012']),
]
DEFAULT_HEADS = {
    'chest': ({UP: .8, MID: 1, LOW: .85}, ['Barnett 1995', 'Stastny 2017']),
    'shoulders': ({FD: 1, SD: .7, RD: .45}, ['Campos 2020', 'Botton 2013']),
    'biceps': ({BL: 1, BS: .95, BR: .8}, ['Oliveira 2009', 'Kleiber 2015']),
    'triceps': ({TLO: .8, TLA: 1, TME: 1}, ['Kholinne 2018']),
    'forearms': ({FBR: .55, FFL: 1, FEX: .6}, ['Mogk 2003']),
    # "grip" in the rules = the finger flexors (in the forearms group).
    'grip': ({'forearms (finger flexors)': 1}, []),
    'abdominals': ({UA: 1, LA: .9, OB: .7}, ['Escamilla 2006', 'Lehman 2001']),
    'serratus': ({'serratus (serratus anterior)': 1}, []),
    'hip flexors': ({IL: 1}, []),
    'lats': ({LAT: 1, TMJ: .7}, ['Broome 1971']),
    'middle back': ({RHO: 1}, []),
    'lower back': ({'lower back (erector spinae)': 1}, []),
    'traps': ({TU: 1, TM: .7, TL: .5}, ['Ekstrom 2003']),
    'neck': ({'neck': 1}, []),
    'rotator cuff': ({SUP: 1, INF: 1, TMI: .8, SUB: .8}, ['Reinold 2009']),
    'glutes': ({'glutes (maximus)': 1}, []),
    'abductors': ({GMED: 1, TFL: .6}, ['Selkowitz 2013']),
    'adductors': ({ALB: 1, AMG: .8}, ['Serner 2014']),
    'quadriceps': ({RF: .75, VL: 1, VM: .9}, ['Escamilla 1998']),
    'hamstrings': ({BF: 1, SEMI: 1}, ['McAllister 2014']),
    'calves': ({GAS: 1, SOL: .8}, ['Hebert-Losier 2012']),
    'shins': ({TIB: 1}, []),
}

# Upper vs lower rectus abdominis. It is one muscle, so the difference is modest (Lehman 2001 found
# little difference for most exercises); curling the trunk favours the upper part, and lifting the
# legs / tilting the pelvis back favours the lower part (Sarti 1996).
ABS_SPLIT = [
    (r'reverse crunch|leg raise|knee raise|hip raise|butt-ups|toes to bar|flutter|scissor|leg tuck|knee tuck|pull-in|hanging|l-sit|dead bug|mountain climber',
     {UA: .85, LA: 1}, ['Sarti 1996', 'Lehman 2001']),
    (r'crunch|sit-?up|curl-?up|jackknife|v-up|cocoons|roll-?up', {UA: 1, LA: .8}, ['Sarti 1996', 'Escamilla 2006']),
    (r'.', {UA: 1, LA: .9}, ['Lehman 2001', 'Escamilla 2006']),
]

# Middle back in the source data means the muscles between the shoulder blades: rhomboids plus the
# middle and lower trapezius, which do the same job (scapular retraction) in rows (Ekstrom 2003).
MIDDLE_BACK_EXTRA = ({TM: .9, TL: .55}, ['Ekstrom 2003', 'Fenwick 2009'])

# Extra muscles (absolute weight vs the main muscle) that the group rules don't cover.
# (name regex, required primary group or None, {muscle: weight}, sources)
ADD = [
    # Iliopsoas does the hip flexion in leg / knee raises and the second half of sit-ups.
    (r'leg raise|knee raise|hip flexion|toes to bar|knee tuck|leg tuck|v-up|jackknife|l-sit|flutter|scissor|mountain climber|hanging',
     None, {IL: .8}, ['Andersson 1997', 'Juker 1998']),
    (r'sit-?up|knee/hip raise|air bike|bicycle|reverse crunch|dead bug', None, {IL: .55}, ['Andersson 1997', 'Juker 1998']),
    (r'march|high knees|step-up with knee raise|sprint', None, {IL: .5}, ['Neumann 2017']),
    # Gluteus medius stabilises the pelvis on one leg and in hip thrusts.
    (r'single.?leg|one.?leg|pistol|lunge|split squat|step-?up|step down|skater|lateral', 'glutes', {GMED: .45},
     ['Boren 2011', 'Distefano 2009']),
    (r'hip thrust|bridge', None, {GMED: .4}, ['Contreras 2015', 'Boren 2011']),
    # Tibialis anterior lifts the foot.
    (r'toe raise|tibialis|dorsiflexion', None, {TIB: 1}, ['Neumann 2017']),
    # Serratus anterior: push-up "plus" and upward rotation during overhead work.
    (r'push-?ups?\b|push up', None, {'serratus (serratus anterior)': .45}, ['Ludewig 2004', 'Decker 1999']),
    # Supraspinatus helps the deltoid start every raise.
    (r'lateral raise|side laterals|front .*raise|scaption', None, {SUP: .45}, ['Reinold 2009', 'Kelly 1996']),
]
