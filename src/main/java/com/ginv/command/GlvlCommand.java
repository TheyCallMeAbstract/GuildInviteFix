package com.ginv.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ginv.data.GinvDataStore;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * {@code /glvl <level>} — thin chat adapter over
 * {@link GinvCommand#queueByLevel(int)}.
 *
 * <p>Preconditions, the tab scan and the skip counts live in the command API
 * (shared with the menu's Control tab); this class only parses the argument
 * and renders the result as chat feedback.
 */
public class GlvlCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommands.literal("glvl")
                                .then(ClientCommands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(GlvlCommand::execute))
                )
        );
    }

    private static int execute(CommandContext<FabricClientCommandSource> context) {
        int minLevel = IntegerArgumentType.getInteger(context, "level");
        GinvDataStore.setGuildLevelThreshold(minLevel);
        LevelQueueResult result = GinvCommand.queueByLevel(minLevel);
        var source = context.getSource();

        return switch (result.error()) {
            case NOT_SKYBLOCK -> {
                source.sendFeedback(Component.literal(
                        "§c[Glvl] §fYou're not in SkyBlock! Please join a SkyBlock lobby first."
                ));
                yield 0;
            }
            case NOT_CONNECTED -> {
                source.sendFeedback(Component.literal(
                        "§c[Glvl] §fNot connected to a server."
                ));
                yield 0;
            }
            case NO_MATCHES -> {
                source.sendFeedback(Component.literal(
                        "§c[Glvl] §fNo players found with level §e≥ " + minLevel + "§f. " +
                        "(skipped " + result.skippedNoLevel() + " with no level, "
                        + result.skippedLowLevel() + " below threshold)"
                ));
                yield 0;
            }
            case NONE -> {
                source.sendFeedback(Component.literal(
                        "§a[Glvl] §fQueued §e" + result.queued() + " §fguild invite(s) " +
                        "§7(level ≥ " + minLevel + ", skipped " + result.skippedNoLevel()
                        + " no-level, " + result.skippedLowLevel() + " below)"
                ));
                yield Command.SINGLE_SUCCESS;
            }
        };
    }
}
