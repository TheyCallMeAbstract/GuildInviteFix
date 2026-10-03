package com.ginv.data;

import com.ginv.data.GinvDataStore.ListState;
import com.ginv.data.GinvDataStore.PlayerSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Store-level contract for per-player blacklist durations and un-blacklist
 * persistence:
 *
 * <ul>
 *   <li>a {@link ListDuration} blacklist stamps {@code now + duration} and
 *       survives a forced {@code players.json} reload byte-for-byte;</li>
 *   <li>clearing to {@link ListState#NONE} persists across a reload even when
 *       the player has invite history (the retained record keeps {@code none});</li>
 *   <li>{@link ListDuration#FOREVER} is effectively permanent;</li>
 *   <li>an explicit {@code NONE} zeroes a previously-set custom expiry.</li>
 * </ul>
 *
 * <p>Guarded with the same FabricLoader probe as {@link TempListExpiryTest},
 * and every entry it creates is removed afterwards.
 */
class BlacklistDurationPersistenceTest {

    private static final String THREE_DAY = "Bdp3Day";
    private static final String UNBLACK = "BdpUnblack";
    private static final String FOREVER = "BdpForever";
    private static final String ZERO = "BdpZero";

    private static final String[] NAMES = {THREE_DAY, UNBLACK, FOREVER, ZERO};

    private static final long FIFTY_YEARS_MS = 50L * 365 * 24 * 60 * 60 * 1000;
    private static final long TWO_MINUTES_MS = 120_000L;

    private Boolean originalWhitelistOnly;
    private Long originalBlacklistTtlMs;

    @BeforeEach
    void requireStore() {
        try {
            originalWhitelistOnly = GinvDataStore.whitelistOnly();
            originalBlacklistTtlMs = GinvDataStore.blacklistTtlMs();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "GinvDataStore unavailable: " + t);
        }
        // A live blacklist must block regardless of a whitelist-only leftover.
        GinvDataStore.setWhitelistOnly(false);
    }

    @AfterEach
    void cleanup() {
        if (originalWhitelistOnly == null) return;
        GinvDataStore.setWhitelistOnly(originalWhitelistOnly);
        if (originalBlacklistTtlMs != null) {
            GinvDataStore.setBlacklistTtlMs(originalBlacklistTtlMs);
        }
        for (String name : NAMES) {
            GinvDataStore.removePlayer(name);
        }
    }

    @Test
    void perPlayerDurationPersistsAcrossReload() throws Exception {
        long now = System.currentTimeMillis();
        GinvDataStore.setListState(THREE_DAY, ListState.BLACKLIST, ListDuration.DAYS_3);

        PlayerSnapshot snap = GinvDataStore.snapshot(THREE_DAY);
        assertNotNull(snap, "the per-player blacklist entry must exist");
        assertEquals(ListState.BLACKLIST, snap.listState());
        long expected = now + ListDuration.DAYS_3.millis();
        assertTrue(Math.abs(snap.listExpiresAt() - expected) <= TWO_MINUTES_MS,
                "the 3-day preset must stamp ~now + 3d");
        long captured = snap.listExpiresAt();

        forceReload();

        PlayerSnapshot after = GinvDataStore.snapshot(THREE_DAY);
        assertNotNull(after, "the entry must survive a forced reload");
        assertEquals(ListState.BLACKLIST, after.listState());
        assertEquals(captured, after.listExpiresAt(),
                "the exact per-player expiry must round-trip players.json");
        assertEquals("black", rawListField(THREE_DAY),
                "the persisted list state must read back as black");
    }

    @Test
    void unblacklistWithInviteHistoryPersistsNoneAcrossReload() throws Exception {
        GinvDataStore.recordInvite(UNBLACK);
        GinvDataStore.setListState(UNBLACK, ListState.BLACKLIST);
        GinvDataStore.setListState(UNBLACK, ListState.NONE);

        PlayerSnapshot snap = GinvDataStore.snapshot(UNBLACK);
        assertNotNull(snap, "invite history must keep the record after un-blacklisting");
        assertEquals(ListState.NONE, snap.listState());
        assertEquals(0L, snap.listExpiresAt());
        assertEquals("none", rawListField(UNBLACK));

        forceReload();

        PlayerSnapshot after = GinvDataStore.snapshot(UNBLACK);
        assertNotNull(after, "the retained record must survive a forced reload");
        assertEquals(ListState.NONE, after.listState(),
                "an un-blacklist must not resurrect across a reload");
    }

    @Test
    void foreverBlacklistIsEffectivelyPermanent() throws Exception {
        GinvDataStore.setListState(FOREVER, ListState.BLACKLIST, ListDuration.FOREVER);

        PlayerSnapshot snap = GinvDataStore.snapshot(FOREVER);
        assertNotNull(snap);
        long now = System.currentTimeMillis();
        assertTrue(snap.listExpiresAt() >= now + FIFTY_YEARS_MS,
                "FOREVER must be stamped far beyond 50 years");
        assertFalse(GinvDataStore.isAllowedAt(FOREVER, now + FIFTY_YEARS_MS),
                "a FOREVER blacklist must still block 50 years out");

        forceReload();

        PlayerSnapshot after = GinvDataStore.snapshot(FOREVER);
        assertNotNull(after);
        assertEquals(ListState.BLACKLIST, after.listState());
    }

    @Test
    void explicitNoneZeroesCustomExpiry() {
        GinvDataStore.recordInvite(ZERO);
        long now = System.currentTimeMillis();
        GinvDataStore.setListState(ZERO, ListState.BLACKLIST, ListDuration.DAYS_30);

        PlayerSnapshot snap = GinvDataStore.snapshot(ZERO);
        assertNotNull(snap);
        long expected = now + ListDuration.DAYS_30.millis();
        assertTrue(Math.abs(snap.listExpiresAt() - expected) <= TWO_MINUTES_MS,
                "the 30-day preset must stamp ~now + 30d");

        GinvDataStore.setListState(ZERO, ListState.NONE);

        PlayerSnapshot after = GinvDataStore.snapshot(ZERO);
        assertNotNull(after, "invite history keeps the record after NONE");
        assertEquals(ListState.NONE, after.listState());
        assertEquals(0L, after.listExpiresAt(),
                "an explicit NONE must zero the custom expiry");
        assertTrue(GinvDataStore.isAllowedAt(ZERO, System.currentTimeMillis()),
                "an un-blacklisted player must be allowed again");
    }

    /** Drops the loaded cache flag so the next access re-reads {@code players.json}. */
    private static void forceReload() throws Exception {
        Field loaded = GinvDataStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
    }

    /** Reads {@code players.<name>.list} straight from {@code players.json}. */
    private static String rawListField(String name) throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("guildinvitefix").resolve("players.json");
        assertTrue(Files.exists(file), "players.json must exist after a mutation");
        JsonObject root = JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.has("players"), "players.json must carry the players object");
        JsonObject players = root.getAsJsonObject("players");
        assertTrue(players.has(name), "players.json must carry the tracked player");
        return players.getAsJsonObject(name).get("list").getAsString();
    }
}
