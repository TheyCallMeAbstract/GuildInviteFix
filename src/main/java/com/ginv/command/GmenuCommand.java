package com.ginv.command;

import com.ginv.ui.GinvMenuScreen;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

/**
 * Opens the LDLib2 menu: {@code /gmenu} (popup) or
 * {@code /gmenu popup|screen}.
 */
public class GmenuCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommands.literal("gmenu")
                                .executes(context -> open(true))
                                .then(ClientCommands.literal("popup")
                                        .executes(context -> open(true)))
                                .then(ClientCommands.literal("screen")
                                        .executes(context -> open(false)))
                )
        );
    }

    private static int open(boolean popup) {
        Minecraft.getInstance().setScreen(new GinvMenuScreen(popup));
        return Command.SINGLE_SUCCESS;
    }
}
