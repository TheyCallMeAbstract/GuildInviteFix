package com.ginv.data;

/**
 * Pure decision helpers for temporary whitelist/blacklist entries.
 *
 * <p>{@link GinvDataStore} owns persistence and the lapse side effect; this
 * class owns the two rules that actually make a TTL work: when an entry has
 * expired, and whether a (post-expiry) state still blocks an invite. Staying
 * static and side-effect-free lets the headless JUnit suite hit every boundary
 * without a store or a {@code FabricLoader}.
 */
final class ListPolicy {

    private ListPolicy() {
    }

    /**
     * Whether a list entry has lapsed at {@code nowMs}.
     *
     * <p>A non-positive {@code expiresAt} is <b>permanent</b> — legacy entries
     * predate the field and read back as {@code 0} — and never expires.
     */
    static boolean isExpired(long expiresAt, long nowMs) {
        return expiresAt > 0 && nowMs >= expiresAt;
    }

    /**
     * Invite policy for a list state that already had expiry folded in: a
     * blacklist always blocks; whitelist-only mode additionally requires a
     * whitelist entry.
     */
    static boolean allows(GinvDataStore.ListState state, boolean whitelistOnly) {
        if (state == GinvDataStore.ListState.BLACKLIST) return false;
        if (whitelistOnly && state != GinvDataStore.ListState.WHITELIST) return false;
        return true;
    }
}
