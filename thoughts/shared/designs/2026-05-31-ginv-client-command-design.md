---
date: 2026-05-31
topic: "Client Command /ginv - Brigadier with Tab List Completion"
status: draft
---

## Problem Statement

We need a client-side command `/ginv` that accepts an arbitrary number of Minecraft usernames as parameters. The command should leverage the Brigadier command framework to provide tab-completion using the server's tab list, and store the collected usernames efficiently for later processing.

## Constraints

- **Fabric 1.21.11** modding framework with official Mojang mappings
- **Client command** — runs locally, no server-side permission checks
- **Brigadier** is the required command framework (bundled with Minecraft)
- **Java 21** target
- **Tab list completion** — suggestions must come from the live tab list
- **Variable argument count** — must handle 1 to N usernames
- Command logic is a placeholder for now; only registration and storage matter

## Approach

**Greedy string with custom suggestions** — register the command with `StringArgumentType.greedyString()` and a `SuggestionProvider` that queries the client's tab list, filters out already-typed names, and suggests matches.

I considered a recursive command tree (each name node pointing to an optional next name node) but rejected it because:
- Brigadier depth grows linearly with argument count
- Tab completion degrades at depth > 5-6
- No real benefit over greedy string for this use case

## Architecture

### New Files

| File | Responsibility |
|---|---|
| `com.ginv.client.GuildInviteFixClient` | Fabric `ClientModInitializer` entrypoint. Registers the client command. |
| `com.ginv.command.GinvCommand` | Brigadier command definition, suggestion provider, and `LinkedHashSet<String>` storage. |

### Modified Files

| File | Change |
|---|---|
| `fabric.mod.json` | Add `"client"` entrypoint pointing to `com.ginv.client.GuildInviteFixClient` |

## Components

### GuildInviteFixClient

- Implements `ClientModInitializer`
- In `onInitializeClient()`, calls `GinvCommand.register()`
- Uses Fabric's `ClientCommandRegistrationCallback` to hook into command registration

### GinvCommand

**Command registration:**
- Literal: `ginv`
- Argument: `names` of type `StringArgumentType.greedyString()`
- Suggestion provider: queries tab list, filters by prefix

**SuggestionProvider logic:**
1. Get the raw input string already typed after `/ginv `
2. Split on spaces to get currently typed names
3. Query `ClientPlayConnection.getInstance().getPlayerList()` for tab list entries
4. Filter out names already present in the typed set
5. Filter remaining names by prefix (last partial name being typed)
6. Return matching names as suggestions

**Storage:**
- `LinkedHashSet<String>` — O(1) add/contains, preserves insertion order, auto-deduplicates
- Static accessor method for other classes to read the stored names
- `setGinvTargets(Set<String>)` and `getGinvTargets()` accessors

## Data Flow

```
Player types "/ginv " or "/ginv Player"
       |
       v
SuggestionProvider invoked by Brigadier
       |
       v
Query tab list via ClientPlayConnection.getPlayerList()
       |
       v
Split current input -> set of already-typed names
       |
       v
Filter tab list: exclude already-typed, match prefix
       |
       v
Return List<Suggestion> to Brigadier
       |
       v
Player hits Enter -> greedyString parsed
       |
       v
Split raw string on spaces, trim, filter blanks
       |
       v
Store in LinkedHashSet<String>
       |
       v
Logic placeholder (to be implemented later)
```

## Error Handling

- **Empty input:** command fails gracefully with usage message
- **Duplicate names:** silently deduplicated by `LinkedHashSet`
- **Player not in tab list:** not a concern — suggestions only come from tab list, and raw input is accepted regardless (player might type a name not currently online)

## Testing Strategy

- Manual in-game testing: verify tab completion populates from tab list
- Verify duplicate names are deduplicated in storage
- Verify any number of names (1, 5, 10+) are accepted and stored
- Verify partial name filtering works for suggestions

## Open Questions

- Should the command clear previous targets when re-invoked, or accumulate?
- Should there be a max name count for sanity? (Currently unlimited per design)
