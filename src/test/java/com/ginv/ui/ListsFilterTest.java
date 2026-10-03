package com.ginv.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Lists tab's pure name/level contract, split out of {@code GinvMenuScreen}
 * so it is testable without a client: case-insensitive substring queries,
 * inclusive bounds, unknown-level exclusion once bounded, and blank/non-numeric
 * fields parsing to unset (never zero).
 */
class ListsFilterTest {

    @Test
    void emptyMatchesEverything() {
        assertTrue(ListsFilter.EMPTY.matches("Alice", 42));
        assertTrue(ListsFilter.EMPTY.matches("Carol", null));
        assertFalse(ListsFilter.EMPTY.isActive());
    }

    @Test
    void queryIsCaseInsensitiveSubstring() {
        ListsFilter filter = new ListsFilter("ali", null, null);
        assertTrue(filter.matches("Alice", 1));
        assertTrue(filter.matches("ALICIA", 1));
        assertFalse(filter.matches("Bob", 1));
        assertTrue(filter.isActive());
    }

    @Test
    void queryIsTrimmedAndBlankBecomesEmpty() {
        ListsFilter filter = new ListsFilter("   ", null, null);
        assertEquals("", filter.query());
        assertFalse(filter.isActive());
    }

    @Test
    void boundsAreInclusive() {
        ListsFilter filter = ListsFilter.parse("", "40", "42");
        assertTrue(filter.matches("Alice", 40));
        assertTrue(filter.matches("Alice", 42));
        assertFalse(filter.matches("Alice", 39));
        assertFalse(filter.matches("Alice", 43));
    }

    @Test
    void unknownLevelShownWhenUnboundedButExcludedWhenBounded() {
        assertTrue(new ListsFilter("", null, null).matches("Carol", null));
        assertFalse(ListsFilter.parse("", "40", null).matches("Carol", null));
        assertFalse(ListsFilter.parse("", null, "50").matches("Carol", null));
    }

    @Test
    void blankAndNonNumericBoundsParseToNull() {
        ListsFilter blank = ListsFilter.parse("", "", "  ");
        assertNull(blank.minLevel());
        assertNull(blank.maxLevel());
        assertEquals("", blank.query());
        assertFalse(blank.isActive());

        ListsFilter malformed = ListsFilter.parse("", "abc", "4x2");
        assertNull(malformed.minLevel());
        assertNull(malformed.maxLevel());
    }

    @Test
    void minGreaterThanMaxMatchesNothing() {
        ListsFilter filter = ListsFilter.parse("", "50", "40");
        assertFalse(filter.matches("Alice", 45));
        assertFalse(filter.matches("Alice", 60));
        assertFalse(filter.matches("Alice", 30));
    }
}
