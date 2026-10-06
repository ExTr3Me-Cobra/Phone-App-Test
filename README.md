# Pop Down

Makes **every** new notification pop down from the top as a real One UI pop-up, triggers One UI's
own lighting effect for it, and turns the screen on when it was off. No overlays: it only uses
Android's own notifications.

## How it works
A notification listener sees each new notification. If it wouldn't pop down by itself, Pop Down
immediately posts a silent copy on an urgent ("pop on screen") channel, which Android/One UI shows
as a normal pop-down banner with Samsung's lighting effect. When the screen is off, the copy is
sent as a full-screen alert (the alarm-app mechanism), which turns the screen on. Tapping the copy
opens the original, its buttons (reply, mark as read…) work, and the copy removes itself after a
few seconds (and as soon as the original is dismissed), so the notification list keeps only the
originals. The original app still plays its own sound; the copy is silent.

## Setup
1. Install `PopDown.apk` from Releases (→ *Pop Down*).
2. Open Pop Down and complete: Notification access, Allow notifications, Full screen
   notifications, Unrestricted battery.
3. Samsung's lighting effect: Settings → Notifications → Notification pop-up style → turn on the
   lighting effect and allow **Pop Down** in its app list.
4. Use *Test in 6 s* and lock the phone to check the screen wakes.

## Options
Wake the screen · skip notifications that already pop down (avoids doubles) · include silent
notifications · how long the copy stays · per-app exclusions · recent activity log.

If the Shade app switched off Samsung's pop-ups system-wide, turn Shade off (or switch
"Samsung pop-ups" back on in Shade) — Pop Down warns you about this.
