package com.ginv.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListDurationTest {

    @Test
    void millisMapsEachPreset() {
        assertEquals(3L * 86_400_000L, ListDuration.DAYS_3.millis());
        assertEquals(7L * 86_400_000L, ListDuration.DAYS_7.millis());
        assertEquals(14L * 86_400_000L, ListDuration.DAYS_14.millis());
        assertEquals(30L * 86_400_000L, ListDuration.DAYS_30.millis());
        assertEquals(ListDuration.PERMANENT_TTL_MS, ListDuration.FOREVER.millis());
    }

    @Test
    void displayNamesAreExact() {
        assertEquals("3 days", ListDuration.DAYS_3.displayName());
        assertEquals("7 days", ListDuration.DAYS_7.displayName());
        assertEquals("14 days", ListDuration.DAYS_14.displayName());
        assertEquals("30 days", ListDuration.DAYS_30.displayName());
        assertEquals("Forever", ListDuration.FOREVER.displayName());
    }

    @Test
    void clampBounds() {
        assertEquals(60_000L, ListDuration.clamp(0L));
        assertEquals(60_000L, ListDuration.clamp(-5L));
        assertEquals(ListDuration.PERMANENT_TTL_MS, ListDuration.clamp(Long.MAX_VALUE));
        assertEquals(123_456L, ListDuration.clamp(123_456L));
    }

    @Test
    void nearestFallsBackToDefault() {
        assertEquals(ListDuration.DAYS_3, ListDuration.nearest(ListDuration.DAYS_3.millis()));
        assertEquals(ListDuration.DAYS_30, ListDuration.nearest(ListDuration.DAYS_30.millis()));
        assertEquals(ListDuration.FOREVER, ListDuration.nearest(ListDuration.PERMANENT_TTL_MS));
        assertEquals(ListDuration.DAYS_7, ListDuration.nearest(123L));
    }

    @Test
    void defaultIsSevenDays() {
        assertEquals(ListDuration.DAYS_7, ListDuration.DEFAULT);
        assertEquals(ListDuration.DAYS_7.millis(), ListDuration.DEFAULT_MS);
    }
}
