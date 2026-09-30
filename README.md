# GuildInviteFix

A client-side Fabric mod that batch-sends guild invites to multiple players at once.

## Requirements

- Minecraft **26.1.2**
- Fabric Loader **≥ 0.19.5**
- **Fabric API** (required)
- **LDLib2** (Fabric build from [ldlib2-Architectury](https://github.com/TheyCallMeAbstract/ldlib2-Architectury), required)
- **Architectury API ≥ 20.0.12** (required by LDLib2)
- **YACL ≥ 3.9.1** (required by LDLib2)

## Usage

```
/ginv [player1] [player2] [player3] ...
```

Invite specific players by name. Supports tab completion from the tab list.

```
/glvl <level>
```

Invite all players in the tab list with a guild level **≥** the given threshold.
Requires being in a SkyBlock instance. Players without a level (NPCs) are skipped.

```
/gfreeze
```

Toggle freeze on the invite queue. When frozen, no invites are sent but new targets are still queued.
Run again to resume sending.

```
/gmenu [popup|screen]
```

Open the in-game menu (LDLib2 UI) with four tabs:

- **Control** — state banner (RUNNING/STOPPED + pending), the big
  **STOP/RESUME INVITES** toggle, queue by name(s), queue by guild level
  (live tab-range caption; disabled with a hint outside SkyBlock), and the
  current-target list with per-row remove and **Clear**
- **Settings** — invite delay range (min/max ms), whitelist-only mode, and the
  menu **scale** (75–200%, persisted; the pop-out window keeps its physical
  size regardless of Minecraft's GUI-scale option)
- **Lists** — whitelist/blacklist management; each row shows the player's head,
  name, their guild level (colored like the server renders it), a **⚡** queue
  button and three buttons: **W** (whitelist), **B** (blacklist), **X** (remove
  record and unqueue). Add offline players by name.
- **Monitor** — live queue status plus per-player invite counts and last-invite
  times (persisted across restarts)

The title bar's **↗** button pops the menu out into its own program-style OS
window (LDLib2 `ModularUIWindow`) so it stays visible outside the Minecraft
window; the in-game screen closes when the window opens. The window behaves
like a desktop app: drag to move, drag the edges to resize, double-click the
title bar to maximize/restore, **Esc** to close (the first press leaves a
focused text field), a **↩ re-dock** button that puts the menu back into the
screen mode it came from, and — where the platform supports it — a pin for
always-on-top. Both the in-game menu and the window share a status bar with
live queue status and action feedback.

`popup` (default) renders over a transparent background and closes on an outside
click; `screen` renders with a dimmed backdrop. Blacklisted players are never
invited; whitelist-only mode invites only whitelisted players. Invite delays
apply to `/ginv` and `/glvl` alike.

## Installation

1. Install Fabric Loader for Minecraft 26.1.2.
2. Download the latest release JAR from [Releases](https://github.com/TheyCallMeAbstract/GuildInviteFix/releases).
3. Place the JAR in your `.minecraft/mods/` folder.
4. Also install in the same folder: **Fabric API**, **LDLib2** (Fabric build), **Architectury API**, and **YACL**.

## Building from source

Requirements: **JDK 25**.

No tokens, accounts or CI secrets are needed: `ldlib2-fabric` is downloaded from
the fork's **public GitHub release assets**, and `mavenLocal()` is checked
first.

```bash
./gradlew build
```

### Building against a local fork build

1. Clone the fork: `git clone -b 26.1 https://github.com/TheyCallMeAbstract/ldlib2-Architectury.git`
2. Build and publish it locally: `cd ldlib2-Architectury && ./gradlew :fabric:publishToMavenLocal` (JDK 25)
3. Our build checks `mavenLocal()` first, so your local build of the fork wins.

## Testing

Two layers:

- **JUnit (headless, runs inside `./gradlew build`)** — pure logic:
  guild-prefix parsing (`GuildLevels.parsePrefix`), target-string parsing
  (`GinvCommand.parseTargets`), and the `LevelQueueResult` contract. Run
  alone with `./gradlew test`.
- **GUI suite (LDLib2 uitest scenarios, development builds)** — six scenarios
  driving a real client: menu-open regression, tab structure, control-flow
  over the mock invite route, level queue with fixture rosters, scale
  presets, and pop-out stability + re-dock.

```bash
# headless tests only
./gradlew test

# full GUI suite (launches a dev client, needs a display)
./gradlew runClient -PldTest=mod:guildinvitefix
```

The report and screenshots land in `build/reports/lduitest/`. Optional
flags: `-PldTestHeadless` (synthetic input, hidden window),
`-PldTestGuiScale=<n>`, `-PldTestWindow=<WxH>`, `-PldTestKeepOpen`.
Selection grammar: `all`, `<name>`, `a,b,c`, `group:guildinvitefix`,
`tag:ui`, `mod:guildinvitefix`, `regex:<pattern>`.

The GUI suite installs `GuildTestGateway` fixtures (fake roster, SkyBlock
verdict, recorded sends instead of network commands). `install()` **throws**
outside a development environment running a singleplayer world, so the mock
invite route can never activate in production.

## Troubleshooting

- **`Could not find com.lowdragmc.lowdraglib2:ldlib2-fabric:<version>`** —
  no GitHub release exists for the version pinned in `gradle.properties`
  (`ldlib2_version`), and the artifact is not in `mavenLocal()`. Publish the
  fork release for that version, or build the fork locally
  (`:fabric:publishToMavenLocal`) as described above.

## License

This project is available under the CC0 1.0 license. See [LICENSE](LICENSE) for details.
