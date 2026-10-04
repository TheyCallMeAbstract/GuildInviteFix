package com.guildinvitefix.testing.scenarios;

import com.ginv.data.GinvDataStore;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.guildinvitefix.testing.layout.LayoutAssert;
import com.guildinvitefix.testing.layout.LayoutAssert.Box;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ElementRef;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opens the in-game menu popup and walks every tab: each tab
 * click must reveal its pane and hide the other two, the panel must be
 * centered in the screen viewport, and each tab state is screenshotted for
 * the visual record.
 *
 * <p>Covers the layout/structure half of the suite: tab ids
 * ({@code ginv_tab_<slug>}), pane ids ({@code ginv_pane_<slug>}), the
 * {@code ginv_tab}/{@code ginv_pane} classes, and the TabView
 * select/hide contract.
 */
@LDLRegisterClient(name = "screen_tabs_structure", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class ScreenTabsStructureScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(50).tags("ui", "menu").requiresWorld(true).guiScale(2);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("start from a clean slate", ctx -> {
            GinvMenuWindow stale = GinvMenuWindow.active();
            if (stale != null) stale.onCloseRequested();
            if (ctx.screen() != null) ctx.mc().setScreen(null);
            GinvDataStore.setListsLevelFilter(null, null);
            GinvDataStore.setQueueAutoRun(false);
        })
                .ticks(1)
                .openScreen("gmenu", ctx -> new GinvMenuScreen())
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkCount(".ginv_tab", 3)
                .checkCount(".ginv_pane", 3)
                .check("the panel is centered in the screen viewport", ctx -> {
                    var bounds = ctx.el("#ginv_panel").bounds();
                    var window = ctx.mc().getWindow();
                    return !bounds.isEmpty()
                            && bounds.isCenterOnScreen(window.getGuiScaledWidth(),
                                    window.getGuiScaledHeight());
                })
                // Regression guards for the top-bar polish: the SkyBlock chip is
                // centred over the bar (not parked in the right cluster), and
                // button text is vertically centred inside its button (Taffy's
                // default top-alignment otherwise creeps as u() scales the font).
                .check("the SkyBlock chip is centered in the top bar", ctx -> {
                    var bar = ctx.el("#ginv_topbar").bounds();
                    var chip = ctx.el("#ginv_skyblock_status").bounds();
                    return !chip.isEmpty()
                            && Math.abs(chip.centerX() - bar.centerX()) <= 1.0f;
                })
                .check("button text is vertically centered in its button", ctx -> {
                    var button = ctx.el("#ginv_view_menu").bounds();
                    var texts = ctx.query("#ginv_view_menu .__button_text__").visible().list();
                    if (texts.isEmpty()) return false;
                    return Math.abs(texts.get(0).bounds().centerY() - button.centerY()) <= 1.0f;
                })
                .screenshot("screen_open");

        String[] slugs = {"control", "lists", "settings"};
        for (String slug : slugs) {
            s.click("#ginv_tab_" + slug)
                    .ticks(1)
                    .checkVisible("#ginv_pane_" + slug);
            for (String other : slugs) {
                if (!other.equals(slug)) {
                    s.checkHidden("#ginv_pane_" + other);
                }
            }
            s.screenshot("tab_" + slug);
        }

        // Settings form (selector-only classes, no sheet rules): the 4:6 grid
        // must give every label one left edge and every control column one left
        // edge, never let a label cross into its control, and keep the two
        // flex-grown delay fields symmetric. Settings is still open from the
        // slug walk above.
        s.check("settings labels share one column", ctx -> {
            List<Box> labels = boxes(ctx, ".ginv-setting-label");
            return labels.size() >= 3 && LayoutAssert.alignedLeft(labels, 0.5f);
        })
                .check("settings controls share one column", ctx -> {
                    List<Box> controls = boxes(ctx, ".ginv-setting-control");
                    return controls.size() >= 3 && LayoutAssert.alignedLeft(controls, 0.5f);
                })
                .check("settings label never overlaps its control", ctx -> {
                    List<Box> rows = boxes(ctx, ".ginv-setting-row");
                    List<Box> labels = boxes(ctx, ".ginv-setting-label");
                    List<Box> controls = boxes(ctx, ".ginv-setting-control");
                    if (rows.size() != labels.size() || labels.size() != controls.size()) {
                        return false;
                    }
                    for (int i = 0; i < rows.size(); i++) {
                        if (labels.get(i).right() > controls.get(i).x() + 0.5f) {
                            return false;
                        }
                    }
                    return !rows.isEmpty();
                })
                .check("the delay fields are equal width", ctx -> {
                    Box form = Box.of(ctx.el("#ginv_settings_form").bounds());
                    List<Box> fields = ctx.query().type(TextField.class).list().stream()
                            .filter(ref -> !ref.bounds().isEmpty())
                            .map(ref -> Box.of(ref.bounds()))
                            .filter(field -> LayoutAssert.contains(form, field, 0.5f))
                            .toList();
                    return fields.size() == 2 && LayoutAssert.sameWidth(fields, 0.5f);
                });

        // T14 section organization: the form is grouped under three
        // ginv-section headers (INVITES / FILTERING / APPEARANCE) in reading
        // order, and the row labels carry the normalized copy.
        s.check("settings has the three section headers in order", ctx ->
                sectionHeaders(ctx).stream().map(ElementRef::text).toList()
                        .equals(List.of("INVITES", "FILTERING", "APPEARANCE")))
                .check("settings labels use the standard copy", ctx -> {
                    Set<String> labels = ctx.query("#ginv_pane_settings .ginv-setting-label")
                            .visible().list().stream()
                            .map(ElementRef::text)
                            .collect(Collectors.toSet());
                    return labels.equals(Set.of("Invite delay (ms):", "Keep queue running:",
                            "Whitelist only:", "Blacklist duration:", "Theme:"));
                });

        // T13 containment: with Settings open (last slug walked) the pane and
        // the status-bar pieces must live inside the panel — the office
        // rework re-parented them, and a stray absolute position would put
        // them behind/next to the panel instead of in it.
        s.check("the settings pane is contained in the panel", ctx -> {
            var pane = ctx.el("#ginv_pane_settings").bounds();
            var panel = ctx.el("#ginv_panel").bounds();
            return contains(panel, pane);
        })
                .check("the status bar is contained in the panel", ctx -> {
                    var bar = ctx.el("#ginv_statusbar").bounds();
                    var panel = ctx.el("#ginv_panel").bounds();
                    return contains(panel, bar);
                })
                // Containment of the page document: the tab selector band and the
                // status footer share the same left/right edges, so header, body
                // and footer read as one continuous frame rather than three
                // separately-aligned boxes.
                .check("the tab header and status footer share one document frame", ctx -> {
                    var header = ctx.el(".__tab-view_tab_header_container__").bounds();
                    var footer = ctx.el("#ginv_statusbar").bounds();
                    float tolerance = 0.5f;
                    return !header.isEmpty() && !footer.isEmpty()
                            && Math.abs(header.x() - footer.x()) <= tolerance
                            && Math.abs(header.right() - footer.right()) <= tolerance;
                })
                .check("status text stays within the panel", ctx -> {
                    var bounds = ctx.el("#ginv_status").bounds();
                    var panel = ctx.el("#ginv_panel").bounds();
                    return bounds.isEmpty() || contains(panel, bounds);
                })
                .check("feedback text stays within the panel", ctx -> {
                    var bounds = ctx.el("#ginv_feedback").bounds();
                    var panel = ctx.el("#ginv_panel").bounds();
                    return bounds.isEmpty() || contains(panel, bounds);
                })
                .closeScreen()
                .teardown("close anything left over", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) window.onCloseRequested();
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                    GinvDataStore.setListsLevelFilter(null, null);
                    GinvDataStore.setQueueAutoRun(false);
                });
    }

    /** Visible {@code ginv-section} headers inside the Settings pane, top-down. */
    private static List<ElementRef> sectionHeaders(TestContext ctx) {
        return ctx.query("#ginv_pane_settings .ginv-section").visible().list().stream()
                .filter(ref -> ref.text() != null && !ref.text().isBlank())
                .sorted(Comparator.comparingDouble(ref -> ref.bounds().y()))
                .toList();
    }

    /** Laid-out boxes for a selector (hidden elements match with a zero box). */
    private static List<Box> boxes(TestContext ctx, String selector) {
        return ctx.query(selector).list().stream()
                .filter(ref -> !ref.bounds().isEmpty())
                .map(ref -> Box.of(ref.bounds()))
                .toList();
    }

    /** {@code inner} inside {@code outer}, 0.5 px of slack for rounding. */
    private static boolean contains(ElementBounds outer, ElementBounds inner) {
        float tolerance = 0.5f;
        return inner.x() >= outer.x() - tolerance
                && inner.y() >= outer.y() - tolerance
                && inner.right() <= outer.right() + tolerance
                && inner.bottom() <= outer.bottom() + tolerance;
    }
}
