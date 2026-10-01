package com.ginv.utils;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts guild levels from tab-list team prefixes, including the § color
 * the server renders them in.
 *
 * <p>Hypixel shows a player's guild level as {@code [101]} inside the team
 * prefix component, usually with a color code next to the digits, e.g.
 * {@code "§8[§e101§8] "}. The colored forms are matched first so the menu can
 * tint its badge like the server does; a color-stripped match is the fallback.
 * Players with no team, no prefix, or no number (NPCs, other servers) have no
 * level at all.
 */
public final class GuildLevels {

    /**
     * The number directly after a formatting code ({@code §e[101]}) or
     * directly after a bracket ({@code [§e101}); group 1 or 2 holds the color
     * code, group 3 always holds the digits.
     */
    private static final Pattern COLORED_LEVEL = Pattern.compile("(?:§([0-9a-f])\\[|\\[§([0-9a-f]))(\\d+)");
    /** Matches {@code [101]} in a fully color-stripped prefix. */
    private static final Pattern PLAIN_LEVEL = Pattern.compile("\\[(\\d+)]");

    /** Badge color when the prefix carries no usable color code (opaque). */
    public static final int DEFAULT_COLOR = 0xFFE0E0E0;

    private GuildLevels() {
    }

    /** A parsed guild level and the color the server shows it in. */
    public record LevelInfo(int value, int color) {
    }

    /** The level range across a tab list, from at least one leveled player. */
    public record LevelRange(int min, int max) {
    }

    /**
     * Reads one player's guild level from their team prefix.
     *
     * @return the level with its render color, or {@code null} when the player
     *         has no team, no prefix, or no {@code [number]} in the prefix
     */
    @Nullable
    public static LevelInfo extract(PlayerInfo info) {
        PlayerTeam team = info.getTeam();
        if (team == null) return null;
        Component prefix = team.getPlayerPrefix();
        if (prefix == null) return null;
        return parsePrefix(prefix.getString());
    }

    /**
     * Parses a raw team-prefix string — the pure half of {@link #extract},
     * split out so the parsing rules are testable without a client.
     *
     * @param raw the prefix as the server rendered it, formatting codes intact
     * @return the level with its render color, or {@code null} when the prefix
     *         carries no {@code [number]}
     */
    @Nullable
    public static LevelInfo parsePrefix(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;

        Matcher colored = COLORED_LEVEL.matcher(raw);
        if (colored.find()) {
            String code = colored.group(1) != null ? colored.group(1) : colored.group(2);
            return of(colored.group(3), colorOf(code.charAt(0)));
        }

        Matcher plain = PLAIN_LEVEL.matcher(raw.replaceAll("§.", ""));
        if (plain.find()) {
            return of(plain.group(1), DEFAULT_COLOR);
        }
        return null;
    }

    /**
     * The min/max guild level across the current directory (NPCs skipped).
     *
     * @return the range, or {@code null} when nobody has a level
     */
    @Nullable
    public static LevelRange tabRange() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        boolean any = false;
        for (GuildDirectory.Entry entry : GuildDirectory.online()) {
            LevelInfo level = entry.level();
            if (level == null) continue;
            any = true;
            min = Math.min(min, level.value());
            max = Math.max(max, level.value());
        }
        return any ? new LevelRange(min, max) : null;
    }

    @Nullable
    private static LevelInfo of(String digits, int color) {
        try {
            return new LevelInfo(Integer.parseInt(digits), color);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Minecraft formatting code → ARGB, matching vanilla chat colors. */
    public static int colorOf(char code) {
        return switch (code) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFAA5500;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            default -> DEFAULT_COLOR;
        };
    }
}
