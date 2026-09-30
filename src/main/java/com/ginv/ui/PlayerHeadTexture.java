package com.ginv.ui;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.rendering.GuiTextureRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Draws a classic 8x8 player face (with hat layer) for a named player.
 *
 * <p>Used as an element {@code backgroundTexture}. Skin resolution: exact tab-list
 * name first, then {@code getPlayerInfoIgnoreCase}, then the deterministic default
 * skin for the name — so rows never render blank, even for players that already
 * left the tab list.
 *
 * <p>The renderer half is registered once at client init via
 * {@code GuiTextureRendererRegistry.register(PlayerHeadTexture.class, ...)}.
 */
public record PlayerHeadTexture(String playerName) implements IGuiTexture {

    @Override
    public IGuiTexture copy() {
        return this;
    }

    /** Resolves the current skin for a name; never returns null. */
    static PlayerSkin resolveSkin(String name) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(name);
            if (info == null) {
                info = connection.getPlayerInfoIgnoreCase(name);
            }
            if (info != null) {
                PlayerSkin skin = info.getSkin();
                if (skin != null) {
                    return skin;
                }
            }
        }
        UUID offlineId = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return DefaultPlayerSkin.get(offlineId);
    }

    /** Registered as the draw implementation for {@link PlayerHeadTexture}. */
    public static final class Renderer implements GuiTextureRenderer<PlayerHeadTexture> {

        public static final Renderer INSTANCE = new Renderer();

        private Renderer() {
        }

        @Override
        public void draw(PlayerHeadTexture texture, GUIContext context,
                         float x, float y, float width, float height) {
            PlayerSkin skin = resolveSkin(texture.playerName());
            Identifier textureId = skin.body().texturePath();
            int ix = Math.round(x);
            int iy = Math.round(y);
            int iw = Math.max(1, Math.round(width));
            int ih = Math.max(1, Math.round(height));

            // Face: 8x8 region at (8, 8) of the 64x64 skin, scaled to the element.
            context.graphics.blit(RenderPipelines.GUI_TEXTURED, textureId,
                    ix, iy, 8f, 8f, iw, ih, 8, 8, 64, 64);
            // Hat overlay: 8x8 region at (40, 8), alpha-blended on top.
            context.graphics.blit(RenderPipelines.GUI_TEXTURED, textureId,
                    ix, iy, 40f, 8f, iw, ih, 8, 8, 64, 64);
        }
    }
}
