package com.ginv.client;

import com.ginv.command.GinvCommand;
import net.fabricmc.api.ClientModInitializer;

public class GuildInviteFixClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GinvCommand.register();
    }
}
