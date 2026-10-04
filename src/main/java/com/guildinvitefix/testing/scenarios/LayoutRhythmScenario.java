package com.guildinvitefix.testing.scenarios;

import com.ginv.data.GinvDataStore;
import com.ginv.testing.GuildTestGateway;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.ginv.utils.GuildDirectory;
import com.ginv.utils.GuildLevels;
import com.guildinvitefix.testing.layout.LayoutAssert;
import com.guildinvitefix.testing.layout.LayoutAssert.Box;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementRef;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import java.util.List;

/**
 * Front-end geometry coverage: the player table must hold a professional
 * rhythm — uniform row heights, columns aligned across rows, action buttons the
 * same size and right edge, and the name-to-level gap equal to the declared
 * 3u track gap — at every menu scale (100/125/150/200%).
 *
 * <p>Uses {@link LayoutAssert} over live {@code ElementBounds}; the table's
 * per-column cell classes ({@code .ginv-cell-<id>}) make each column
 * addressable. The fixture roster guarantees four rows — one of them a long
 * name — so "across rows" assertions are meaningful and prove the name column
 * stays content-independent (a long name must not widen its cell, push the
 * columns, or raise the body's horizontal scrollbar). The final check proves the
 * rhythm scales linearly with the panel (100% → 200% doubles the gap).
 */
@LDLRegisterClient(name = "layout_rhythm", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class LayoutRhythmScenario implements UIScenario {

    private static final int[] PRESETS = {100, 125, 150, 200};
    /** The name column's track gap in authored units (matches listColumns). */
    private static final float DECLARED_GAP_U = 3f;
    private static final float DESIGN_WIDTH = 340f;

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(50).tags("ui", "layout").requiresWorld(true).guiScale(2);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("install a 3-player roster and pin scale 100%", ctx -> {
            GinvMenuWindow stale = GinvMenuWindow.active();
            if (stale != null) stale.onCloseRequested();
            if (ctx.screen() != null) ctx.mc().setScreen(null);
            GinvDataStore.setUiScale(1.0);
            GinvDataStore.setAutoscale(false);
            GinvDataStore.setListsLevelFilter(null, null);
            GinvDataStore.setQueueAutoRun(false);
            GuildTestGateway.install(List.of(
                    new GuildDirectory.Entry("Alice", new GuildLevels.LevelInfo(42, 0xFF55FF55)),
                    new GuildDirectory.Entry("Bob", new GuildLevels.LevelInfo(15, 0xFF55FFFF)),
                    new GuildDirectory.Entry("Carol", null),
                    // A name well past the name column: the cell must clip it,
                    // not grow, push the fixed columns or raise a horizontal bar.
                    new GuildDirectory.Entry("DianasaurusRexington", null)), true);
            // One whitelisted and one blacklisted row so the active green/red
            // fills (and the icon-on-fill contrast) show in every capture.
            GinvDataStore.setListState("Alice", GinvDataStore.ListState.WHITELIST);
            GinvDataStore.setListState("Bob", GinvDataStore.ListState.BLACKLIST);
        })
                .ticks(1)
                .openScreen("gmenu", ctx -> new GinvMenuScreen())
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .click("#ginv_tab_lists")
                .ticks(1)
                .checkVisible("#ginv_pane_lists");

        for (int preset : PRESETS) {
            if (preset != 100) {
                // Switch preset through the View popover (the same path a user
                // takes) and wait for the in-place screen rebuild.
                s.click("#ginv_view_menu")
                        .ticks(1)
                        .checkVisible("#ginv_view_popover")
                        .click("#ginv_scale_" + preset)
                        .waitUntil("uiScale is " + preset + "%",
                                ctx -> Math.abs(GinvDataStore.uiScale() - preset / 100.0) < 1e-9)
                        .awaitScreen(GinvMenuScreen.class)
                        .awaitModularUI();
            }
            // savedTab restores Lists, but be explicit so the queries are stable.
            s.click("#ginv_tab_lists").ticks(1).checkVisible("#ginv_pane_lists");

            final int p = preset;
            s.check("lists shows at least the 4 fixture rows at " + p + "%",
                            ctx -> visibleRows(ctx).size() >= 4)
                    .check("row boxes are uniform, symmetric and equal-width at " + p + "%",
                            ctx -> {
                                List<Box> rows = boxes(ctx, ".ginv-table-row");
                                return rows.size() >= 3
                                        && LayoutAssert.uniform(rows.stream().map(Box::h).toList())
                                        && LayoutAssert.alignedLeft(rows)
                                        && LayoutAssert.alignedRight(rows)
                                        && LayoutAssert.sameWidth(rows);
                            })
                    .check("name column aligns across rows at " + p + "%",
                            ctx -> alignedColumn(ctx, ".ginv-cell-name"))
                    .check("action buttons share a size and right edge at " + p + "%",
                            ctx -> {
                                List<Box> buttons = boxes(ctx, ".ginv-cell-remove");
                                return buttons.size() >= 3
                                        && LayoutAssert.sameSize(buttons)
                                        && LayoutAssert.alignedRight(buttons);
                            })
                    // The name column is a fixed 1fr track regardless of what a
                    // row contains, and the body never scrolls sideways: both
                    // used to break with a long name (content-sized track ->
                    // overflowing rows -> a horizontal scrollbar).
                    .check("a long name does not widen its column at " + p + "%",
                            ctx -> {
                                List<Box> names = boxes(ctx, ".ginv-cell-name");
                                return names.size() >= 4
                                        && LayoutAssert.alignedLeft(names)
                                        && LayoutAssert.sameWidth(names);
                            })
                    .check("rows never spill past the table frame at " + p + "%",
                            ctx -> {
                                List<ElementRef> frames = ctx.query("#ginv_pane_lists .ginv-list-frame")
                                        .visible().list().stream()
                                        .filter(LayoutRhythmScenario::laidOut)
                                        .toList();
                                if (frames.isEmpty()) return false;
                                Box frame = Box.of(frames.get(0).bounds());
                                List<Box> rows = boxes(ctx, ".ginv-table-row");
                                return !rows.isEmpty()
                                        && rows.stream().allMatch(row -> row.right() <= frame.right() + 1.5f);
                            })
                    .check("the table body shows no horizontal scrollbar at " + p + "%",
                            ctx -> ctx.query("#ginv_pane_lists .ginv-table-body .__scroller_view_horizontal-scroller__")
                                    .list().stream().allMatch(ref -> ref.bounds().isEmpty()))
                    .check("first row sits inside the page card at " + p + "%",
                            ctx -> {
                                List<ElementRef> rows = visibleRows(ctx);
                                if (rows.isEmpty()) return false;
                                Box pane = Box.of(ctx.el("#ginv_pane_lists").bounds());
                                return LayoutAssert.contains(pane, Box.of(rows.get(0).bounds()), 1.5f);
                            })
                    .check("name-to-level gap equals the declared 3u rhythm at " + p + "%",
                            ctx -> columnGapMatches(ctx, p))
                    .screenshot("lists_" + p);
        }

        // Deep cross-scale check: the declared rhythm is linear in the panel,
        // so the gap must double from the 100% capture to the 200% capture.
        s.check("3u rhythm doubles from 100% to 200%", ctx -> {
            Object base = ctx.get("gap_100");
            Object double_ = ctx.get("gap_200");
            if (!(base instanceof Float g100) || !(double_ instanceof Float g200) || g100 <= 0f) {
                return false;
            }
            return Math.abs((g200 / g100) - 2.0f) <= 0.15f;
        })
                // Leave the shared static savedTab on Control so later scenarios
                // open on the pane they expect.
                .click("#ginv_tab_control")
                .ticks(1)
                .teardown("restore roster and scale", ctx -> {
                    GuildTestGateway.reset();
                    GinvDataStore.setListState("Alice", GinvDataStore.ListState.NONE);
                    GinvDataStore.setListState("Bob", GinvDataStore.ListState.NONE);
                    GinvDataStore.setUiScale(1.0);
                    GinvDataStore.setAutoscale(false);
                    GinvDataStore.setListsLevelFilter(null, null);
                    GinvDataStore.setQueueAutoRun(false);
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }

    private static List<ElementRef> visibleRows(TestContext ctx) {
        return ctx.query(".ginv-table-row").visible().list().stream()
                .filter(LayoutRhythmScenario::laidOut)
                .toList();
    }

    private static List<Box> boxes(TestContext ctx, String selector) {
        return ctx.query(selector).visible().list().stream()
                .filter(LayoutRhythmScenario::laidOut)
                .map(ref -> Box.of(ref.bounds()))
                .toList();
    }

    /**
     * {@code ElementQuery.visible()} only checks the element's <em>own</em>
     * display flag, so rows inside a hidden page still match with a zero-size
     * box. Require real laid-out area.
     */
    private static boolean laidOut(ElementRef ref) {
        return !ref.bounds().isEmpty();
    }

    /** Every visible instance of a column class shares a left edge and width. */
    private static boolean alignedColumn(TestContext ctx, String selector) {
        List<Box> column = boxes(ctx, selector);
        return column.size() >= 3
                && LayoutAssert.alignedLeft(column)
                && LayoutAssert.sameWidth(column);
    }

    /**
     * The gap between the player-name label and the guild-level badge that hugs
     * it (both inside the same name cell) must equal the declared 3u inline gap
     * scaled by the current panel scale ({@code panelWidth / 340}), and the
     * measured gap is recorded for the cross-scale check.
     */
    private static boolean columnGapMatches(TestContext ctx, int preset) {
        List<Box> names = boxes(ctx, ".ginv-name");
        List<Box> badges = boxes(ctx, ".ginv-level");
        if (names.size() < 4 || badges.size() < 4) return false;
        float gap = LayoutAssert.hGap(names.get(0), badges.get(0));
        float scale = ctx.el("#ginv_panel").bounds().width() / DESIGN_WIDTH;
        float expected = DECLARED_GAP_U * scale;
        ctx.put("gap_" + preset, gap);
        return Math.abs(gap - expected) <= 1.0f;
    }
}
