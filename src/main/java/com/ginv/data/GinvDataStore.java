package com.ginv.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.ginv.GuildInviteFix;
import com.ginv.ui.theme.GinvTheme;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persistent, thread-safe store for invite tracking, list membership and queue settings.
 *
 * <p>Two small JSON files live under {@code config/guildinvitefix/}:
 * <ul>
 *   <li>{@code players.json} — exact-cased name → invite count, last invite time, list state</li>
 *   <li>{@code settings.json} — min/max delay (ms), whitelist-only, always-on-top,
 *       menu scale, the autoscale flag and the theme id</li>
 * </ul>
 *
 * <p>All access is guarded by a single lock because mutations happen both on the client
 * thread (UI buttons) and on the {@code GinvScheduler} thread (recordInvite after send).
 * Every mutation bumps {@link #version()} so the menu can cheaply detect changes.
 */
public final class GinvDataStore {


    public enum ListState {
        NONE, WHITELIST, BLACKLIST;

        static ListState parse(String raw) {
            if (raw == null) return NONE;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "white", "whitelist" -> WHITELIST;
                case "black", "blacklist" -> BLACKLIST;
                default -> NONE;
            };
        }

        public String id() {
            return switch (this) {
                case WHITELIST -> "white";
                case BLACKLIST -> "black";
                default -> "none";
            };
        }
    }


    public record PlayerSnapshot(String name, int invites, long lastInviteMs, ListState listState) {
    }

    private static final class PlayerEntry {
        int invites;
        long lastInvite;
        String list = "none";

        PlayerEntry() {
        }

        PlayerEntry(int invites, long lastInvite, String list) {
            this.invites = invites;
            this.lastInvite = lastInvite;
            this.list = list;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static final int DEFAULT_MIN_DELAY_MS = 220;
    private static final int DEFAULT_MAX_DELAY_MS = 720;
    private static final int MIN_DELAY_BOUND = 50;
    private static final int MAX_DELAY_BOUND = 60_000;
    private static final double DEFAULT_UI_SCALE = 1.0;
    private static final double MIN_UI_SCALE = 0.5;
    private static final double MAX_UI_SCALE = 3.0;

    private static boolean loaded;
    private static int version;

    private static final Map<String, PlayerEntry> players = new LinkedHashMap<>();
    private static int minDelayMs = DEFAULT_MIN_DELAY_MS;
    private static int maxDelayMs = DEFAULT_MAX_DELAY_MS;
    private static boolean whitelistOnly;
    private static boolean alwaysOnTop;
    private static double uiScale = DEFAULT_UI_SCALE;
    private static String theme = "dusk";

    // Off by default: the menu opens at the 100% preset (a real preset, not a
    // viewport fit). The View menu's Autoscale switch opts in explicitly.
    private static boolean autoscale = false;

    private GinvDataStore() {
    }

    //paths

    private static Path storeDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("guildinvitefix");
    }

    private static Path playersFile() {
        return storeDir().resolve("players.json");
    }

    private static Path settingsFile() {
        return storeDir().resolve("settings.json");
    }

    //loading

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = playersFile();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root != null && root.has("players") && root.get("players").isJsonObject()) {
                        players.clear();
                        for (Map.Entry<String, com.google.gson.JsonElement> e
                                : root.getAsJsonObject("players").entrySet()) {
                            PlayerEntry entry = GSON.fromJson(e.getValue(), PlayerEntry.class);
                            if (entry != null) {
                                players.put(e.getKey(), entry);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            GuildInviteFix.LOGGER.error("[Ginv] Failed to load players.json, starting empty", e);
            players.clear();
        }

        try {
            Path file = settingsFile();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root != null) {
                        if (root.has("minDelayMs")) minDelayMs = clampDelay(root.get("minDelayMs").getAsInt());
                        if (root.has("maxDelayMs")) maxDelayMs = clampDelay(root.get("maxDelayMs").getAsInt());
                        if (root.has("whitelistOnly")) whitelistOnly = root.get("whitelistOnly").getAsBoolean();
                        if (root.has("alwaysOnTop")) alwaysOnTop = root.get("alwaysOnTop").getAsBoolean();
                        if (root.has("uiScale")) uiScale = clampScale(root.get("uiScale").getAsDouble());
                        // Missing key → default false (100% on first open).
                        if (root.has("autoscale")) autoscale = root.get("autoscale").getAsBoolean();
                        // Unknown/blank id → DUSK (GinvTheme.parse default).
                        if (root.has("theme")) theme = GinvTheme.parse(root.get("theme").getAsString()).id();
                    }
                }
            }
        } catch (Exception e) {
            GuildInviteFix.LOGGER.error("[Ginv] Failed to load settings.json, using defaults", e);
            minDelayMs = DEFAULT_MIN_DELAY_MS;
            maxDelayMs = DEFAULT_MAX_DELAY_MS;
            whitelistOnly = false;
            alwaysOnTop = false;
            uiScale = DEFAULT_UI_SCALE;
            theme = "dusk";
            autoscale = false;
        }

        if (minDelayMs > maxDelayMs) {
            int t = minDelayMs;
            minDelayMs = maxDelayMs;
            maxDelayMs = t;
        }
    }

    private static int clampDelay(int value) {
        return Math.max(MIN_DELAY_BOUND, Math.min(MAX_DELAY_BOUND, value));
    }

    private static double clampScale(double value) {
        if (!Double.isFinite(value)) return DEFAULT_UI_SCALE;
        return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, value));
    }



    private static void savePlayers() {
        try {
            Files.createDirectories(storeDir());
            JsonObject root = new JsonObject();
            JsonObject map = new JsonObject();
            for (Map.Entry<String, PlayerEntry> e : players.entrySet()) {
                map.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            }
            root.add("players", map);
            atomicWrite(playersFile(), GSON.toJson(root));
        } catch (IOException e) {
            GuildInviteFix.LOGGER.error("[Ginv] Failed to save players.json", e);
        }
    }

    private static void saveSettings() {
        try {
            Files.createDirectories(storeDir());
            JsonObject root = new JsonObject();
            root.addProperty("minDelayMs", minDelayMs);
            root.addProperty("maxDelayMs", maxDelayMs);
            root.addProperty("whitelistOnly", whitelistOnly);
            root.addProperty("alwaysOnTop", alwaysOnTop);
            root.addProperty("uiScale", uiScale);
            root.addProperty("autoscale", autoscale);
            root.addProperty("theme", theme);
            atomicWrite(settingsFile(), GSON.toJson(root));
        } catch (IOException e) {
            GuildInviteFix.LOGGER.error("[Ginv] Failed to save settings.json", e);
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    public static int version() {
        synchronized (LOCK) {
            ensureLoaded();
            return version;
        }
    }


    public static List<String> trackedNames() {
        synchronized (LOCK) {
            ensureLoaded();
            return List.copyOf(players.keySet());
        }
    }

    public static PlayerSnapshot snapshot(String name) {
        synchronized (LOCK) {
            ensureLoaded();
            PlayerEntry entry = find(name);
            if (entry == null) return null;
            return new PlayerSnapshot(name, entry.invites, entry.lastInvite, ListState.parse(entry.list));
        }
    }

    public static List<PlayerSnapshot> snapshots() {
        synchronized (LOCK) {
            ensureLoaded();
            List<PlayerSnapshot> out = new ArrayList<>(players.size());
            for (Map.Entry<String, PlayerEntry> e : players.entrySet()) {
                PlayerEntry entry = e.getValue();
                out.add(new PlayerSnapshot(e.getKey(), entry.invites, entry.lastInvite, ListState.parse(entry.list)));
            }
            return out;
        }
    }

    private static PlayerEntry find(String name) {
        PlayerEntry direct = players.get(name);
        if (direct != null) return direct;
        for (Map.Entry<String, PlayerEntry> e : players.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    private static String canonicalKey(String name) {
        if (players.containsKey(name)) return name;
        for (String key : players.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return name;
    }

    //Invite policy: Whether an invite must be sent right now.

    public static boolean isAllowed(String name) {
        synchronized (LOCK) {
            ensureLoaded();
            PlayerEntry entry = find(name);
            ListState state = entry == null ? ListState.NONE : ListState.parse(entry.list);
            if (state == ListState.BLACKLIST) return false;
            if (whitelistOnly && state != ListState.WHITELIST) return false;
            return true;
        }
    }

    /** Records a successfully sent invite; creates the entry on first sight. */
    public static void recordInvite(String name) {
        synchronized (LOCK) {
            ensureLoaded();
            PlayerEntry entry = find(name);
            String key = entry == null ? name : canonicalKey(name);
            if (entry == null) {
                entry = new PlayerEntry();
            }
            players.put(key, entry);
            entry.invites++;
            entry.lastInvite = System.currentTimeMillis();
            version++;
            savePlayers();
        }
    }




    public static boolean touch(String name) {
        synchronized (LOCK) {
            ensureLoaded();
            if (find(name) != null) return false;
            players.put(name, new PlayerEntry());
            version++;
            savePlayers();
            return true;
        }
    }


    public static void setListState(String name, ListState state) {
        synchronized (LOCK) {
            ensureLoaded();
            PlayerEntry entry = find(name);
            if (entry == null) {
                if (state == ListState.NONE) return;
                entry = new PlayerEntry();
                players.put(name, entry);
            }
            entry.list = state.id();
            if (state == ListState.NONE && entry.invites == 0 && entry.lastInvite == 0) {
                players.remove(canonicalKey(name));
            }
            version++;
            savePlayers();
        }
    }


    public static void removePlayer(String name) {
        synchronized (LOCK) {
            ensureLoaded();
            if (players.remove(canonicalKey(name)) != null) {
                version++;
                savePlayers();
            }
        }
    }

    //settings

    public static boolean whitelistOnly() {
        synchronized (LOCK) {
            ensureLoaded();
            return whitelistOnly;
        }
    }

    public static void setWhitelistOnly(boolean value) {
        synchronized (LOCK) {
            ensureLoaded();
            if (whitelistOnly != value) {
                whitelistOnly = value;
                version++;
                saveSettings();
            }
        }
    }

    public static boolean alwaysOnTop() {
        synchronized (LOCK) {
            ensureLoaded();
            return alwaysOnTop;
        }
    }

    public static void setAlwaysOnTop(boolean value) {
        synchronized (LOCK) {
            ensureLoaded();
            if (alwaysOnTop != value) {
                alwaysOnTop = value;
                version++;
                saveSettings();
            }
        }
    }


    public static double uiScale() {
        synchronized (LOCK) {
            ensureLoaded();
            return uiScale;
        }
    }


    public static void setUiScale(double value) {
        synchronized (LOCK) {
            ensureLoaded();
            double clamped = clampScale(value);
            if (clamped != uiScale) {
                uiScale = clamped;
                version++;
                saveSettings();
            }
        }
    }


    public static boolean autoscale() {
        synchronized (LOCK) {
            ensureLoaded();
            return autoscale;
        }
    }


    public static void setAutoscale(boolean value) {
        synchronized (LOCK) {
            ensureLoaded();
            if (autoscale != value) {
                autoscale = value;
                version++;
                saveSettings();
            }
        }
    }

    /** Persisted theme id (always a valid {@link GinvTheme} id; defaults to {@code "dusk"}). */
    public static String themeId() {
        synchronized (LOCK) {
            ensureLoaded();
            return theme;
        }
    }

    /**
     * Persists the selected theme. The id is normalized through
     * {@link GinvTheme#parse(String)} so an unknown value degrades to
     * {@code "dusk"}; the version bumps only on a real change.
     */
    public static void setTheme(String value) {
        synchronized (LOCK) {
            ensureLoaded();
            String normalized = GinvTheme.parse(value).id();
            if (!normalized.equals(theme)) {
                theme = normalized;
                version++;
                saveSettings();
            }
        }
    }

    public static int minDelayMs() {
        synchronized (LOCK) {
            ensureLoaded();
            return minDelayMs;
        }
    }

    public static int maxDelayMs() {
        synchronized (LOCK) {
            ensureLoaded();
            return maxDelayMs;
        }
    }

    public static void setDelays(int min, int max) {
        synchronized (LOCK) {
            ensureLoaded();
            int lo = clampDelay(min);
            int hi = clampDelay(max);
            if (lo > hi) {
                int t = lo;
                lo = hi;
                hi = t;
            }
            if (lo != minDelayMs || hi != maxDelayMs) {
                minDelayMs = lo;
                maxDelayMs = hi;
                version++;
                saveSettings();
            }
        }
    }


    public static void init() {
        synchronized (LOCK) {
            ensureLoaded();
        }
    }
}
