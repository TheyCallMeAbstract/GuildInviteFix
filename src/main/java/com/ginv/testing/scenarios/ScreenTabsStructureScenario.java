package com.ginv.testing.scenarios;

import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

/**
 * Opens the menu as a full {@code screen} and walks every tab: each tab
 * click must reveal its pane and hide the other three, the panel must be
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
        })
                .ticks(1)
                .openScreen("gmenu screen", ctx -> new GinvMenuScreen(false))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkCount(".ginv_tab", 4)
                .checkCount(".ginv_pane", 4)
                .check("the panel is centered in the screen viewport", ctx -> {
                    var bounds = ctx.el("#ginv_panel").bounds();
                    var window = ctx.mc().getWindow();
                    return !bounds.isEmpty()
                            && bounds.isCenterOnScreen(window.getGuiScaledWidth(),
                                    window.getGuiScaledHeight());
                })
                .screenshot("screen_open");

        String[] slugs = {"control", "lists", "monitor", "settings"};
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

        s.closeScreen()
                .teardown("close anything left over", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) window.onCloseRequested();
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }
}
