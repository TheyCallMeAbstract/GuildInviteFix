package com.ginv.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-exclusion predicate and the fresh-start freeze default. Both are pure
 * headless checks: {@code isSelf} touches no Minecraft state and the freeze
 * flag is a plain static boolean.
 */
class GinvSelfExclusionTest {

    @Test
    void isSelfTrueForCaseInsensitiveEqual() {
        assertTrue(GinvCommand.isSelf("Alice", "alice"));
        assertTrue(GinvCommand.isSelf("ALICE", "Alice"));
        assertTrue(GinvCommand.isSelf("Steve", "Steve"));
    }

    @Test
    void isSelfFalseForNullsAndDifferentNames() {
        assertFalse(GinvCommand.isSelf(null, "Alice"));
        assertFalse(GinvCommand.isSelf("Alice", null));
        assertFalse(GinvCommand.isSelf(null, null));
        assertFalse(GinvCommand.isSelf("Alice", "Bob"));
    }

    @Test
    void startsFrozenAndTogglesRoundTrip() {
        assertTrue(GinvCommand.isFrozen(), "a fresh process starts frozen");
        try {
            GinvCommand.toggleFreeze();
            assertFalse(GinvCommand.isFrozen(), "first toggle resumes");
            GinvCommand.toggleFreeze();
            assertTrue(GinvCommand.isFrozen(), "second toggle freezes again");
        } finally {
            if (!GinvCommand.isFrozen()) {
                GinvCommand.toggleFreeze();
            }
        }
    }
}
