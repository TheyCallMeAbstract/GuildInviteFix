package com.ginv.command;

import com.ginv.ui.GinvMenuScreen;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens the LDLib2 menu: {@code /gmenu} (popup) or
 * {@code /gmenu popup|screen}.
 *
 * <p>The screen open is deferred by one tick: {@code ChatScreen} closes itself
 * with {@code setScreen(null)} in the same key-event stack that runs this
 * command, which would clobber a screen opened immediately. The command arms
 * {@link #pendingOpen} instead, and the end-client-tick applier opens the menu
 * once chat is gone (the same trick ldlib2's own screen-test commands use).
 */
public class GmenuCommand {

    /** Pending open request: {@code null} = none, otherwise popup vs screen. */
    private static Boolean pendingOpen;

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

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            Boolean popup = pendingOpen;
            if (popup == null) return;

            Screen current = minecraft.screen;
            if (current instanceof ChatScreen) {
                return; // wait until chat has closed
            }
            pendingOpen = null;
            if (current == null) {
                minecraft.setScreen(new GinvMenuScreen(popup));
            }
            // else: another screen took over — drop the request instead of clobbering it.
        });
    }

    private static int open(boolean popup) {
        pendingOpen = popup;
        return Command.SINGLE_SUCCESS;
    }
}
