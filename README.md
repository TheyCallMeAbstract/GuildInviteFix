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

Open the in-game menu (LDLib2 UI):

- **Settings** — invite delay range (min/max ms) and whitelist-only mode
- **Lists** — whitelist/blacklist management; each row shows the player's head,
  name and three buttons: **W** (whitelist), **B** (blacklist), **X** (remove
  record and unqueue). Add offline players by name.
- **Monitor** — live queue status plus per-player invite counts and last-invite
  times (persisted across restarts)

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

The `ldlib2-fabric` artifact is resolved from GitHub Packages, which requires a
token with `read:packages`, even for public packages. Add your credentials to
`~/.gradle/gradle.properties`:

```properties
gpr.user=<your GitHub username>
gpr.key=<PAT with read:packages scope>
```

CI reads the same credentials from the `LDLIB2_PACKAGES_READ` repository secret.

Then:

```bash
./gradlew build
```

### Credential-free alternative (mavenLocal escape hatch)

1. Clone the fork: `git clone -b 26.1 https://github.com/TheyCallMeAbstract/ldlib2-Architectury.git`
2. Build and publish it locally: `cd ldlib2-Architectury && ./gradlew :fabric:publishToMavenLocal` (JDK 25)
3. Our build checks `mavenLocal()` before GitHub Packages, so no token is needed.

## Troubleshooting

- **`Could not resolve com.lowdragmc.lowdraglib2:ldlib2-fabric` (401/404)** —
  GitHub Packages auth is missing or the token is expired. 404 on a public
  package almost always means "not authenticated". Create a classic PAT with
  the `read:packages` scope (GitHub → Settings → Developer settings →
  Personal access tokens → Tokens (classic) → Generate new token) and set
  `gpr.user` / `gpr.key` as shown above. In CI, update the
  `LDLIB2_PACKAGES_READ` secret.

## License

This project is available under the CC0 1.0 license. See [LICENSE](LICENSE) for details.
