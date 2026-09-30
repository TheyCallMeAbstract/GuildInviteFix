package com.ginv.testing.scenarios;

import com.ginv.data.GinvDataStore;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

/**
 * Settings-tab scale presets: clicking 150% must persist {@code uiScale}
 * 1.5, rebuild the screen in place, and land back on the Settings tab
 * (the savedTab restore); clicking 100% must restore 1.0.
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
        })
                .ticks(1)
                .openScreen("gmenu popup", ctx -> new GinvMenuScreen(true))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .click("#ginv_tab_settings")
                .ticks(1)
                .checkVisible("#ginv_pane_settings")
                .screenshot("settings_tab")
                .click("#ginv_scale_150")
                .waitUntil("uiScale is 1.5", ctx -> Math.abs(GinvDataStore.uiScale() - 1.5) < 1e-9)
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkVisible("#ginv_pane_settings")
                .check("uiScale persisted at 1.5",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.5) < 1e-9)
                .screenshot("scale_150")
                .click("#ginv_scale_100")
                .waitUntil("uiScale restored to 1.0",
                        ctx -> Math.abs(GinvDataStore.uiScale() - 1.0) < 1e-9)
                .teardown("restore scale", ctx -> {
                    GinvDataStore.setUiScale(1.0);
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }
}
