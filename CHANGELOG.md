# Changelog

## [Unreleased]

### Added
- LDLib2 integration (Fabric port `ldlib2-fabric` from the
  TheyCallMeAbstract/ldlib2-Architectury fork), resolved tokenless from the
  fork's public GitHub release assets (no credentials or CI secrets required)
- Required companion dependencies: Architectury API ≥ 20.0.12 and YACL ≥ 3.9.1
- In-game menu (`/gmenu [popup|screen]`, LDLib2 `ModularUI`) with three
  sections: Settings (min/max invite delay, whitelist-only toggle), Lists
  (whitelist/blacklist management) and Monitor (live queue status and
  per-player invite counts)
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
- Guild **level badges** on player rows in Lists, Monitor and the targets
  list, colored from the server's prefix (§ code) with a muted `—` for
  offline/unleveled players; the tab change token now includes levels so
  badges refresh on level-ups; Lists rows gained a **⚡ queue-now** button
- Independent **menu scale**: segmented 75/100/125/150/200% presets persisted
  as `uiScale` in `settings.json` (now offered in the View menu); rebuilds on
  scale or GUI-scale changes, keeping the pop-out window's position, size,
  pin and maximized state
- **↩ re-dock** button in the pop-out title bar: returns the menu to the
  popup/screen mode it was popped out of
- Shared command API layer: `GuildLevels` (level + prefix-color extraction,
  tab range) and `GinvCommand.queueByLevel`/`parseTargets` — chat commands
  and the UI render the same results; `/glvl` behavior unchanged
- **UI test suite** — six LDLib2 uitest scenarios (`group:guildinvitefix`):
  menu-open regression (deferred `/gmenu` path + element-tree/bounds
  assertions), tab structure with per-tab screenshots, control-flow (hero
  STOP/RESUME, queue-by-name, scheduler → recorded sends) and level queue
  (fixture roster, skip counts, exact send order) over a **singleplayer-only
  mock invite route** (`GuildTestGateway`, throws outside dev + singleplayer),
  scale presets with saved-tab restore, and pop-out stability (12-tick
  instance-identity check) + re-dock through the window's real input path —
  run with `./gradlew runClient -PldTest=mod:guildinvitefix`, report +
  screenshots in `build/reports/lduitest/`
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
  (`ginv_autoscale`, default ON — the fitted scale is applied to the build
  and never persisted; clicking a preset turns autoscale off) and the
  popup/screen mode selector (`ginv_mode_group` with `ginv_mode_popup` /
  `ginv_mode_screen`, a state-carrying no-op inside the OS window)
- **Inter** font for the menu (`assets/guildinvitefix/font/inter.json` +
  `inter.ttf`)
- Headless JUnit for the pure scale math split out of `GinvMenuScreen`:
  `fitScaleFor` (0.75 floor, 2.0 cap, degenerate-viewport fallback, never
  persists), `popoutSizeFor` (WYSIWYG panel × guiScale × contentScale with
  the platform floor, ±10% contract) and `GuildTestGateway`'s SkyBlock
  verdict gating (`active && skyBlock`, `reset()` drops fixtures)

### Changed
- Menu tabs reordered to **Control | Lists | Monitor | Settings**, with every
  authored size routed through a single scale helper (`u()`) so both contexts
  scale as one system
- `/glvl` is now a thin adapter over the shared `queueByLevel` API (chat
  output unchanged)
- Fabric Loader minimum bumped to 0.19.5 (required by LDLib2)
- Fabric API bumped to 0.155.3+26.1.2
- Invite delay range (default 220–720 ms) is now configurable from the menu
- The **Settings** tab now holds only invite delays and whitelist-only mode;
  scale presets, autoscale and the popup/screen mode moved to the View menu
- Autoscale is **on by default**: every in-screen build fits the menu into
  the `[0.75, 2.0]` band for the current viewport without touching the
  stored preset
- The pop-out window is **WYSIWYG**: it opens from the measured panel
  (panel px × GUI scale × framebuffer scale, floored at the platform
  minimum), with the legacy `base × scale / contentScale` kept only as the
  fallback when the panel cannot be measured — proportions now track the
  in-game menu at any Minecraft GUI-scale option
- Top-bar element ids replaced the legacy title-bar set (`ginv_topbar`,
  `ginv_close`, `ginv_always_on_top`, `ginv_view_menu` in place of
  `ginv_titlebar`, `ginv_win_close`, `ginv_pin`); the uitest scenarios and
  docs use the new contract only

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

## [1.0.0] - 2026-07-31

### Added
- `/ginv` command with tab completion for batch guild invites
- `/glvl` command for level-based invites (SkyBlock only)
- `/gfreeze` command to pause/resume the invite queue
- Anti-detection throttling (220-720ms random delays)
- NPC filtering (names starting with `!`)
- SkyBlock instance detection via scoreboard
- Ported from Minecraft 1.21.11 version
