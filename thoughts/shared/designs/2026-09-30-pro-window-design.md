date: 2026-09-30
topic: "Rework the guild-invite menu into a full-fledged Windows program window"
status: validated

# Design: Program-Window Menu Rework

## Problem Statement

The menu's pop-out currently works but looks and behaves like a bare panel in an
OS window: no maximize, no double-click, no status bar, no theme, close-only
chrome. The user wants it to behave and look like a **full-fledged Windows
program window**, using yoga-like (Taffy flexbox) layout discipline and following
LDLib2's original documentation and its own window implementations.

## Constraints

- **Native (decorated) window chrome is forbidden**: a decorated window's
  move/resize runs a nested modal loop inside `glfwPollEvents` — dragging would
  freeze the game. Documented in `OsWindowHints`, `ModularUIWindow` and
  `FloatingViewRoot` javadoc. Chrome must be drawn in-UI (LDLib2's own approach).
- Fresh-UI pop-out must stay: closing the screen fires `onRemoved()` →
  `styleEngine.dispose()` on the live UI.
- In-screen popup semantics (outside-click close, dimmed screen mode) unchanged.
- Design-only workflow; planner subagent broken all session (model error) →
  direct implementation fallback, established precedent.

## Research Findings (LDLib2 docs + source)

- Official docs: `low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2` — UI is
  **Taffy-based (Block/Flexbox/Grid)**; built-in themes via
  `StylesheetManager` (GDP, MC, MODERN, DUSK, CARBON, …).
- `ModularUIWindow` provides: `dragArea` (buttons auto drag-blockers), 6px
  edge-resize with cursor shapes, `MIN_WIDTH/MIN_HEIGHT` 200/150,
  `toggleMaximized()/isMaximized()/restoredBounds()`, `onCloseRequested`
  (Alt+F4), `setAlwaysOnTop` + static `supportsAlwaysOnTop()`, per-frame
  stylesheet mirroring, tooltip flush, GUI-scale reflow.
- **No iconify/minimize API** (`OsWindow` only has `isIconified`) → minimize
  button impossible; `Icons.WINDOW_MINIMIZE` sprite exists but has no backing
  API.
- LDLib2's own program windows are the reference recipes:
  - `FloatingViewWindow`/`FloatingViewRoot`: 15px title bar, flex spacer,
    icon-only max/close buttons (`noText().addPreIcon(...)`, dynamic maximize
    icon supplier), `setDragArea(titleBar)`, `setMaximized` mirror.
  - `UIDebuggerWindow`: themed via
    `UI.of(root, StylesheetManager…MODERN)`, maximize/close wired to
    `toggleMaximized()`/`onCloseRequested()`, shortcuts caught by overriding
    `handleEvent` **before** the UI sees them, always-on-top toggle gated on
    `OsWindow.supportsAlwaysOnTop()`, and an explicit comment that **Esc must
    not close out from under a focused text field**.
- Key dispatch: key events go only to `ModularUI.focusedElement` when set;
  edge-level interception in the window is the only reliable global shortcut
  path. Focus is observable via the `__focused__` class that
  `ModularUI.requestFocus` maintains on the focused element.
- `modern.lss` is a dark professional theme: `#1E1F22` fields, blue `#71A4F4`
  accents, JetBrains Mono, `text-shadow: false`. `__white_icon__` overrides
  exist only in `ore.lss` → title-bar buttons must be inline-styled regardless
  of theme.
- `UI.of(root, Stylesheet…/Identifier…)` overloads exist for theming a fresh
  tree; `DynamicTexture.of(supplier)` gives per-frame stateful icons;
  `Icons.WINDOW_MAXIMIZE/RESTORE/CLOSE/MINIMIZE` exist.

## Approach

**Draw our own chrome (LDLib2 way) + MODERN theme + flexbox discipline.**

Rejected:

1. **Native decorated chrome** — game freeze (documented). Rejected.
2. **Custom hand-written LSS theme** — duplicates MODERN. Rejected; use
   MODERN + selective inline overrides.
3. **Minimize button** — no API. Omitted, noted as upstream gap.
4. **Menu bar (File/Edit)** — nothing to put there. YAGNI.
5. **`setStyleSource` mirroring** — no runtime theme switcher in this mod;
   explicit theme on both UIs is simpler. (dispose() does not clear
   globalSheets, so mirroring would work if ever needed.)
6. **Aero snap / Win+arrow** — requires decorated windows. Impossible.

## Architecture

New/changed pieces:

- **`GinvMenuWindow extends ModularUIWindow`** — adds:
  1. *Double-click title bar → toggleMaximized()*: detected in an overridden
     gesture start (not at an edge, over the drag area, ≤350ms and ≤3px since
     last press); consumes the press and swallows the matching release.
  2. *Esc semantics*: caught in an overridden event handler (debugger pattern);
     if any `TextField` holds focus (`__focused__` class walk over
     `getAllElements()`), clear focus and consume; otherwise
     `onCloseRequested()`.
- **Chrome on `GinvRoot`** (mirrors `FloatingViewRoot` field layout): titleBar,
  titleLabel, spacer, pinButton (windowed only), maximizeButton (windowed
  only), closeButton; plus existing refresh widgets. Pop-out rewires
  close → close-request, max → toggleMaximized, pin → always-on-top toggle.
- **Title bar**: 15px row, dark bg, gap 2, horizontal padding 6, vertical 1;
  title `adaptiveWidth` + vertical center; flex-1 drag spacer; icon-only
  buttons height 12. Idle transparent hover-translucent-white; **close hover
  = Windows red `#E81123`**; icons white (LDLib2 sprites), maximize icon =
  `DynamicTexture.of(() -> isMaximized() ? RESTORE : MAXIMIZE)`.
- **Status bar** (new, both contexts): 13px row at the bottom of the panel —
  left: live status text (moved from Monitor tab); right: transient feedback
  (apply results, validation errors, pop-out failure).
- **Theme**: MODERN applied to the screen UI and the window UI via
  `UI.of(root, stylesheet)`. Our W/B/X active states switch from text color to
  background tints for contrast on themed blue buttons.
- **Single-instance**: static open-window reference; `/gmenu` focuses the
  existing window (`OsWindow.focus()`) instead of opening a duplicate.
- **Pin**: button gated on `supportsAlwaysOnTop()`; persisted
  (`alwaysOnTop`, default false) in `GinvDataStore` settings.

## Layout (Taffy flexbox)

- Window root: opaque `#1E1F22`-family fill, column →
  titleBar (fixed 15) → body (`flexGrow 1`, `minWidth 0`, overflow guard) →
  status bar (fixed ~13). Root panel wraps body content with its border frame.
- Screen dialog: same chrome bands; panel 340×266 (height bumped for status
  bar), centered; popup keeps dim/transparent root + outside-click listener.
- `TabView` fills body with `flexGrow 1`; scrollers `flexGrow 1`; text fields
  `flexGrow 1` + `minWidth 0`; rows fixed-height flex rows; no absolute
  positioning except dialog centering. Window resize → LDLib2 re-inits UI →
  full reflow.
- Default open size **420×300**; platform floor 200×150.

## Data Flow

- Refresh loop unchanged: `GinvRoot.screenTick()` (screen via
  `ScreenMixin.ldlib2$tick`, window via tick-while-rendering) — now updates the
  status-bar label.
- Maximize icon: per-frame supplier reading `isMaximized()` — no manual sync.
- Pin: click → `setAlwaysOnTop(!…)` → persist → applied on next open.
- Pop-out: fresh `buildLayout(popup, true)` → `GinvMenuWindow` → rewire chrome
  → `open(MIN_VALUE, MIN_VALUE, 420, 300, false)` → success: `setScreen(null)`;
  failure: status-bar feedback, stay in-game.

## Error Handling

- Open refused → red feedback in status bar, stay in-game (existing path).
- Static reference + `isOpen()` guards every focus/close call.
- Pin hidden when platform unsupported.
- Esc-first-blur prevents closing under active edits.
- Close button and Alt+F4 share the close-request hook.

## Testing Strategy

- `./gradlew clean build` with JDK 25 (compile gate).
- Manual `runClient` checklist: pop-out; drag; edge cursors; double-click
  maximize/restore; maximize button icon swap; close; Alt+F4; Esc blur→close
  while a delay field is focused; pin round-trip; `/gmenu` focus guard;
  status bar live updates; W/B/X contrast under MODERN; popup outside-click;
  pop-out failure feedback.
- README (menu section) + CHANGELOG updates.

## Open Questions

- Default window size 420×300 — assumed acceptable.
- Minimize deferred pending an LDLib2 iconify API (upstream feature request).
- Always-on-top persistence included; trivial to drop if unwanted.
