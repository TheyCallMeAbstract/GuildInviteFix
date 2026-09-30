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

### Changed
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
