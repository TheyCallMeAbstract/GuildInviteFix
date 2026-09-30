---
date: 2026-09-30
topic: "Pop the GinvMenuScreen out of Minecraft via LDLib2 ModularUIWindow"
status: validated
---

# Design: pop-out menu window (ModularUIWindow)

## Problem Statement

LDLib2 provides `com.lowdragmc.lowdraglib2.gui.ui.window.ModularUIWindow` —
"Hosts any ModularUI in its own operating-system window" (undecorated,
drag/resize handled in-library, offscreen-rendered with the game's own
rendering context, input replayed through the same widget a Screen would
drive). We want our menu to use it: a pop-out affordance on the menu widget
that lifts the menu out of the Minecraft screen into a real OS window (e.g.
for a second monitor).

The class javadoc gives the recipe: construct with a `ModularUI` and title,
`setDragArea(titleBar)`, optionally `setStyleSource(parentUI)`, then
`open(x, y, w, h, decorated)` — returning `false` when no second window is
available.

## Constraints

- The menu's live refresh (status line, list rebuilds on store/tab-list
  change, switch sync) currently lives in `GinvMenuScreen.tick()`. That
  method does not exist once the screen is closed, so refresh must move to a
  home that runs in both contexts.
- The live `ModularUI` must NOT be shared between the closing screen and the
  window: LDLib2's `ScreenMixin.ldlib2$removed` calls `mui.onRemoved()` when
  the screen closes, which sets `removed = true` and disposes the style
  engine.
- `ModularUIWindow.open()` can fail (`OsWindowManager.isAvailable()` false,
  platform refuses a second window) — must fall back to staying in-game with
  visible feedback.
- `buildLayout` currently wires the title-bar X to `Minecraft.setScreen(null)`
  — never valid from inside an OS window (would close whatever screen the
  player has, or nothing).
- No new dependencies. Verification is compile plus source-level reasoning
  (headless environment; no scriptable in-game typing).
- YAGNI: no `/gmenu window` subcommand, no pop-back-in, no window position
  memory.

## Approach

**Chosen: fresh-UI pop-out + refresh moved into the root element.**

### 1. Refresh portability

Add a static nested `GinvRoot extends UIElement` inside `GinvMenuScreen`
overriding `screenTick()` with the logic moved from
`GinvMenuScreen.tick()` (version/tab-list gated list rebuilds, status label,
whitelist switch sync), then calling `super.screenTick()`. Its widget
references are assigned by `buildLayout`.

`UIElement.screenTick()` is what `ModularUI.tick()` invokes on the root
(`ui.rootElement.screenTick()`), and the UI is ticked in both contexts:

- **In-screen:** LDLib2's `ScreenMixin.ldlib2$tick` injects at `Screen.tick`
  RETURN and ticks the ModularUI of every `IModularUIHolder` child
  (`ModularUIWidget` is one; it is in `children()` via `addRenderableWidget`).
- **In-window:** `ModularUIWindow`'s constructor sets
  `modularUI.setTickWhileRending(true)` ("Nothing else ticks this UI"), and
  `ModularUIWidget.extractRenderState` ticks it once per client tick.

`GinvMenuScreen.tick()` is deleted — refresh must not run twice in-screen.

### 2. Pop-out button (screen layout only)

A title-bar button (`↗`, tooltip "Pop out into its own window") wired during
`buildLayout(popup, windowed=false)`:

1. Build a fresh windowed layout: `buildLayout(popup, true)` — fresh widgets,
   no screen-attached popup outside-click listener (that listener is added by
   the screen constructor, not by `buildLayout`), refresh driven by
   `GinvRoot.screenTick`.
2. `var win = new ModularUIWindow(new ModularUI(UI.of(root)), "Guild Invite Fix")`
3. `win.setDragArea(layout.titleBar)`
4. Rewire the fresh layout's close button to `win.close()` (replaces the
   placeholder handler; `Button.setOnClick` is a single-field setter).
5. `win.open(Integer.MIN_VALUE, Integer.MIN_VALUE, 372, 288, false)`
   (platform-placed; above `MIN_WIDTH/MIN_HEIGHT` 200×150; sized for the
   340×252 panel plus padding).
   - **Success** → `Minecraft.setScreen(null)`. The screen's own UI retires
     through its normal `onRemoved` lifecycle; the window owns its independent
     copy.
   - **Failure** → feedback label "Pop-out unavailable — staying in-game"
     (error color); menu remains open in-game.

Why a fresh UI instead of handing over the screen's live instance:

- `ScreenMixin.ldlib2$removed` would dispose the shared style engine the
  window still uses (`syncStylesheets` runs every frame).
- The screen's popup outside-click listener (closes on outside click) would
  live on in the window and could fire stale `setScreen(null)` calls.
- Cost: unapplied text-field edits are not carried over. Settings/lists/
  monitor state all live in `GinvDataStore`, and the fields initialize from
  it, so only a not-yet-pressed "Apply" is lost.

Widget lifetime is safe for a fresh UI: `ModularUIClientState`'s constructor
eagerly creates the `ModularUIWidget`, so the window's
`ModularUIClientAccess.getWidget(...)` path never sees null — matching the
javadoc example, which constructs a window from a UI that was never in a
screen.

### 3. Layout variant flag

`buildLayout(boolean popup, boolean windowed)`:

- `windowed=false` (in-game): pop-out button present; close button wired to
  `setScreen(null)` (only ever reachable while the owning screen is current —
  it is the screen's own button).
- `windowed=true` (for the OS window): no pop-out button (cannot pop out a
  pop-out); close button placeholder, rewire-before-open as above.
- `Layout` record shrinks to `(root, panel, titleBar)` — widget references
  needed by refresh live on `GinvRoot`; the screen keeps `panel` for the
  popup outside-click bounds and `root` for the listener attachment.

Rejected alternatives:

- **Share the live ModularUI** — style-engine disposal + stale popup
  listener, see constraints.
- **`ModularHudLayer`** — renders inside the game window; does not pop out of
  the Minecraft screen.
- **`/gmenu window` command only** — the request is a widget-level
  affordance; a direct-open command can be added later if wanted.
- **Mirror LDLib2 `setStyleSource(parentUI)`** — this mod never installs
  custom stylesheets/themes; a fresh UI gets the same defaults the screen UI
  does, so there is nothing to inherit.

## Architecture

Unchanged outside `GinvMenuScreen`:

- `/gmenu` (deferred open) → `GinvMenuScreen(popup)` → in-game menu.
- Pop-out → fresh `ModularUIWindow` hosting an identical build → in-OS-window
  menu; `GinvDataStore` shared as the single source of truth.
- Window close → back to game; `/gmenu` reopens in-game as before.

## Data Flow

`↗ click` → fresh `buildLayout(popup, true)` → `ModularUIWindow` wraps the
fresh `ModularUI` → `setDragArea(titleBar)` + close rewire → `open(...)`
(offscreen surface + second GLFW window) → `setScreen(null)` (screen UI
retires) → each client tick: window pump → `renderFrame` → widget
`extractRenderState` → `modularUI.tick()` → `GinvRoot.screenTick()` refresh →
offscreen render blitted into the OS window. `X`/OS close →
`OsWindowManager.close(host)`.

## Error Handling

- `open()` returns false → feedback label in the Settings tab, menu stays in
  the screen, no state lost.
- Close from window only closes the window (rewired handler); close from
  screen only closes the screen. No cross-context `setScreen(null)`.
- Screen-owned popup listener dies with the screen; windowed layout never
  gets one.
- If the player's screen changes between pop-out click and `open()`, the
  handler still only touches its own screen (`Minecraft.getInstance().screen`
  is consulted at call time) — worst case the player stays where they are and
  the window opens.

## Testing Strategy

- `./gradlew clean build` (JDK 25) compile gate.
- Source-level verification performed during design: `ModularUIWindow`
  (ctor/open/renderFrame/drag), `ScreenMixin` tick/removed hooks,
  `ModularUI.tick` → `rootElement.screenTick()`,
  `ModularUIWidget.extractRenderState` tickWhileRending path,
  `ModularUIClientState` eager widget creation, `Button.onClick` single-field
  setter, `UI.of`/`getParent` APIs.
- Manual smoke (GUI machine): pop out → second undecorated window appears,
  draggable title bar, resizable edges, Lists/Monitor live-refresh, `X`
  closes only the window, `/gmenu` reopens in-game; on a machine without
  second-window support → feedback label and in-game menu persists.

## Open Questions

None blocking. Possible later additions: `/gmenu window` direct open,
remembered window bounds, pop-back-in button.
