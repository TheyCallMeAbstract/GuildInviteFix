package com.ginv.client;

import com.ginv.command.GfreezeCommand;
import com.ginv.command.GinvCommand;
import com.ginv.command.GlvlCommand;
import com.ginv.command.GmenuCommand;
import com.ginv.data.GinvDataStore;
import com.ginv.ui.PlayerHeadTexture;
import com.ginv.ui.SkyBlockStatusElement;
import com.lowdragmc.lowdraglib2.gui.texture.rendering.GuiTextureRendererRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.UIElementRendererRegistry;
import net.fabricmc.api.ClientModInitializer;

public class GuildInviteFixClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Persisted invite/list/settings store (also loadable on first use).
        GinvDataStore.init();
        // Startup freeze state follows the persisted "Keep queue running" policy.
        GinvCommand.initFromSettings();

        // Draws player faces for the menu's row icons.
        GuiTextureRendererRegistry.register(PlayerHeadTexture.class, PlayerHeadTexture.Renderer.INSTANCE);

        // State dot for the top-bar SkyBlock chip (label stays as fallback).
        UIElementRendererRegistry.register(SkyBlockStatusElement.class, new SkyBlockStatusElement.Renderer());

        GinvCommand.register();
        GlvlCommand.register();
        GfreezeCommand.register();
        GmenuCommand.register();
    }
}
