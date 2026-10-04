package com.guildinvitefix.testing.scenarios;

import com.ginv.data.GinvDataStore;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;

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
            GinvDataStore.setListsLevelFilter(null, null);
            GinvDataStore.setQueueAutoRun(false);
        })
                .ticks(1)
                .openScreen("gmenu", ctx -> new GinvMenuScreen())
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .awaitElement("#ginv_popout")
                .screenshot("before_popout")
                .hover("#ginv_popout")
                // Button.onClick fires on MOUSE_DOWN (onMouseDown → popOut →
                // mc.setScreen(null)), so click()'s release step would re-resolve
                // "#ginv_popout" against a screen that no longer exists. Press at
                // the element's centre and release at the same remembered point —
                // mouseUp is a no-op once the screen is gone, but still lands
                // correctly on the button if the pop-out was refused.
                .step("press #ginv_popout", ctx -> {
                    ElementBounds bounds = ctx.el("#ginv_popout").bounds();
                    ctx.put("popout_pt", bounds);
                    ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                })
                .step("release #ginv_popout", ctx -> {
                    ElementBounds bounds = ctx.get("popout_pt");
                    ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                })
                .waitUntil("the pop-out window opened", ctx -> GinvMenuWindow.active() != null)
                .step("remember the window instance", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    ctx.put("popout", window);
                    // The window opens at its reflow base, so this ratio is the
                    // one the resize constraint locks to.
                    var os = window.window();
                    ctx.put("open_ratio", os.getWindowWidth() / (double) os.getWindowHeight());
                })
                .ticks(12)
                .check("the same window instance survived 12 ticks (no rebuild storm)",
                        ctx -> ctx.<GinvMenuWindow>get("popout") == GinvMenuWindow.active())
                .check("the live window keeps the opening aspect and never drops below the floor",
                        ctx -> {
                            GinvMenuWindow window = GinvMenuWindow.active();
                            var os = window.window();
                            double base = ctx.<Double>get("open_ratio");
                            double ratio = os.getWindowWidth() / (double) os.getWindowHeight();
                            return Math.abs(ratio - base) <= 0.03
                                    && os.getWindowWidth() >= 200 && os.getWindowHeight() >= 150;
                        })
                .step("capture the window surface", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    ctx.screenshotSurface("popout_window", window.surface());
                })
                // Re-dock through the window's own input queue — one primitive
                // per step, as the queue drains once per frame. The press is the
                // whole gesture: Button.onClick fires on MOUSE_DOWN, which
                // re-docks and closes the window, and WindowInput refuses a
                // closed window ("nothing would drain its events") — so there is
                // no release left to post.
                .step("aim at re-dock", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    var target = ctx.in(window.getModularUI(), "#ginv_redock").one();
                    ctx.input(window).moveTo(target.element());
                })
                .step("press re-dock", ctx -> ctx.input(GinvMenuWindow.active()).mouseDown(0))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkExists("#ginv_panel")
                .screenshot("redocked")
                .teardown("close window and screen", ctx -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) window.onCloseRequested();
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                    GinvDataStore.setListsLevelFilter(null, null);
                    GinvDataStore.setQueueAutoRun(false);
                });
    }
}
