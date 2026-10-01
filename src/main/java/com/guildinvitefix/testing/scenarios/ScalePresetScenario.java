package com.guildinvitefix.testing.scenarios;

import com.ginv.data.GinvDataStore;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

/**
 * View-menu scale presets (T13: scale left the Settings tab): with
 * autoscale ON at open the panel must be fitted into the [0.75, 2.0] band
 * without persisting the fit; picking 150% from the {@code #ginv_view_menu}
 * popover must persist {@code uiScale} 1.5, switch autoscale off, rebuild
 * the screen in place, and land back on the previously selected tab pane
 * (the savedTab restore — never a hard-coded Settings pane); picking 100%
 * must restore 1.0.
 *
 * <p>The rebuild is the risky part — it swaps the screen instance mid-
 * scenario, so the waits after each click also prove the rebuilt menu is
 * interactive rather than a zombie.
 */
@LDLRegisterClient(name = "scale_preset", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class ScalePresetScenario implements UIScenario {

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
            GinvDataStore.setUiScale(1.0);
            GinvDataStore.setAutoscale(true);
        })
                .ticks(1)
                .openScreen("gmenu popup", ctx -> new GinvMenuScreen(true))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                // Autoscale at open: the panel renders at a fitted scale in
                // [0.75, 2.0] (width/340 back-solves it) and the fit is never
                // written to settings.json.
                .check("autoscale fitted the panel into the [0.75, 2.0] band", ctx -> {
                    double fit = ctx.el("#ginv_panel").bounds().width() / 340.0;
                    return fit >= 0.73 && fit <= 2.02;
                })
                .check("the fit never persisted over the stored preset",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.0) < 1e-9
                                && GinvDataStore.autoscale())
                .click("#ginv_view_menu")
                .ticks(1)
                .checkVisible("#ginv_view_popover")
                .screenshot("view_menu_open")
                .click("#ginv_scale_150")
                .waitUntil("uiScale is 1.5 and autoscale switched off",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.5) < 1e-9
                                && !GinvDataStore.autoscale())
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                // Previously selected tab pane (the savedTab restore) — the
                // settings pane must not be hard-coded here anymore.
                .checkVisible("#ginv_pane_control")
                .check("uiScale persisted at 1.5",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.5) < 1e-9)
                .screenshot("scale_150")
                .click("#ginv_view_menu")
                .ticks(1)
                .checkVisible("#ginv_view_popover")
                .click("#ginv_scale_100")
                .waitUntil("uiScale restored to 1.0",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.0) < 1e-9)
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkVisible("#ginv_pane_control")
                .check("uiScale persisted at 1.0",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.0) < 1e-9)
                .screenshot("scale_100")
                .teardown("restore scale and autoscale", ctx -> {
                    GinvDataStore.setUiScale(1.0);
                    GinvDataStore.setAutoscale(true);
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }
}
