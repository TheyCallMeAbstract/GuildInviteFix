package com.guildinvitefix.testing.scenarios;

import com.ginv.command.GinvCommand;
import com.ginv.data.GinvDataStore;
import com.ginv.data.ListDuration;
import com.ginv.testing.GuildTestGateway;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.ginv.utils.GuildDirectory;
import com.ginv.utils.GuildLevels;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementRef;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression guard for "the Lists tab grows the whole menu once it holds many
 * players": LDLib2's {@code TabView} content slot has {@code flex-grow:1} but
 * no {@code flex-shrink} (its default is 0), so a long list used to stretch the
 * slot — and the page sized {@code height:100%} against it — until the panel
 * towered past the viewport and its tabs left the screen.
 *
 * <p>The scenario pins the panel / page / table-body heights against a short
 * (4-player) baseline, then installs 60 players and asserts those heights are
 * unchanged, the vertical scrollbar has appeared, the rows overflow the fixed
 * viewport, and the wheel actually scrolls them. A short list must not show a
 * scrollbar.
 */
@LDLRegisterClient(name = "lists_overflow", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class ListsOverflowScenario implements UIScenario {

    private static final String PANEL = "#ginv_panel";
    private static final String PAGE = "#ginv_pane_lists";
    private static final String BODY = "#ginv_pane_lists .ginv-table-body";
    private static final String VIEWPORT = "#ginv_pane_lists .__scroller_view_view-port__";
    private static final String V_SCROLLER = "#ginv_pane_lists .__scroller_view_vertical-scroller__";
    private static final String ROWS = PAGE + " .ginv-table-row";

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
            GinvCommand.clearTargets();
            if (GinvCommand.isFrozen()) GinvCommand.toggleFreeze();
            GinvDataStore.setWhitelistOnly(false);
            GinvDataStore.setBlacklistTtlMs(ListDuration.DEFAULT_MS);
            GinvDataStore.setListsLevelFilter(null, null);
            GinvDataStore.setQueueAutoRun(false);
            for (String name : List.copyOf(GinvDataStore.trackedNames())) {
                GinvDataStore.removePlayer(name);
            }
            GuildTestGateway.reset();
        })
                .ticks(1)
                .openScreen("gmenu", ctx -> new GinvMenuScreen())
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                // Select Lists while the panel is still small; a ballooned panel
                // would push the tab out of the viewport.
                .click("#ginv_tab_lists")
                .ticks(1)
                .checkVisible(PAGE);

        // Baseline: a short list fits without a scrollbar and fixes the heights
        // the long list must not exceed.
        s.step("install a 4-player roster", ctx -> GuildTestGateway.install(roster(4), true));
        s.ticks(2)
                .check("all four fixture players are listed", ctx -> rows(ctx) == 4)
                .check("a short list needs no vertical scrollbar", ctx -> !ctx.el(V_SCROLLER).isVisible())
                .step("record the bounded panel/page/body heights", ctx -> {
                    ctx.put("panelH", ctx.el(PANEL).bounds().height());
                    ctx.put("pageH", ctx.el(PAGE).bounds().height());
                    ctx.put("bodyH", ctx.el(BODY).bounds().height());
                    ctx.put("tabY", ctx.el("#ginv_tab_lists").bounds().y());
                });

        // Long list: must stay exactly as tall as the short one and scroll inside
        // the table body instead of stretching the menu.
        s.step("install a 60-player roster", ctx -> GuildTestGateway.install(roster(60), true));
        s.ticks(2)
                .check("all 60 players are listed", ctx -> rows(ctx) == 60)
                .check("the panel keeps its bounded height", ctx -> sameHeight(ctx, PANEL, "panelH"))
                .check("the page keeps its bounded height", ctx -> sameHeight(ctx, PAGE, "pageH"))
                .check("the table body keeps its bounded height", ctx -> sameHeight(ctx, BODY, "bodyH"))
                .check("the tab bar does not move off screen", ctx ->
                        Math.abs(ctx.el("#ginv_tab_lists").bounds().y() - (Float) ctx.get("tabY")) <= 1f)
                .check("the vertical scrollbar appears", ctx -> {
                    ElementRef bar = ctx.el(V_SCROLLER);
                    return bar.isVisible() && bar.bounds().width() > 0f;
                })
                .check("the rows overflow the fixed viewport", ctx -> {
                    float vpBottom = ctx.el(VIEWPORT).bounds().bottom();
                    List<ElementRef> rows = ctx.query(ROWS).list();
                    return !rows.isEmpty() && rows.get(rows.size() - 1).bounds().bottom() > vpBottom + 1f;
                })
                .screenshot("lists_overflow")
                .step("record the first-row position", ctx ->
                        ctx.put("rowY", ctx.query(ROWS).nth(0).one().bounds().y()))
                .scroll(BODY, -8)
                .ticks(1)
                .check("wheeling scrolls the rows under the header", ctx ->
                        ctx.query(ROWS).nth(0).one().bounds().y() < (Float) ctx.get("rowY") - 1f)
                .screenshot("lists_overflow_scrolled")
                // Leave the shared static savedTab on Control so later scenarios
                // (e.g. scale_preset) reopen on the pane they expect.
                .click("#ginv_tab_control")
                .ticks(1)
                .teardown("restore state", ctx -> {
                    GuildTestGateway.reset();
                    GinvCommand.clearTargets();
                    if (GinvCommand.isFrozen()) GinvCommand.toggleFreeze();
                    GinvDataStore.setWhitelistOnly(false);
                    GinvDataStore.setBlacklistTtlMs(ListDuration.DEFAULT_MS);
                    GinvDataStore.setListsLevelFilter(null, null);
                    GinvDataStore.setQueueAutoRun(false);
                    for (String name : List.copyOf(GinvDataStore.trackedNames())) {
                        GinvDataStore.removePlayer(name);
                    }
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }

    private static List<GuildDirectory.Entry> roster(int n) {
        List<GuildDirectory.Entry> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new GuildDirectory.Entry("Player" + i,
                    new GuildLevels.LevelInfo(1 + i, 0xFFFFFF55)));
        }
        return out;
    }

    /** Lists rows with a real laid-out area (hidden panes match a zero box). */
    private static int rows(TestContext ctx) {
        return (int) ctx.query(ROWS).list().stream()
                .filter(ref -> !ref.bounds().isEmpty())
                .count();
    }

    private static boolean sameHeight(TestContext ctx, String selector, String key) {
        return Math.abs(ctx.el(selector).bounds().height() - (Float) ctx.get(key)) <= 0.5f;
    }
}
