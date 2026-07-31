package com.ginv.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;

/**
 * Detects whether the player is currently in a Hypixel SkyBlock instance
 * by reading the server-rendered scoreboard objective display name.
 *
 * On SkyBlock, the server sets the sidebar objective's display name to
 * contain "SKYBLOCK" (e.g. "§6§lSkyBlock"). This is the same mechanism
 * used by SkyHanni, Skytils, and other major Hypixel mods.
 */
public class SkyBlockDetector {

    private static final String SKYBLOCK_MARKER = "skyblock";

    /**
     * Returns true if the player is currently on a Hypixel SkyBlock instance.
     * Reads directly from the client's scoreboard — no network calls, no mixins.
     * Returns false if not connected, not on Hypixel, or not in SkyBlock.
     */
    public static boolean isSkyBlock() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return false;
        }

        Scoreboard scoreboard = level.getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return false;
        }

        String displayName = sidebar.getDisplayName().getString();
        return displayName.toLowerCase(Locale.ROOT).contains(SKYBLOCK_MARKER);
    }
}
