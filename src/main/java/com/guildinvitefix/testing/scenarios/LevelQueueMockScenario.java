package com.guildinvitefix.testing.scenarios;

import com.ginv.command.GinvCommand;
import com.ginv.data.GinvDataStore;
import com.ginv.testing.GuildTestGateway;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.ginv.utils.GuildDirectory;
import com.ginv.utils.GuildLevels;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import java.util.List;

/**
 * Queue-by-level over the mock roster: first asserts the pre-gateway
 * precondition (disabled + "SkyBlock only."), then installs fixtures
 * (Alice 42, Bob 15, Carol no-level, Dave 60, SkyBlock=true) and queues
 * {@code ≥ 40} — expecting 2 queued, 1 no-level, 1 below, and exactly
 * Alice then Dave recorded as sent, in roster order.
 */
@LDLRegisterClient(name = "level_queue_mock", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class LevelQueueMockScenario implements UIScenario {

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
            GinvDataStore.setDelays(50, 50);
            // No gateway yet: the level queue must show its precondition state.
            GuildTestGateway.reset();
        })
                .ticks(1)
                .openScreen("gmenu", ctx -> new GinvMenuScreen())
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                // Precondition: not SkyBlock → the range caption is hidden (the
                // top-bar chip carries the verdict) and the button is disabled.
                .checkHidden("#ginv_range")
                .step("install the fixture roster", ctx -> GuildTestGateway.install(List.of(
                        new GuildDirectory.Entry("Alice", new GuildLevels.LevelInfo(42, 0xFFFFFF55)),
                        new GuildDirectory.Entry("Bob", new GuildLevels.LevelInfo(15, 0xFFFFFF55)),
                        new GuildDirectory.Entry("Carol", null),
                        new GuildDirectory.Entry("Dave", new GuildLevels.LevelInfo(60, 0xFFFFFF55))
                ), true))
                // Let screenTick re-evaluate the preconditions and the range caption.
                .ticks(3)
                .checkTextContains("#ginv_range", "tab: 15–60")
                .typeInto("#ginv_level_input", "40")
                .click("#ginv_queue_level")
                .checkText("#ginv_feedback", "Queued 2 · 1 no-level · 1 below")
                .ticks(1)
                .checkCount("#ginv_target_row", 2)
                .waitUntil("Alice and Dave recorded", ctx -> GuildTestGateway.sentInvites().size() >= 2)
                .check("invites were sent as Alice then Dave (roster order)",
                        ctx -> GuildTestGateway.sentInvites().equals(List.of("Alice", "Dave")))
                .screenshot("level_queue")
                .teardown("restore state", ctx -> {
                    GuildTestGateway.reset();
                    GinvCommand.clearTargets();
                    if (GinvCommand.isFrozen()) GinvCommand.toggleFreeze();
                    GinvDataStore.setDelays(220, 720);
                    GinvDataStore.setWhitelistOnly(false);
                    if (ctx.screen() != null) ctx.mc().setScreen(null);
                });
    }
}
