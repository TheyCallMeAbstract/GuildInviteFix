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
Your own name is always excluded from invites and tab-completion suggestions.

```
/glvl <level>
```

Invite all players in the tab list with a guild level **≥** the given threshold.
Requires being in a SkyBlock instance. Players without a level (NPCs) are skipped.

```
/gfreeze
```

Toggle freeze on the invite queue. When frozen, no invites are sent but new targets are still queued.
Run again to resume sending. The mod **starts frozen**: no invites are sent
until you resume, either by pressing **STOP/RESUME INVITES** in the menu or by
running `/gfreeze`.

```
/gmenu
```

Open the in-game menu (LDLib2 UI) as a popup over the HUD, with three tabs:

- **Control** — state banner (RUNNING/STOPPED + pending), the big
  **STOP/RESUME INVITES** toggle, queue by name(s) (queued names are also
  recorded for the Lists tab), queue by guild level
  (live tab-range caption inside SkyBlock; disabled outside), and the
  current-target list with per-row remove, per-player invite count/last-invite
  age and **Clear**
- **Settings** — sectioned into **Invites** (invite delay range in ms with an
  **Apply** button), **Filtering** (whitelist only plus a configurable
  **Blacklist duration** — default 7 days; whitelist entries are always
  permanent) and **Appearance** (the menu theme picker)
- **Lists** — whitelist/blacklist management with a player-name search box
  plus a **LVL ▾** popover holding an inclusive min/max range (each field
  optional; blank = unset) and a **Clear** action; the filter applies live and
  an unknown-level player is excluded whenever a bound is set. Each row shows
  the player's head, name and guild level badge (colored like the server
  renders it) immediately after the name, with three borderless icon buttons —
  a whitelist page, a blacklist page and a lightning bolt to remove/unqueue
  (active whitelist/blacklist show a green/red border); a fully filtered-out list
  shows **"No players match."**

Above the tabs, the **top bar** shows the menu title, the live queue status,
the centered **SkyBlock chip** (grey outside SkyBlock, blue inside) and a
**View ▾** menu with the scale presets (100–200%) and an **Autoscale** switch
(fit the menu to the viewport; default off, so the menu opens at exactly 100%,
the fit itself is never persisted, and choosing a preset turns it off).

The top bar's **↗** button pops the menu out into its own program-style OS
window (LDLib2 `ModularUIWindow`) so it stays visible outside the Minecraft
window; the in-game screen closes when the window opens. The window opens
**WYSIWYG**: its size comes from the measured panel scaled to physical
pixels (panel × GUI scale × framebuffer scale, floored at the platform's
200×150 minimum), falling back to the base layout × menu scale when the
panel cannot be measured — so its proportions track the in-game menu at any
Minecraft GUI-scale option. It behaves like a desktop app: drag to move,
drag the edges to resize, double-click the title bar to maximize/restore,
**Esc** to close (the first press leaves a focused text field), a **↩
re-dock** button that puts the menu back in-game as the popup, and — where the
platform supports it — a pin for always-on-top. If the game is fullscreen when
you pop out, it drops to windowed first (a second window cannot open over a
fullscreen one) and stays windowed afterwards. Both the in-game menu and the
window share a status bar with live queue status and action feedback. Resizing
is a proportional zoom locked to the window's opening size — it can grow, but
never shrink below the layout it opened with.

The menu always opens as the popup (transparent background, closes on an
outside click) — there is no separate full-screen mode. Blacklisted players are
never invited; whitelist-only mode invites only whitelisted players. Invite
delays apply to `/ginv` and `/glvl` alike.

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
  (`GinvCommand.parseTargets`), the `LevelQueueResult` contract, and the
  `ListsFilter` name/level contract. Run alone with `./gradlew test`.
- **GUI suite (LDLib2 uitest scenarios, development builds)** — eight
  scenarios driving a real client: menu-open regression, tab structure,
  control-flow over the mock invite route, level queue with fixture rosters,
  scale presets, pop-out stability + re-dock, layout rhythm (column
  alignment, gap and symmetry checks for the player table at every scale),
  and Lists search + LVL filtering (name narrowing, inclusive bounds,
  no-level exclusion, Clear).

```bash
# headless tests only
./gradlew test

# full GUI suite (launches a dev client, needs a display)
./gradlew runClient -PldTest=mod:guildinvitefix
```

The report and screenshots land in `build/reports/lduitest/`:
`report.json` (per-step results, errors and capture references), and one PNG
per `.screenshot()` step — plus one for any failed step — under
`screenshots/<scenario>/<step>_<label>.png`. Optional
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
- **`Selection '...' matched no scenarios`** — the run aborts before any
  screenshot is taken. Check the expression against the grammar above;
  `mod:<id>` matches the scenario's *package* (needs a `.guildinvitefix.`
  segment), `group:` matches the annotation group, and a bare term is an
  annotation name (`menu_open_regression`, …).

## License

This project is available under the CC0 1.0 license. See [LICENSE](LICENSE) for details.
