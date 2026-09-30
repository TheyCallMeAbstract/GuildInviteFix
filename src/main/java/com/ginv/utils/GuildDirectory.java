package com.ginv.utils;

import com.ginv.testing.GuildTestGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The roster the menu and the invite commands read guild levels from.
 *
 * <p>One seam, two providers: when the {@link GuildTestGateway} is installed
 * (dev environment + singleplayer only) the fixture roster wins, so tests can
 * populate fake players with guild levels; otherwise this reads the real tab
 * list exactly as the commands always have. Every level-consuming call site
 * ({@code queueByLevel}, {@code tabRange}, the menu's badges and change
 * tokens) goes through here so a single install swaps all of them at once.
 */
public final class GuildDirectory {

    /** One online player with their parsed guild level, if any. */
    public record Entry(String name, @Nullable GuildLevels.LevelInfo level) {
    }

    private GuildDirectory() {
    }

    /** The current roster: gateway fixtures when installed, else the tab list. */
    public static List<Entry> online() {
        if (GuildTestGateway.isActive()) {
            return GuildTestGateway.roster();
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.startsWith("!")) continue;
            entries.add(new Entry(name, GuildLevels.extract(info)));
        }
        return entries;
    }
}
