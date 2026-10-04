# Web Shortcuts

A small Android app (Kotlin, Jetpack Compose) that puts websites on your home screen with an
icon you choose. Made for Samsung's stock One UI Home launcher, with no other launcher needed.

* Enter a web address (https:// is added for you) and a name.
* Pick an image with the Android Photo Picker (no storage permission).
* Frame it: **Fill** (covers the whole icon) or **Fit** (inside the safe zone on a background
  colour you choose), drag to move, pinch or slide to zoom.
* Live circle and squircle previews.
* **Add to Home Screen** uses `ShortcutManager.requestPinShortcut` with
  `Icon.createWithAdaptiveBitmap`, so One UI sizes and masks the icon exactly like app icons.
  Tapping it opens the site in your default browser.
* The app keeps a list of its shortcuts. Edit the icon, name or address and the icon on the home
  screen updates in place (`ShortcutManager.updateShortcuts`). Delete entries from the list.
* No internet permission, no ads, no analytics, no permissions at all.

## How the icon is built

An adaptive icon is a 108×108dp square. The launcher scales it so the middle 72×72dp fills the icon
slot and cuts it with its mask. Only the middle 66dp circle (the "safe zone") is guaranteed to show
under every mask. The editor shows the full 108dp canvas: the dimmed band is never shown, the solid
outline is the typical visible shape, and the dashed circle is the safe zone.

The icon bitmap is rendered at the largest size the system keeps for shortcut icons
(`ShortcutManager.getIconMaxWidth/Height` × 1.5 for the adaptive margin), drawn at twice that
size and scaled down once for sharpness.

## Build it yourself (Windows, Android Studio)

### 1. Get the project onto your PC
1. On GitHub, open this repository, switch to the **claude/web-shortcuts-app** branch
   (branch drop-down at the top left of the file list).
2. Click the green **Code** button → **Download ZIP**.
3. Right-click the downloaded ZIP → **Extract All…** and pick a simple folder such as
   `C:\Projects\WebShortcuts`. (Avoid very long paths or OneDrive folders.)

### 2. Open it in Android Studio
1. Open Android Studio → **File → Open…** and select the extracted folder (the one that contains
   `settings.gradle.kts`), then **OK**. If asked, choose **Trust Project**.
2. Wait for the **Gradle sync** to finish (progress bar at the bottom; the first time it downloads
   a lot and can take several minutes). If Android Studio offers to install a missing SDK
   platform (Android 16 / API 36), accept it.
3. If it suggests upgrading the Android Gradle Plugin, you can safely say **Don't remind me**.
   The project builds as is.

### 3. Build the APK
1. Open **Build Variants** (View → Tool Windows → Build Variants) and set `app` to **release**.
   Release builds are optimised and noticeably smoother than debug builds. They're signed with
   Android Studio's own debug key, which is fine for installing on your own phone.
2. **Build → Generate App Bundles or APKs → Generate APKs** (older versions: **Build → Build
   Bundle(s) / APK(s) → Build APK(s)**).
3. When it says **APK(s) generated successfully**, click **locate** in the pop-up. The file is
   `app\build\outputs\apk\release\app-release.apk`.

### 4. Copy it to your Samsung phone
Pick one:
* **USB cable:** connect the phone, pull down the notification shade, tap the USB notification and
  choose **File transfer**. In File Explorer open the phone → **Internal storage → Download**
  and copy `app-release.apk` there.
* **Without a cable:** email it to yourself, or upload it to Google Drive and download it on the
  phone.

### 5. Install it
1. On the phone open **My Files → Downloads** (or Internal storage → Download) and tap
   `app-release.apk`.
2. The first time, Android says installing unknown apps isn't allowed: tap **Settings**, switch on
   **Allow from this source** for My Files, then go back and tap **Install**.
   (You can find this later at Settings → Apps → ⋮ → Special access → Install unknown apps.)
3. If **Auto Blocker** blocks it: Settings → Security and privacy → Auto Blocker → turn it off,
   install, then turn it back on.
4. If Play Protect warns about an unknown app, tap **More details → Install anyway**.

**Updating:** build again and install the new APK over the old one; your shortcuts are kept.
(This works because Android Studio signs every build with the same key on your PC.)

## Ready-made APK

GitHub Actions also builds the app on every push to this branch: see **Releases → Web Shortcuts**.
Those builds are signed with a different key than your PC's, so if you switch between the two you
must uninstall first (which removes the app's shortcut list).

## Good to know
* If "Add to Home Screen" does nothing on Samsung, check Settings → Home screen →
  **Lock Home screen layout** is off.
* Samsung may show the Web Shortcuts icon as a tiny badge on the shortcut; that's One UI's choice.
* Deleting an entry can't remove the icon from the home screen (apps aren't allowed to). Long-press
  the icon → Remove. The delete dialog can also grey the icon out so it stops working.
