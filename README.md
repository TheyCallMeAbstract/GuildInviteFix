# GuildInviteFix

A client-side Fabric mod that batch-sends guild invites to multiple players at once.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **≥ 0.19.2**
- **Fabric API** (required)

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


## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Download the latest release JAR from [Releases](https://github.com/TheyCallMeAbstract/GuildInviteFix/releases).
3. Place the JAR in your `.minecraft/mods/` folder.
4. Make sure **Fabric API** is also installed in the same folder.

## License

This project is available under the CC0 1.0 license. See [LICENSE](LICENSE) for details.
