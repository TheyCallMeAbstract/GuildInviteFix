package com.guildinvitefix.testing.scenarios;

import com.ginv.command.GmenuCommand;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

/**
 * The menu-open regression scenario: drives the exact path {@code /gmenu}
 * takes (deferred open armed by the command, applied on the next
 * end-client-tick) and asserts the popup comes up with a real, centered,
 * non-trivial element tree.
 *
 * <p>If this fails at {@code awaitScreen}, the deferred applier never opened
 * the menu (stale window focus, chat-screen clobber, exception in the
 * screen constructor) — that is the "menu unavailable" class of bug. The
 * element-tree and bounds checks catch a screen that opens but never lays
 * out (the rebuild-storm class of bug).
 */
@LDLRegisterClient(name = "menu_open_regression", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class MenuOpenRegressionScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(50).tags("ui", "menu").requiresWorld(true).guiScale(2);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("start from a clean slate", ctx -> {
            // A stale window would make focusExisting() swallow the request —
            // that is one of the suspected regression paths.
            GinvMenuWindow stale = GinvMenuWindow.active();
            if (stale != null) stale.onCloseRequested();
            if (ctx.screen() != null) ctx.mc().setScreen(null);
        })
                .ticks(1)
                // The real /gmenu path: arm the deferred open, let the
                // end-client-tick applier perform it.
                .step("arm the deferred /gmenu popup open", ctx -> GmenuCommand.requestOpen(true))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .awaitElement("#ginv_panel")
                .checkExists("#ginv_panel")
                .checkTextContains("#ginv_banner", "RUNNING")
                .checkCount(".ginv_tab", 4)
                .check("the panel rendered a real element tree",
                        ctx -> ctx.requireUI().getAllElements().size() > 30)
                .check("the panel has finite, centered bounds", ctx -> {
                    var bounds = ctx.el("#ginv_panel").bounds();
                    var window = ctx.mc().getWindow();
                    return !bounds.isEmpty()
                            && Float.isFinite(bounds.x())
                            && Float.isFinite(bounds.y())
                            && Float.isFinite(bounds.width())
                            && Float.isFinite(bounds.height())
                            && bounds.isCenterOnScreen(window.getGuiScaledWidth(),
                                    window.getGuiScaledHeight());
                })
                .screenshot("menu_open_popup")
                .closeScreen()
                .teardown("close anything left over", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) window.onCloseRequested();
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }
}
