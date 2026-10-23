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

* **Auto** (sparkle): one-tap automatic brightness, contrast, highlights, shadows and colour.
* **Rotate and crop**: rotate left/right and flip here; **Crop…** opens Image Toolbox's tools.
* **Filters**: one-tap looks (Vivid, Warm, Cool, Fade, Dramatic, Mono, Noir).
* **Adjust**: round "bubbles" for Brightness, Exposure, Contrast, Highlights, Shadows, Saturation,
  Warmth, Tint, Sharpness and Vignette, each from -100 to 100, set with the tick-mark wheel
  (double-tap the wheel to reset). **Auto** fills them in automatically.
* **More tools**: opens Image Toolbox's 16 tools with the photo, including any edits made here.

Undo/redo, Revert and Save are along the top. Save never changes the original: it saves a new
photo named `<original>_edited` in the same folder (or `Pictures/Viper Vision` if Android doesn't
allow that folder), keeping the date, camera details and location. Gallery's **Edit** button opens
this editor for every image except GIFs, which still go to Image Toolbox.

Code: `Adjustments.kt` (the image maths, run on the CPU across all cores), `EditorController.kt`
(state, undo/redo, looks), `EditorScreen.kt` and `EditorControls.kt` (UI), `ImageFiles.kt`
(loading and saving), `EditorActivity.kt` (entry point).

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
* Update checker off: it looks at Image Toolbox's own GitHub releases, which aren't Viper Vision
  updates. The first-launch "Updates" dialog is never shown (`FirstLaunchSetupDialog.kt`),
  `RootComponent.tryGetUpdate()` returns straight away, and the Updates settings section and its
  three help tips are removed.

**gallery/**
* Removed Fossify's editor: `EditActivity` and its filters, crop/draw views, dialogs and layouts.
* `extensions/Activity.kt`: `openEditor()` opens Image Toolbox.
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
