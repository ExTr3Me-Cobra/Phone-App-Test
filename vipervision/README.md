# Viper Vision

One Android app made from three open-source projects:

| Folder | What it is | Upstream | Imported version | License |
|---|---|---|---|---|
| `gallery/` | Fossify Gallery: browsing, viewing and organising photos | [FossifyOrg/Gallery](https://github.com/FossifyOrg/Gallery) | 1.14.0, commit `a089289` | GPL-3.0 |
| `fossify-commons/` | Fossify Commons: shared screens, dialogs and themes that Gallery is built on | [FossifyOrg/Commons](https://github.com/FossifyOrg/Commons) | 7.1.3, commit `227e7d3` | GPL-3.0 |
| `imagetoolbox/` | Image Toolbox: the photo editor (and the build root) | [T8RIN/ImageToolbox](https://github.com/T8RIN/ImageToolbox) | 4.3.0-beta03, commit `7b81a24` | Apache-2.0 |
| `vipervision/` | Viper Vision's own code: the photo editor, app name/ID, build files for Gallery and Commons, app icon | (this repo) | | |

The combined app is GPL-3.0 (Apache-2.0 code can be included in a GPL-3.0 app).

## How it fits together

* The launcher icon opens **Gallery**. Its **Edit** button opens the photo in **Image Toolbox**,
  which is inside the same app. Fossify's own editor was removed.
* `imagetoolbox/` is the Gradle project that gets built. Its `settings.gradle.kts` pulls in
  Gallery and Commons as two extra modules, `:vipervision-gallery` and `:fossify-commons`, whose
  build files are in `vipervision/gallery/` and `vipervision/commons/`. Those build files point at the
  source in `gallery/` and `fossify-commons/`, so that code stays in its own folders.
* Image Toolbox's `Application` class is the only one; it calls `ViperVisionGallery.init()`
  (in `vipervision/gallery/src/`) to do the startup work Gallery's own `App` class used to do.

## The editor (`vipervision/editor`)

Viper Vision's own photo editor, styled after Samsung's: the photo as large as the screen allows
(press and hold it to compare with the original), and along the bottom:

* **AI tools** (blue sparkle): **Auto background remover** and **Manual background remover**
  (below), **Blemish remover** (paint over something and Image Toolbox's LaMa model fills in
  what was behind it, about 200 MB), the Samsung bubbles (below), then one bubble per job.
  Each offers the one or two models regarded as best for it, with a sheet saying what each is
  good at, its size and whether it's downloaded:
  * Restore faces: GFPGAN 1.4 (natural, keeps identity) or CodeFormer (new, from FaceFusion's
    ONNX; rebuilds badly damaged faces).
  * Denoise: SCUNet Real (GAN, keeps texture) or SCUNet Smooth (PSNR, cleanest).
  * Deblur: NAFNet (motion blur) or Fatality DeBlur (soft focus).
  * JPEG cleanup: FBCNN (adapts to the compression level) or SBDV DeJPEG Lite (fast).
  * Low light (Zero-DCE++), Fix exposure (Overexposure Correction).
  * Auto background remover: U2NetP (built in) or BiRefNet (best edges).
  Each model downloads once, then works offline. AI steps run on the full-size photo, happen
  before rotate/crop/adjustments, and undo like any other edit. With the background removed, the
  photo saves as PNG to keep the transparency.
* **Manual background remover** (scissors, in the AI row): Image Toolbox's whole Background Remover in the
  editor's style. **Smart select** turns rough painting into EdgeSAM (Segment Anything)
  prompts: paint over what to keep (green) or switch to "Leave out" (blue) and the AI finds the
  exact edge, live. **Refine edges** (optional) then has BiRefNet redraw the edge of the
  selection when cutting out: hair and fur strands, no background fringe, shadows dropped. **Auto** runs any of its models (U2NetP built in; U2Net, RMBG, InSPyReNet,
  BiRefNet, ISNet, YOLO, MODNet, MobileSAM download on first use). **Erase** / **Restore**
  brushes with shapes (free, line, lasso, rectangle, oval, fill, spray), size (side slider) and
  softness, zoom/pan and magnifier. **Options**: trim empty edges, show the original faintly,
  grid, magnifier, clear brushes, start over. The result becomes an undoable step and the photo
  saves as PNG to keep the transparency.
* **Rotate and crop**: a crop frame over the whole photo (drag a corner or edge to resize, drag
  inside to move; a rule-of-thirds grid shows while dragging), shape chips (Free, Original, 1:1,
  4:3, 3:4, 16:9, 9:16), rotate left/right, flip and reset crop. The crop turns with the photo.
  **Advanced** opens Image Toolbox's Crop tool: straighten by any angle, perspective (free
  corners) crop, and shaped crops (circle, star, heart, your own image as a mask...).
* **Adjust**: round "bubbles" for Brightness, Exposure, Contrast, Highlights, Shadows, Saturation,
  Warmth, Tint, Sharpness and Vignette, each from -100 to 100, set with the tick-mark wheel
  (double-tap the wheel to reset). **Auto** fills them in automatically. These use Image
  Toolbox's own filters: the Aire library for brightness, contrast and saturation, and its GPU
  filters (exposure, wide-range highlights/shadows, white balance, sharpen, vignette) in one GPU
  pass. `Adjustments.kt` notes how each dial maps onto the filter's range. The last bubble,
  **Curves** (after Heal) opens Image Toolbox's tone curve editor (RGB and each channel, with
  the photo's histogram) with preset import/export (.itcurve, Photoshop .acv, and more).
  **Heal**, removes spots and small blemishes: paint over them and it heals them like a healing
  brush: it finds a nearby patch whose surroundings match and blends that patch's real texture in
  (OpenCV seamless clone, `PatchHeal.kt`), so skin grain, fabric and straight edges carry through.
  No download, not AI. While painting (Heal or Object
  eraser), one finger paints, two fingers pinch to zoom and move around, and the wedge slider on
  the right sets the brush size (a circle shows how big while you drag it).
* **Draw and decorate** (pen tab):
  * **Draw**: Image Toolbox's Draw tool in the editor's layout — pen, highlighter and neon;
    quick colours; size on the side slider; eraser, undo/redo, move/zoom. **Settings** opens Image
    Toolbox's Draw options for those modes (colour picker and gradients, opacity, softness, every
    shape and arrow, line styles, line angle, fonts, grid, magnifier).
  * **Text & layers**: Image Toolbox's Markup Layers — text, pictures, emoji and shapes as layers
    you can drag, pinch, twist, snap, restyle (fonts, colours, outlines, shadows, blend modes,
    opacity), group, lock, reorder and edit until you tick.
  Both become an undoable step.

Undo/redo, Revert and Save are along the top. **⋮ → Save as…** uses Image Toolbox's export
options: any of its formats (JPEG, PNG and its optimised variants, WebP, AVIF, HEIC, JXL, BMP,
TIFF, QOI, ICO...) with their quality settings, resizing with its resize types and scaling
algorithms, and keeping, editing or removing the photo details. Save never changes the original: it saves a new
photo named `<original>_edited` in the same folder (or `Pictures/Viper Vision` if Android doesn't
allow that folder), keeping the date, camera details and location. Gallery's **Edit** button opens
this editor for every image (a GIF opens as a still picture and saves as a copy).

**Video editor** (`VideoEditorActivity.kt`, `VideoAdjustEffect.kt`): Gallery's **Edit** button on a
video opens it. The video fills the space above the controls; pinch to zoom (drag to move,
double-tap to reset). The first bubble is **Trim**: drag either end of the frame strip, or anywhere
else to move the playhead; press and hold to zoom the strip into ~2 seconds around your finger for
frame-accurate moves until you let go. Playback loops inside the kept part. Then the same Adjust
dials as photos, shown live while playing; while paused, the exact frame is shown with the dials
applied by the photo editor's own maths (`VideoFrames.kt` reads single frames).
The dials run as one GPU shader with the photo editor's maths (Aire brightness/contrast, then
Image Toolbox's GPU saturation, exposure, highlights/shadows, white balance, sharpen and
vignette). Save exports with Media3 Transformer (HDR video is converted to SDR) to a new
`<original>_edited.mp4` next to the original (DCIM, Movies or Pictures; otherwise
`Movies/Viper Vision`).

**Camera thumbnail shortcut** (`CameraThumbnailService.kt`): an accessibility service, turned
on from Gallery's ⋮ → "Camera thumbnail → Viper Vision". While Samsung Camera is on screen (and the
phone is unlocked) it puts an invisible button over the camera's thumbnail; tapping it opens the
newest photo or video in DCIM/Camera in Viper Vision's viewer instead of Samsung Gallery. It finds
the thumbnail by its id/description, or failing that the spot left of the shutter button.

Code: `Adjustments.kt` (adjustments via Image Toolbox's libraries), `HighlightShadowWideRangeFilter.kt`
(Image Toolbox's shader, copied with attribution), `AiTools.kt` (AI bubbles and the runner),
`EditorController.kt` (state, undo/redo, crop shapes, AI results), `PixelSteps.kt` and
`PaintOverlay.kt` (Heal / Object eraser brush and masks), `EditorScreen.kt`,
`EditorControls.kt` and `CropOverlay.kt` (UI), `ImageFiles.kt` (rotate/flip/crop, loading and
saving), `EditorActivity.kt` (entry point, AI, heal and erase runs), `EditFinisher.kt` (adjustments and curves for preview and saving), `ToolboxSupport.kt` (hosting Image Toolbox's sheets: the editor is an Image
Toolbox `ComposeActivity` with its settings forced to a dark theme).

## The Studio (`ViperStudioActivity`)

Image Toolbox's tools that work on several photos or make new pictures, each fully featured, in
Viper Vision's layout (a black, compact screen with the picture large and the settings below).
Image Toolbox's shared tool layout (`core/ui/.../AdaptiveLayoutScreen.kt`) switches to
`ViperStudioLayout.kt` when `LocalViperStyle` is set, so the tools themselves are unchanged.

* From Gallery's selection menu (⋮ with photos selected): **Compare** (2 photos: slider, side by
  side, tap to switch, transparency, pixel difference; save or animate the comparison), **Fuse
  frames** (2+ photos: HDR, focus stacking, low-light noise reduction).
* The Create menu (Gradient, Mesh gradient, Texture, Export wallpapers) was removed, along with
  the Mesh Gradients, Texture Generation and Wallpapers Export modules, and later Gradient over
  photos with the Gradient Maker module.

Image Toolbox's Gallery and Image Preview tools aren't moved over: Fossify Gallery already does
both.

## The icon

`icon/viper_eye_original.jpg` is the icon picture (a blue macro snake eye), and
`icon/viper_eye_1024.png` is the same picture with its dark frame trimmed. `res/` holds the
app-level resources built from it, which win over anything with the same name in Gallery, Commons
or Image Toolbox:

* `drawable-*/vv_launcher_photo.png`: the icon layer. The picture is shrunk a little inside it
  so the part launchers show (the middle two thirds) still includes the scales around the eye.
* `mipmap-anydpi-v26/ic_launcher*.xml`: every launcher icon, including all of Gallery's
  "change icon colour" variants, so the icon stays the snake eye whichever colour is picked.
* `drawable/ic_launcher_monochrome.xml`: eye outline for Android's themed icons.
* `drawable/ic_launcher_foreground.xml`: same outline in blue, used for notifications and on
  Image Toolbox's settings screen.

To use a different picture, regenerate the five `vv_launcher_photo.png` sizes (108, 162, 216,
324 and 432 px) from it. `icon/snake_eye.py` is the generator for the earlier rendered (green)
icon, kept in case it's useful.

## Changing the name, ID or version

Builds on GitHub use the workflow run number as the version (shown as "1.0.0 build N" in
Android's app info and the About screen), so you can always tell which build is installed.

Edit `vipervision/vipervision.properties`. Changing `APP_ID` after you've installed the app makes
Android treat it as a different app (you'd have both).

## Building

GitHub Actions (`.github/workflows/build-vipervision-apk.yml`) builds a signed release whenever any of
the four folders change, and posts **ViperVision.apk** on the Releases page. It's built for arm64
phones only (every current Samsung Galaxy and practically every phone from the last ~8 years),
which keeps builds faster (and Image Toolbox's slow extra APK recompression is off, see
`OPTIMIZE_APK`); add more processor types under `ABIS` in `vipervision.properties` if
needed. A build takes roughly 25-40 minutes from scratch, less once GitHub's build cache is warm.

Locally (Android SDK + JDK 21):

```
cd imagetoolbox
./gradlew :app:assembleFossDebug      # debug APKs in app/build/outputs/apk/foss/debug/
./gradlew :app:assembleFossRelease    # unsigned release APKs
```

The `foss` flavor is the one without Google services. `gallery/` and `fossify-commons/` aren't
built on their own any more, only as part of Viper Vision.

## Changes made to the upstream code

Every change is marked with a `Viper Vision:` comment (search for it). When updating a folder from
upstream, these are the things to carry over.

**imagetoolbox/**
* `settings.gradle.kts`: adds the Gallery and Commons modules and their version catalogs.
* `app/build.gradle.kts`: app ID/name/version from `vipervision.properties`, minimum Android 8.0
  (Gallery needs it), own file provider name, depends on Gallery, adds `vipervision/res` (icon).
* `app/src/main/AndroidManifest.xml`: no launcher icon for Image Toolbox; takes over the
  Application name, icon and theme from Gallery's manifest.
* `ImageToolboxApplication.kt`: calls `ViperVisionGallery.init(this)`.
* `gradle.properties`: Jetifier on (Gallery's pattern lock library uses the old support library).
* **Trimmed to 16 tools:** Single Edit, Format Conversion, Crop, Filter, Draw, Background
  Remover, Markup Layers, AI Tools, Tone Curves, Gradient Maker, Multi-frame Fusion, Texture
  Generation, Compare, Gallery, Image Preview and Wallpapers Export. The other 38 tools' modules
  (and `lib/collages`) are deleted and dropped from `settings.gradle.kts` and
  `feature/root/build.gradle.kts`. `core/ui/.../navigation/ViperVisionRemovedTools.kt` lists
  them; tool lists, the "open with" picker, help tips and navigation all skip anything on it.
  Color Picker and Archive Tools are hidden the same way but their modules stay, because Draw,
  Filter and Gradient Maker use them. Their quick-settings tiles are gone, the random filename
  helper moved from Cipher into `core/data`, and `instrumentation-tests` is left out of the
  build. To bring a tool back, restore its module folder from git history and undo these.
* No Image Toolbox promotion: the "Contact me" settings section, the Telegram group link, and the
  Telegram, donate, translate (Weblate) and GitHub review pop-ups are removed
  (`SettingsGroup.kt`, `RootDialogs.kt`).
* Update checker off: it looks at Image Toolbox's own GitHub releases, which aren't Viper Vision
  updates. The first-launch "Updates" dialog is never shown (`FirstLaunchSetupDialog.kt`),
  `RootComponent.tryGetUpdate()` returns straight away, and the Updates settings section and its
  three help tips are removed.
* Image Toolbox's own app removed: its main screen (`AppActivity`, `feature/root`, `feature/main`),
  settings screens, help, logs, usage statistics, libraries/about pages, easter egg, Single Edit,
  Image Preview, the curves screen and its quick-settings tiles (plus `lib/fast-scroller`). It no
  longer appears in "Open with" or Share. What stays is only what Viper Vision's editor and
  Studio use behind the scenes (`feature/filters` is kept for its filter engine). "Open the app"
  links (notifications, the crash screen) open Gallery.
* Size trims: English only (`localeFilters` in `app/build.gradle.kts`; Viper Vision's own
  screens are English), no PDF library (pdfbox) or BouncyCastle (only removed tools used them),
  no PDF font, no QR scanner set-up, no DjVu decoder, and emojis are static (the animated
  Lottie set and its player are gone).
* AI chat Assistant removed entirely: the `feature/assistant` module, its language-model code in
  `lib/neural-tools` (and the ONNX GenAI library it needed), `Screen.Assistant`, its buttons on
  the main screen and the settings edge tab, its setting, stored preferences and help tips.

**gallery/**
* Removed Fossify's editor: `EditActivity` and its filters, crop/draw views, dialogs and layouts.
* `extensions/Activity.kt`: `openEditor()` opens Viper Vision's editor.
* `AndroidManifest.xml`: no `EditActivity`; the file provider uses `ViperVisionFileProvider`.
* Strings `height`, `width`, `images` renamed to `gallery_*` (Image Toolbox has strings with the
  same names but different wording).

**fossify-commons/**
* `helpers/ViperVision.kt`: launcher icon aliases are looked up under `org.fossify.gallery`.
* Removed the checks that treat any app ID outside `org.fossify.*` as a fake copy: the random
  "fake version" pop-up, the theme screen being blocked after 50/100 launches, and SD card
  permission prompts being skipped.
* `SharedPreferencesProducerExtensions.kt`: rewritten for newer Kotlin (no context receivers).
* Strings `error`, `size` renamed to `commons_*` (same reason as above).
* `extensions/SupportPrompt.kt`: Fossify's automatic donation / rating prompt is off.

## AI models and AI search

* **AI models**: every model the app uses (the editor's AI bubbles, Blemish remover, Manual
  background remover's models and Smart select, and AI search), about 3.7 GB, is mirrored into
  this repository's permanent `vipervision-ai-models` release (`vipervision/models/urls.txt`,
  `.github/workflows/mirror-ai-models.yml`). The app downloads from there first, falling back to
  the original site (`AndroidDownloadManager.mirrorUrl`). Settings → AI models (offered once on
  first launch) downloads them all at once; they stay through updates.
* **AI search** (magnifier button on the albums and photos screens, `search/`): Google's SigLIP 2
  (base, 256 px) for what's in each photo, plus PaddleOCR for words in it; a search adds both
  scores. Photos are indexed in the background (WorkManager), new ones as they arrive. Image
  Toolbox's CLIP search in its photo picker is switched off.
