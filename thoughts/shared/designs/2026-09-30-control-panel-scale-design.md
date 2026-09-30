---
date: 2026-09-30
topic: "Control panel, scale system & professional UI"
status: validated
---

# Control Panel, Scale System & Professional UI

## Problem Statement

The menu exposes command features only as chat commands (`/ginv`, `/glvl`,
`/gfreeze`). It has no independent scale control — LDLib2 windows inherit
Minecraft's GUI scale (`OffscreenSurface.guiScale()` delegates to the main
window), so the menu jumps around with the MC scale option. The pop-out window
cannot return to the in-game screen. Guild levels (the data `/glvl` depends on)
are not shown anywhere. The UI reads as a dev prototype rather than a product.

## Constraints

- LDLib2 is consumed as a jar — no upstream edits; patterns come from its
  source and test suite only
- Planner subagents are broken (`gpt-5.2-codex` missing) — established
  fallback: direct implementation after this doc's commit
- Build: `export JAVA_HOME=/tmp/opencode/jdk25 && ./gradlew build`
- Decorated OS windows freeze the game (modal loop) — custom chrome stays
- Feature 2 (user's pending "second thing") is out of scope

## Approach

### Scale: one dimension helper, not transforms

Every authored px in layout goes through a single `u(x)`:

- **In-screen:** `u(x) = x × S` — S multiplies on top of MC scale (inherent
  to any in-screen UI; MC owns the canvas)
- **Windowed:** `u(x) = x × S / guiScale`, window opened at
  `basePx × S / contentScale`
  - Window canvas units = `px × contentScale / guiScale`; dividing authored
    values by `guiScale` cancels it exactly → **identical physical size and
    proportions at guiScale 1/2/3** (this fixes the existing distortion where
    at guiScale 2 the fixed-width fields eat double the relative width)
  - `guiScale` = `Minecraft.getWindow().getGuiScale()`; `contentScale` =
    main framebuffer px / main window screen px, fallback 1
- `S` (`uiScale`) ∈ {0.75, 1.0, 1.25, 1.5, 2.0}, persisted in
  `settings.json` like `alwaysOnTop`
- Changing S rebuilds the current context: screen → fresh
  `GinvMenuScreen(popup)`; window → reopen. The window also rebuilds if MC's
  guiScale changes while open (watched in `screenTick`, windowed only)
- Scale context lives in static fields set at `buildLayout` entry and read by
  `u()` (client-thread only, consistent with `GinvDataStore` statics)

**Rejected alternatives:**

- `Transform2D` scale (LDLib2-native, hit-tested — `TestFontRendering`
  proves non-integer scales render cleanly): rejected because percent-filling
  children (windowed panel is `widthPercent(100)`) would be scaled on top of
  an already-scaled canvas → proportion distortion
- Leaving the window at fixed px while scaling only layout numbers: the
  current guiScale-distortion bug, formalized

### Command encapsulation: thin adapters

Domain logic moves into API methods returning result objects; chat commands
and the UI both become formatting layers:

- `GinvCommand.queueByLevel(int)` → `LevelQueueResult{ok, queued,
  skippedNoLevel, skippedLowLevel, errorKind}` — extracted from
  `GlvlCommand`'s private `execute()`
- `GinvCommand.parseTargets(String)` → whitespace-split, trim, dedupe
  (shared by `/ginv` and the name field)
- `GuildLevels` (new util in `com.ginv.utils`) → `LevelInfo{value, color}`
  extracted from team prefixes **keeping the § color** (today
  `GlvlCommand.extractLevel` is private and strips color)
- Commands keep arg parsing + chat feedback; UI calls the same APIs +
  status-bar feedback. Single source of truth, two renderers

## Architecture

```
Commands (thin)            UI (Control tab)
  GlvlCommand   ──┐
  GinvCommand   ──┼──► GinvCommand API layer (queueByLevel, parseTargets,
  GfreezeCommand ──┤      toggleFreeze, queueAndSchedule, clearTargets…)
                   │              │
                   └──► GuildLevels (prefix → level+color) ◄── player rows
```

- `buildLayout` split into section builders (`controlTab()`,
  `queueSection()`, `targetsSection()`, `statusBar()`, …) — the flat method
  is ~250 lines today and this adds ~150 more; mirrors
  `TestComponentExamples`' per-example supplier structure
- `GinvRoot` grows: banner labels, hero STOP button, targets scroller +
  change token
- `GinvMenuWindow` grows: origin mode field (`popup`) for re-dock

## Components

### Control tab (first tab: Control | Lists | Monitor | Settings)

- **State banner**: colored dot + `RUNNING · N pending` / `STOPPED · N
  pending` (success/danger), refreshed each tick from `isFrozen()` /
  `getPendingCount()`
- **Factory STOP** (hero): full width, `u(24)` tall; red `STOP INVITES` ⇄
  green `RESUME INVITES`; calls `toggleFreeze()`; echoes `GfreezeCommand`
  wording ("Queue frozen. N invites pending.") into the status bar
- **Queue section**
  - By name(s): field (placeholder `player1 player2 …`) + **Queue** →
    `parseTargets` + `queueAndSchedule` → `Queued N invites.`
  - By level: number field + **Queue ≥** → `queueByLevel` → skip-aware
    feedback `Queued 7 · 2 no-level · 3 below`; caption shows live tab range
    (`tab: 3–240`); **disabled with warn caption outside SkyBlock or without
    a connection** (preconditions surfaced proactively)
- **Current targets section**: header `Current targets (n)` + **Clear**
  (`clearTargets()`); scroller of `name [level] ✕` rows (`removeFromQueue`).
  This is bare `/ginv`'s target-list feature, previously with no UI. Refresh
  via set-equality token in `screenTick` (same pattern as `lastVersion`)

### Player level display (both row contexts, one `buildPlayerRow`)

- Badge after the name: `[42]` in the server's prefix color (parsed from the
  § code before stripping), fixed `u(16)`, tooltip `Guild level`
- Offline/tracked-only/NPC: muted `—`
- Level map (`name → LevelInfo`) built during row fills;
  `onlineSnapshot()` records `name+level` so prefix changes rebuild rows
- Lists rows gain a **⚡ queue-now button** (`queueAndSchedule(single)`,
  the `/ginv <name>` equivalent)

### Re-dock (pop-out → game)

- Windowed title bar: `[title] [pin] [↩] [max] [close]`
- `↩` chrome button (`Icons.LEFT` family, tooltip `Back into the game`):
  `mc.setScreen(new GinvMenuScreen(originMode))` then `window.close()` —
  returns to the mode it was popped from (popup vs screen)
- `GinvMenuWindow` carries the origin mode (constructor param from
  `popOut(popup)`)

### Scale control (Settings tab)

- **Segmented control**: `ToggleGroupElement` with `75 · 100 · 125 · 150 ·
  200` (pattern from `TestBuiltinStyles`); active preset mirrors `uiScale`;
  change → persist + rebuild current context

### Visual system

Design tokens; use theme classes where possible (`.panel_bg` / `.preview_bg`
exist in `modern.lss` — stylesheet-driven beats hand-rolled backgrounds):

| Token | Value | Use |
|---|---|---|
| Surfaces | theme `.panel_bg` (rounded `#dd2c2c34`), chrome `#18181B`, window `#1E1F22` | sections, title/status bars |
| Accent / success / danger / warn | `#71A4F4` / `#22C55E` / `#EF4444` / `#F59E0B` | headers, banner, STOP, preconditions |
| Type | section title `u(10)` accent + textShadow (idiom: `TestComponentExamples` uses `fontSize(12).textColor(accent).textShadow(true)`), body `u(10)`, caption `u(9)`, muted `#9CA3AF` | all text |
| Spacing | base `u(4)`; panel padding `u(6)`, gap `u(4)` | rhythm |
| Buttons | theme height 16, chrome 12, hero 24; uniform tooltips | no one-off sizes |

## Command Coverage Matrix

| Command capability | UI exposure |
|---|---|
| `/ginv names` queue | Control: queue-by-name |
| `/ginv` bare → show current targets + frozen | Control: targets readout + per-row remove (**new**) |
| `/glvl level` + skip stats | Control: queue-by-level + skip feedback |
| `/glvl` preconditions (SkyBlock, connection) | Disabled controls + inline warn caption (**new**) |
| `/gfreeze` | Hero STOP/RESUME |
| Tab-list level parsing | Row level badges + tab-range caption (**new**) |
| `removeFromQueue` / `clearTargets` | Row ✕ (exists) + Clear button (**new**) |
| `/gmenu popup\|screen` | Pop-out (exists) + re-dock to origin mode (**new**) |
| Delay range / whitelist gating | Settings + Lists (exist) |

## Data Flow

Click → API call → result object → status-bar feedback (color-coded) →
`screenTick` token-change refresh. Banner/targets read state each tick (cheap
sets); rows rebuild only on set/version/level change. No optimistic UI —
tick-driven refresh converges sub-100ms.

## Error Handling

- Failures return typed results (`NOT_SKYBLOCK`, `NOT_CONNECTED`, `NO_NAMES`,
  `NO_MATCHES`) → red/warn status-bar text; no exceptions reach click
  handlers
- Precondition failures also disable the controls (can't click what can't
  succeed)
- `queueByLevel` empty-result mirrors `/glvl`'s skip-count message
- Pop-out failure path and Esc-blur-first behavior unchanged

## Testing Strategy

Following the test suite's design philosophy (`uitest/` scenarios are
given/when/then regression guards, several pinned to `guiScale(3)`):

1. Build gate: `JAVA_HOME=/tmp/opencode/jdk25 ./gradlew build`
2. Scale verification: run client, pop out at MC guiScale 1 **and** 3 —
   window stays ~`base×S` physical px with identical proportions; log
   `canvas units vs authored width` on open to confirm the formula
   (one-line adjustment is the documented fallback if LDLib2's offscreen
   target sizing differs from assumed)
3. Manual scenario checklist:
   - queue-by-level outside SkyBlock → disabled + caption
   - STOP → banner, status bar, and hero button all flip
   - targets list updates live; Clear empties it; ✕ removes one
   - level badges colored; offline rows show `—`
   - re-dock returns to the originating popup/screen mode
   - scale preset persists and rebuilds; window size independent of MC
     guiScale
   - `/gmenu` focuses an open window instead of duplicating
4. Stretch: register an `ldlib2:screen_test` entry
   (`@LDLRegisterClient` pattern from `TestComponentExamples`) if the
   registry is reachable from a dependent mod in dev — verify first

## Open Questions

- Re-dock icon: `Icons.LEFT` vs `Icons.REPLAY` — final pick from the icon
  sheet at implementation
- HiDPI (Retina): formula includes contentScale compensation but is untested
  there; dev box is Linux (contentScale 1) — covered by the verification log
- Feature 2: awaiting the user's description
