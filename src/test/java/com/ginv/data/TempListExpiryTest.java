package com.ginv.data;

import com.ginv.data.GinvDataStore.ListState;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Store-level contract for temporary list TTLs: the per-type default expiry
 * (whitelist permanent, blacklist {@code blacklistTtlMs}), the
 * lapse-and-delete side effect on an invite attempt, whitelist-only
 * interaction, the {@link GinvDataStore#purgeExpired()} sweep, the persisted
 * default's settings.json round-trip, and the legacy
 * {@code 0}-expiry-is-permanent migration.
 *
 * <p>Guarded with the same FabricLoader probe as {@code GinvDataStoreThemeTest},
 * and every entry it creates is removed afterwards so the shared store is left
 * as it was found.
 */
class TempListExpiryTest {

    private static final String PERM_WHITE = "TtlTestPermWhite";
    private static final String DEFAULT_BLACK = "TtlTestDefaultBlack";
    private static final String THIRTY_BLACK = "TtlTestThirtyBlack";
    private static final String EXPIRED_BLACK = "TtlTestExpiredBlack";
    private static final String EXPIRED_WHITE = "TtlTestExpiredWhite";
    private static final String LIVE_WHITE = "TtlTestLiveWhite";
    private static final String PURGE_EXPIRED = "TtlTestPurgeExpired";
    private static final String PURGE_LIVE = "TtlTestPurgeLive";
    private static final String LEGACY = "TtlTestLegacy";

    private static final String[] NAMES = {
            PERM_WHITE, DEFAULT_BLACK, THIRTY_BLACK, EXPIRED_BLACK, EXPIRED_WHITE,
            LIVE_WHITE, PURGE_EXPIRED, PURGE_LIVE, LEGACY
    };

    private static final long DAY_MS = 86_400_000L;
    private static final long FIFTY_YEARS_MS = 50L * 365 * 24 * 60 * 60 * 1000;

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
    void permanentWhitelistIsStampedFarInTheFutureAndStaysAllowing() {
        GinvDataStore.setListState(PERM_WHITE, ListState.WHITELIST);
        GinvDataStore.PlayerSnapshot snap = GinvDataStore.snapshot(PERM_WHITE);
        assertNotNull(snap);
        assertTrue(snap.listExpiresAt() >= System.currentTimeMillis() + FIFTY_YEARS_MS,
                "a plain whitelist must be stamped ~100 years out");
        assertTrue(GinvDataStore.isAllowedAt(PERM_WHITE,
                        System.currentTimeMillis() + FIFTY_YEARS_MS),
                "a permanent whitelist is still allowing 50 years out");
    }

    @Test
    void plainBlacklistStampsDefaultTtl() {
        GinvDataStore.setListState(DEFAULT_BLACK, ListState.BLACKLIST);
        GinvDataStore.PlayerSnapshot snap = GinvDataStore.snapshot(DEFAULT_BLACK);
        assertNotNull(snap);
        long now = System.currentTimeMillis();
        assertTrue(snap.listExpiresAt() > now + 6 * DAY_MS,
                "a plain blacklist must be stamped ~7 days out");
        assertTrue(snap.listExpiresAt() <= now + 8 * DAY_MS,
                "a plain blacklist must not be stamped ~100 years out");

        assertFalse(GinvDataStore.isAllowedAt(DEFAULT_BLACK, now + 6 * DAY_MS),
                "a live 7-day blacklist still blocks at 6 days");
        assertTrue(GinvDataStore.isAllowedAt(DEFAULT_BLACK, now + 8 * DAY_MS),
                "a 7-day blacklist lapses at 8 days and allows the invite");
        assertNull(GinvDataStore.snapshot(DEFAULT_BLACK),
                "the lapsed blacklist entry must be deleted");
    }

    @Test
    void blacklistTtlSettingAppliesToNewEntries() {
        GinvDataStore.setBlacklistTtlMs(ListDuration.DAYS_30.millis());
        GinvDataStore.setListState(THIRTY_BLACK, ListState.BLACKLIST);
        GinvDataStore.PlayerSnapshot snap = GinvDataStore.snapshot(THIRTY_BLACK);
        assertNotNull(snap);
        long now = System.currentTimeMillis();
        assertTrue(snap.listExpiresAt() > now + 29 * DAY_MS,
                "a 30-day default must stamp past 29 days");
        assertTrue(snap.listExpiresAt() <= now + 31 * DAY_MS,
                "a 30-day default must stamp before 31 days");
    }

    @Test
    void expiredBlacklistInvitesAndDeletes() {
        long past = System.currentTimeMillis() - 1_000;
        GinvDataStore.setListState(EXPIRED_BLACK, ListState.BLACKLIST, past);
        assertTrue(GinvDataStore.isAllowedAt(EXPIRED_BLACK, System.currentTimeMillis()),
                "a lapsed blacklist must allow the invite");
        assertNull(GinvDataStore.snapshot(EXPIRED_BLACK),
                "the lapsed entry must be deleted from the store");
    }

    @Test
    void lapsedWhitelistUnderWhitelistOnlyBlocksAndDeletes() {
        GinvDataStore.setWhitelistOnly(true);
        long past = System.currentTimeMillis() - 1_000;
        GinvDataStore.setListState(EXPIRED_WHITE, ListState.WHITELIST, past);
        assertFalse(GinvDataStore.isAllowedAt(EXPIRED_WHITE, System.currentTimeMillis()),
                "a lapsed whitelist grant must not allow under whitelist-only");
        assertNull(GinvDataStore.snapshot(EXPIRED_WHITE),
                "the lapsed whitelist entry must be deleted");
    }

    @Test
    void liveWhitelistUnderWhitelistOnlyAllows() {
        GinvDataStore.setWhitelistOnly(true);
        GinvDataStore.setListState(LIVE_WHITE, ListState.WHITELIST);
        assertTrue(GinvDataStore.isAllowedAt(LIVE_WHITE, System.currentTimeMillis()),
                "a live whitelist entry must allow under whitelist-only");
    }

    @Test
    void purgeSweepsOnlyLapsedEntries() {
        long now = System.currentTimeMillis();
        GinvDataStore.setListState(PURGE_EXPIRED, ListState.BLACKLIST, now - 1_000);
        GinvDataStore.setListState(PURGE_LIVE, ListState.BLACKLIST, now + 60_000);

        assertTrue(GinvDataStore.purgeExpired(), "the lapsed entry must trigger a sweep");
        assertNull(GinvDataStore.snapshot(PURGE_EXPIRED), "lapsed entries are dropped");
        GinvDataStore.PlayerSnapshot live = GinvDataStore.snapshot(PURGE_LIVE);
        assertNotNull(live, "an unexpired entry must survive the sweep");
        assertEquals(ListState.BLACKLIST, live.listState());
    }

    @Test
    void blacklistTtlRoundTripsThroughSettingsJsonOnReload() throws Exception {
        GinvDataStore.setBlacklistTtlMs(ListDuration.DAYS_14.millis());
        assertEquals(ListDuration.DAYS_14.millis(), blacklistTtlInSettingsFile(),
                "setBlacklistTtlMs must write the key to disk");

        // Drop the cache flag so blacklistTtlMs() re-reads settings.json from disk.
        Field loaded = GinvDataStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);

        assertEquals(ListDuration.DAYS_14.millis(), GinvDataStore.blacklistTtlMs(),
                "the default must survive a save/load round-trip through settings.json");
    }

    @Test
    void legacyEntryWithZeroExpiryReadsAsPermanent() throws Exception {
        GinvDataStore.setListState(LEGACY, ListState.BLACKLIST);
        zeroExpiry(LEGACY);

        // 200y out: a real ~100y stamp would have lapsed and allowed the invite,
        // so a 0 (legacy) expiry must still block here.
        long beyondAnyTtl = System.currentTimeMillis() + 2 * FIFTY_YEARS_MS;
        assertFalse(GinvDataStore.isAllowedAt(LEGACY, beyondAnyTtl),
                "a 0 expiry must read as permanent, not lapsed");
        GinvDataStore.PlayerSnapshot snap = GinvDataStore.snapshot(LEGACY);
        assertNotNull(snap, "a permanent entry must survive the far-future check");
        assertEquals(ListState.BLACKLIST, snap.listState());
    }

    private static long blacklistTtlInSettingsFile() throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("guildinvitefix").resolve("settings.json");
        assertTrue(Files.exists(file), "settings.json must exist after setBlacklistTtlMs");
        JsonObject root = JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.has("blacklistTtlMs"), "settings.json must carry the blacklistTtlMs key");
        return root.get("blacklistTtlMs").getAsLong();
    }

    /** Simulates a pre-TTL {@code players.json} entry by zeroing its expiry field. */
    private static void zeroExpiry(String name) throws Exception {
        Field playersField = GinvDataStore.class.getDeclaredField("players");
        playersField.setAccessible(true);
        Map<?, ?> players = (Map<?, ?>) playersField.get(null);
        Object entry = null;
        for (Map.Entry<?, ?> e : players.entrySet()) {
            if (String.valueOf(e.getKey()).equalsIgnoreCase(name)) {
                entry = e.getValue();
                break;
            }
        }
        assertNotNull(entry, "entry must exist before zeroing its expiry");
        Field expires = entry.getClass().getDeclaredField("listExpiresAt");
        expires.setAccessible(true);
        expires.setLong(entry, 0L);
    }
}
