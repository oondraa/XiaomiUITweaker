# iOS-style Lock Screen, Notification Center & Control Center — spec for implementation

Target: Redmi Note 10S (rosemary), MIUI 14 Global, Android 13, 1080×2400 @ ~409 dpi (density 2.75).
Reference: iOS 16–26 behavior. Copy the **mechanics and layout**, not Apple assets
(SF Pro / Apple icons are not redistributable → use open fonts with similar feel, own icons).

Units below are iOS points (pt). On this phone: 1 pt ≈ 1 dp (iPhone logical width 390–430 pt vs. this
phone 392 dp — nearly identical, so pt values can be used directly as dp).

---

## 1. Lock Screen — layout (top → bottom)

```
┌───────────────────────────────┐
│ status bar (carrier · icons)  │  system, ~44 dp
│                               │
│        Sat 15 November        │  date line, ~17 dp semibold, 85 % white
│          ┌─────────┐          │  (optional inline widget replaces/precedes date)
│          │  20:19  │          │  CLOCK: ~96–110 dp tall glyphs, centered
│          └─────────┘          │  (resizable in iOS 26: can grow down to ~1/2 screen)
│   [widget][widget][widget]    │  up to 4 small circular / 2 rect widgets, under clock
│                               │
│          (wallpaper)          │
│                               │
│ ┌───────────────────────────┐ │  NOTIFICATIONS grow from the BOTTOM upward
│ │ notification stack        │ │  (above the bottom controls)
│ └───────────────────────────┘ │
│  (◉)                     (◉)  │  2 round buttons, 50 dp, 46 dp from sides, ~ 50 dp from bottom
│             ────              │  home indicator bar 134×5 dp, "swipe up to unlock"
└───────────────────────────────┘
```

### Clock
- Default: heavy rounded-ish sans, tabular numerals, white, very large. Hours:minutes, no seconds, no AM/PM on 24h.
- **Customization (edit mode):** long-press lock screen → "Customize" → tap clock → bottom sheet:
  - **Font**: ~8 choices (sans light, sans bold, rounded, serif, stencil/condensed, mono, etc.). Each shown as "12" sample tile.
  - **Weight**: slider (for variable fonts).
  - **Color**: row of ~8 swatches derived from the wallpaper + full color picker; also "Glass" (translucent, frosted) vs "Solid".
  - **Transparency** slider (iOS 26.2).
  - **Size**: drag handle at bottom-right corner of clock to enlarge (clock may extend behind subject).
  - Numeral system (Latin/Arabic/…) — skip.
- Date line above clock is tappable in edit mode → replace with a small inline widget (weather, next event, battery).
- Colors of date, widgets and bottom controls follow the clock color tint.

### Wallpaper
- Any user photo. Pinch/drag to position in edit mode. Optional filters (B&W, duotone, color wash, studio).
- "Depth effect": subject in front of the clock (needs segmentation — optional/late feature; ML Kit Subject Segmentation can do it on-device).
- Legibility: subtle top & bottom gradient (black 0→25 %) behind text.
- Wallpaper dims ~ to 60 % when notification list is pulled up / Notification Center is open, plus blur.

### Bottom controls (2 buttons)
- Default: Flashlight (left), Camera (right). Circle 50 dp, background = frosted glass (blur + 20 % white), glyph white 20 dp.
- **Activation: long-press (~0.4 s) with haptic**, not a tap (prevents pocket triggers). Flashlight button turns solid white with dark glyph when ON.
- Camera: alternatively **swipe left anywhere** on lock screen opens camera.
- **Customizable (iOS 18+)**: in edit mode each button shows a "–" badge; tap empty slot → picker of controls (flashlight, camera, any app, Wi-Fi, DND, timer, calculator, QR scanner, voice recorder, shortcut…). Slot can be left empty.

### Gestures on Lock Screen
| Gesture | Result |
|---|---|
| Swipe up from bottom edge | unlock (Face ID → here: fingerprint/PIN via system keyguard) |
| Swipe up in middle area | reveals older notifications (Notification Center history) |
| Swipe down | if notifications list is expanded, collapses it |
| Swipe right (from left/middle) | Today View widgets page (optional/skip) |
| Swipe left | Camera |
| Swipe down from top-right corner | Control Center |
| Long-press empty area | edit / wallpaper gallery (swipe between multiple saved lock screens) |
| Pinch on notifications | switch display mode Count ↔ Stack ↔ List |
| Raise / tap | wake (system) |

---

## 2. Notifications — the important part

### Card anatomy
- Width: screen − 2×8 dp margins (≈ 376 dp). Corner radius **~22 dp** (continuous/squircle-ish).
- Background: **frosted material** — background blur (~30 px) + white at ~55–65 % (light) or dark gray ~45 % (dark mode); 1 px subtle inner highlight. iOS 26 "Liquid Glass" = more transparent, with edge refraction highlight.
- Padding 14 dp. Row 1: app icon 38 dp (rounded square, radius ~9) on the LEFT, vertically top-aligned.
- Right of icon: **Title** (app name or sender, 15 dp semibold), right-aligned **timestamp** ("now", "5m ago", "20:14") 13 dp secondary color on the same line.
- Body: 15 dp regular, max 4 lines collapsed, ellipsis.
- Optional thumbnail image 38 dp right side; for messages: contact avatar replaces app icon with small app badge overlapped bottom-right.
- Spacing between cards: 8 dp.

### Display modes (user setting + pinch to switch)
- **Count**: only "N Notifications" pill at the bottom; tap/swipe up → expands to list.
- **Stack** (default): all notifications collapsed into ONE pile at the bottom — top card fully visible, 2 "ghost" cards peeking below it (each ~8 dp lower, ~5 % narrower, lower opacity). Tap → fans out into list.
- **List**: all cards stacked vertically, growing up from bottom.

### Grouping
- Notifications from the same app (or same conversation thread) are grouped: newest on top, cards behind it peek out (same ghost look), label "N more notifications".
- Tap group → expands with spring animation; header appears with app name + **"Show less"** and **"×" (clear group)** button.
- Group setting per app: Automatic (by app/thread) / By App / Off.

### Gestures on a card
| Gesture | Result |
|---|---|
| Tap | open the notification (for lock screen: requires unlock first, then launch) |
| Swipe left partially | reveals 2 buttons: **Options** and **Clear** (frosted rounded rectangles) |
| Swipe left fully (fast/long) | clears immediately, card slides out, others animate up |
| Swipe right | open (iOS: "open"); on lock screen → unlock then open |
| Long-press | expanded preview (bigger card, full text, image) + action buttons (Reply, Mark as read…) below; blur background |
| Options | Mute for 1 hour / Mute for today / View settings / Turn off |

### Behavior
- New notification while on lock screen: slides in from the bottom with spring, screen wakes.
- **Freshness**: notifications seen on the lock screen move to "Notification Center" history after the user unlocks;
  lock screen then only shows **new** ones. History is reachable by swiping up in the middle of the lock screen.
- History section has header "Notification Centre" and the "×" clear-all button (long-press × → "Clear All Notifications").
- Content hiding: "Show previews: Always / When unlocked / Never" — when locked + hidden, body replaced by "Notification".
- Sorting: newest first; within history by time.

---

## 3. Notification Center on an unlocked phone (the "fake lock screen")
- Swipe **down from top-left / top-center** edge on ANY screen → a full-screen sheet drops down that **is the lock screen**:
  same wallpaper (blurred slightly less), same clock + date, same notification list (all, incl. history).
- It follows the finger (interactive drag) and settles with a spring; swipe **up from bottom** closes it.
- Bottom has the same 2 controls (flashlight/camera).
- Tapping a notification opens app and dismisses the sheet.

## 4. Control Center
- Swipe **down from the top-right corner** (status bar battery area) — on any screen and on lock screen.
- Background: whole screen blurred heavily + dimmed (~30 % black). Panels fade/scale in from top-right.
- Grid: 4 columns of **1×1 modules 62 dp** squares with radius ~18 dp, gaps 16 dp; side margin ~28 dp.
- Layout (classic):
  - **Connectivity module (2×2)**: Airplane, Mobile data, Wi-Fi, Bluetooth as 4 circular toggles (blue/green when on). **Long-press → expands** into full panel with Hotspot, AirDrop… and Wi-Fi network list (long-press Wi-Fi).
  - **Now Playing (2×2)**: track, play/pause/skip.
  - Row: Rotation lock, Focus/DND.
  - Sliders: **Brightness** and **Volume** — tall vertical pills (1×2), fill from bottom, drag anywhere to change.
  - Bottom row: Flashlight, Timer, Calculator, Camera, Screen record, QR…
- Toggle colors: active Wi-Fi/BT = system blue `#0A84FF`, mobile data = green `#30D158`, airplane = orange `#FF9F0A`, flashlight = white bg + dark glyph.
- iOS 18+: multiple pages (swipe up), long-press empty area → edit mode with resizing of controls.
- Dismiss: swipe up, or tap empty area.

---

## Design tokens (dark)
```
blur radius          30–40 px (RenderEffect.createBlurEffect)
card bg (dark)       #1C1C1E @ 55 %   | glass edge highlight #FFFFFF @ 12 %
card bg (light)      #FFFFFF @ 60 %
text primary         #FFFFFF
text secondary       #EBEBF5 @ 60 %
accent blue          #0A84FF   green #30D158   orange #FF9F0A   red #FF453A
radius: card 22, CC module 18, button circle, icon 9
spring: damping ~0.8, response ~0.35 s (Compose: spring(dampingRatio=0.8f, stiffness=400f))
```

Fonts (open alternatives): **Inter** (UI text, closest to SF Pro), clock options: Inter Display Bold, **Manrope**,
**Nunito** (rounded, already in fonts/), **DM Serif Display**, **Bebas Neue** (condensed), **JetBrains Mono**,
**Outfit** (light), **Space Grotesk**.

Sources: Apple Support "View and respond to notifications on iPhone", "Change notification settings";
AppleInsider "iOS 26: customize the Lock Screen clock"; Tom's Guide iOS 26.2 transparency slider;
9to5Mac iOS 16 notifications at bottom; Apple HIG Notifications.
