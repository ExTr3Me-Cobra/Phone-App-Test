package com.ironlog.app

/** Made by tools/gen_kotlin.py from tools/detail_weights.py – edit that and re-run. */
object Muscles {
    /** Each muscle group (used for the list filters and the line under an exercise's name) and its individual muscles. */
    val groups: Map<String, List<String>> = linkedMapOf(
        "chest" to listOf("chest (upper)", "chest (middle)", "chest (lower)"),
        "shoulders" to listOf("shoulders (front delt)", "shoulders (side delt)", "shoulders (rear delt)"),
        "biceps" to listOf("biceps (long head)", "biceps (short head)", "brachialis"),
        "triceps" to listOf("triceps (long head)", "triceps (lateral head)", "triceps (medial head)"),
        "forearms" to listOf("forearms (brachioradialis)", "forearms (wrist flexors)", "forearms (wrist extensors)", "forearms (finger flexors)"),
        "abdominals" to listOf("abs (upper)", "abs (lower)", "obliques"),
        "serratus" to listOf("serratus (serratus anterior)"),
        "hip flexors" to listOf("hip flexors (iliopsoas)"),
        "lats" to listOf("lats", "teres major"),
        "middle back" to listOf("middle back (rhomboids)"),
        "lower back" to listOf("lower back (erector spinae)"),
        "traps" to listOf("traps (upper)", "traps (middle)", "traps (lower)"),
        "neck" to listOf("neck"),
        "rotator cuff" to listOf("rotator cuff (supraspinatus)", "rotator cuff (infraspinatus)", "rotator cuff (teres minor)", "rotator cuff (subscapularis)"),
        "glutes" to listOf("glutes (maximus)"),
        "abductors" to listOf("abductors (glute medius)", "abductors (TFL)"),
        "adductors" to listOf("adductors (longus & brevis)", "adductors (magnus)"),
        "quadriceps" to listOf("quadriceps (rectus femoris)", "quadriceps (vastus lateralis)", "quadriceps (vastus medialis)"),
        "hamstrings" to listOf("hamstrings (biceps femoris)", "hamstrings (semitendinosus & semimembranosus)"),
        "calves" to listOf("calves (gastrocnemius)", "calves (soleus)"),
        "shins" to listOf("shins (tibialis anterior)"),
    )

    /** Usual share of a group's work for each of its muscles, when nothing more specific is known. */
    val defaultShares: Map<String, Map<String, Float>> = mapOf(
        "chest" to mapOf("chest (upper)" to 0.8f, "chest (middle)" to 1f, "chest (lower)" to 0.8f),
        "shoulders" to mapOf("shoulders (front delt)" to 1f, "shoulders (side delt)" to 0.7f, "shoulders (rear delt)" to 0.5f),
        "biceps" to mapOf("biceps (long head)" to 1f, "biceps (short head)" to 0.9f, "brachialis" to 0.8f),
        "triceps" to mapOf("triceps (long head)" to 0.8f, "triceps (lateral head)" to 1f, "triceps (medial head)" to 1f),
        "forearms" to mapOf("forearms (brachioradialis)" to 0.6f, "forearms (wrist flexors)" to 1f, "forearms (wrist extensors)" to 0.6f),
        "grip" to mapOf("forearms (finger flexors)" to 1f),
        "abdominals" to mapOf("abs (upper)" to 1f, "abs (lower)" to 0.9f, "obliques" to 0.7f),
        "serratus" to mapOf("serratus (serratus anterior)" to 1f),
        "hip flexors" to mapOf("hip flexors (iliopsoas)" to 1f),
        "lats" to mapOf("lats" to 1f, "teres major" to 0.7f),
        "middle back" to mapOf("middle back (rhomboids)" to 1f),
        "lower back" to mapOf("lower back (erector spinae)" to 1f),
        "traps" to mapOf("traps (upper)" to 1f, "traps (middle)" to 0.7f, "traps (lower)" to 0.5f),
        "neck" to mapOf("neck" to 1f),
        "rotator cuff" to mapOf("rotator cuff (supraspinatus)" to 1f, "rotator cuff (infraspinatus)" to 1f, "rotator cuff (teres minor)" to 0.8f, "rotator cuff (subscapularis)" to 0.8f),
        "glutes" to mapOf("glutes (maximus)" to 1f),
        "abductors" to mapOf("abductors (glute medius)" to 1f, "abductors (TFL)" to 0.6f),
        "adductors" to mapOf("adductors (longus & brevis)" to 1f, "adductors (magnus)" to 0.8f),
        "quadriceps" to mapOf("quadriceps (rectus femoris)" to 0.8f, "quadriceps (vastus lateralis)" to 1f, "quadriceps (vastus medialis)" to 0.9f),
        "hamstrings" to mapOf("hamstrings (biceps femoris)" to 1f, "hamstrings (semitendinosus & semimembranosus)" to 1f),
        "calves" to mapOf("calves (gastrocnemius)" to 1f, "calves (soleus)" to 0.8f),
        "shins" to mapOf("shins (tibialis anterior)" to 1f),
    )

    /** The studies the percentages come from. */
    val refs: Map<String, String> = mapOf(
        "Andersen 2014" to "Andersen V, Fimland MS, Wiik E, Skoglund A, Saeterbakken AH. Effects of grip width on muscle strength and activation in the lat pull-down. J Strength Cond Res. 2014;28(4):1135-1142.",
        "Andersson 1997" to "Andersson EA, Nilsson J, Ma Z, Thorstensson A. Abdominal and hip flexor muscle activation during various training exercises. Eur J Appl Physiol. 1997;75:115-123.",
        "Barnett 1995" to "Barnett C, Kippers V, Turner P. Effects of variations of the bench press exercise on the EMG activity of five shoulder muscles. J Strength Cond Res. 1995;9(4):222-227.",
        "Boland 2008" to "Boland MR, Spigelman T, Uhl TL. The function of brachioradialis. J Hand Surg Am. 2008;33(10):1853-1859.",
        "Boren 2011" to "Boren K, Conrey C, Le Coguic J, et al. Electromyographic analysis of gluteus medius and gluteus maximus during rehabilitation exercises. Int J Sports Phys Ther. 2011;6(3):206-223.",
        "Botton 2013" to "Botton CE, Wilhelm EN, Ughini CC, Pinto RS, Lima CS. Electromyographical analysis of the deltoid between different strength training exercises. Medicina Sportiva. 2013;17(2):67-71.",
        "Bourne 2017" to "Bourne MN, Williams MD, Opar DA, et al. Impact of exercise selection on hamstring muscle activation. Br J Sports Med. 2017;51(13):1021-1028.",
        "Broome 1971" to "Broome HL, Basmajian JV. The function of the teres major muscle: an electromyographic study. Anat Rec. 1971;170(3):309-310.",
        "Calatayud 2015" to "Calatayud J, Borreani S, Colado JC, et al. Bench press and push-up at comparable levels of muscle activity results in similar strength gains. J Strength Cond Res. 2015;29(1):246-253.",
        "Campos 2020" to "Campos YAC, Vianna JM, Guimaraes MP, et al. Different shoulder exercises affect the activation of deltoid portions in resistance-trained individuals. J Hum Kinet. 2020;75:5-14.",
        "Caterisano 2002" to "Caterisano A, Moss RF, Pellinger TK, et al. The effect of back squat depth on the EMG activity of 4 superficial hip and thigh muscles. J Strength Cond Res. 2002;16(3):428-432.",
        "Contreras 2015" to "Contreras B, Vigotsky AD, Schoenfeld BJ, Beardsley C, Cronin J. A comparison of gluteus maximus, biceps femoris, and vastus lateralis EMG activity in the back squat and barbell hip thrust exercises. J Appl Biomech. 2015;31(6):452-458.",
        "Coratella 2020" to "Coratella G, Tornatore G, Longo S, Esposito F, Ce E. An electromyographic analysis of lateral raise variations and frontal raise in competitive bodybuilders. Int J Environ Res Public Health. 2020;17(17):6015.",
        "Decker 1999" to "Decker MJ, Hintermeister RA, Faber KJ, Hawkins RJ. Serratus anterior muscle activity during selected rehabilitation exercises. Am J Sports Med. 1999;27(6):784-791.",
        "Decker 2003" to "Decker MJ, Tokish JM, Ellis HB, Torry MR, Hawkins RJ. Subscapularis muscle activity during selected rehabilitation exercises. Am J Sports Med. 2003;31(1):126-134.",
        "Distefano 2009" to "Distefano LJ, Blackburn JT, Marshall SW, Padua DA. Gluteal muscle activation during common therapeutic exercises. J Orthop Sports Phys Ther. 2009;39(7):532-540.",
        "Ekstrom 2003" to "Ekstrom RA, Donatelli RA, Soderberg GL. Surface electromyographic analysis of exercises for the trapezius and serratus anterior muscles. J Orthop Sports Phys Ther. 2003;33(5):247-258.",
        "Ema 2013" to "Ema R, Wakahara T, Miyamoto N, Kanehisa H, Kawakami Y. Inhomogeneous architectural changes of the quadriceps femoris induced by resistance training. Eur J Appl Physiol. 2013;113(11):2691-2703.",
        "Escamilla 1998" to "Escamilla RF, Fleisig GS, Zheng N, et al. Biomechanics of the knee during closed kinetic chain and open kinetic chain exercises. Med Sci Sports Exerc. 1998;30(4):556-569.",
        "Escamilla 2001" to "Escamilla RF. Knee biomechanics of the dynamic squat exercise. Med Sci Sports Exerc. 2001;33(1):127-141.",
        "Escamilla 2002" to "Escamilla RF, Francisco AC, Kayes AV, Speer KP, Moorman CT. An electromyographic analysis of sumo and conventional style deadlifts. Med Sci Sports Exerc. 2002;34(4):682-688.",
        "Escamilla 2006" to "Escamilla RF, Babb E, DeWitt R, et al. Electromyographic analysis of traditional and nontraditional abdominal exercises: implications for rehabilitation and training. Phys Ther. 2006;86(5):656-671.",
        "Fenwick 2009" to "Fenwick CM, Brown SH, McGill SM. Comparison of different rowing exercises: trunk muscle activation and lumbar spine motion, load, and stiffness. J Strength Cond Res. 2009;23(5):1408-1417.",
        "Glass 1997" to "Glass SC, Armstrong T. Electromyographical activity of the pectoralis muscle during incline and decline bench presses. J Strength Cond Res. 1997;11(3):163-167.",
        "Hamlyn 2007" to "Hamlyn N, Behm DG, Young WB. Trunk muscle activation during dynamic weight-training exercises and isometric instability activities. J Strength Cond Res. 2007;21(4):1108-1112.",
        "Hebert-Losier 2012" to "Hebert-Losier K, Schneiders AG, Garcia JA, Sullivan SJ, Simoneau GG. Influence of knee flexion angle and age on triceps surae muscle activity during heel raises. J Strength Cond Res. 2012;26(11):3124-3133.",
        "Juker 1998" to "Juker D, McGill S, Kropf P, Steffen T. Quantitative intramuscular myoelectric activity of lumbar portions of psoas and the abdominal wall during a wide variety of tasks. Med Sci Sports Exerc. 1998;30(2):301-310.",
        "Kelly 1996" to "Kelly BT, Kadrmas WR, Speer KP. The manual muscle examination for rotator cuff strength: an electromyographic investigation. Am J Sports Med. 1996;24(5):581-588.",
        "Kholinne 2018" to "Kholinne E, Zulkarnain RF, Sun YC, et al. The different role of each head of the triceps brachii muscle in elbow extension. Acta Orthop Traumatol Turc. 2018;52(3):201-205.",
        "Kleiber 2015" to "Kleiber T, Kunz L, Disselhorst-Klug C. Muscular coordination of biceps brachii and brachioradialis in elbow flexion with respect to hand position. Front Physiol. 2015;6:215.",
        "Kubo 2019" to "Kubo K, Ikebukuro T, Yata H. Effects of squat training with different depths on lower limb muscle volumes. Eur J Appl Physiol. 2019;119(9):1933-1942.",
        "Lauver 2016" to "Lauver JD, Cayot TE, Scheuermann BW. Influence of bench angle on upper extremity muscular activation during bench press exercise. Eur J Sport Sci. 2016;16(3):309-316.",
        "Lehman 2001" to "Lehman GJ, McGill SM. Quantification of the differences in electromyographic activity magnitude between the upper and lower portions of the rectus abdominis muscle during selected trunk exercises. Phys Ther. 2001;81(5):1096-1101.",
        "Ludewig 2004" to "Ludewig PM, Hoff MS, Osowski EE, Meschke SA, Rundquist PJ. Relative balance of serratus anterior and upper trapezius muscle activity during push-up exercises. Am J Sports Med. 2004;32(2):484-493.",
        "Lusk 2010" to "Lusk SJ, Hale BD, Russell DM. Grip width and forearm orientation effects on muscle activity during the lat pull-down. J Strength Cond Res. 2010;24(7):1895-1900.",
        "Maeo 2021" to "Maeo S, Huang M, Wu Y, et al. Greater hamstrings muscle hypertrophy but similar damage protection after training at long versus short muscle lengths. Med Sci Sports Exerc. 2021;53(4):825-837.",
        "Maeo 2023" to "Maeo S, Wu Y, Huang M, et al. Triceps brachii hypertrophy is substantially greater after elbow extension training performed in the overhead versus neutral arm position. Eur J Sport Sci. 2023;23(7):1240-1250.",
        "Marchetti 2011" to "Marchetti PH, Uchida MC. Effects of the pullover exercise on the pectoralis major and latissimus dorsi muscles as evaluated by EMG. J Appl Biomech. 2011;27(4):380-384.",
        "McAllister 2013" to "McAllister MJ, Schilling BK, Hammond KG, Weiss LW, Farney TM. Effect of grip width on electromyographic activity during the upright row. J Strength Cond Res. 2013;27(1):181-187.",
        "McAllister 2014" to "McAllister MJ, Hammond KG, Schilling BK, et al. Muscle activation during various hamstring exercises. J Strength Cond Res. 2014;28(6):1573-1580.",
        "McGill 2009" to "McGill SM, McDermott A, Fenwick CM. Comparison of different strongman events: trunk muscle activation and lumbar spine motion, load, and stiffness. J Strength Cond Res. 2009;23(4):1148-1161.",
        "Mogk 2003" to "Mogk JPM, Keir PJ. The effects of posture on forearm muscle loading during gripping. Ergonomics. 2003;46(9):956-975.",
        "Neumann 2017" to "Neumann DA. Kinesiology of the Musculoskeletal System, 3rd ed. Elsevier; 2017. (muscle actions where no EMG study fits)",
        "Oliveira 2009" to "Oliveira LF, Matta TT, Alves DS, Garcia MA, Vieira TM. Effect of the shoulder position on the biceps brachii EMG in different dumbbell curls. J Sports Sci Med. 2009;8(1):24-29.",
        "Ono 2011" to "Ono T, Higashihara A, Fukubayashi T. Hamstring functions during hip-extension exercise assessed with electromyography and magnetic resonance imaging. Res Sports Med. 2011;19(1):42-52.",
        "Reinold 2004" to "Reinold MM, Wilk KE, Fleisig GS, et al. Electromyographic analysis of the rotator cuff and deltoid musculature during common shoulder external rotation exercises. J Orthop Sports Phys Ther. 2004;34(7):385-394.",
        "Reinold 2009" to "Reinold MM, Escamilla RF, Wilk KE. Current concepts in the scientific and clinical rationale behind exercises for glenohumeral and scapulothoracic musculature. J Orthop Sports Phys Ther. 2009;39(2):105-117.",
        "Rodriguez-Ridao 2020" to "Rodriguez-Ridao D, Antequera-Vique JA, Martin-Fuentes I, Muyor JM. Effect of five bench inclinations on the electromyographic activity of the pectoralis major, anterior deltoid, and triceps brachii during the bench press exercise. Int J Environ Res Public Health. 2020;17(19):7339.",
        "Saeterbakken 2013" to "Saeterbakken AH, Fimland MS. Effects of body position and loading modality on muscle activity and strength in shoulder presses. J Strength Cond Res. 2013;27(7):1824-1831.",
        "Sarti 1996" to "Sarti MA, Monfort M, Fuster MA, Villaplana LA. Muscle activity in upper and lower rectus abdominus during abdominal exercises. Arch Phys Med Rehabil. 1996;77(12):1293-1297.",
        "Selkowitz 2013" to "Selkowitz DM, Beneck GJ, Powers CM. Which exercises target the gluteal muscles while minimizing activation of the tensor fascia lata? J Orthop Sports Phys Ther. 2013;43(2):54-64.",
        "Serner 2014" to "Serner A, Jakobsen MD, Andersen LL, et al. EMG evaluation of hip adduction exercises for soccer players: implications for exercise selection in prevention and treatment of groin injuries. Br J Sports Med. 2014;48(14):1108-1114.",
        "Stastny 2017" to "Stastny P, Golas A, Blazek D, et al. A systematic review of surface electromyography analyses of the bench press movement task. PLoS One. 2017;12(2):e0171632.",
        "Trebs 2010" to "Trebs AA, Brandenburg JP, Pitney WA. An electromyography analysis of 3 muscles surrounding the shoulder joint during the performance of a chest press exercise at several angles. J Strength Cond Res. 2010;24(7):1925-1930.",
        "Youdas 2010" to "Youdas JW, Amundson CL, Cicero KS, et al. Surface electromyographic activation patterns and elbow joint motion during a pull-up, chin-up, or perfect-pullup rotational exercise. J Strength Cond Res. 2010;24(12):3404-3414.",
    )

    private val groupOf: Map<String, String> = groups.flatMap { (g, hs) -> hs.map { it to g } }.toMap()

    /** Every individual muscle, grouped. */
    val all: List<String> = groups.values.flatten()

    fun group(muscle: String): String = groupOf[muscle] ?: muscle

    /** "biceps (long head)" -> "Biceps (Long Head)"; "middle back (rhomboids)" -> "Middle Back (Rhomboids)". */
    fun label(m: String): String = m.split(" ").joinToString(" ") { w ->
        val i = w.indexOfFirst { it.isLetter() }
        if (i < 0 || w == "&" || w == "TFL") w else w.substring(0, i) + w[i].uppercase() + w.substring(i + 1)
    }

    /** Muscle names saved by older versions -> current ones (unknown names are dropped). */
    fun migrate(detail: Map<String, Float>): Map<String, Float> {
        if (detail.keys.all { it in groupOf }) return detail
        val out = HashMap<String, Float>()
        fun put(k: String, v: Float) { out[k] = maxOf(out[k] ?: 0f, v) }
        for ((k, v) in detail) when (k) {
            "abdominals (rectus abdominis)" -> { put("abs (upper)", v); put("abs (lower)", v * 0.9f) }
            "abdominals (obliques)" -> put("obliques", v)
            else -> if (k in groupOf) put(k, v)
        }
        return out
    }

    /** Group weights (old style: primary groups 1, secondary groups by weight) -> individual muscles. */
    fun expand(groupWeights: Map<String, Float>): Map<String, Float> {
        val out = HashMap<String, Float>()
        for ((g, w) in groupWeights) {
            val shares = defaultShares[g] ?: if (g in groupOf) mapOf(g to 1f) else continue
            for ((h, s) in shares) out[h] = maxOf(out[h] ?: 0f, w * s)
        }
        return out
    }
}
