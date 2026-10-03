package com.ginv.data;

/**
 * Preset durations for temporary blacklist entries. {@link #FOREVER} maps to
 * the same {@code now + 100y} sentinel the store uses for permanent entries.
 * Pure: no Fabric or Minecraft imports, so it is headless-testable.
 */
public enum ListDuration {
    DAYS_3,
    DAYS_7,
    DAYS_14,
    DAYS_30,
    FOREVER;

    static final long PERMANENT_TTL_MS = 100L * 365 * 24 * 60 * 60 * 1000;
    private static final long DAY_MS = 86_400_000L;

    public static final ListDuration DEFAULT = DAYS_7;
    public static final long DEFAULT_MS = 7 * DAY_MS;

    public long millis() {
        return switch (this) {
            case DAYS_3 -> 3 * DAY_MS;
            case DAYS_7 -> 7 * DAY_MS;
            case DAYS_14 -> 14 * DAY_MS;
            case DAYS_30 -> 30 * DAY_MS;
            case FOREVER -> PERMANENT_TTL_MS;
        };
    }

    public String displayName() {
        return switch (this) {
            case DAYS_3 -> "3 days";
            case DAYS_7 -> "7 days";
            case DAYS_14 -> "14 days";
            case DAYS_30 -> "30 days";
            case FOREVER -> "Forever";
        };
    }

    /** Exact preset match for a stored millisecond value, else {@link #DEFAULT}. */
    public static ListDuration nearest(long millis) {
        for (ListDuration candidate : values()) {
            if (candidate.millis() == millis) {
                return candidate;
            }
        }
        return DEFAULT;
    }

    /** Clamps an arbitrary millisecond value to [1 minute, 100 years]. */
    public static long clamp(long millis) {
        return Math.max(60_000L, Math.min(PERMANENT_TTL_MS, millis));
    }
}
