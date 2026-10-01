---
date: 2026-09-30
topic: "Control-panel GUI test suite (uitest scenarios + JUnit) with singleplayer mock invite routes"
status: validated
---

# GInv UI Test Suite & Singleplayer Mock Routes — Design

## Problem Statement

The menu (Control / Lists / Monitor / Settings, pop-out window, scale system)
has no automated coverage, so regressions ship: the current build broke the
popup/pop-out path ("popup menu is unavailable") and the GUI quality has
regressed. The invite flows also cannot be exercised outside Hypixel, so even
manual testing cannot reach queue-by-level, badges or the send path.

We want the same class of tests ldlib2-Architectury provides — layout
structure, computed style (LSS) rules, screenshot verification, driven flows —
as a JUnit-style suite runnable from Gradle, plus singleplayer-only mock
invitation routes (fake players with guild levels, recorded invites).

## Constraints

- LDLib2 is consumed as a release jar; no upstream edits. Its `uitest`
  harness ships **inside** the jar (113 classes confirmed) and discovers
  scenarios by annotation scan across dependent mods — zero LDLib2 changes.
- Scenario harness only activates in a dev environment
  (`Platform.isDevEnv()` gates `UITestBootstrap.register()`); scenarios are
  registered `RegistrationEnvironment.DEV_ONLY`.
- Mock routes must be impossible to activate in production: dev-env AND
  singleplayer only, enforced by throwing.
- Build gate stays `JAVA_HOME=/tmp/opencode/jdk25 ./gradlew build`; planner
  models are broken, so implementation is direct (established fallback).
- No planner/subagent dependency for execution.

## Regression findings (fixed as part of this work)

1. **Windowed rebuild storm — RETRACTED during implementation.** The original
   claim was that `GinvRoot.lastGuiScale` defaults to `1`, so `screenTick`
   would compare `1` against MC's GUI scale on the first tick of a popped-out
   window and call `rebuildActiveWindow()` forever. Code reading disproved it:
   `buildLayout` already assigns `root.lastGuiScale = windowed ? mcGuiScale()
   : 1` at construction, so the first-tick comparison matches and no storm
   occurs. No code change was made for this finding; it stays recorded here
   because scenario 6's 12-tick instance-identity check keeps guarding
   against such a storm reappearing.
2. **Pop-out open size below window minimum (fix applied)** — open dims became
   `420×300 × uiScale / contentScale`, which on HiDPI or low scale can fall
   under `ModularUIWindow.MIN_WIDTH/MIN_HEIGHT` (200×150) and fail the open
   path (literal feedback: "Pop-out unavailable"). Fix: clamp open dims to the
   minimums, and include the computed dims in the failure feedback so a
   scenario failure screenshot localizes a size refusal.

## Approach

Three layers, one seam set:

- **Layer A (primary): LDLib2 uitest scenarios** — six scenarios registered
  by our mod, covering structure, computed styles, flows, screenshots and the
  mock invite route. Run: `./gradlew runClient -PldTest=mod:guildinvitefix`.
- **Layer B: JUnit via fabric-loader-junit** — pure domain logic
  (`GuildLevels` parsing, `parseTargets`, `LevelQueueResult`) inside the normal
  `./gradlew build`.
- **Layer C: `GuildTestGateway`** — singleplayer-only fixtures + recorded
  sends + SkyBlock override, consumed through three seams so production code
  paths (queue, delay scheduler, whitelist/blacklist, freeze, store recording)
  stay fully real.

Alternatives considered: pure JUnit with mocked MC client (rejected — cannot
render or layout, misses the actual GUI); headless pixel-diff frameworks
(rejected — the ldlib2 harness already standardizes screenshots, reports and
CI exit codes).

## Architecture

```
scenarios (com.guildinvitefix.testing.scenarios, DEV_ONLY)
    │  requestOpen / openScreen / WindowInput
    ▼
GmenuCommand ──► GinvMenuScreen / GinvMenuWindow        (UI under test)
    │                    │ reads
    ▼                    ▼
GinvCommand ──► GuildDirectory.online()   ──┐
    │                 ▲ gateway first       │ seam
    │                 │ else tab list       ▼
    └──► InviteRoute.send(name)      GuildTestGateway (dev+SP only)
              │ gateway first                  - fixture roster (name, level)
              │ else connection.sendCommand    - sentInvites recorder
              ▼                               - skyBlock override
          GinvDataStore.recordInvite           - install()/reset()
```

JUnit (src/test) exercises the same domain functions the UI calls, with no
client needed.

## Components

- **`com.ginv.utils.GuildDirectory`** — `record Entry(String name,
  @Nullable LevelInfo level)`; `online()` returns gateway fixtures when
  active, otherwise the tab-list scan (moved out of `GinvCommand`,
  `GuildLevels.tabRange`, `GinvMenuScreen.collectLevels/onlineSnapshot`).
- **`com.ginv.command.InviteRoute`** — `send(String name)`: gateway records +
  returns true when active; else `connection.sendCommand("guild invite …")`.
- **`com.ginv.testing.GuildTestGateway`** — static install/reset/isActive;
  `install(List<Entry>, boolean skyBlock)` throws unless
  `Platform.isDevEnv() && Minecraft.hasSingleplayerServer()`; holds
  `sentInvites` (copy-on-write list) consulted by `InviteRoute` and the
  SkyBlock override consulted by `SkyBlockDetector.isSkyBlock()`.
- **`GmenuCommand.requestOpen(boolean)`** — public hook so scenarios drive the
  real deferred-open applier (chat-close safe path) instead of bypassing it.
- **Testability ids** on the menu (all `ginv_*`): `banner`, `hero`,
  `name_input`, `queue_names`, `level_input`, `queue_level`, `range`,
  `targets_header`, `clear`, `feedback`, `status`, `popout`, `close`,
  `redock`, `maximize`, `win_close`, `pin`; tabs `ginv_tab_<slug>` and panes
  `ginv_pane_<slug>` (plus shared classes `ginv_tab` / `ginv_pane`); scale
  toggles `ginv_scale_<percent>`.
- **`com.guildinvitefix.testing.scenarios.*`** — six `UIScenario` implementations,
  `@LDLRegisterClient(registry = UIScenario.REGISTRY, group =
  "guildinvitefix", environment = DEV_ONLY)`, common options: `requiresWorld(
  true)`, `guiScale(2)`, `tags("ui","menu")`, `defaultSettleMs(50)`.
- **`GuildLevels.parsePrefix(String)`** — pure extraction half of
  `extract(PlayerInfo)` so JUnit can test colored/plain/no-level parsing.
- **build.gradle** — `testImplementation fabric-loader-junit` +
  `junit-jupiter`, `test { useJUnitPlatform() }`; conditional `runs.client
  .property(...)` block mapping `-PldTest` (plus optional `ldTestHeadless`,
  `ldTestWindow`, `ldTestGuiScale`) to `ldlib2.uitest.*` system properties.

## Scenario suite (Layer A)

1. **`menu_open_regression`** — `requestOpen(true)` → `awaitScreen(
   GinvMenuScreen)` → `awaitModularUI` → `#ginv_panel` exists, banner contains
   "RUNNING", 4 tabs, tree > 30 elements, every element's bounds finite and
   non-negative → screenshot → close. (Catches constructor/applier crashes via
   watchdog report naming the last step.)
2. **`screen_tabs_structure`** — screen mode: count `.ginv_tab == 4`; click
   each tab → its `.ginv_pane` visible and the previous hidden; panel bounds
   within viewport and centered (`ElementBounds.isCenterOnScreen`); uniform
   row heights (hero/banner/rows within tolerance of `16×uiScale`); status bar
   and hero text present → screenshots.
3. **`control_flow`** — click `#ginv_hero` → banner "STOPPED" + button "RESUME
   INVITES"; click again → "RUNNING"; type "Alice Bob" into `#ginv_name_input`,
   click `#ginv_queue_names` → feedback "Queued 2 invites.", header
   "Current targets (2)", two `#ginv_target_row`s; wait until the gateway
   recorded both sends (delays set to 50 ms in setup); Clear → header (0) →
   screenshots.
4. **`level_queue_mock_e2e`** — opens with `#ginv_queue_level` inactive and
   `#ginv_range` = "SkyBlock only."; install fixtures (Alice 42, Bob 15,
   Carol no-level, Dave 60) + skyBlock=true → precondition flips live; type
   40 → feedback "Queued 2 · 1 no-level · 1 below", two target rows with
   badges (42, 60), `sent == [Alice, Dave]` in order after the scheduler runs
   → screenshots; teardown resets gateway, targets, freeze.
5. **`scale_preset`** — Settings tab → click `#ginv_scale_150` →
   `uiScale == 1.5`, screen rebuilt on the Settings tab (savedTab restore) →
   click `#ginv_scale_100` → restored → screenshot; teardown sets scale back.
6. **`popout_stability_redock`** — open popup → click `#ginv_popout` →
   `GinvMenuWindow.active() != null`; capture instance, `ticks(12)`, assert
   **same instance** (storm catcher); titlebar/redock reachable through
   `ctx.in(window.getModularUI(), …)`; `WindowInput` aims + clicks
   `#ginv_redock` → `awaitScreen(GinvMenuScreen)` again → screenshot.

Scenario teardown hygiene: `GuildTestGateway.reset()`,
`GinvCommand.clearTargets()`, unfreeze if frozen, restore delays/scale,
`closeScreen()`.

## Data flow

Scenario step → seam (gateway or production fallback) → existing domain logic
(GinvCommand scheduler, whitelist/blacklist, GinvDataStore) → UI reads the
same seams every tick → assertions against selectors, texts, bounds, and the
gateway's recorded sends. JUnit calls the domain functions directly.

## Error handling

- `GuildTestGateway.install()` outside dev-env/singleplayer → `IllegalStateException` with a message naming the missing condition — never a silent no-op.
- Scenario step failure → harness captures a failure screenshot, records the step + stack in `report.json`, exits non-zero (watchdog `halt(3)` on hangs, naming the last step).
- Selector misuse (comma/attribute syntax) → harness `Selectors.validate` fails loudly rather than matching nothing.
- Gradle: `runClient` failure propagates as a task failure; JUnit failures fail `build` through the standard `check` lifecycle.

## Testing strategy

- `./gradlew test` — JUnit layer (runs on every build/CI).
- `./gradlew runClient -PldTest=mod:guildinvitefix` — full GUI suite;
  report + screenshots at `build/reports/lduitest/`; optional
  `-PldTestHeadless` (synthetic input, hidden window) and
  `-PldTestGuiScale=<n>`.
- Manual smoke after fixes: pop-out at MC GUI scale 1 and 3 (physical size
  stable, no flicker), `/gmenu` after pop-out close (screen opens, no zombie
  focus), low-scale pop-out on HiDPI (opens at clamped minimum).

## Open Questions

The runtime root cause of the reported "popup menu is unavailable" remains
unconfirmed — static analysis retracted the storm theory and narrowed the
candidates to (a) pop-out open dims below the window minimum (now clamped),
(b) `focusExisting()` on a stale tracked window (scenarios now close stale
windows first and assert instance identity), and (c) an exception in the
screen constructor (would surface as scenario 1's `awaitScreen` timeout).
Scenarios 1 and 6 localize whichever it is on first run: a failure
screenshot plus the dims in the pop-out feedback label distinguish the
paths.
