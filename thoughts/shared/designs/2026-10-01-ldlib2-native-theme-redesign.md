---
date: 2026-10-01
topic: "LDLib2-native theme re-design for /gmenu (inherit paper theme, remove bespoke palette)"
status: validated
---

# Design: LDLib2-native Theme Re-design for `/gmenu`

## Problem Statement

The menu's structure is fine; its **paint layer** is incoherent. Three compounding causes were confirmed by source audit:

1. **A mod inline override defeats our own stylesheet.** `panel.getStyle().backgroundTexture(Sprites.BORDER)` (`GinvMenuScreen.java:285`) sets an `INLINE` candidate; `INLINE(3) > STYLESHEET(2)`, so `#ginv_panel { background: #FFFFFF }` never applies. The "light" menu is wrapped in a dark GDP slate frame (`#605A66` center / `#0D0D11` edge).
2. **A bespoke palette duplicated in two places.** `office.lss` (Office blue `#0F6CBD`) plus ~20 inline Java constants (`COLOR_*`, `TINT_ACTIVE_*`, `CHROME_*`, `CLOSE_*`) produce four greens, four reds, three blues and a dozen near-identical grays. Roles disagree between sheet and code.
3. **Incomplete state coverage.** Components flip between sheet paint, constructor defaults and inline paint; e.g. in-screen light top bar paired with dark-window hover/close textures.

**Key correction to earlier assumptions:** LDLib2 component defaults are **themeable**. Constructors write raw `INLINE`, but `UIElement.internalSetup()` calls `StyleBag.moveInlineAsDefault()`, demoting them to `DEFAULT(0)` — so a stylesheet wins. The dark surfaces are therefore *our* fault (inline overrides), not LDLib2's.

## Constraints

- **Structure and ids stay**: `ginv_*` test-id contract is unchanged; this is a paint-layer re-design. Only exception: a justified primitive swap for the View menu (Open Question 3).
- **LDLib2 is read-only**; all theming lives in `src/main/resources/assets/guildinvitefix/lss/`.
- **Cascade reality**: `INLINE` beats `STYLESHEET`. Any remaining `getStyle()`/`style()` call on a themed property must be removed, or written at `DEFAULT` via `Style.defaultPipeline` / `lss(prop, value, StyleOrigin.DEFAULT)`.
- **Switch/Toggle are special**: they force their own `background` slot as `IMPORTANT` but derive its value from themeable state properties (`base-background`, `pressed-background`, `mark-background`, `unmark-background`). Theme the state properties, never `background`.
- **No dark-theme UI now** (excluded scope); tokens stay centralized so it is a later cheap swap.
- Font is Inter (shipped) rather than LDLib2's JetBrains Mono — a deliberate document-app deviation.
- Existing repo conventions: design first, then plan, then implementation; commits only on request.

## Approach

**Chosen: inherit LDLib2's `paper` design system, then ship a thin mod delta that speaks its token language.**

`paper.lss` is LDLib2's current light system (surfaces `#dfe3ea` / `#eff1f5` / `#ffffff`, accent `#3b6ef6`, hairline `#1f000000`), and every builtin component already carries paper rules. Making it the base and deleting our inline paint means components become consistently themed by construction.

**Rejected alternatives:**

- *Keep patching `office.lss` over `MODERN`* — MODERN is dark; every surface fights its defaults. (Current state.)
- *Recolor only (change hex values)* — leaves ~20 inline constants and the split-brain cascade intact.
- *`latte` warm-paper base* — pleasant but less Office-like; retained as a later palette option.

**Borrowed house style (LDLib2 shape language):** radius 5 for controls, 7 for surfaces; 1px hairline borders; **no drop shadows**; control height 16; neutral raised buttons with the **accent reserved for focus, selection, and primary CTAs** (LDLib2's own rationale: "a screen full of accent-coloured buttons reads as a toy").

## Architecture

Three layers (replacing the current MODERN + hand-rolled-office pair):

1. **Base — `paper.lss`** as the screen's global stylesheet (replacing `MODERN` in `modernSheet()`/`UI.of(root, sheet)`). Supplies all component surfaces, states, scrollers, fields, tabs, dialogs.
2. **Mod delta — a single `guildinvitefix:lss/<theme>.lss`** written in paper tokens, covering only what paper does not know: `#ginv_topbar`, `#ginv_pane`, `.ginv-scale-group` / `.ginv-mode-group`, `#ginv_view_popover` (or menu primitive), the SkyBlock chip (`ginv-sky-on/-off`), and tab selection. No Java color constants.
3. **Classes over inline paint**: elements opt into the theme via standard hooks (`.panel_bg` for the panel) and express state as classes (`.__selected__`, hover/pressed, `ginv-sky-*`), so the sheet owns presentation.

Runtime theme switching is a live operation (`styleEngine.clearAllStylesheets()` then `addStylesheet(...)`, no tree rebuild) — the basis for the deferred theme-picker (see Open Questions).

## Components

- **Panel** — delete the `Sprites.BORDER` inline override; adopt the theme panel surface. Single largest visual fix.
- **Top bar** — keep structure (icon / title / SkyBlock chip / View / chrome). Paint from theme tokens; remove `CHROME_*` / `CLOSE_*` constants; restrict dark chrome to the real windowed title bar if retained (Open Question 5).
- **View menu** — replace the hand-rolled absolute popover with LDLib2's menu primitive (`MenuTab` + `TreeBuilder.Menu`, as the editor's `ViewMenu` uses). Builtin themes already style menu nodes; this fixes the self-close/clipping bug class structurally and removes the manual dismissal guards. Keep `ginv_view_popover` as the popup root id if scenarios depend on it.
- **Tabs** — inherit paper's one-card tab treatment: header `7 7 0 0`, content `0 0 7 7`, selected tab becomes the surface.
- **Text fields / scrollers / tab content** — leave to paper; stop overriding them in our sheet, and confirm our selectors (if any) do not mis-target.
- **SkyBlock chip** — keep the component; restate `.ginv-sky-on/-off` in paper tokens.
- **Feedback / status / hero / banner / list buttons / level badge** — collapse inline color constants into semantic classes; hero keeps dynamic state colors but derived from theme tokens; fix `GuildLevels.DEFAULT_COLOR` (missing `0xFF` alpha) so badges are visible.

## Data Flow

- **Compile-time**: one token block in the mod sheet; elements reference classes/ids; no Java color values.
- **Runtime**: paper + mod delta attached to the screen's `ModularUI`; the style engine resolves candidates per element, `STYLESHEET` overriding demoted component `DEFAULT`s.
- **Deferred theme picker**: picker action → `clearAllStylesheets()` → `addStylesheet(<palette>)` → live re-style (same tree).

## Error Handling

- Mod sheet fails to load → `paper` still paints every surface (delta only adds ids/classes) — graceful.
- Font family missing → LDLib2 falls back to its default font (existing behavior).
- Token typo → rule silently does not match; caught by visual QA/screenshots and the contrast checks below.
- Accidental inline regression → caught by the "no themed inline paint" grep in Testing.

## Testing Strategy

- Same 6 uitest scenarios stay green; ids unchanged; ≥53 checks.
- **Contrast assertions** for text on light surfaces (the earlier low-contrast bug class), where numerically feasible.
- **No-dark-remnant sweep**: grep the screen for `getStyle()` / `style(` on themed visual properties — should remain only for genuinely dynamic values; `Sprites.BORDER` absent.
- **Palette-single-source check**: no `0xFF......` color constants remain in Java for themed roles.
- **Visual before/after** against the three original complaints (dark frame, split light/dark ink, fragmented semantics), using fresh screenshots.
- Optional probe: run LDLib2's own `theme_gallery` scenario (group `ldlib2`) to confirm palettes still render.

## Open Questions

1. **Base palette**: `paper` (recommended) vs `latte`.
2. **Font**: keep `Inter` (recommended) vs revert to LDLib2's JetBrains Mono for native consistency.
3. **View menu**: swap to LDLib2 menu primitive (recommended) vs recolor the custom popover. Affects scenario selectors; preserve `ginv_view_popover` either way.
4. **Theme picker**: defer to a phase 2 (recommended) vs include now.
5. **Windowed title bar**: native dark chrome (recommended) vs light to match the in-screen top bar.
