# Changelog

## [Unreleased]

### Added
- LDLib2 integration (Fabric port `ldlib2-fabric` from the
  TheyCallMeAbstract/ldlib2-Architectury fork) via GitHub Packages Maven
- Required companion dependencies: Architectury API ≥ 20.0.12 and YACL ≥ 3.9.1
- CI package-read credential wiring (`LDLIB2_PACKAGES_READ` secret)

### Changed
- Fabric Loader minimum bumped to 0.19.5 (required by LDLib2)
- Fabric API bumped to 0.155.3+26.1.2

## [1.0.0] - 2026-07-31

### Added
- `/ginv` command with tab completion for batch guild invites
- `/glvl` command for level-based invites (SkyBlock only)
- `/gfreeze` command to pause/resume the invite queue
- Anti-detection throttling (220-720ms random delays)
- NPC filtering (names starting with `!`)
- SkyBlock instance detection via scoreboard
- Ported from Minecraft 1.21.11 version
