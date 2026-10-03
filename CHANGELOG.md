# Changelog

## [Unreleased]

### Added
- LDLib2 integration (Fabric port `ldlib2-fabric` from the
  TheyCallMeAbstract/ldlib2-Architectury fork), resolved tokenless from the
  fork's public GitHub release assets (no credentials or CI secrets required)
- Required companion dependencies: Architectury API ≥ 20.0.12 and YACL ≥ 3.9.1
- In-game menu (`/gmenu [popup|screen]`, LDLib2 `ModularUI`) with three tabs:
  Control (state banner, STOP/RESUME, queue by name/level, current-target list
  with per-player invite counts), Lists (whitelist/blacklist management) and
  Settings (min/max invite delay, whitelist-only toggle). Per-player
  invite-count tracking lives inline on the Control target rows
- Persistent per-player invite tracking and list membership under
  `config/guildinvitefix/` (`players.json`, `settings.json`), saved atomically
- Player-face row icons resolved from the tab list with a default-skin fallback
- The invite queue now skips blacklisted players always, and skips everyone but
  whitelisted players when whitelist-only mode is on
- `/gmenu` command to open the menu
- Pop-out mode for the menu: the **↗** title-bar button lifts it into its own
  draggable/resizable OS window (LDLib2 `ModularUIWindow`) and closes the
  in-game screen; falls back with an in-menu notice if a second window cannot
  open
- Program-window chrome for the pop-out menu: icon title-bar buttons
  (maximize/restore, close with the Windows-style red hover), a persisted
  always-on-top pin where the platform supports it, double-click the title bar
  to maximize/restore, and Esc to close (the first press leaves a focused
  text field)
- Shared status bar under the tabs — live queue status on the left, transient
  action feedback on the right — in both the in-game menu and the pop-out
  window
- LDLib2 MODERN theme applied to the menu in both contexts; active W/B list
  buttons now tint their background instead of recoloring their text
- `/gmenu` focuses an already-open pop-out menu window instead of opening a
  second copy
- **Control tab** (now the first tab): live state banner (RUNNING/STOPPED ·
  pending), a full-width factory **STOP/RESUME INVITES** button, queue by
  name(s), queue by guild level with skip-aware feedback (`Queued 7 · 2
  no-level · 3 below`) and a live tab-range caption, plus the current-targets
  readout (the bare `/ginv` feature) with per-row remove and **Clear** —
  level queueing is disabled with an inline hint outside SkyBlock or without
  a connection
- Guild **level badges** on player rows in Lists and the targets list,
  colored from the server's prefix (§ code) with a muted `—` for
  offline/unleveled players; the tab change token now includes levels so
  badges refresh on level-ups
- **Lists tab search + filters**: a player-name search box and inclusive
  **LVL** min/max range (each field optional; blank = unset) with a **Clear**
  action, applied live over the tracked/online roster; an unknown-level player
  is excluded whenever a bound is set, and an all-filtered-out list shows a
  distinct **"No players match."** empty state
- Independent **menu scale**: segmented 75/100/125/150/200% presets persisted
  as `uiScale` in `settings.json` (now offered in the View menu); rebuilds on
  scale or GUI-scale changes, keeping the pop-out window's position, size,
  pin and maximized state
- **↩ re-dock** button in the pop-out title bar: returns the menu to the
  popup/screen mode it was popped out of
- Shared command API layer: `GuildLevels` (level + prefix-color extraction,
  tab range) and `GinvCommand.queueByLevel`/`parseTargets` — chat commands
  and the UI render the same results; `/glvl` behavior unchanged
- **UI test suite** — eight LDLib2 uitest scenarios (`group:guildinvitefix`):
  menu-open regression (deferred `/gmenu` path + element-tree/bounds
  assertions), tab structure with per-tab screenshots (including the Settings
  form's label/control column alignment), control-flow (hero
  STOP/RESUME, queue-by-name, scheduler → recorded sends) and level queue
  (fixture roster, skip counts, exact send order) over a **singleplayer-only
  mock invite route** (`GuildTestGateway`, throws outside dev + singleplayer),
  scale presets with saved-tab restore, pop-out stability (12-tick
  instance-identity check) + re-dock through the window's real input path,
  layout rhythm (per-column alignment/symmetry at every scale), and Lists
  search + LVL filtering (name narrowing, inclusive bounds, no-level
  exclusion, Clear) — run with
  `./gradlew runClient -PldTest=mod:guildinvitefix`, report + screenshots in
  `build/reports/lduitest/`
- Headless JUnit tests (guild-prefix parsing, target parsing,
  `LevelQueueResult` contract) wired into `./gradlew build` via
  fabric-loader-junit
- Stable element ids/classes across the menu (`ginv_tab_*`, `ginv_pane_*`,
  `ginv_panel`, `ginv_banner`, `ginv_hero`, name/level inputs, queue/clear
  buttons, `ginv_target_row`, `ginv_scale_*`, title-bar chrome buttons) so
  the uitest scenarios — and users' automation — can target widgets reliably
- Pop-out open dimensions are clamped to the window minimum (200×150) and
  the "Pop-out unavailable" feedback now includes the computed size, so a
  refused open is localizable from the failure screenshot
- **Office theme**: a custom LDLib2 stylesheet (`office.lss`) renders the
  menu as light document panels with office-blue accents, semantic text
  classes (`.ginv-body`, `.ginv-caption`, `.ginv-section`) and shared
  chrome surfaces, applied as a subtree so vanilla screens stay untouched
- **Top bar** (`ginv_topbar`) in both contexts: menu title, live queue
  status, the **SkyBlock chip** (`ginv_skyblock_status`, `.ginv-sky-on` /
  `.ginv-sky-off` — grey outside SkyBlock, blue inside) and a **View ▾**
  menu (`ginv_view_menu` → `ginv_view_popover`) holding the scale presets
  (`ginv_scale_100` … `ginv_scale_200`), the **Autoscale** switch
  (`ginv_autoscale`, default OFF — the menu opens at exactly 100%; when on,
  the fitted scale is applied to the build and never persisted, and clicking a
  preset turns autoscale off)
- **Inter** font for the menu (`assets/guildinvitefix/font/inter.json` +
  `inter.ttf`)
- Headless JUnit for the pure scale math split out of `GinvMenuScreen`:
  `fitScaleFor` (0.75 floor, 2.0 cap, degenerate-viewport fallback, never
  persists), `popoutSizeFor` (WYSIWYG panel × guiScale × contentScale with
  the platform floor, ±10% contract) and `GuildTestGateway`'s SkyBlock
  verdict gating (`active && skyBlock`, `reset()` drops fixtures)

### Changed
- **Lists active state** on the whitelist/blacklist icon buttons is now a
  semantic green/red 1px border instead of a solid fill; idle buttons stay
  borderless
- **Settings tab** reorganized into sectioned groups — **INVITES** (delay),
  **FILTERING** (whitelist-only + its explanatory note) and **APPEARANCE**
  (theme) — under the same `sectionHeader` banners the Control and Lists tabs
  use; copy normalized to one standard (`Invite delay (ms)`, `Whitelist only`,
  `Theme`), the note reworded to two plain sentences (*Only whitelisted players
  are invited. Blacklisted players are always blocked.*), and the status bar now
  reads `Whitelist only: ON/OFF`
- **Settings tab** rebuilt on a shared two-column form (`GinvSettingsForm`):
  labels sit in a 4/10 column and controls in a 6/10 column, so the delay
  fields, whitelist toggle and theme picker all start at the same x, with the
  min/max delay fields and **Apply** sharing one right edge; the theme picker
  and whitelist toggle are now part of the same aligned form
- **Spacing scale** applied across the whole menu: ad-hoc inline gaps and
  paddings now snap to a documented five-step scale (`SPACE_1..SPACE_5` =
  2/4/6/8/12 authored units, still scaled through `u()`), so the vertical
  rhythm is consistent between pages and scales with the menu
- **Lists toolbar** gets an explicit section break above the table (the
  filter bar now sits 8u clear of the table header), and the concealed
  min/max disclosure is padded to S4
- The Lists min–max **dash** is now a fixed-width, center-aligned label, so
  the cluster reads as a centered, evenly-gapped `[min] – [max]` group
- **Lists tab**: the "Add player by name" row was removed; queue-by-name on
  the Control tab now records each queued name (`GinvDataStore.touch`) so the
  Lists roster tracks it
- The Lists **LVL** min/max filter is concealed behind a **LVL ▾** button that
  reveals it in flow (reusing the popover paint class; an absolute overlay did
  not paint above the table's scroll surface); the button marks itself with a
  **LVL •** active state when a bound is set, and the search box and **Clear**
  stay in the bar
- Menu tabs are **Control | Lists | Settings** (the separate Monitor tab was
  dropped; its invite counts moved inline onto the Control target rows), with
  every authored size routed through a single scale helper (`u()`) so both
  contexts scale as one system
- `/glvl` is now a thin adapter over the shared `queueByLevel` API (chat
  output unchanged)
- Fabric Loader minimum bumped to 0.19.5 (required by LDLib2)
- Fabric API bumped to 0.155.3+26.1.2
- Invite delay range (default 220–720 ms) is now configurable from the menu
- The **Settings** tab now holds only invite delays and whitelist-only mode;
  scale presets, autoscale and the popup/screen mode moved to the View menu
- Autoscale is **off by default**: the menu opens at exactly 100%. When
  switched on, every in-screen build fits the menu into the `[0.75, 2.0]` band
  for the current viewport without touching the stored preset
- The pop-out window is **WYSIWYG**: it opens from the measured panel
  (panel px × GUI scale × framebuffer scale, floored at the platform
  minimum), with the legacy `base × scale / contentScale` kept only as the
  fallback when the panel cannot be measured — proportions now track the
  in-game menu at any Minecraft GUI-scale option
- Top-bar element ids replaced the legacy title-bar set (`ginv_topbar`,
  `ginv_close`, `ginv_always_on_top`, `ginv_view_menu` in place of
  `ginv_titlebar`, `ginv_win_close`, `ginv_pin`); the uitest scenarios and
  docs use the new contract only
- The Lists tab drops the redundant per-row **⚡** queue button (queueing
  stays on the Control tab) and the **W/B/X** action glyphs, so only the
  **Player** header prints above the icon-action columns
- Player rows now render the guild **level badge immediately after the name**
  inside the same cell (the standalone level column is gone), so the level
  reads with its player instead of the trailing controls; the Lists and Control
  row actions are now **borderless icon buttons** — whitelist/blacklist pages
  and a lightning bolt for remove — while the active whitelist/blacklist fill
  stays green/red
- The popped-out window now resizes as a proportional zoom of its opening
  size, locked to the opening aspect ratio and floored at the opening
  dimensions, so it can be enlarged but never shrunk into a collapsed layout

### Fixed
- The documented GUI-suite command `-PldTest=mod:guildinvitefix` matched no
  scenarios and aborted before taking any screenshot: LDLib2 resolves `mod:`
  against the scenario's *package* (`.guildinvitefix.`), and the scenarios
  lived under `com.ginv.*`. They now live under
  `com.guildinvitefix.testing.scenarios`, and a guard test
  (`ScenarioSelectionTest`) fails the build if the documented `mod:`/`group:`
  selections ever stop matching them
- Scenario *discovery* in dev runs: LDLib2's classpath scan only inspects
  directories containing a `com/lowdragmc/lowdraglib2/` subtree, which Loom's
  `build/classes/java/main` lacks, so the six annotated scenarios were never
  registered (only LDLib2's own 27 were). The build now creates that marker
  directory after `classes`; release jars are unaffected (they are scanned via
  their own root path)
- `popout_stability_redock` scenario gestures: LDLib2 `Button` fires `onClick`
  on mouse *press*, and both pop-out and re-dock close their own context
  mid-gesture (screen / window), so an atomic click's release step threw
  against a vanished target. The scenario now presses at the element's
  remembered centre and releases without re-resolving the selector (the screen
  driver no-ops on a closed screen; the window driver refuses one, so the
  re-dock press is the whole gesture)
- **Lists/Control tables**: a stray horizontal margin plus `width:100%` on
  each 1px row rule pushed the scroller's content one 3u gutter past its
  view-port, so the body always drew a horizontal scrollbar. The rule now
  stretches to the frame minus its margins, the body is locked to vertical
  scrolling, the view-port's base padding is reset so rows line up with the
  header, and a long player name clips inside its fixed `1fr` column instead
  of widening the grid. Guarded by the long-name/overflow checks in the
  `layout_rhythm` scenario

## [1.0.0] - 2026-07-31

### Added
- `/ginv` command with tab completion for batch guild invites
- `/glvl` command for level-based invites (SkyBlock only)
- `/gfreeze` command to pause/resume the invite queue
- Anti-detection throttling (220-720ms random delays)
- NPC filtering (names starting with `!`)
- SkyBlock instance detection via scoreboard
- Ported from Minecraft 1.21.11 version
