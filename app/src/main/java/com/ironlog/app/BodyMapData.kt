package com.ironlog.app

/**
 * Muscle outlines traced on the body picture (assets/body.jpg, 1082 x 976).
 * Each shape is mirrored to the other side unless it sits on the centre line (neck).
 */
object BodyMapData {
    const val W = 1082f
    const val H = 976f
    /** Crop boxes: left, top, right, bottom. */
    val frontBox = floatArrayOf(100f, 80f, 470f, 900f)
    val backBox = floatArrayOf(615f, 80f, 985f, 900f)
    val regions: List<Region> = listOf(
        Region("chest", floatArrayOf(283f, 222f, 255f, 214f, 228f, 222f, 208f, 240f, 204f, 265f, 214f, 288f, 240f, 300f, 268f, 298f, 283f, 292f), 285f, true),
        Region("shoulders", floatArrayOf(228f, 212f, 205f, 206f, 182f, 212f, 166f, 232f, 160f, 258f, 165f, 285f, 176f, 300f, 190f, 290f, 200f, 262f, 210f, 240f, 222f, 222f), 285f, true),
        Region("traps", floatArrayOf(272f, 196f, 250f, 208f, 228f, 212f, 250f, 218f, 272f, 214f), 285f, true),
        Region("biceps", floatArrayOf(196f, 280f, 185f, 272f, 170f, 285f, 160f, 315f, 158f, 345f, 165f, 360f, 180f, 352f, 192f, 325f, 198f, 300f), 285f, true),
        Region("triceps", floatArrayOf(162f, 282f, 150f, 295f, 142f, 322f, 146f, 350f, 155f, 352f, 158f, 320f), 285f, true),
        Region("forearms", floatArrayOf(172f, 358f, 160f, 355f, 140f, 372f, 126f, 405f, 122f, 440f, 126f, 455f, 142f, 455f, 155f, 428f, 165f, 398f, 174f, 372f), 285f, true),
        Region("grip", floatArrayOf(125f, 458f, 150f, 457f, 168f, 470f, 172f, 500f, 160f, 515f, 138f, 512f, 124f, 490f), 285f, true),
        Region("abdominals", floatArrayOf(283f, 300f, 262f, 305f, 258f, 340f, 258f, 380f, 262f, 420f, 272f, 450f, 285f, 460f), 285f, true),
        Region("abdominals", floatArrayOf(256f, 344f, 240f, 346f, 226f, 338f, 222f, 370f, 225f, 410f, 240f, 440f, 258f, 450f, 262f, 420f, 258f, 380f), 285f, true),
        Region("serratus", floatArrayOf(252f, 302f, 232f, 300f, 222f, 312f, 226f, 330f, 242f, 342f, 256f, 334f), 285f, true),
        Region("quadriceps", floatArrayOf(200f, 470f, 240f, 458f, 258f, 470f, 260f, 530f, 255f, 590f, 242f, 630f, 222f, 640f, 200f, 630f, 190f, 600f, 186f, 540f, 190f, 495f), 285f, true),
        Region("adductors", floatArrayOf(258f, 470f, 280f, 480f, 282f, 505f, 276f, 550f, 262f, 590f, 255f, 590f, 260f, 530f), 285f, true),
        Region("abductors", floatArrayOf(196f, 445f, 186f, 470f, 182f, 500f, 190f, 500f, 200f, 470f), 285f, true),
        Region("calves", floatArrayOf(232f, 680f, 244f, 700f, 244f, 750f, 236f, 780f, 226f, 770f, 224f, 720f), 285f, true),
        Region("calves", floatArrayOf(190f, 690f, 198f, 700f, 200f, 760f, 192f, 800f, 184f, 790f, 180f, 740f), 285f, true),
        Region("neck", floatArrayOf(272f, 182f, 298f, 182f, 300f, 214f, 285f, 222f, 270f, 214f), null, true),
        Region("traps", floatArrayOf(800f, 182f, 785f, 185f, 770f, 200f, 745f, 212f, 725f, 222f, 745f, 232f, 760f, 240f, 775f, 270f, 790f, 320f, 800f, 345f), 800f, false),
        Region("shoulders", floatArrayOf(720f, 222f, 700f, 215f, 678f, 222f, 660f, 245f, 652f, 275f, 660f, 300f, 680f, 300f, 700f, 280f, 715f, 255f), 800f, false),
        Region("rotator cuff", floatArrayOf(720f, 242f, 760f, 240f, 770f, 250f, 765f, 295f, 735f, 300f, 720f, 290f), 800f, false),
        Region("middle back", floatArrayOf(770f, 240f, 790f, 245f, 797f, 300f, 793f, 330f, 780f, 320f, 770f, 290f), 800f, false),
        Region("lats", floatArrayOf(722f, 302f, 760f, 302f, 780f, 322f, 790f, 360f, 788f, 400f, 770f, 420f, 750f, 430f, 735f, 410f, 725f, 370f, 718f, 330f), 800f, false),
        Region("lower back", floatArrayOf(790f, 335f, 800f, 332f, 800f, 445f, 785f, 445f, 775f, 420f, 780f, 370f), 800f, false),
        Region("triceps", floatArrayOf(712f, 290f, 695f, 280f, 665f, 290f, 648f, 310f, 640f, 345f, 648f, 362f, 670f, 358f, 690f, 345f, 705f, 320f), 800f, false),
        Region("forearms", floatArrayOf(665f, 362f, 648f, 362f, 640f, 390f, 642f, 430f, 648f, 460f, 662f, 462f, 672f, 430f, 680f, 395f, 690f, 370f), 800f, false),
        Region("grip", floatArrayOf(645f, 462f, 665f, 462f, 690f, 478f, 695f, 505f, 680f, 518f, 655f, 516f, 642f, 495f), 800f, false),
        Region("glutes", floatArrayOf(800f, 432f, 770f, 426f, 748f, 440f, 738f, 470f, 745f, 505f, 770f, 522f, 797f, 520f, 800f, 500f), 800f, false),
        Region("abductors", floatArrayOf(742f, 415f, 762f, 420f, 752f, 440f, 738f, 455f), 800f, false),
        Region("hamstrings", floatArrayOf(735f, 524f, 788f, 527f, 792f, 560f, 785f, 610f, 770f, 655f, 745f, 660f, 725f, 640f, 712f, 590f, 715f, 545f), 800f, false),
        Region("adductors", floatArrayOf(790f, 527f, 800f, 530f, 800f, 580f, 794f, 600f, 790f, 570f), 800f, false),
        Region("calves", floatArrayOf(705f, 665f, 745f, 665f, 758f, 690f, 755f, 740f, 740f, 770f, 720f, 775f, 700f, 760f, 692f, 720f, 695f, 690f), 800f, false),
        Region("neck", floatArrayOf(786f, 160f, 814f, 160f, 816f, 190f, 800f, 196f, 784f, 190f), null, false),
    )
    /** Muscles drawn as a ring round a joint: centre x, y, radius x, y, ring thickness (mirrored). */
    val rings: List<Ring> = listOf(
        Ring("rotator cuff", 190f, 240f, 24f, 30f, 9f, 285f, true),
        Ring("rotator cuff", 688f, 242f, 24f, 30f, 9f, 800f, false),
    )
    class Region(val muscle: String, val pts: FloatArray, val mirrorX: Float?, val front: Boolean)
    class Ring(val muscle: String, val cx: Float, val cy: Float, val rx: Float, val ry: Float, val thick: Float, val mirrorX: Float, val front: Boolean)
}
