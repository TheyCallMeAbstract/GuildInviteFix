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
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;

import java.util.List;

/**
 * Right-click blacklist-duration popover over a one-player fixture: the trigger
 * is present and the overlay starts hidden, a button-1 press on the row's
 * blacklist icon reveals it, and choosing "3 days" blacklists that player with
 * a per-player ~now+3d expiry (not the global default). Non-blocking: if the
 * right-button dispatch cannot be driven, the open assertion is the only thing
 * that can fail and the feature itself is covered by the headless suite.
 */
@LDLRegisterClient(name = "blacklist_duration_popover", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class BlacklistDurationPopoverScenario implements UIScenario {

    private static final String ALICE = "Alice";

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
                .click("#ginv_tab_lists")
                .ticks(1)
                .checkVisible("#ginv_pane_lists");

        s.step("install the fixture roster", ctx -> GuildTestGateway.install(List.of(
                new GuildDirectory.Entry(ALICE, new GuildLevels.LevelInfo(42, 0xFFFFFF55))
        ), true));

        s.ticks(2)
                .checkExists("#ginv_blacklist_duration_popover")
                .checkHidden("#ginv_blacklist_duration_popover")
                .checkExists("#ginv_pane_lists .ginv-list-blacklist-trigger")
                .screenshot("blacklist_popover_hidden")
                .step("right-click Alice's blacklist trigger", ctx -> {
                    ElementBounds b = ctx.el("#ginv_pane_lists .ginv-list-blacklist-trigger").bounds();
                    ctx.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_RIGHT);
                    ctx.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_RIGHT);
                })
                .ticks(1)
                .checkVisible("#ginv_blacklist_duration_popover")
                .checkVisible("#ginv_blacklist_duration_days_3")
                .screenshot("blacklist_popover_open")
                // The option applies-and-hides on MOUSE_DOWN, so a helper
                // .click() would fail resolving the target for its release
                // half. Capture the bounds once and drive both edges here.
                .step("choose 3 days", ctx -> {
                    ElementBounds b = ctx.el("#ginv_blacklist_duration_days_3").bounds();
                    float cx = b.centerX();
                    float cy = b.centerY();
                    ctx.input().mouseDown(cx, cy, Keys.MOUSE_LEFT);
                    ctx.input().mouseUp(cx, cy, Keys.MOUSE_LEFT);
                })
                .ticks(1)
                .checkHidden("#ginv_blacklist_duration_popover")
                .check("choosing 3 days blacklists Alice with a 3-day expiry", ctx -> {
                    GinvDataStore.PlayerSnapshot snap = GinvDataStore.snapshot(ALICE);
                    if (snap == null || snap.listState() != GinvDataStore.ListState.BLACKLIST) return false;
                    long target = System.currentTimeMillis() + ListDuration.DAYS_3.millis();
                    return Math.abs(snap.listExpiresAt() - target) < 120_000L;
                })
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
}
