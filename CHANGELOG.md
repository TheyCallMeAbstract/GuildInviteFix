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
- Independent **menu scale** in Settings: segmented 75/100/125/150/200%
  control, persisted as `uiScale` in `settings.json`; the pop-out window opens
  at `base × scale / contentScale` physical pixels and layout divides by the
  game GUI scale, so it keeps one physical size and its proportions at any
  Minecraft GUI-scale option (rebuilds on scale or GUI-scale changes, keeping
  the window's position, size, pin and maximized state)
- **↩ re-dock** button in the pop-out title bar: returns the menu to the
  popup/screen mode it was popped out of
- Shared command API layer: `GuildLevels` (level + prefix-color extraction,
  tab range) and `GinvCommand.queueByLevel`/`parseTargets` — chat commands
  and the UI render the same results; `/glvl` behavior unchanged

### Changed
- Menu tabs reordered to **Control | Lists | Monitor | Settings**, with every
  authored size routed through a single scale helper (`u()`) so both contexts
  scale as one system
- `/glvl` is now a thin adapter over the shared `queueByLevel` API (chat
  output unchanged)
- Fabric Loader minimum bumped to 0.19.5 (required by LDLib2)
- Fabric API bumped to 0.155.3+26.1.2
- Invite delay range (default 220–720 ms) is now configurable from the menu

## [1.0.0] - 2026-07-31

### Added
- `/ginv` command with tab completion for batch guild invites
- `/glvl` command for level-based invites (SkyBlock only)
- `/gfreeze` command to pause/resume the invite queue
- Anti-detection throttling (220-720ms random delays)
- NPC filtering (names starting with `!`)
- SkyBlock instance detection via scoreboard
- Ported from Minecraft 1.21.11 version
