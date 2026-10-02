# Vibrate Only

An Android app (made for Samsung Galaxy / One UI) that toggles **Vibrate Only Mode** when you
press **Volume Up + Volume Down at the same time**.

| | Vibrate Only Mode ON |
|---|---|
| Texts, notifications, system sounds | Vibrate only |
| Phone calls | Ring out loud (and vibrate) |
| Alarms | Sound normally |
| Music / video / media | Unchanged |

* One long buzz = mode turned **ON**. Two long buzzes = mode turned **OFF**.
* Runs in the background all the time and starts again by itself after a reboot.

## Install on your phone

1. On your phone, open this repository on GitHub, tap **Releases** and open the newest one.
2. Tap **VibrateOnly.apk** to download it, then open the downloaded file.
3. If the phone says installing unknown apps is blocked, tap **Settings** in that message and
   allow your browser (or My Files) to install apps. If Samsung's **Auto Blocker** stops it, turn
   it off in Settings > Security and privacy > Auto Blocker, install, then turn it back on.
4. Open the **Vibrate Only** app and follow the 4 setup steps on screen. Each step gets a ✅ when
   it's done.
   * On step 1, if the switch is greyed out ("Restricted setting"), use step 1b: App info >
     ⋮ menu > **Allow restricted settings**, then try step 1 again.

**Updating:** uninstall the old version first, then install the new APK and redo the setup steps.
(Each build is signed with a fresh key, so Android won't install one directly over another.)

## Good to know

* **Screen off:** Android doesn't let apps see button presses with the screen off, so the app uses
  a workaround (an invisible, silent media session) to catch the volume buttons while the screen
  is off. It should work, but it's not guaranteed on every phone or software update. With the
  screen on (including the lock screen) it always works.
* **Volume buttons on their own** still change the volume as usual, just a split second later
  (the app waits ~0.15 s to see whether the other button is pressed too).
* **Calls while the mode is on** use your default ringtone, played at the ringtone volume you had
  before turning the mode on. Pressing a volume button or the power button silences it.
* Switching the sound mode back to "Sound" another way (quick settings, or pressing Volume Up from
  vibrate) also ends Vibrate Only Mode, and you'll feel the two-buzz signal.

## How it's built

GitHub Actions (`.github/workflows/build-apk.yml`) builds the APK on every push and attaches it to
a new release. The code is in `app/src/main/java/com/vibrateonly/app/`.
