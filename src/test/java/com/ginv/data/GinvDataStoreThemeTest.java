package com.ginv.data;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage-2 persistence contract for the {@code theme} key in
 * {@code settings.json}: normalization through {@link #setTheme}, the
 * unknown-id DUSK fallback, one version bump per distinct change, and a
 * full save → disk → reload round-trip.
 *
 * <p>Guarded with the same FabricLoader probe as {@code ScaleFitMathTest} so
 * a headless environment without the loader skips rather than fails.
 */
class GinvDataStoreThemeTest {

    private String original;

    @BeforeEach
    void requireStore() {
        try {
            original = GinvDataStore.themeId();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "GinvDataStore unavailable: " + t);
        }
    }

    @AfterEach
    void restoreTheme() {
        if (original != null) {
            GinvDataStore.setTheme(original);
        }
    }

    @Test
    void setThemeNormalizesAndPersists() throws Exception {
        GinvDataStore.setTheme("paper");
        assertEquals("paper", GinvDataStore.themeId());
        assertEquals("paper", themeInSettingsFile());
    }

    @Test
    void unknownThemeFallsBackToDusk() {
        GinvDataStore.setTheme("not-a-theme");
        assertEquals("dusk", GinvDataStore.themeId());
    }

    @Test
    void versionBumpsOnlyOnADistinctChange() {
        GinvDataStore.setTheme("carbon");
        int after = GinvDataStore.version();
        GinvDataStore.setTheme("carbon");
        assertEquals(after, GinvDataStore.version(), "same id must not bump the version");
        GinvDataStore.setTheme("mint");
        assertEquals(after + 1, GinvDataStore.version(), "a distinct id bumps exactly once");
    }

    @Test
    void themeRoundTripsThroughSettingsJsonOnReload() throws Exception {
        GinvDataStore.setTheme("plum");
        assertEquals("plum", themeInSettingsFile(), "setTheme must write the key to disk");

        // Drop the cache flag so themeId() re-reads settings.json from disk.
        Field loaded = GinvDataStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);

        assertEquals("plum", GinvDataStore.themeId(),
                "theme must survive a save/load round-trip through settings.json");
    }

    private static String themeInSettingsFile() throws Exception {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("guildinvitefix").resolve("settings.json");
        assertTrue(Files.exists(file), "settings.json must exist after setTheme");
        JsonObject root = JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.has("theme"), "settings.json must carry the theme key");
        return root.get("theme").getAsString();
    }
}
