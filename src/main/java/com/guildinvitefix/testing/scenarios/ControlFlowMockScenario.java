package com.guildinvitefix.testing.scenarios;

import com.ginv.command.GinvCommand;
import com.ginv.data.GinvDataStore;
import com.ginv.testing.GuildTestGateway;
import com.ginv.ui.GinvMenuScreen;
import com.ginv.ui.GinvMenuWindow;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import java.util.List;

/**
 * Control-tab flow over the mock invite route: the hero STOP/RESUME round
 * trip (banner + button text), queue-by-name (feedback, header count, target
 * rows), the actual scheduler → {@code InviteRoute} → gateway send path, and
 * Clear.
 *
 * <p>Everything except the network edge is the real implementation — queue,
 * delays, freeze, store recording — with {@link GuildTestGateway} swapping
 * only the roster/SkyBlock/send seam (dev environment + singleplayer only).
 */
@LDLRegisterClient(name = "control_flow_mock", group = "guildinvitefix",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public class ControlFlowMockScenario implements UIScenario {

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
            // Empty roster: queue-by-name does not read the directory, and the
            // gateway's only job here is recording sends.
            GuildTestGateway.install(List.of(), false);
        })
                .ticks(1)
                .openScreen("gmenu popup", ctx -> new GinvMenuScreen(true))
                .awaitScreen(GinvMenuScreen.class)
                .awaitModularUI()
                .checkTextContains("#ginv_banner", "RUNNING")
                // Hero STOP ⇄ RESUME round trip.
                .click("#ginv_hero")
                .ticks(1)
                .checkTextContains("#ginv_banner", "STOPPED")
                .checkText("#ginv_hero", "RESUME INVITES")
                .click("#ginv_hero")
                .ticks(1)
                .checkTextContains("#ginv_banner", "RUNNING")
                .checkText("#ginv_hero", "STOP INVITES")
                // Queue two names.
                .typeInto("#ginv_name_input", "Alice Bob")
                .click("#ginv_queue_names")
                .checkText("#ginv_feedback", "Queued 2 invites.")
                .ticks(1)
                .checkTextContains("#ginv_targets_header", "(2)")
                .checkCount("#ginv_target_row", 2)
                // The scheduler must actually push both through InviteRoute.
                .waitUntil("both invites recorded", ctx -> GuildTestGateway.sentInvites().size() >= 2)
                .check("invites were sent as Alice then Bob",
                        ctx -> GuildTestGateway.sentInvites().equals(List.of("Alice", "Bob")))
                .screenshot("control_flow_queued")
                // Clear empties the queue and the readout.
                .click("#ginv_clear")
                .ticks(1)
                .checkCount("#ginv_target_row", 0)
                .checkTextContains("#ginv_targets_header", "(0)")
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
