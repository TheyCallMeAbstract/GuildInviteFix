package com.ginv.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public class GfreezeCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("gfreeze")
                                .executes(GfreezeCommand::execute)
                )
        );
    }

    private static int execute(CommandContext<FabricClientCommandSource> context) {
        GinvCommand.toggleFreeze();

        boolean nowFrozen = GinvCommand.isFrozen();
        int pending = GinvCommand.getPendingCount();

        if (nowFrozen) {
            context.getSource().sendFeedback(Component.literal(
                    "§c[Gfreeze] §fQueue frozen. §e" + pending + " §finvite(s) pending."
            ));
        } else {
            context.getSource().sendFeedback(Component.literal(
                    "§a[Gfreeze] §fQueue resumed. §e" + pending + " §finvite(s) pending."
            ));
        }

        return Command.SINGLE_SUCCESS;
    }
}
