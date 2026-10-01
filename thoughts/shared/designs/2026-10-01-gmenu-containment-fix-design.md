---
date: 2026-10-01
topic: "gmenu text containment fix — restore D6 (LSS unit-free, Java owns scaled geometry)"
status: validated
---

# Design: `/gmenu` Text Containment Fix

## Problem Statement

After the office redesign (commit `ceb81d5`), text in several regions is clipped
against its container: the section headers ("Queue", "Current targets") lose
their bottom third, the status bar descenders are cut, and the state banner
("RUNNING · 0 pending") is cut by the hero button below it. Confirmed against
200 % crops of `build/reports/lduitest/screenshots/menu_open_regression/19_menu_open_popup.png`.

The elements are correctly sized; **the text boxes are not**.

## Root Cause

Three facts from the LDLib2 fork combine:

1. `Label()` hardcodes an **inline** `getLayout().height(9)`, and
   `PropertyRegistry.ADAPTIVE_HEIGHT` defaults **`false`**, so a label never
   grows to its text. Inline origin (3) beats stylesheet (2), so **a
   stylesheet `label { height: … }` can never override it** — only Java can.
2. `TextElement.TextElementRenderer.drawBackgroundAdditional` draws each line
   at exactly `fontSize` tall (`scale = fontSize / font.lineHeight`).
3. Fonts are authored through `u()`; at the autoscale fit `uiScaleContext ≈ 1.781`
   (`u(9)=16`, `u(10)=17.8`, `u(11)=19.6`), while the containing boxes are raw
   9 / 13 / 14 / 16.

`office.lss` currently carries **87 raw size declarations** (width/height/
min-*/padding-*/gap-*), which violates the office plan's **D6**
(`thoughts/shared/plans/2026-10-01-office-ui-overhaul.md` line 133–141):
*"LSS numeric values are raw — they bypass `uiScale` … all
widths/heights/paddings/gaps/font-sizes stay inline in Java via `u()`"*,
verified by `grep -cE '^\s*(width|height|font-size|padding-all|gap-all):' → 0`.

## Constraints

- Test-id contract: every `ginv_*` id / `ginv-*` class preserved.
- Do NOT edit scenario files.
- Text must stay crisp: keep `u()` font sizing (no whole-subtree `Transform2D`
  scale, which would render text at base size and blur it).
- LSS hex is `#AARRGGBB` (8-digit).

## Approach

**Restore D6.** Java owns all geometry via `u()`; `office.lss` owns only
unit-free presentation. Text boxes auto-size.

Rejected alternatives:

- *Scale the whole subtree with `Transform2D.scale(uiScale)` and author in base
  units* — clean proportionality but text is rasterised at base size then
  scaled ⇒ blurry; also would still fight `Label`'s hardcoded 9.
- *Bump only the three offending containers' heights* — fixes the visible
  clipping but leaves the proportionality drift and the D6 violation.

## Architecture

Two layers, unchanged from the office design except for the ownership split:

- **`office.lss`** — presentation only: `flex-direction`, `align-*`,
  `justify-*`, `flex-grow`/`flex-shrink`, `min-width: 0`/`min-height: 0`,
  `clip: scissor`, `position`, `z-index`, backgrounds/borders, `text-color`, `font`.
- **Java (`GinvMenuScreen`)** — all widths/heights/min-*/padding/gap and font
  sizes, every value wrapped in `u()`.

## Components

### A. Auto-sizing labels (root fix)

Add `adaptiveHeight(true)` to the `textStyle(...)` of every `Label` the screen
builds — in particular the helpers `sectionTitle`, `caption`, `bodyLabel`,
`dashLabel`, and the standalone labels: window title, feedback, status, banner,
keybind hint, list/monitor row labels, and any `label(...)` factory. The
renderer then sets the box height to the rendered line height (= the
`u()`-scaled `fontSize`) at IMPORTANT origin, overriding the hardcoded 9. No
magic numbers; scales exactly with the font.

### B. Move scale-bearing geometry from `office.lss` to Java `u()`

Relocate (or delete where Java already sets it inline):

- `.ginv-topbar`: `height`, `padding-horizontal`, `gap-all` (screen + windowed
  variants).
- `.ginv-banner`: `height`, `padding-horizontal`, `gap-all`.
- `.ginv-statusbar`: `height`, `padding-horizontal`, `gap-all`.
- `.ginv-panel`: `padding-all`, `gap-all`, `max-height` (percent stays a
  percent in Java).
- `.ginv-body`: `gap-all`; `.ginv-row`: `gap-column`, `min-height`;
  `.ginv-tab-column`: `gap-row`.
- `#ginv_view_popover`: `right`, `top` (position offsets), plus `width`,
  `max-height`, `padding-all`, `gap-all` where not already inline.
- delay/progress control `height`/`padding`; `.ginv-banner .ginv-dot` padding.
- `100%` widths/heights → Java `widthPercent`/`heightPercent`.
- Redundant (already inline Java): app icon, View button, hero, banner dot,
  sky-chip padding, `.ginv-ctl` width/gap — delete the LSS lines.

### C. `office.lss` after

Only unit-free declarations remain. A confirmation grep for any numeric
width/height/min-*/padding/gap (excluding `*-width: 0` / `min-height: 0`)
returns empty.

## Data Flow

Unchanged. `u(x)` resolves against `uiScaleContext` (persisted preset, or the
autoscale fit) at build time; every box and its text now share that factor.

## Error Handling

Unchanged. A missing/unparseable `office.lss` still degrades to the MODERN base.
With geometry removed from LSS the layout is now *more* robust to a missing
sheet, because the sizes live in Java.

## Testing Strategy

- **Static:** D6 grep for numeric size declarations in `office.lss` → empty
  (except allowed `min-width: 0` / `min-height: 0`).
- **GUI suite:** all six scenarios green (60/60 checks); specifically re-crop
  the section header, banner and status bar from
  `menu_open_regression/19_menu_open_popup.png` and confirm no clipping at the
  autoscale fit.
- **Scale matrix:** confirm no clipping at 75 %, 100 %, 150 %, 200 % and at the
  autoscale fit (the `scale_preset` scenario screenshots).
- **JUnit:** `./gradlew test` green.

## Open Questions

None blocking. The exact authored values for the relocated rows come from the
mock's tokens (topbar 14u, banner 16u, statusbar 13u; horizontal paddings
3/6/4u; gaps 3/3/4u).
