package com.ginv.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Target-string parsing for {@code /ginv} and the menu's Queue button —
 * whitespace-split, order-preserving, deduplicating.
 */
class ParseTargetsTest {

    @Test
    void splitsOnWhitespacePreservingOrder() {
        var targets = GinvCommand.parseTargets("Alice  Bob\tCarol");
        assertEquals(List.of("Alice", "Bob", "Carol"), List.copyOf(targets));
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        var targets = GinvCommand.parseTargets("  Alice   ");
        assertEquals(List.of("Alice"), List.copyOf(targets));
    }

    @Test
    void deduplicatesWhileKeepingFirstOccurrence() {
        var targets = GinvCommand.parseTargets("Alice Bob Alice");
        assertEquals(List.of("Alice", "Bob"), List.copyOf(targets));
    }

    @Test
    void nullAndBlankYieldAnEmptySet() {
        assertTrue(GinvCommand.parseTargets(null).isEmpty());
        assertTrue(GinvCommand.parseTargets("").isEmpty());
        assertTrue(GinvCommand.parseTargets("   ").isEmpty());
    }

    @Test
    void commasAreNotSeparators() {
        // Documents the actual contract: whitespace only. "Alice,Bob" is one
        // (invalid) player name, not two targets.
        var targets = GinvCommand.parseTargets("Alice,Bob");
        assertEquals(List.of("Alice,Bob"), List.copyOf(targets));
    }
}
