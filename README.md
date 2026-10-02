# Video Wallpaper

An Android live wallpaper that loops a video of your choice, seamlessly and forever, on your home
screen and/or lock screen. You can use a different video for each.

* Starts automatically after every restart. You never need to open the app again.
* Silent, fills the screen (crops the edges if the video's shape doesn't match).
* Pauses whenever it's hidden (screen off, an app on top) to save battery, and carries on
  instantly when you can see it again.
* Keeps its own copy of each video, so you can move or delete the original.

## Install on your phone

1. On your phone, open this repository on GitHub, tap **Releases** and open the newest
   **Video Wallpaper** build.
2. Tap **VideoWallpaper.apk** to download it, then open the downloaded file and install it.
   (Same "unknown apps" / Auto Blocker steps as any APK you install from outside the Play Store.)
3. Open **Video Wallpaper**, choose a video, and tap **Set as wallpaper**. On the preview screen
   tap the set/apply button and pick where to use it:
   * **Same video everywhere:** choose "Home and lock screens".
   * **Different lock screen video:** pick it under "Lock screen video" in the app, apply the
     wallpaper to "Home screen", then tap **Set as wallpaper** again and apply it to "Lock screen".

Changing a video later updates the wallpaper straight away.

## Good to know

* For a perfectly seamless loop, the video itself should be a loop (its last frame flows into its
  first). The app adds no gap or flash between repeats.
* Lock-screen support for third-party live wallpapers depends on the phone's software. If only
  "Home screen" is offered, Samsung's own lock screen can play a video: Settings > Wallpaper and
  style > lock screen > add a video.
* Updating: uninstall the old version first, then install the new APK.

## How it's built

GitHub Actions (`.github/workflows/build-video-wallpaper.yml`) builds the APK on every push and
attaches it to a new release. Code is in `app/src/main/java/com/videowallpaper/app/`; playback uses
AndroidX Media3 ExoPlayer for gapless looping.
