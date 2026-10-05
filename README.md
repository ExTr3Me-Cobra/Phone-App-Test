# Shade

A One UI 8.5-style notification panel, quick settings, pop-ups, edge lighting and lock screen
notifications for Samsung phones, with far more control than the built-in one. Kotlin + Jetpack
Compose, no other launcher or root needed.

## What it does
* **Panel** (swipe down from the top): status row, big clock, row of round quick-setting tiles,
  brightness pill, notifications. Pull again (or tap *Quick settings*) for the full tile card,
  brightness + dark mode, and media player. Notifications group by app, swipe to dismiss, expand,
  action buttons, inline reply, long-press to snooze. *Clear* and *Notification settings* buttons.
* **Per-app rules**: default / always pop up / silent / hidden; pop-up style; lock screen
  show / hide content / don't show; lighting on the lock screen, wake the screen, lighting while
  unlocked; lighting colour.
* **Pop-ups**: detailed card or brief bubble, duration, skip for the app you're using, full-screen
  and lock screen options.
* **Edge lighting**: glow / line / spinning gradient / pulse; app-icon, notification, custom or
  multicolour; thickness, transparency, repeats; wakes the screen and turns it off again; quiet hours.
* **Lock screen**: cards or icons, hide content, number of cards, position, opacity.
* **Tiles**: Wi-Fi, Bluetooth, flashlight, auto rotate, screen recorder, airplane mode, mobile data,
  Smart View, Do not disturb, sound mode, location, hotspot, power saving, dark mode, eye comfort,
  NFC. Reorder in *Edit tiles*. Long-press a tile for its settings.

## How the Wi-Fi / Bluetooth / mobile data tiles work
Android doesn't let apps switch these. Shade opens Samsung's own quick panel *underneath* its
panel, presses Samsung's matching tile through the accessibility service, reads the new state and
closes Samsung's panel again. Keep those tiles in Samsung's quick panel.

## Setup
Open Shade and follow the checklist: notification access, accessibility service (use
*Allow restricted settings* if it's greyed out), modify system settings, Do not disturb access,
unrestricted battery. Then *Turn off Samsung's duplicates* so pop-ups and lock screen
notifications don't show twice (automatic after a one-time `adb shell pm grant` command, or by
hand).

## Limits
* Samsung's own panel still exists underneath and can still open in some situations.
* Edge lighting with the screen off has to wake the screen (only Samsung can draw on a dark screen).
* Sounds and vibration are still played by Android per Samsung's per-app settings.

## Build
GitHub Actions builds the APK on every push (Releases → *Shade*). Or open the project in Android
Studio, pick the *release* build variant and Build → Generate APKs.
