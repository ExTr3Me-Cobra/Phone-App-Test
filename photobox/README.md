# PhotoBox

One Android app made from three open-source projects:

| Folder | What it is | Upstream | Imported version | License |
|---|---|---|---|---|
| `gallery/` | Fossify Gallery: browsing, viewing and organising photos | [FossifyOrg/Gallery](https://github.com/FossifyOrg/Gallery) | 1.14.0, commit `a089289` | GPL-3.0 |
| `fossify-commons/` | Fossify Commons: shared screens, dialogs and themes that Gallery is built on | [FossifyOrg/Commons](https://github.com/FossifyOrg/Commons) | 7.1.3, commit `227e7d3` | GPL-3.0 |
| `imagetoolbox/` | Image Toolbox: the photo editor (and the build root) | [T8RIN/ImageToolbox](https://github.com/T8RIN/ImageToolbox) | 4.3.0-beta03, commit `7b81a24` | Apache-2.0 |
| `photobox/` | The glue: app name/ID, build files for Gallery and Commons, app icon | (this repo) | | |

The combined app is GPL-3.0 (Apache-2.0 code can be included in a GPL-3.0 app).

## How it fits together

* The launcher icon opens **Gallery**. Its **Edit** button opens the photo in **Image Toolbox**,
  which is inside the same app. Fossify's own editor was removed.
* `imagetoolbox/` is the Gradle project that gets built. Its `settings.gradle.kts` pulls in
  Gallery and Commons as two extra modules, `:photobox-gallery` and `:fossify-commons`, whose
  build files are in `photobox/gallery/` and `photobox/commons/`. Those build files point at the
  source in `gallery/` and `fossify-commons/`, so that code stays in its own folders.
* Image Toolbox's `Application` class is the only one; it calls `PhotoBoxGallery.init()`
  (in `photobox/gallery/src/`) to do the startup work Gallery's own `App` class used to do.

## Changing the name, ID or version

Edit `photobox/photobox.properties`. Changing `APP_ID` after you've installed the app makes
Android treat it as a different app (you'd have both).

## Building

GitHub Actions (`.github/workflows/build-photobox-apk.yml`) builds a signed release whenever any of
the four folders change, and posts **PhotoBox.apk** (works on every phone) and
**PhotoBox-arm64.apk** (smaller, for almost every modern phone) on the Releases page.

Locally (Android SDK + JDK 21):

```
cd imagetoolbox
./gradlew :app:assembleFossDebug      # debug APKs in app/build/outputs/apk/foss/debug/
./gradlew :app:assembleFossRelease    # unsigned release APKs
```

The `foss` flavor is the one without Google services. `gallery/` and `fossify-commons/` aren't
built on their own any more, only as part of PhotoBox.

## Changes made to the upstream code

Every change is marked with a `PhotoBox:` comment (search for it). When updating a folder from
upstream, these are the things to carry over.

**imagetoolbox/**
* `settings.gradle.kts`: adds the Gallery and Commons modules and their version catalogs.
* `app/build.gradle.kts`: app ID/name/version from `photobox.properties`, minimum Android 8.0
  (Gallery needs it), own file provider name, depends on Gallery, adds `photobox/res` (icon).
* `app/src/main/AndroidManifest.xml`: no launcher icon for Image Toolbox; takes over the
  Application name, icon and theme from Gallery's manifest.
* `ImageToolboxApplication.kt`: calls `PhotoBoxGallery.init(this)`.
* `gradle.properties`: Jetifier on (Gallery's pattern lock library uses the old support library).

**gallery/**
* Removed Fossify's editor: `EditActivity` and its filters, crop/draw views, dialogs and layouts.
* `extensions/Activity.kt`: `openEditor()` opens Image Toolbox.
* `AndroidManifest.xml`: no `EditActivity`; the file provider uses `PhotoBoxFileProvider`.
* Strings `height`, `width`, `images` renamed to `gallery_*` (Image Toolbox has strings with the
  same names but different wording).

**fossify-commons/**
* `helpers/PhotoBox.kt`: launcher icon aliases are looked up under `org.fossify.gallery`.
* Removed the checks that treat any app ID outside `org.fossify.*` as a fake copy: the random
  "fake version" pop-up, the theme screen being blocked after 50/100 launches, and SD card
  permission prompts being skipped.
* `SharedPreferencesProducerExtensions.kt`: rewritten for newer Kotlin (no context receivers).
* Strings `error`, `size` renamed to `commons_*` (same reason as above).
