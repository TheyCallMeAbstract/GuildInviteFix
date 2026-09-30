---
date: 2026-09-30
topic: "Fix /gmenu not opening the menu (ChatScreen setScreen(null) clobber)"
status: validated
---

# Design: deferred screen open for /gmenu

## Problem Statement

`/gmenu` registers and executes correctly but the menu never appears.

Root cause, proven from vanilla source and run logs:

In `ChatScreen.keyPressed` (MC 26.1.2 decompile), on Enter:

1. `handleChatInput(...)` runs → Fabric dispatches the client command → our
   handler executes `Minecraft.setScreen(new GinvMenuScreen(...))`.
2. In the **same call stack, immediately after**:
   `if (this.closeOnSubmit) { ... this.minecraft.setScreen(null); }` —
   vanilla closing chat clobbers the screen we just opened.

Evidence:

- Decompiled `net/minecraft/client/gui/screens/ChatScreen.java`: the
  confirmation branch calls `handleChatInput(...)` then unconditionally
  `setScreen(null)` when `closeOnSubmit`.
- `run/logs/latest.log`: `/gmenu` produced **no** chat output at all, while the
  mistyped `/ldlib2_menu_test` produced "Unknown or incomplete command". This
  proves `/gmenu` matched the client dispatcher and executed without error —
  then silently lost its screen.
- The ldlib2 fork hit the identical bug: its own dev screen-test commands
  "arm" a pending screen applied on a later frame, commented
  *"always after ChatScreen's own setScreen(null)"*.

## Constraints

- The fix must live in this mod only (vanilla, Fabric API and ldlib2 are
  external).
- Keep the documented CLI: `/gmenu [popup|screen]` (README).
- No coupling to ldlib2 uitest harness internals (`FrameEvents` /
  `ClientCommands` pending-screen machinery is `devEnv`-gated test code).
- No scriptable in-game verification available; verification is compile plus
  source-level reasoning (plus manual smoke when a GUI run is possible).
- YAGNI: no keybind, no alternate triggers.

## Approach

**Chosen: deferred one-shot open driven by a client-tick applier.**

- `GmenuCommand` gains a static `pendingOpen` field (`null` = idle,
  otherwise `Boolean` = popup vs screen).
- The command handler **arms the flag** instead of calling `setScreen`.
- `register()` additionally subscribes once to
  `ClientTickEvents.END_CLIENT_TICK`; the applier:
  - current screen is `ChatScreen` → keep waiting (covers the
    `closeOnSubmit = false` path where chat stays open);
  - current screen is some other screen → **drop** the pending open (never
    clobber an unrelated screen);
  - current screen is `null` → clear the flag and
    `setScreen(new GinvMenuScreen(popup))`.

Timing proof: GLFW input events are polled before the tick phase in the run
loop, and chat's `setScreen(null)` executes synchronously inside the same
key-event stack as our command. Therefore the first `END_CLIENT_TICK` after
submission runs after chat has closed. Worst case: one tick (~50 ms) delay,
imperceptible.

Alternatives considered and rejected:

- **Mirror ldlib2's FrameEvents/pending-screen arm** — couples this mod to
  uitest harness internals for no benefit over a standard Fabric tick event.
- **`Minecraft.execute(...)`** — same-thread execution semantics vary across
  versions; less deterministic than an explicit tick phase.
- **Keybind instead of the command** — does not fix the documented `/gmenu`
  and adds UX nobody asked for.

## Architecture

Unchanged apart from `GmenuCommand`:

- `GuildInviteFixClient.onInitializeClient` → `GmenuCommand.register()`
  (already wired; register now also installs the tick applier).
- `/gmenu` handler → arm pending flag.
- `END_CLIENT_TICK` applier → open `GinvMenuScreen` once chat is gone.

## Data Flow

`Enter` → `ChatScreen.handleChatInput` → Fabric client command dispatch →
`GmenuCommand` arms `pendingOpen` → vanilla `setScreen(null)` closes chat →
run-loop tick → applier fires (`screen == null`) →
`setScreen(new GinvMenuScreen(popup))` → menu renders.

## Error Handling

- Pending open never overwrites a non-null, non-chat screen (dropped).
- Applier waits while a chat screen is current.
- No new failure modes; the command still returns `SINGLE_SUCCESS`.
- `GinvMenuScreen` already tolerates a null connection (list/status building
  is null-guarded).

## Testing Strategy

- `./gradlew clean build` (JDK 25) as the compile gate.
- Source-level verification complete: ChatScreen decompile, Fabric
  `fabric-command-api-v2` mixins, and the session run logs.
- Manual smoke when a GUI run is available: `/gmenu` opens the popup,
  `/gmenu screen` opens the dimmed variant, `/ginv` feedback unaffected.

## Open Questions

None blocking. A menu keybind remains a possible future enhancement, out of
scope here.
