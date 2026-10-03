package com.ginv.data;

import com.ginv.data.GinvDataStore.ListState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary tests for the pure TTL/allow rules behind temporary lists. No store,
 * no {@code FabricLoader} — these always run.
 */
class ListPolicyTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void nonPositiveExpiryNeverExpires() {
        assertFalse(ListPolicy.isExpired(0L, NOW), "missing/legacy expiry reads as permanent");
        assertFalse(ListPolicy.isExpired(-1L, NOW));
    }

    @Test
    void expiryBoundaryIsInclusive() {
        assertFalse(ListPolicy.isExpired(NOW + 1, NOW), "still in the future");
        assertTrue(ListPolicy.isExpired(NOW, NOW), "the exact instant counts as lapsed");
        assertTrue(ListPolicy.isExpired(NOW - 1, NOW), "already past");
    }

    @Test
    void blacklistAlwaysBlocks() {
        assertFalse(ListPolicy.allows(ListState.BLACKLIST, false));
        assertFalse(ListPolicy.allows(ListState.BLACKLIST, true));
    }

    @Test
    void whitelistEntryIsAllowedInBothModes() {
        assertTrue(ListPolicy.allows(ListState.WHITELIST, false));
        assertTrue(ListPolicy.allows(ListState.WHITELIST, true));
    }

    @Test
    void whitelistOnlyBlocksUnlistedPlayers() {
        assertFalse(ListPolicy.allows(ListState.NONE, true));
        assertTrue(ListPolicy.allows(ListState.NONE, false));
    }
}
