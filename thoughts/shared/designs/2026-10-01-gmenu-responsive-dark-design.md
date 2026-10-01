---
date: 2026-10-01
topic: "Responsive dark re-design of /gmenu — layout-in-sheet component classes (DUSK base)"
status: validated
---

# Design: Responsive dark `/gmenu` (layout-in-sheet)

## Problem Statement

The theme refactor fixed colors but exposed structural layout debt. Reported symptom: **components off-center and overlapping**. Ranked root causes from source analysis:

1. **View popover is an absolutely-positioned child of the centered root**, with `left/top` computed in root space while its anchor lives in the centered panel (`GinvMenuScreen.java:420-429`, `:1380-1383`); it has **no `max-height` and no clip** (`:422-427`).
2. **SkyBlock dot is a real 0×0 flex child** (`SkyBlockStatusElement.java:42-48`) that still consumes `gap`, while the renderer hand-places its paint (`:96-108`) → label-collapse overlap.
3. **Centering depends on one frozen size**: panel `u(340)×u(266)` + `%` caps + hardcoded `fitScaleFor(340,266)` (`GinvMenuScreen.java:257-260`, `:104-107`, `:1342`).
4. **All geometry is inline in Java** (`paddingAll/gapAll/width/height`); `office.lss` declares almost no layout, so un-ruled elements silently fall back to theme defaults.

## Constraints

- **LDLib2 LSS has no component/var mechanism** (confirmed): no `@component`, `@define`, `@apply`, nesting, or `var(--x)`; bodies cannot reference other rules. Grammar is flat `selector { prop: value; }`, comma-separated selector lists, `:state` pseudo-classes.
- **All Taffy properties are settable from LSS**: `padding*`, `gap*`, `margin*`, `width/height`, `min/max-*`, `align-items`, `align-self`, `justify-content`, `flex-grow/shrink/basis`, `position`, `left/top/right/bottom`, `z-index`, `display`, `flex-direction/wrap`, `grid-*`. Values support `%` → percent, `auto|min-content|max-content|stretch`, bare number / `px` → GUI units.
- Root `%` resolves against the GUI-scaled screen; the window re-inits layout on resize/scale change (free reflow).
- **No id/structure contract changes**: all `ginv_*` ids stay; 6 scenarios stay green.
- LSS corner-radius shorthand order is **bottom-left, bottom-right, top-right, top-left**.
- Dark sibling: `dusk.lss` shares all 120 selectors with `paper.lss`; only palette literals differ → base swap is one line plus token re-derivation.

## Approach

**Chosen: rewrite the shell as a responsive flex column whose layout lives in the stylesheet, and re-skin to `dusk`.**

**"Components in LSS" realization.** LSS cannot hold reusable components. We implement the intent the way LDLib2 itself does (`panel_bg` idiom): each UI piece becomes a **real wrapper container carrying one named class**, and the sheet owns that class's layout and paint. Reuse is achieved with **comma-separated selector lists** (e.g. `.ginv-row, .ginv-topbar, .ginv-statusbar { flex-direction: row; align-items: center; }`) plus the shared token block.

**Rejected:** patch only popover/dot (not responsive); copy the editor shell wholesale (heavier than needed — borrow the column/grow pattern only).

## Architecture

Single centered content column; regions are flex children; all geometry in the sheet.

- **`#ginv_root`** — flex column, 100%×100%; centering delegated to the shell wrapper.
- **`.ginv-shell`** (new wrapper) — `width:100%; max-width:<N>; max-height:94%; align-self:center; flex-direction:column`. This is the window card. `.ginv-windowed` overrides to `max-width:100%; max-height:100%`.
- **Regions as classes**: `.ginv-topbar` (fixed height, row), `.ginv-banner` (auto), `.ginv-body` (`flex:1; min-height:0`), `.ginv-statusbar` (fixed height, row). `flex:1; min-height:0` on body/pane makes content **scroll instead of overlap**.
- **Sizing is `%`/`auto`/min-max**, not baked `u()` pixels. The frozen shell size and `fitScaleFor` constants collapse into one `max-width` token.

## Components

- **Shell + regions** — new `.ginv-shell`/`.ginv-body` wrappers; topbar/banner/body/statusbar get layout from the sheet via class + comma-grouped rules. Remove shell-region `paddingAll/gapAll/width/height` from Java (`:248-285`, `:309-314`, `:536-569`).
- **Panel `#ginv_panel`** — keeps id (scenario contract) but loses fixed size; `width:100%` inside the shell; surface from the theme.
- **View popover** — keep the custom element and id, but anchor to the **shell/panel box**, add `max-height`, `clip: scissor`, clamp to anchor bounds; content scrolls if tall. Removes the only true overlap vector.
- **SkyBlock dot** — out of flex flow: chip `position:relative`, dot `position:absolute` at the leading edge (or paint as chip pre-icon) so it stops consuming `gap` and cannot collide with the label.
- **Un-ruled elements** (`#ginv_feedback`, `appIcon`, `#ginv_hero`, `.ginv-dot`) — add base class rules so nothing falls back to theme default sizing.
- **Labels** — sheet sets adaptive width / `flex-shrink:0` where needed and `min-width:0` on shrinking captions so long text truncates rather than pushing siblings.

## Data Flow

- Java: structure + ids/classes + dynamic behavior only. **No layout numbers.**
- Sheet (`office.lss`, rewritten): dark token block + component class layout + component class paint; comma selector lists for shared rules.
- Reflow: GUI-scaled `%` + `flex:1` + `min/max` reflow under Taffy on resize/scale; the window's existing re-init triggers it.

## Error Handling

- Unknown class/id → theme default, but never zero-size (every interactive wrapper has a base rule): degrade, don't overlap.
- Sheet layout typo → element collapses to `auto`; caught by contrast/clip checks and screenshots.
- Popover taller than viewport → `max-height` + `clip` + scroller prevent bleed.
- Windowed vs in-screen → `.ginv-windowed` override: one code path, two sizes.

## Testing Strategy

- 6 uitest scenarios green; ids unchanged; ≥53 checks.
- **Structural greps**: no `PAPER` (→ `DUSK`); no `Sprites.BORDER`; no leftover shell `paddingAll/gapAll/width(`/`height(` in region builders; popover has `max-height`+`clip`; dot `position:absolute`.
- **Overlap/centering checks** where feasible: popover inside shell bounds; dot not intersecting label; body/pane `min-height:0`.
- **Dark contrast table**: text `#E7EAF2` / dim `#98A0B4` / faint `#6B7280` on surface `#171A21` / raised `#212632` vs AA targets.
- Host: `./gradlew test`, then `./gradlew runClient -PldTest=mod:guildinvitefix`; confirm centering at 100%/150% and in windowed popout.

## Token Block (dark, DUSK-derived)

bg `#FF0F1116` · surface `#FF171A21` · raised `#FF212632` · hovered `#FF2B3242` · sunken `#FF0B0D11` · line `#16FFFFFF` · line-strong `#2EFFFFFF` · text `#FFE7EAF2` · dim `#FF98A0B4` · faint `#FF6B7280` · accent `#FF7C8CFF` · accent-hover `#FF99A5FF` · accent-press `#FF6774E6` · danger `#FFE5484D` · success (define) `#FF3FB950` · warn (define) `#FFD29922`. Radii 3/5/7, border 1, control height 16.

## Open Questions

1. **Shell max width** — assumed ≈360 GUI units, `%`-capped.
2. **Windowed title-bar chrome** — assumed keep (dark), harmonized to dusk tokens.
3. **Dusk accent `#7C8CFF`** (indigo) — assumed accept; alternative hue possible.
