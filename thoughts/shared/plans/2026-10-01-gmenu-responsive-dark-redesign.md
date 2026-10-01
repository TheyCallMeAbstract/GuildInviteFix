# Plan — Responsive / dark `/gmenu` redesign (layout-in-sheet)

Status: proposed
Date: 2026-10-01
Design: `thoughts/shared/designs/2026-10-01-gmenu-responsive-dark-design.md` (validated)
Scope: planning only — no implementation code in this document.

File under design: `/gmenu` shell, `GinvMenuScreen.java`, `SkyBlockStatusElement.java`,
`src/main/resources/assets/guildinvitefix/lss/office.lss` (rewritten).

---

## 1. Goal

1. Make `/gmenu` **fully responsive and centered** at any GUI scale / window size.
2. Move **all shell-region layout geometry into the LSS sheet** via named wrapper
   classes; Java keeps ids, classes and behavior only.
3. Re-skin the base from `PAPER` to `DUSK` and rewrite the mod delta with the
   dark token block.

---

## 2. Locked decisions (recorded, from the design)

- Base stylesheet `StylesheetManager.PAPER` → `StylesheetManager.DUSK`.
- **LSS has no component / var mechanism.** "Components" are implemented as
  **real wrapper containers carrying one named class**; reuse comes from
  **comma-separated selector lists** and layout-in-sheet. No `@component`,
  `@define`, `@apply`, nesting or `var(--x)`.
- New `.ginv-shell` wrapper centered in `#ginv_root` with `max-width` / `max-height`.
- Regions `.ginv-topbar` / `.ginv-banner` / `.ginv-body` (`flex:1;min-height:0`) /
  `.ginv-statusbar` own their geometry in the sheet.
- Keep the custom View popover + `ginv_view_popover` id; **anchor to the panel**
  (not root), add `max-height` + `clip: scissor`, clamp, scroll content.
- SkyBlock dot out of flex flow (`position:absolute` in a `position:relative` chip).
- Remove shell-region geometry from Java (`paddingAll/gapAll/width/height` at
  `:248-285`, `:309-314`, `:536-569`); keep only ids/classes/behavior.
- Collapse `u(340)×u(266)` panel size + `fitScaleFor(340,266)` into one shell token.
- Dark token block from the design. Add `success` + `warn` (absent in dusk).
- Preserve ALL `ginv_*` ids; 6 uitest scenarios stay green; scenario **selectors** unchanged.

---

## 3. Critical open questions / blockers (calls needed)

These are surfaced by source inspection and must be called before/while executing.

### OQ-SCALE — `uiScale` presets vs. "no `u()` geometry in the sheet" *(blocker)*
`u(x)` multiplies every authored dimension by the persisted `uiScale` (and
`/guiScale` windowed). LSS cannot multiply by a runtime factor (no `var`), and
`Transform2D` is documented as *layout-neutral* (`Transform2D.java:28`) though
`ElementBounds.of` does apply the world pose. Three mutually exclusive options:

| Option | Mechanics | Cost |
|---|---|---|
| **S1 (recommended)** | Sheet owns **structural** geometry in authored units; the single scale-bearing value is the shell `max-width`, applied in Java as `u(SHELL_MAX_WIDTH)`; fonts stay `u()`. Padding/gap/heights no longer scale with the preset. | Small diff, keeps 6 scenarios; visual spacing fixed at 200%. |
| **S2** | Apply `uiScale` as one `Transform2D` scale on `.ginv-shell`; move **every** `u()` layout size inside the shell into the sheet. | Large diff, touches every widget; double-scale hazard if any `u()` remains. |
| **S3** | Drop preset pixel scaling; keep scheme `%`/flex only. | Breaks the documented 75–200% feature and its scenario assertions. |

**Call:** default the plan to **S1** (lowest risk, preserves the scenario value
contract). If "all geometry literally in the sheet" is non-negotiable, switch to
**S2** and add the full `u()`→sheet migration as a separate batch.

### OQ-BODY-COLLISION — `.ginv-body` is already taken *(resolved in plan)*
`bodyLabel()` (`GinvMenuScreen.java:977`) already adds class **`ginv-body`** to
labels, and `office.lss:69` styles it. The locked region name `.ginv-body` would
therefore give text labels `flex:1;min-height:0`. **Resolution:** rename the
label class to **`.ginv-body-text`** (Java `:977` + `office.lss:69`); the region
keeps `.ginv-body`.

### OQ-SHELL-TOKEN — shell width value
`ScalePresetScenario.java:51` back-solves the fit as `panel.width()/340.0` and
asserts `[0.73, 2.02]`. To keep that value contract exact, **choose
`SHELL_MAX_WIDTH = 340`** (design "≈360" is an open question; 340 is the safe
default). Confirm; if 360 is chosen, accept the scenario may need its tolerance
revisited (that is a *value*, not a selector).

### OQ-POPOVER-PARENT — panel vs. shell
Popover becomes a child of **`#ginv_panel`** (the card that owns padding/gap and
clips the visual card). The shell is a transparent sizing wrapper, so anchoring
to the panel avoids the shell padding. Confirm.

### OQ-SKY-DOT — keep Renderer vs. self-render
Recommended: keep `SkyBlockStatusElement.Renderer` (registered at
`GuildInviteFixClient.java:25`), make the chip `position:relative` and the dot
`position:absolute` with zero layout box — it stops consuming `gap` while the
renderer continues to paint the sheet-resolved background in the gutter.
Alternative: drop the Renderer, size the dot, and let it render itself. Confirm.

---

## 4. Sheet structure (`office.lss`, rewritten)

`office.lss` keeps its **filename and `officeSheet()` wiring** (prior plan D2),
but its contents become the DUSK-based dark delta. Precedence: `#ginv_*` ids and
`.ginv-*` classes out-specify `dusk.lss` type rules.

### 4.1 Token block (top of file, dark, all `AARRGGBB`)

`bg #FF0F1116` · `surface #FF171A21` · `raised #FF212632` · `hovered #FF2B3242` ·
`sunken #FF0B0D11` · `line #16FFFFFF` · `line-strong #2EFFFFFF` · `text #FFE7EAF2` ·
`dim #FF98A0B4` · `faint #FF6B7280` · `accent #FF7C8CFF` · `accent-hover #FF99A5FF` ·
`accent-press #FF6774E6` · `danger #FFE5484D` · **`success #FF3FB950` (new)** ·
**`warn #FFD29922` (new)**. Radii 3/5/7, border 1, control height 16.
LSS corner-radius order is **BL, BR, TR, TL**; `dusk.lss` already documents the
same. Tokens are comments + inline literals (no `var`).

### 4.2 Classes: layout + paint

Every value below is **authored GUI units** (S1), not `u()`. Java `u()` is used
only for `SHELL_MAX_WIDTH` and font sizes.

| Selector(s) | Role | Layout properties | Paint | Java removed |
|---|---|---|---|---|
| `#ginv_root` | root | `width:100%; height:100%; flex-direction:column; justify-content:center; align-items:center` | — | `:225-230` |
| `#ginv_root.ginv-screen` | scrim | — | `background: rect(#A6000000)` | `:240` |
| `#ginv_root.ginv-windowed` | OS window | `align-items:stretch; justify-content:flex-start` | `background: rect(#FF0F1116)` | `:236` |
| `.ginv-shell` | **new** card | `width:100%; max-width:340 (Java u()); max-height:94%; flex-direction:column; min-height:0; align-self:center` | — | `:257-260` |
| `#ginv_root.ginv-windowed .ginv-shell` | windowed | `max-width:100%; max-height:100%; flex:1` | — | `:252-255` |
| `#ginv_panel` | card surface | `width:100%; flex:1; min-height:0; flex-direction:column; padding-all:5; gap-all:3; position:relative` | `background: rect(#FF171A21,5,1,#16FFFFFF)` | `:249-255` |
| `.ginv-topbar, .ginv-statusbar` | shared row | `flex-direction:row; align-items:center; width:100%` | — | `:272-274`, `:563-565` |
| `.ginv-topbar` | region | `height:14; padding-horizontal:3; gap-all:3` | `background: rect(#FF212632,5,1,#16FFFFFF)` | `:281-283` |
| `#ginv_root.ginv-windowed .ginv-topbar` | chrome | `height:15; padding-horizontal:6; padding-vertical:1; gap-all:2` | `background: rect(#FF0F1116,0,1,#FF2EFFFFFF)` | `:276-279` |
| `.ginv-banner` | region | `flex-direction:row; align-items:center; width:100%; height:16; padding-horizontal:6; gap-all:6` | `background: rect(#3D7C8CFF,5)` | `:614-619` |
| `.ginv-body` | **new** region | `width:100%; flex:1; min-height:0; flex-direction:column` | — | `:537-538` (tabView) |
| `.ginv-statusbar` | region | `height:13; padding-horizontal:4; gap-all:4` | `background: rect(#FF0B0D11,5,1,#16FFFFFF); clip:scissor` | `:566-568` |
| `.ginv-app-icon` | new | `width:10; height:10` | (sprite stays Java) | `:292-293` |
| `.ginv-title` | new | `flex-grow:1; min-width:0` | — | `:302-303` |
| `.ginv-view-button` | new | `width:30; height:11` | (see button rules) | `:324-325` |
| `.ginv-skyblock` | chip | `position:relative; flex-direction:row; align-items:center; padding-left:12; padding-right:4; padding-vertical:2; gap-all:0` | `background: rect(#FF212632,7,1,#16FFFFFF)`; `-on/-off` states | `:310-313` |
| `.ginv-dot` | dot | `position:absolute; width:0; height:0` | `-on rect(#FF3FB950,99)` / `-off rect(#FFE5484D,99)` | `SkyBlockStatusElement:45-48` |
| `.ginv-popover` | popover | `position:absolute; width:210; max-height:220; padding-all:6; gap-all:4; z-index:1; clip:scissor` | `background: rect(#FF212632,7,1,#2EFFFFFF)` | `:423-428` |
| `.ginv-popover-scroll` | **new** | `width:100%; flex:1; min-height:0` | — | new |
| `.ginv-pane` | tab pane | `flex:1; min-height:0; flex-direction:column` | `background: rect(#FF171A21,5,1,#16FFFFFF); clip:scissor` | tabColumn `:936-941` |
| `.ginv-row` | row component | `flex-direction:row; align-items:center; gap-column:4; width:100%; min-height:16; min-width:0` | — | `:948-953` |
| `.ginv-ctl` | control cluster | `flex-direction:row; align-items:center; gap-column:4; width:165; flex-shrink:0` | — | `:890-895`, `:914-919` |
| `.ginv-scale-group, .ginv-mode-group` | toggles | `flex-direction:row; flex-wrap:wrap; gap-all:2` | — | `:457` |
| `.ginv-section/.ginv-caption/.ginv-muted/.ginv-empty/.ginv-body-text` | labels | `min-width:0; flex-shrink:1` where needed | dark text tokens | `:69` (rename) |
| `#ginv_root button` | buttons | — | `raised` base, `hovered` hover, `accent-press` press, white text | light literals |
| `#ginv_hero.ginv-hero-stop/-resume` | hero | `height:24` | `danger`/`success` base + shaded hover/press | `:650-651` |
| `.ginv-chrome(.-close)` | chrome | `width:16; height:12` | transparent, `hovered` hover, `danger` close | `:1603-1604` |
| `#ginv_root switch`, `#ginv_root toggle` | toggles | — | accent mark, sunken unmark/border | light literals |
| `.__tab-view_tab_header_container__`, `.__tab-view_tab_content_container__`, `.ginv_tab.__selected__` | tabs | — | `raised` + accent underline | light literals |
| `.__scroller_view_view-port__`, scroll bars | scrollers | — | sunken viewport, `hovered` bars | light literals |

`Sprites.BORDER` does not return: already gone from Java; no sheet rule re-adds it.

---

## 5. Java call sites to change (`GinvMenuScreen.java` unless noted)

Legend: **MOVE** = delete Java, add/own in sheet · **DELETE** = collapse into shell
token · **KEEP** = content/behavior, stays Java · **NEW** = add class/id.

### 5.1 Shell / regions

| Line | Current | Action |
|---|---|---|
| 225 | `layout.widthPercent(100)` root | MOVE → `#ginv_root` |
| 226 | `layout.heightPercent(100)` root | MOVE |
| 227 | `layout.flexDirection(COLUMN)` root | MOVE |
| 229 | `layout.justifyContent(CENTER)` root | MOVE |
| 230 | `layout.alignItems(CENTER)` root | MOVE |
| 249 | `layout.flexDirection(COLUMN)` panel | MOVE |
| 250 | `layout.paddingAll(u(5))` panel | MOVE → `#ginv_panel padding-all:5` |
| 251 | `layout.gapAll(u(3))` panel | MOVE → `gap-all:3` |
| 254 | `layout.widthPercent(100)` panel windowed | MOVE |
| 255 | `layout.flexGrow(1)` panel windowed | MOVE |
| 257 | `layout.width(u(340))` panel | **DELETE** → shell `max-width` token |
| 258 | `layout.maxWidthPercent(96)` panel | MOVE → shell/`.ginv-shell` |
| 259 | `layout.height(u(266))` panel | **DELETE** (responsive) |
| 260 | `layout.maxHeightPercent(94)` panel | MOVE → `.ginv-shell max-height:94%` |
| 272 | `layout.flexDirection(ROW)` topbar | MOVE |
| 273 | `layout.alignItems(CENTER)` topbar | MOVE |
| 274 | `layout.widthPercent(100)` topbar | MOVE |
| 276 | `layout.height(u(15))` topbar windowed | MOVE |
| 277 | `layout.paddingHorizontal(u(6))` topbar windowed | MOVE |
| 278 | `layout.paddingVertical(u(1))` topbar windowed | MOVE |
| 279 | `layout.gapAll(u(2))` topbar windowed | MOVE |
| 281 | `layout.height(u(14))` topbar in-screen | MOVE |
| 282 | `layout.paddingHorizontal(u(3))` topbar | MOVE |
| 283 | `layout.gapAll(u(3))` topbar | MOVE |
| 310 | `layout.paddingLeft(u(12))` chip | MOVE |
| 311 | `layout.paddingRight(u(4))` chip | MOVE |
| 312 | `layout.paddingVertical(u(2))` chip | MOVE |
| 313 | `layout.gapAll(u(3))` chip | **MOVE → `gap-all:0`** (dot absolute) |
| 537 | `layout.widthPercent(100)` tabView | MOVE → `.ginv-body` wrapper / tabView rule |
| 538 | `layout.flexGrow(1)` tabView | MOVE |
| 563 | `layout.flexDirection(ROW)` statusbar | MOVE |
| 564 | `layout.alignItems(CENTER)` statusbar | MOVE |
| 565 | `layout.widthPercent(100)` statusbar | MOVE |
| 566 | `layout.height(u(13))` statusbar | MOVE |
| 567 | `layout.paddingHorizontal(u(4))` statusbar | MOVE |
| 568 | `layout.gapAll(u(4))` statusbar | MOVE |
| 572-573 | statusLabel `flexGrow(1); minWidth(0)` | MOVE → `#ginv_status` |
| 575 | feedbackLabel `minWidth(0)` | MOVE → `#ginv_feedback` |

### 5.2 Component wrappers (new classes / moved geometry)

| Line | Current | Action |
|---|---|---|
| 290-294 | appIcon `width(u(10)); height(u(10))` | MOVE → `.ginv-app-icon` |
| 301-303 | titleLabel `flexGrow(1); minWidth(0)` | MOVE → `.ginv-title` |
| 323-325 | viewButton `width(u(30)); height(u(11))` | MOVE → `.ginv-view-button` |
| 420-429 | viewPopover abs/width/padding/gap/zIndex | MOVE → `.ginv-popover`; see §6 |
| 457 | scaleGroup `flexWrap(WRAP)` | MOVE → `.ginv-scale-group` |
| 462/487 | toggle `height(u(14))` | MOVE → `.ginv-scale-group toggle, .ginv-mode-group toggle` |
| 613-620 | banner row/width/height/padding/gap | MOVE → `.ginv-banner` |
| 623-625 | banner dot `width(u(6)); height(u(6))` | MOVE → `.ginv-banner .ginv-dot` |
| 633-634 | bannerLabel `flexGrow(1); minWidth(0)` | MOVE |
| 648-651 | hero `widthPercent(100); height(u(24))` | MOVE → `.ginv-hero` |
| 934-941 | `tabColumn()` inline layout | MOVE → `.ginv-tab-column` (NEW class) |
| 945-956 | `row(float)` inline layout | MOVE → `.ginv-row` (keep the arg for now or drop to const 16) |
| 888-895/912-919 | `ghin-ctl` inline layout | MOVE → `.ginv-ctl` |
| 1592-1605 | `chromeButton` `width(u(16)); height(u(12))` | MOVE → `.ginv-chrome` |
| `SkyBlockStatusElement:45-48` | dot `width(0); height(0)` | MOVE → `.ginv-dot position:absolute; 0×0` |
| 175/494/1575/… | `paperSheet()` call sites | RENAME → `duskSheet()`; `PAPER`→`DUSK` |

### 5.3 Content geometry — **KEEP** in Java (scope boundary)

Per the locked decisions, only shell/region geometry is removed. These are
content pixel sizes and stay `u()` (S1) unless OQ-SCALE chooses S2:

`394-395` popOutButton · `433` autoLabel · `678-682,686` name row ·
`706-713` level row · `740` levelRow · `757-765` targets header/clear ·
`776-779,817-820,832-835` scrollers · `849-866,884-887` settings fields ·
`908-911` whitelist label · `1060-1079,1147,1163-1165` player row/badge/buttons ·
`1248-1268` target row · `1602-1605` chrome button size.

Rationale: they are per-control content dimensions, not card regions; they remain
`u()` so the 75–200% preset still scales readable type and controls. **If the
reviewer rejects the OQ-SCALE recommendation and demands S2, these all move too.**

---

## 6. Popover fix (exact approach)

- **Parent:** `#ginv_panel` (card), not `#ginv_root`. Panel gains
  `position:relative`. Keep `#ginv_view_popover` id and `zIndex(1)`.
- **Class:** `.ginv-popover` → `position:absolute; width:210; max-height:220;
  padding-all:6; gap-all:4; z-index:1; clip:scissor`.
- **Scroll:** wrap all existing popover children in a `ScrollerView`
  (`ginv_popover_scroll`, class `.ginv-popover-scroll`, `width:100%; flex:1;
  min-height:0`). Child ids (`ginv_scale_150`, `ginv_mode_*`, `ginv_autoscale`)
  remain descendants, so the uitest id selectors still resolve.
- **Clamp math (panel-local):** in `toggleViewPopover(root)`:
  ```
  anchorX   = viewAnchor.getPositionX() - panel.getPositionX();
  anchorY   = viewAnchor.getPositionY() - panel.getPositionY();
  popoverW  = viewWidth;                       // u(210)
  popoverH  = popover.getSizeHeight() > 0 ? popover.getSizeHeight() : u(POPOVER_MAX_H);
  left = clamp(anchorX + viewAnchor.getSizeWidth() - popoverW,
               u(2), max(u(2), panel.getSizeWidth() - popoverW - u(2)));
  top  = clamp(anchorY + viewAnchor.getSizeHeight() + u(1),
               u(2), max(u(2), panel.getSizeHeight() - popoverH - u(2)));
  viewPopover.layout(l -> l.left(left).top(top));
  ```
  `root.panel` must be stored alongside `root.viewAnchor`/`root.viewWidth`.
- **Dismissal:** `within(...)`/`inSubtree(...)` continue to work because
  `getPositionX()` is accumulated root-space regardless of parent. The existing
  root MOUSE_DOWN + LAYOUT_CHANGED handlers are unchanged except that the anchor/
  popover subtree checks already short-circuit correctly.
- **Verification:** `max-height` + `clip: scissor` present; popover bounds ⊆ panel
  bounds; opening `#ginv_view_menu` still reveals `#ginv_view_popover`.

---

## 7. SkyBlock dot fix (exact approach)

- Chip: `SkyBlockStatusElement` already carries `.ginv-skyblock`; sheet sets
  `position:relative; gap-all:0`.
- Dot: Java stops calling `layout(width(0).height(0))`; sheet `.ginv-dot` sets
  `position:absolute; width:0; height:0`. It is out of flow → no `gap` cost, no
  label collision.
- State: keep `ginv-skyblock_status` id, `.ginv-sky-on`/`.ginv-sky-off` chip
  classes, `.ginv-dot-on`/`.ginv-dot-off` dot classes, and the
  `SkyBlockStatusElement.Renderer` (registered `GuildInviteFixClient:25`) which
  paints the sheet-resolved dot background in the gutter. Update the class doc
  that claims the dot is a laid-out style carrier.
- Rec(paint): `.ginv-dot-on background: rect(#FF3FB950,99)`;
  `.ginv-dot-off background: rect(#FFE5484D,99)` (banner dot keeps its own rule).
- Verification: `.ginv-dot` rule contains `position: absolute`; `.ginv-skyblock`
  contains `position: relative`; `ginv_skyblock_status` id + both class pairs
  still declared; `MenuOpenRegressionScenario` `.ginv-sky-off` count still 1.

---

## 8. Responsive breakdown

- **Root** is a full-viewport flex column that centers the shell; on windowed it
  stretches (`align-items:stretch; justify-content:flex-start`).
- **`.ginv-shell`**: `width:100%`, `max-width:340` (Java token), `max-height:94%`,
  `flex-direction:column`, `min-height:0`. Windowed override → 100% / 100% / `flex:1`.
- **Topbar** fixed `height:14` row; `title` `flex-grow:1; min-width:0` truncates;
  SkyBlock chip + View + chrome buttons `flex-shrink:0`. Windowed → `height:15`
  and dark chrome paint.
- **Banner** `height:16` row (Control pane) with `flex-grow:1; min-width:0` label.
- **Body** `.ginv-body` `flex:1; min-height:0` wraps the `TabView`; each pane is
  `flex:1; min-height:0`, and every `ScrollerView` is `flex:1; min-height:0`, so
  tall content **scrolls instead of overlapping** the status bar.
- **Statusbar** fixed `height:13` row; `#ginv_status` `flex-grow:1; min-width:0`
  + `ellipsize(...)`; `#ginv_feedback` `min-width:0`.
- **Small windows**: shell width falls to 100% of the viewport (root/panel padding
  only); `max-height:94%` caps vertical growth; panes/scrollers shrink via
  `min-height:0`; labels truncate; nothing paints outside the panel.
- **Windowed popout**: same tree, `.ginv-windowed` overrides; resizing the OS
  window re-runs Taffy (existing guiScale rebuild in `GinvRoot.screenTick` still
  handles GUI-scale changes).
- **Centering** is delegated to the shell inside the centering root; `%`/flex
  reflow is free on resize.

---

## 9. Verification

### 9.1 Structural greps (container-safe; run on host or here)

```bash
# no PAPER / modernSheet left
rg -n "PAPER|modernSheet|paperSheet|MODERN" src/main/java          # expect none

# no shell-region geometry left in region builders
rg -n "paddingAll|gapAll" src/main/java/com/ginv/ui/GinvMenuScreen.java   # expect none

# no border sprite / no 6-digit literals
rg -n "Sprites\.BORDER" src/main/java                              # expect none
rg -n "0x[0-9A-Fa-f]{6}\b" src/main/java                           # expect none

# popover max-height + clip present
rg -n "max-height|clip: scissor" src/main/resources/assets/guildinvitefix/lss/office.lss

# dot absolute + chip relative
rg -n "position: absolute|position: relative" \
  src/main/resources/assets/guildinvitefix/lss/office.lss

# every sheet hex is 8-digit alpha-first
rg -no "#[0-9A-Fa-f]{1,8}" src/main/resources/assets/guildinvitefix/lss/office.lss \
  | rg -v "#[0-9A-Fa-f]{8}"                                       # expect none

# required classes exist
rg -n "\.ginv-shell|\.ginv-body\b|\.ginv-topbar|\.ginv-statusbar|\.ginv-popover|\.ginv-popover-scroll" \
  src/main/resources/assets/guildinvitefix/lss/office.lss

# all ginv_* ids still declared
rg -n 'setId\("ginv_' src/main/java/com/ginv/ui/*.java | wc -l

# no leftover light-token literals (spot check)
rg -n "FFFFFF|EFF1F5|3B6EF6|1B1E24|paper|office light" -i \
  src/main/resources/assets/guildinvitefix/lss/office.lss            # only dark tokens
```

### 9.2 Dark contrast table (WCAG; ≥4.5 body, ≥3.0 large/border)

| Foreground | Background | Target |
|---|---|---|
| text `#E7EAF2` | surface `#171A21` | ≥4.5 |
| dim `#98A0B4` | surface `#171A21` | ≥4.5 |
| faint `#6B7280` | raised `#212632` | ≥3.0 |
| accent `#7C8CFF` | surface `#171A21` | ≥4.5 |
| accent-hover `#99A5FF` | surface `#171A21` | ≥4.5 |
| danger `#E5484D` | surface `#171A21` | ≥4.5 |
| success `#3FB950` | surface `#171A21` | ≥4.5 |
| warn `#D29922` | surface `#171A21` | ≥4.5 |
| text `#E7EAF2` | sunken `#0B0D11` | ≥4.5 |
| text on windowed chrome `#0F1116` | `#0F1116` | ≥4.5 |

Record results as a comment beside the token block.

### 9.3 Overlap / containment checks (host screenshots)

- Popover bounds inside panel bounds for `view_menu_open`.
- `.ginv-dot` does not intersect `#ginv_skyblock_status` label bounds.
- `#ginv_pane_*` / `#ginv_statusbar` / `#ginv_status` / `#ginv_feedback` ⊆ panel
  (existing `ScreenTabsStructureScenario` containment checks).
- Centering at GUI scale 100% / 150% and in the windowed popout.

### 9.4 Host commands (JDK 25, display available)

```bash
./gradlew test
./gradlew runClient -PldTest=mod:guildinvitefix
```
Expected: 6/6 scenarios, ≥53 checks, no failed screenshots.

### 9.5 Container caveat

This container has **no GUI and JDK 21 only** (project needs 25). Gradle cannot
run here; verification in-container is structural (greps + inspection). The host
runs `./gradlew test` and `./gradlew runClient -PldTest=mod:guildinvitefix`.

---

## 10. Micro-tasks (batched by dependency)

One file per task; same-file tasks are sequential. Tasks in the same batch with
**different files** can run in parallel.

### Batch 1 — independent groundwork (parallel)
- **T1 · `office.lss`** — full rewrite: dark token block; shell/region/component
  classes from §4; comma selector lists (`.ginv-topbar, .ginv-statusbar` etc.);
  layout-in-sheet; dark paint; rename `.ginv-body` text selector →
  `.ginv-body-text`; add `.ginv-shell/.ginv-body/.ginv-popover-scroll` and new
  component classes. *Verify:* §9.1 greps 4–7; required classes present; 8-digit hex.
- **T2 · `SkyBlockStatusElement.java`** — remove dot `layout(width(0).height(0))`;
  keep ids/classes/Renderer; update doc. *Verify:* no size `layout(` for dot;
  `ginv_skyblock_status` + `.ginv-sky-off` still emitted.

### Batch 2 — `GinvMenuScreen.java` (same file, strict order)
- **T3 · base sheet** — `PAPER`→`DUSK` in `paperSheet()` and rename to
  `duskSheet()`; update all call sites (`:175`, `:494`, `:1575`, rebuild path)
  and javadoc (`:59-60`, `:145-158`, `:155`). *Verify:* §9.1 grep 1 clean.
- **T4 · shell + region wrappers** — add `.ginv-shell` wrapper between root and
  panel; add `.ginv-body` wrapper around `tabView`; add classes
  (`ginv-app-icon`, `ginv-title`, `ginv-view-button`, `ginv-tab-column`) and
  delete rows `:225-230`, `:249-260`, `:271-285`, `:290-294`, `:301-303`,
  `:309-314`, `:323-325`, `:536-538`, `:560-575`, `:613-620`, `:623-634`,
  `:648-651`, `:934-956`, `:888-895`, `:912-919`, `:1592-1605`. Shell
  `max-width` uses `u(SHELL_MAX_WIDTH)`. *Verify:* no `paddingAll|gapAll`; no
  `width(u(340))|height(u(266))`; ids unchanged.
- **T5 · scale tokens** — define `SHELL_MAX_WIDTH=340` (and optional
  `SHELL_MAX_HEIGHT`) replacing panel constants; update `fitScaleFor` to use the
  token(s); remove the frozen 340/266 references (`:257-260`, `:104-107`,
  `:1340-1344`). *Verify:* single token referenced; `ScalePresetScenario` math
  still `panel.width()/340` valid (S1). *(Do per OQ-SCALE call.)*
- **T6 · popover** — re-parent to panel; store `root.panel`; add `.ginv-popover`
  class + `ScrollerView` wrapper; new clamp math (§6); keep id/zIndex. *Verify:*
  `ginv_view_popover` exists; opening/closing still toggles display; child
  `ginv_scale_150` resolvable.
- **T7 · cleanup** — remove now-unused imports/constants/fields (`u()` call
  sites in deleted regions; `FlexDirection`/`AlignItems`/`AlignContent` only if
  truly unused), update class javadoc. *Verify:* compiles logically; §9.1 clean.

### Batch 3 — verification (after Batch 2)
- **T8 · structural greps** (host, §9.1).
- **T9 · contrast table** (host, §9.2) — record beside token block.
- **T10 · `./gradlew test`** (host JDK 25).
- **T11 · `./gradlew runClient -PldTest=mod:guildinvitefix`** (host, display) —
  6/6 scenarios, ≥53 checks, screenshots reviewed for centering + dark theme.

**Parallelism:** T1 ∥ T2 (Batch 1). T3→T4→T5→T6→T7 (one file). T8/T9 may run
concurrently on host; T10→T11 sequential.

---

## 11. Risks

- **R1 (OQ-SCALE):** if S1 is rejected, T5/T7 expand into a full `u()`→sheet
  migration; do not start T4 until the call is made.
- **R2 (popover scroll):** wrapping popover children in a `ScrollerView` changes
  the DOM depth; confirm uitest id resolution is depth-agnostic (it is:
  `ctx.el("#id")` scans all elements).
- **R3 (transform/event space):** not used in S1. If S2 is chosen, re-audit the
  popup outside-click geometry checks (event coords vs transformed bounds) and
  prefer subtree-target checks.
- **R4 (`.ginv-body` collision):** must be renamed everywhere together (Java
  `:977`, `office.lss:69`), or labels gain `flex:1`.
- **R5 (scenario value deps):** `ScalePresetScenario:51` assumes 340; keep
  `SHELL_MAX_WIDTH=340` (OQ-SHELL-TOKEN) or accept a value-only scenario tweak
  (selectors untouched).

---

## 12. Definition of done

- §9.1 structural greps all clean.
- §9.2 dark contrast table all pass.
- Popover has `max-height` + `clip: scissor`, is a child of `#ginv_panel`, and is
  contained in the panel.
- `.ginv-dot` is `position:absolute` in a `position:relative` `.ginv-skyblock`.
- No shell-region `paddingAll/gapAll/width/height` left in `GinvMenuScreen`.
- All `ginv_*` ids preserved; 6/6 uitest scenarios, ≥53 checks green on host.
- `office.lss` is the single source of shell layout + every themed color; base is
  `DUSK`.
