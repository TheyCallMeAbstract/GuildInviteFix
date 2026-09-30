package com.ginv.client;

import com.ginv.command.GfreezeCommand;
import com.ginv.command.GinvCommand;
import com.ginv.command.GlvlCommand;
import com.ginv.command.GmenuCommand;
import com.ginv.data.GinvDataStore;
import com.ginv.ui.PlayerHeadTexture;
import com.lowdragmc.lowdraglib2.gui.texture.rendering.GuiTextureRendererRegistry;
import net.fabricmc.api.ClientModInitializer;

public class GuildInviteFixClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Persisted invite/list/settings store (also loadable on first use).
        GinvDataStore.init();

        // Draws player faces for the menu's row icons.
        GuiTextureRendererRegistry.register(PlayerHeadTexture.class, PlayerHeadTexture.Renderer.INSTANCE);

        GinvCommand.register();
        GlvlCommand.register();
        GfreezeCommand.register();
        GmenuCommand.register();
    }
}
