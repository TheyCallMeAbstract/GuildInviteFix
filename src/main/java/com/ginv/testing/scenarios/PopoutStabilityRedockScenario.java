package com.ginv.testing.scenarios;

import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

/**
 * The pop-out regression scenario: pop the menu into an OS window, keep it
 * alive for 12 ticks and assert the instance identity never changes (a
 * rebuild storm replaces {@link GinvMenuWindow#active()} — that is the bug
 * this catches), capture the window's own surface, then click re-dock
 * through the window's real GLFW dispatch path and assert the menu comes
 * back as a screen.
 *
 * <p>If the window never opens, {@code waitUntil} times out with a
 * failure screenshot of the in-game screen — the feedback label prints the
 * computed open dimensions, which localizes a size-refusal failure.
 */
@LDLRegisterClient(name = "popout_stability_redock", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class PopoutStabilityRedockScenario implements UIScenario {

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
                .openScreen("gmenu popup", ctx -> new GinvMenuScreen(true))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .awaitElement("#ginv_popout")
                .screenshot("before_popout")
                .click("#ginv_popout")
                .waitUntil("the pop-out window opened", ctx -> GinvMenuWindow.active() != null)
                .step("remember the window instance", ctx -> ctx.put("popout", GinvMenuWindow.active()))
                .ticks(12)
                .check("the same window instance survived 12 ticks (no rebuild storm)",
                        ctx -> ctx.<GinvMenuWindow>get("popout") == GinvMenuWindow.active())
                .step("capture the window surface", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    ctx.screenshotSurface("popout_window", window.surface());
                })
                // Re-dock through the window's own input queue — one primitive
                // per step, as the queue drains once per frame.
                .step("aim at re-dock", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    var target = ctx.in(window.getModularUI(), "#ginv_redock").one();
                    ctx.input(window).moveTo(target.element());
                })
                .step("press re-dock", ctx -> ctx.input(GinvMenuWindow.active()).mouseDown(0))
                .step("release re-dock", ctx -> ctx.input(GinvMenuWindow.active()).mouseUp(0))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkExists("#ginv_panel")
                .screenshot("redocked")
                .teardown("close window and screen", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) window.onCloseRequested();
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }
}
