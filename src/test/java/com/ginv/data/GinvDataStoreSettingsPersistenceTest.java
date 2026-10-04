package com.ginv.data;

import com.ginv.command.GinvCommand;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence contract for the Lists level-filter bounds and the queue
 * auto-run policy in {@code settings.json}:
 *
 * <ul>
 *   <li>{@code listsLevelMin} / {@code listsLevelMax} round-trip present,
 *       single-sided and cleared, and an unset bound writes no key;</li>
 *   <li>{@code queueAutoRun} defaults to off and survives a reload;</li>
 *   <li>{@code guildLevelThreshold} round-trips, defaults to 0 when missing
 *       and clamps to 0..999;</li>
 *   <li>each setting bumps {@code version()} only on a real change;</li>
 *   <li>{@link GinvCommand#initFromSettings()} follows the persisted policy.</li>
 * </ul>
 *
 * <p>Guarded with the same FabricLoader probe as
 * {@link BlacklistDurationPersistenceTest} so a headless environment without
 * the loader skips rather than fails.
 */
class GinvDataStoreSettingsPersistenceTest {

    private Integer originalMin;
    private Integer originalMax;
    private Boolean originalAutoRun;
    private Boolean originalFrozen;
    private Integer originalThreshold;

    @BeforeEach
    void requireStore() {
        try {
            originalMin = GinvDataStore.listsLevelMin();
            originalMax = GinvDataStore.listsLevelMax();
            originalAutoRun = GinvDataStore.queueAutoRun();
            originalFrozen = GinvCommand.isFrozen();
            originalThreshold = GinvDataStore.guildLevelThreshold();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "GinvDataStore unavailable: " + t);
            return;
        }
        GinvDataStore.setListsLevelFilter(null, null);
        GinvDataStore.setQueueAutoRun(false);
        GinvDataStore.setGuildLevelThreshold(0);
    }

    @AfterEach
    void restore() {
        if (originalMin == null && originalMax == null && originalAutoRun == null) return;
        GinvDataStore.setListsLevelFilter(originalMin, originalMax);
        if (originalAutoRun != null) {
            GinvDataStore.setQueueAutoRun(originalAutoRun);
        }
        if (originalFrozen != null) {
            GinvCommand.setFrozen(originalFrozen);
        }
        if (originalThreshold != null) {
            GinvDataStore.setGuildLevelThreshold(originalThreshold);
        }
    }

    @Test
    void boundsRoundTripThroughSettingsJson() throws Exception {
        GinvDataStore.setListsLevelFilter(40, 60);
        assertEquals(Integer.valueOf(40), GinvDataStore.listsLevelMin());
        assertEquals(Integer.valueOf(60), GinvDataStore.listsLevelMax());
        assertEquals(40, settings().get("listsLevelMin").getAsInt());
        assertEquals(60, settings().get("listsLevelMax").getAsInt());

        forceReload();

        assertEquals(Integer.valueOf(40), GinvDataStore.listsLevelMin());
        assertEquals(Integer.valueOf(60), GinvDataStore.listsLevelMax());
    }

    @Test
    void singleBoundWritesOnlyItsKey() throws Exception {
        GinvDataStore.setListsLevelFilter(40, null);

        assertNull(GinvDataStore.listsLevelMax());
        JsonObject settings = settings();
        assertTrue(settings.has("listsLevelMin"), "a set min must write its key");
        assertFalse(settings.has("listsLevelMax"), "an unset max must not write the key");
    }

    @Test
    void absentKeysReadAsUnset() throws Exception {
        GinvDataStore.setListsLevelFilter(null, null);
        forceReload();

        assertNull(GinvDataStore.listsLevelMin());
        assertNull(GinvDataStore.listsLevelMax());
    }

    @Test
    void clearingBoundsRemovesThemAcrossReload() throws Exception {
        GinvDataStore.setListsLevelFilter(10, 20);
        GinvDataStore.setListsLevelFilter(null, null);

        assertNull(GinvDataStore.listsLevelMin());
        JsonObject settings = settings();
        assertFalse(settings.has("listsLevelMin"));
        assertFalse(settings.has("listsLevelMax"));

        forceReload();

        assertNull(GinvDataStore.listsLevelMin());
        assertNull(GinvDataStore.listsLevelMax());
    }

    @Test
    void queueAutoRunDefaultsOffAndRoundTrips() throws Exception {
        assertFalse(GinvDataStore.queueAutoRun(), "default must be off");
        GinvDataStore.setQueueAutoRun(true);
        assertTrue(GinvDataStore.queueAutoRun());
        assertTrue(settings().get("queueAutoRun").getAsBoolean());

        forceReload();

        assertTrue(GinvDataStore.queueAutoRun(), "the policy must survive a reload");
    }

    @Test
    void versionBumpsOnlyOnRealChanges() {
        GinvDataStore.setListsLevelFilter(5, 7);
        int after = GinvDataStore.version();
        GinvDataStore.setListsLevelFilter(5, 7);
        assertEquals(after, GinvDataStore.version(), "same bounds must not bump the version");
        GinvDataStore.setQueueAutoRun(true);
        assertEquals(after + 1, GinvDataStore.version(), "a policy flip bumps exactly once");
        GinvDataStore.setQueueAutoRun(true);
        assertEquals(after + 1, GinvDataStore.version(), "same policy must not bump the version");
    }

    @Test
    void commandFrozenFollowsPersistedPolicy() {
        GinvDataStore.setQueueAutoRun(false);
        GinvCommand.initFromSettings();
        assertTrue(GinvCommand.isFrozen(), "policy off must start frozen");

        GinvDataStore.setQueueAutoRun(true);
        GinvCommand.initFromSettings();
        assertFalse(GinvCommand.isFrozen(), "policy on must start running");

        GinvCommand.setFrozen(true);
        assertTrue(GinvCommand.isFrozen());
        GinvCommand.setFrozen(false);
        assertFalse(GinvCommand.isFrozen());
    }

    @Test
    void guildLevelThresholdRoundTripsThroughSettingsJson() throws Exception {
        GinvDataStore.setGuildLevelThreshold(42);
        assertEquals(42, GinvDataStore.guildLevelThreshold());
        assertEquals(42, settings().get("guildLevelThreshold").getAsInt());

        forceReload();

        assertEquals(42, GinvDataStore.guildLevelThreshold(),
                "the threshold must survive a reload");
    }

    @Test
    void guildLevelThresholdDefaultsToZeroWhenMissing() throws Exception {
        GinvDataStore.setGuildLevelThreshold(55);
        removeSettingsKey("guildLevelThreshold");

        forceReload();

        assertEquals(0, GinvDataStore.guildLevelThreshold(),
                "a missing key must read back as the default 0");
    }

    @Test
    void guildLevelThresholdClampsToValidRange() {
        GinvDataStore.setGuildLevelThreshold(-1);
        assertEquals(0, GinvDataStore.guildLevelThreshold(), "below-range clamps to 0");

        GinvDataStore.setGuildLevelThreshold(1000);
        assertEquals(999, GinvDataStore.guildLevelThreshold(), "above-range clamps to 999");

        GinvDataStore.setGuildLevelThreshold(500);
        assertEquals(500, GinvDataStore.guildLevelThreshold(), "in-range is preserved");
    }

    @Test
    void guildLevelThresholdBumpsOnlyOnRealChanges() {
        GinvDataStore.setGuildLevelThreshold(10);
        int after = GinvDataStore.version();
        GinvDataStore.setGuildLevelThreshold(10);
        assertEquals(after, GinvDataStore.version(), "same threshold must not bump the version");
        GinvDataStore.setGuildLevelThreshold(-1);
        assertEquals(after + 1, GinvDataStore.version(), "a real change bumps exactly once");
        GinvDataStore.setGuildLevelThreshold(-1);
        assertEquals(after + 1, GinvDataStore.version(),
                "a clamped repeat must not bump the version again");
    }

    private static JsonObject settings() throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("guildinvitefix").resolve("settings.json");
        assertTrue(Files.exists(file), "settings.json must exist after a mutation");
        return JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Drops the loaded cache flag so the next access re-reads {@code settings.json}. */
    private static void forceReload() throws Exception {
        Field loaded = GinvDataStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
    }

    /** Removes a key from {@code settings.json} to simulate a missing setting. */
    private static void removeSettingsKey(String key) throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("guildinvitefix").resolve("settings.json");
        assertTrue(Files.exists(file), "settings.json must exist before removing a key");
        JsonObject root = JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        root.remove(key);
        Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
    }
}
