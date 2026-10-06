# Pop Down

Makes **every** new notification pop down from the top as a real One UI pop-up, triggers One UI's
own lighting effect for it, and turns the screen on when it was off. No overlays: it only uses
Android's own notifications.

## How it works
A notification listener sees each new notification. If it wouldn't pop down by itself, Pop Down
immediately posts a silent copy on an urgent ("pop on screen") channel, which Android/One UI shows
as a normal pop-down banner. When the screen is off, Pop Down also sends a short, separate
alarm-style full-screen alert (the mechanism alarm apps use) plus a wake lock to turn the screen on,
then logs whether it worked and, if not, why. Tapping the copy
opens the original, its buttons (reply, mark as read…) work, and the copy removes itself after a
few seconds (and as soon as the original is dismissed), so the notification list keeps only the
originals. The original app still plays its own sound; the copy is silent.

## Setup
1. Install `PopDown.apk` from Releases (→ *Pop Down*).
2. Open Pop Down and complete: Notification access, Allow notifications, Full screen
   notifications, Unrestricted battery.
3. Edge lighting: switch on **Pop Down Lighting** in Settings → Accessibility → Installed apps (if
   it's greyed out: Pop Down's App info → ⋮ → Allow restricted settings). Works with Detailed pop-ups.
4. Use *Test in 6 s* and lock the phone; *Recent activity* then says whether the screen turned on.

## Edge lighting
Pop Down draws its own lighting around the screen edge for each new notification (an accessibility
window, the only kind allowed over the lock screen; it reads nothing and ignores touches). Choose
the effect (25: around the screen, five Echo variations, or the sides only), colour (each app's own, one
custom colour or a two-colour gradient, with swatches and RGB sliders), speed, thickness,
brightness, how long it plays and corner curve (or match the screen's real corners).
Lock screen switches: pop down there too (off by default: the lock screen just lights up), lighting
on the lock screen or while unlocked, keep lighting until you unlock, turn the screen back off after.
A live preview and a full-screen test are in the app.

## Options
Wake the screen · skip notifications that already pop down (avoids doubles) · include silent
notifications · how long the copy stays · per-app exclusions · recent activity log.

If the Shade app switched off Samsung's pop-ups system-wide, turn Shade off (or switch
"Samsung pop-ups" back on in Shade) — Pop Down warns you about this.
