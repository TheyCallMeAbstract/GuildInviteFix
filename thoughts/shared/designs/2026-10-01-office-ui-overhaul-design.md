---
date: 2026-10-01
topic: "Office UI overhaul for /gmenu (top bar, View options, SkyBlock status widget, Fluent light theme)"
status: validated
---

# Design: Office UI Overhaul for `/gmenu`

## Problem Statement

The `/gmenu` LDLib2 menu fails on five user-facing fronts, all confirmed against fresh uitest screenshots (run `2813181`, 6/6 scenarios green, 13 screenshots):

- **Popout shrink**: the detached window is sized from a fixed `420×300` base, while the in-screen popup measures `≈340 × uiScale × guiScale` (≈678px at guiScale 2) — popping out makes the menu *smaller* than it was in-screen.
- **Settings overflow**: the scale `ToggleGroup` has no horizontal orientation (COLUMN default), so five scale buttons stack vertically and cross the panel's right border; the layout tree has no shrink/clip/overflow rules anywhere.
- **Bad theme**: near-black surfaces (`#1E1F22`/`#15171A`), borders `#2E3137`, accent `#71A4F4` — dark, flat, amateurish, nothing like Office.
- **Poor shapes / invisible text**: buttons and tabs render as blank blue pills; a latent color bug mixes 6-digit RGB with 8-digit ARGB constants (LDLib2 expects ARGB), rendering every 6-digit color fully transparent — labels like "Delay", "Whitelist only", player names, and the W/B/X glyphs are invisible. The hero panel also ships the default text `"Button"`.
- **No Office resemblance**: there is no top chrome at all — no title bar, no View/environment controls, no status presentation.

## Constraints

- **LDLib2 is read-only.** All changes live in this mod. LSS semantics follow the official docs: [StyleSheet](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/preliminary/stylesheet.html), [Smooth Font Rendering](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/fonts.html), [UI Agent Guide](https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/agent_guide.html).
- **Test-id contract.** `ginv_*` ids/classes are stable API for the six uitest scenarios. Anything relocated is updated in lockstep — `ScalePresetScenario` moves with the scale presets.
- **Verification runs on the host and CI.** The container has no JDK 25/GUI; uitest runs happen on the user's host; CI (`.github/workflows/build.yml`, JDK 25, `./gradlew build`) must stay green.
- **Pro-window size contract addendum.** Existing docs define the popout window as `BASE(420×300) × uiScale ÷ contentScale` (GUI-scale-independent). This design deliberately amends that to a WYSIWYG measured size; the prior contract is superseded (kept as fallback only).
- **Scope guards (YAGNI).** No Office ribbon-collapse engine (four tabs stay tabs), no dark-theme variant (tokens are centralized so a dark swap is a future, cheap change), no density toggle.
- **Repo conventions.** Design doc committed first (`designs: ...`); implementation commit follows with CHANGELOG/README updates.

## Approach

**Chosen: LSS-first restyle + structural top bar.**

The official docs position stylesheets as the mechanism to "separate presentation from logic" and make them resource-pack overridable. The StyleOrigin cascade (`DEFAULT < STYLESHEET < INLINE < ANIMATION < IMPORTANT`) lets our stylesheet override component defaults cleanly, with no sprawl of inline Java style calls. Subtree-scoped stylesheets (`addLocalStylesheet`) let us bind the theme to the menu root only — zero risk of restyling other mods' screens.

**Rejected alternatives:**

- *Inline Java styling everywhere* — INLINE-origin values scattered through construction code; un-overridable, un-reviewable, exactly the mess that produced the mixed RGB/ARGB bug.
- *Same-path merge into `ldlib2:lss/modern.lss`* — the merged-stylesheet feature would patch the global MODERN theme and affect every screen using it, ours and other mods' alike.
- *Ribbon-collapse engine and density toggle* — Office cosplay beyond what this menu needs.

## Architecture

Four layers, bottom-up:

1. **Base theme — built-in MODERN.** The screen already loads MODERN via `StylesheetManager`; it supplies sane component defaults (Toggle, Button, TextField, TabView) so we only override what differs.
2. **Office theme — our LSS.** A single stylesheet under `assets/guildinvitefix/lss/` with a **token block at the top** (all colors 8-digit ARGB) and rules scoped to `#ginv_*` ids and `.ginv-*` classes. Attached to the menu's style engine / menu root subtree so it overrides the base at STYLESHEET origin, within our tree only.
3. **Structure — top bar row.** The screen tree gains a top bar above the existing ribbon tab strip; content panes gain explicit shrink/clip/ellipsis rules; Settings becomes two-column rows.
4. **Font — ship Inter TTF** (SIL OFL) as the UI sans family, rendered through LDLib2's smooth-font pipeline (font-family wiring to be verified — Open Questions). JetBrains Mono is no longer the UI default; it remains available for genuinely code-like text (currently none).

**Amended popout contract:** at detach time, measure the in-screen panel's physical bounds (logical units × contentScale, clamped to a minimum of 200×150) and open the window at that size — popup ≈ window, WYSIWYG. If measurement is unavailable, fall back to the legacy `420×300` base.

## Components

- **Top bar (`ginv_topbar`)** — left: app icon + "Guild Invite Fix"; right cluster: **SkyBlock status widget** → **View ▾ control** → chrome buttons. In-screen shows popout/close; windowed shows pin/redock/max/close. Existing ids `ginv_popout`, `ginv_redock`, `ginv_close`, `ginv_always_on_top` are preserved wherever they map to these buttons.
- **View menu (`ginv_view_menu`)** — a dropdown/popover containing:
  - **Autoscale toggle** (default ON): automatically fits the base menu into the viewport, clamped to 0.75–2.0.
  - **Manual presets 75/100/125/150/200%**, reusing the existing `ginv_scale_*` ids; choosing a preset switches autoscale off.
  - **Mode group** Popup|Screen (existing behavior, relocated).
  - This relocates the scale presets out of Settings, which dissolves most of the overflow; Settings keeps Delay + Whitelist rows.
- **SkyBlock status widget (`ginv_skyblock_status`)** — a mod-side `UIElement` subclass with custom renderer registration; ON/OFF state is carried as an LSS class (e.g. a "sky-on"/"sky-off" class) so the chip's look is pure stylesheet. Data comes from the existing SkyBlock detector, refreshed on screen tick. The `GuildTestGateway` provides a verdict override so scenarios exercise both states without a real SkyBlock world.
- **Ribbon tabs** — the existing tab strip (`ginv_tab` …) restyled: flat Office-style tabs, active tab with an accent underline, title-case labels in Inter.
- **Panes (`ginv_pane_*`)** — card layout with header rows. Settings becomes two-column rows (label left, fixed-width control column right); the scale `ToggleGroup` becomes horizontal via `flex-direction: row` (LSS owns flex direction per the layout docs). Every scrollable/long pane gets explicit shrink/clip/ellipses so no child can cross a border.
- **Status bar (`ginv_status_*`)** — clipped to the panel width with ellipsis on overflow; all colors converted to ARGB (fixes the invisible-label bug); hero default text fixed to a real string.

## Data Flow

- **Autoscale** — viewport size → clamp(0.75, 2.0) → `uiScale` → propagates to screen contentScale and to popout window sizing at open. A manual preset click sets the scale directly, flips autoscale off, and persists through the existing menu-scale setting storage.
- **SkyBlock status** — detector verdict → screen tick → widget state class → LSS renders the chip; status-bar text updates on transitions.
- **Popout** — user detaches → measure panel physical bounds (with clamps) → window opens at measured size → subtree rebuilt with identical `ginv_*` ids, so scenario queries are agnostic to in-screen vs windowed.
- **View menu interaction** — toggle/preset clicks mutate the same scale/mode state the current Settings controls use; no new state backend.

## Error Handling

- **Theme load failure** — use safe stylesheet access (the `getStylesheetSafe` pattern): a missing or unparseable LSS degrades to the plain MODERN base; the menu never fails to open.
- **Font family missing** — LDLib2 falls back to Minecraft's Unicode font when a glyph/family is unavailable (per the fonts doc): readable, just less pretty.
- **Renderer registration missing** — the SkyBlock widget falls back to a plain text label.
- **Degenerate popout measurement** (zero, absurdly large) — clamp, then legacy BASE fallback, with a log line.
- **Tests never depend on live world state** — the gateway override covers SkyBlock; autoscale is exercised by explicit View-menu interactions.

## Testing Strategy

- **All six existing scenarios stay green** (45+ checks); `ScalePresetScenario` is rewritten to reach presets through the View menu.
- **New checks:**
  - `ginv_topbar`, `ginv_view_menu`, `ginv_skyblock_status` present after menu open.
  - Settings pane bounds contained within the panel bounds (overflow regression guard).
  - Popout window size ≥ popup size at guiScale 2 (shrink regression guard).
  - Status-bar feedback text renders within the panel.
- **Visual pass** — fresh screenshot set reviewed against the original five critiques (shrink, overflow, theme, shapes, Office resemblance).
- **Runs** — host uitest run + `./gradlew build`; CI JDK 25 untouched. Stale `*_error.png` leftovers from old runs cleaned during implementation.

## Open Questions

1. **TTF font-family wiring in LSS** — exact `font:` value form and whether a vanilla font JSON provider registration is required; the official fonts page covers the rendering pipeline, not family registration. Verify against fork source at `/tmp/opencode/ldr`.
2. **Multi-stylesheet precedence** — order in which the style engine applies built-in MODERN vs our sheet at equal specificity; fallback is the subtree-local stylesheet, whose scope the docs guarantee.
3. **`paper`/`latte` light themes** (claimed by earlier research) do not appear in official docs — non-blocking either way; MODERN is the base regardless.
4. **Measurement hook timing** in the popout path — the exact point where contentScale is final for measuring; planner reads the current detach code.
5. **Click-outside/focus behavior** for the View popover in both screen and windowed contexts — planner checks existing dropdown/Selector patterns in the mod.
