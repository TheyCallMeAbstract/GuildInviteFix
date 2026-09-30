---
date: 2026-09-30
topic: "GuildInviteFix 3-Section UI Menu (LDLib2)"
status: validated
---

# GuildInviteFix UI Menu Design

## Problem Statement

The mod can queue guild invites (`/ginv`, `/glvl`) but has no visual interface: no way to
see queue state, no per-player invite history, no whitelist/blacklist management, and no
configurable delays (hard-coded 220–720 ms). We need an in-game menu, built on the
LDLib2 dependency integrated earlier, that covers these gaps without changing the
command workflow.

## Constraints

- LDLib2 (`com.lowdragmc.lowdraglib2:ldlib2-fabric:26.1.2.40`) is already on the
  compile classpath (plain `implementation`, no `modImplementation` in this Loom).
- Player names are stored **exact-cased** as they appear on Hypixel SkyBlock.
- Invite counts are **permanent** (persist to disk under `config/guildinvitefix/`).
- Blacklist must always block invites; whitelist-only is an opt-in setting.
- No `runClient` smoke tests available (headless) — verification is `clean build`.
- Subagents/planner are broken this session; implementation is done directly.

## Approach

**Chosen: one `ModularUI` hosted by a `ModularUIScreen` subclass, opened by `/gmenu
[popup|screen]`.**

- **Popup mode** — transparent screen background (LDLib2's `ModularUIScreen.extractBackground`
  is a no-op), click-outside closes, ESC closes.
- **Screen mode** — same UI with a dimmed full-screen backdrop (`ColorRectTexture`),
  modal semantics (ESC only).

I rejected hosting the UI in a HUD overlay or an OS window: a screen keeps focus/ESC
semantics trivial and matches how LDLib2 itself opens UIs (`setScreen(new
ModularUIScreen(...))`).

Sections are an LDLib2 `TabView` (first tab auto-selected):

1. **Settings** — min/max delay `TextField`s (`setNumbersOnlyInt`) + Apply button,
   whitelist-only `Switch` (saves on change), hint + feedback labels.
2. **Lists** — add-by-name row (`TextField` + Add button), then a `ScrollerView` of
   player rows: **ICON_HEAD + exact name + 3 buttons (W whitelist / B blacklist /
   X remove-record-and-unqueue)**. Rows cover DB players ∪ online tab players (minus
   NPC `!` names), alphabetical.
3. **Monitor** — live status line (pending count, frozen state, whitelist-only flag),
   then a `ScrollerView` of the same player rows **plus a `×N · <relative-time>` count
   label**, sorted by invites desc.

The screen rebuilds row lists on tick when the store version or online snapshot changes;
status labels refresh every tick.

## Architecture

**New package `com.ginv.ui` + `com.ginv.data`:**

- **`GinvDataStore`** — static, `synchronized`, Gson-persisted:
  - `config/guildinvitefix/players.json` → `{ name → { invites, lastInviteMs, list } }`
  - `config/guildinvitefix/settings.json` → `{ minDelayMs=220, maxDelayMs=720,
    whitelistOnly=false }`
  - Atomic writes (`.tmp` + move), corruption-tolerant load, `version()` counter the UI
    polls for rebuilds. Lazy `ensureLoaded()` so init order never bites.
- **`PlayerHeadTexture`** — `implements IGuiTexture` (only `copy()` needed) with a
  renderer registered at client init via `GuiTextureRendererRegistry.register`.
  Skin resolution: tab list `getPlayerInfo` exact → `getSkin()`, else
  `DefaultPlayerSkin.get(name-based UUID)`. Face = classic blit: base 8×8 @ (8,8) +
  hat 8×8 @ (40,8) from a 64×64 skin via
  `GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, ...)`. Used as an element
  `backgroundTexture`, 10×10 px.
- **`GinvMenuScreen`** — builds the whole tree in the constructor (rows, tab view,
  wiring), owns tick-refresh, outside-click-close (popup only), Apply/row button
  handlers.
- **`GmenuCommand`** — `/gmenu [popup|screen]`.

**Integrations (edits):**

- `GinvCommand.processNext()` — per-send: skip if `!GinvDataStore.isAllowed(name)`
  (blacklist always; whitelist-only restricts), delay from store settings, and
  `recordInvite(name)` after a successful send. Adds `removeFromQueue(name)` for the
  X button. Applies to `/glvl` automatically (shared queue).
- `GuildInviteFixClient` — load store, register head-texture renderer, register
  `/gmenu`.

## Data Flow

1. `/gmenu` → `GinvMenuScreen` ctor builds UI from store snapshot + tab list.
2. Row buttons mutate the store → `version++` + save (client or scheduler thread,
   under lock).
3. `tick()` notices version/online change → rebuild row containers
   (`clearAllScrollViewChildren` + re-add).
4. Scheduler thread pops the queue → `isAllowed` gate → send → `recordInvite`
   (invites++, lastInvite=now) → save.

## Error Handling

- JSON load failure → log + empty store (never crashes startup).
- Save failure → log, keep running with in-memory state.
- Delay fields validated: numbers-only input, swapped if min>max, clamped to 50–60000.
- Skin lookup always falls back to a default skin — rows never render blank.
- Blocked (black/whitelisted-out) names are skipped silently at send time; their row
  state in the Lists tab explains why.

## Testing Strategy

- `JAVA_HOME=/tmp/opencode/jdk25 ./gradlew clean build` (compile + jar; no client
  smoke test available headless).
- Manual checklist for a real client: open both modes, toggle settings, whitelist /
  blacklist / remove round-trips persist across restart, monitor counts increment per
  sent invite, blacklisted names never send.

## Open Questions

- Relative-time granularity in Monitor rows may need polish after real-client testing
  (planned: `Xs/Xm/Xh/Xd ago`).
- If GitHub Packages credentials are absent, the build relies on the `mavenLocal()`
  escape-hatch jar installed earlier (documented in the integration design).
