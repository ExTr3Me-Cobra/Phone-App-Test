# Vibrate Only

An Android app (made for Samsung Galaxy / One UI) that toggles **Vibrate Only Mode** when you
press **Volume Up + Volume Down at the same time**.

| | Vibrate Only Mode ON |
|---|---|
| Phone calls, texts, notifications, system sounds | Vibrate only |
| Alarms | Sound normally |
| Music / video / media | Muted |
| Volume buttons | Media volume while something is playing (unmutes it until 30 s after it stops), otherwise alarm volume. They never take the phone off vibrate. |

* One long buzz = mode turned **ON**. Two long buzzes = mode turned **OFF** (only when you press
  the buttons).
* **Headphones / speakers:** connecting any media device (Bluetooth, wired, USB, car, hearing aid)
  switches the mode off straight away. 5 seconds after it disconnects, Vibrate Only comes back on.
  If the mode was off, devices just work normally. The two-button press is ignored while one is
  connected.
* **Workplace:** optionally turns on when you arrive at work and off when you leave
  (Settings → Workplace; needs location "Allow all the time").
* **Settings** button in the app: press timing, screen-off on/off, media muting, workplace, buzz
  strength/length, a status notification with a "Turn off" button, an auto-off timer, and whether
  changing the sound mode elsewhere ends the mode.
* Optional quick settings tile: swipe down twice, tap the pencil (edit), drag in **Vibrate Only**.
* Runs in the background all the time and starts again by itself after a reboot.

## Install on your phone

1. On your phone, open this repository on GitHub, tap **Releases** and open the newest one.
2. Tap **VibrateOnly.apk** to download it, then open the downloaded file.
3. If the phone says installing unknown apps is blocked, tap **Settings** in that message and
   allow your browser (or My Files) to install apps. If Samsung's **Auto Blocker** stops it, turn
   it off in Settings > Security and privacy > Auto Blocker, install, then turn it back on.
4. Open the **Vibrate Only** app and follow the setup steps on screen. Each step gets a ✅ when
   it's done.
   * On step 1, if the switch is greyed out ("Restricted setting"), use step 1b: App info >
     ⋮ menu > **Allow restricted settings**, then try step 1 again.

**Updating:** builds are now all signed with the same key, so new versions install over the old
one. (Builds made before this change used a different key each time: uninstall that old version
once, then install the new one and redo the setup.)

## Good to know

* **Screen off:** Android doesn't let apps see button presses with the screen off, so the app uses
  a workaround (an invisible, silent media session) to catch the volume buttons while the screen
  is off. It should work, but it's not guaranteed on every phone or software update. With the
  screen on (including the lock screen) it always works.
* **Volume buttons on their own** still work, just a split second later (the app waits
  ~0.15 s, adjustable in Settings, to see whether the other button is pressed too).
* By default, if the sound mode gets switched in quick settings while the mode is on, the app puts
  it straight back to vibrate. You can change that in Settings.

## How it's built

GitHub Actions (`.github/workflows/build-apk.yml`) builds the APK on every push and attaches it to
a new release. The code is in `app/src/main/java/com/vibrateonly/app/`.
