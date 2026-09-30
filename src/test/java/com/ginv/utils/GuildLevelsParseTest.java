package com.ginv.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pure parsing rules for guild-level prefixes — the half of
 * {@link GuildLevels} that was split out of {@code extract} precisely so it
 * can be tested without a client.
 */
class GuildLevelsParseTest {

    @Test
    void parsesColoredPrefixWithCodeAfterBracket() {
        // The Hypixel form: §8[§e101§8] — color code right after the bracket.
        var level = GuildLevels.parsePrefix("§8[§e101§8] ");
        assertEquals(new GuildLevels.LevelInfo(101, 0xFFFFFF55), level);
    }

    @Test
    void parsesColoredPrefixWithCodeBeforeBracket() {
        var level = GuildLevels.parsePrefix("§e[101]");
        assertEquals(new GuildLevels.LevelInfo(101, 0xFFFFFF55), level);
    }

    @Test
    void parsesAdjacentColorCodeWithItsVanillaColor() {
        var level = GuildLevels.parsePrefix("§7[42]");
        assertEquals(new GuildLevels.LevelInfo(42, 0xFFAAAAAA), level);
    }

    @Test
    void parsesPlainBracketFormWithDefaultColor() {
        var level = GuildLevels.parsePrefix("Guild [42] member");
        assertEquals(new GuildLevels.LevelInfo(42, GuildLevels.DEFAULT_COLOR), level);
    }

    @Test
    void fallsBackToPlainMatchWhenColorIsNotAdjacent() {
        // §a colors the label, not the bracket: strip-then-plain must win.
        var level = GuildLevels.parsePrefix("§aLeveled [42]");
        assertEquals(new GuildLevels.LevelInfo(42, GuildLevels.DEFAULT_COLOR), level);
    }

    @Test
    void returnsNullForNullOrEmpty() {
        assertNull(GuildLevels.parsePrefix(null));
        assertNull(GuildLevels.parsePrefix(""));
    }

    @Test
    void returnsNullWhenNoNumberInBrackets() {
        assertNull(GuildLevels.parsePrefix("§8[§eVIP§8] "));
        assertNull(GuildLevels.parsePrefix("[rank]"));
    }

    @Test
    void returnsNullWithoutBrackets() {
        assertNull(GuildLevels.parsePrefix("101"));
        assertNull(GuildLevels.parsePrefix("just a prefix"));
    }

    @Test
    void returnsNullForOverflowingNumber() {
        assertNull(GuildLevels.parsePrefix("[9999999999999]"));
    }

    @Test
    void colorOfMapsVanillaCodesAndFallsBackToDefault() {
        assertEquals(0xFFFFFF55, GuildLevels.colorOf('e'));
        assertEquals(0xFF000000, GuildLevels.colorOf('0'));
        assertEquals(0xFF555555, GuildLevels.colorOf('8'));
        assertEquals(GuildLevels.DEFAULT_COLOR, GuildLevels.colorOf('z'));
    }
}
