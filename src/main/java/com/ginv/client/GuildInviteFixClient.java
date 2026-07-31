package com.ginv.client;

import com.ginv.command.GinvCommand;
import com.ginv.command.GlvlCommand;
import com.ginv.command.GfreezeCommand;
import net.fabricmc.api.ClientModInitializer;

public class GuildInviteFixClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GinvCommand.register();
        GlvlCommand.register();
        GfreezeCommand.register();
    }
}
