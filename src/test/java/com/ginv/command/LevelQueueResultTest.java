package com.ginv.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queue-attempt result contract shared by {@code /glvl}'s chat feedback
 * and the menu's status-bar feedback.
 */
class LevelQueueResultTest {

    @Test
    void failCarriesTheErrorWithZeroCounts() {
        var result = LevelQueueResult.fail(LevelQueueResult.Error.NOT_SKYBLOCK);

        assertEquals(LevelQueueResult.Error.NOT_SKYBLOCK, result.error());
        assertFalse(result.ok());
        assertEquals(0, result.queued());
        assertEquals(0, result.skippedNoLevel());
        assertEquals(0, result.skippedLowLevel());
    }

    @Test
    void successCarriesQueuedAndSkipCounts() {
        var result = new LevelQueueResult(LevelQueueResult.Error.NONE, 2, 1, 1);

        assertTrue(result.ok());
        assertEquals(2, result.queued());
        assertEquals(1, result.skippedNoLevel());
        assertEquals(1, result.skippedLowLevel());
    }

    @Test
    void nonNoneErrorsAreNotOk() {
        assertFalse(new LevelQueueResult(LevelQueueResult.Error.NOT_CONNECTED, 0, 0, 0).ok());
        assertFalse(new LevelQueueResult(LevelQueueResult.Error.NO_MATCHES, 0, 3, 4).ok());
    }

    @Test
    void errorKindsCoverEveryFailureTheCallersRender() {
        assertArrayEquals(new LevelQueueResult.Error[]{
                        LevelQueueResult.Error.NONE,
                        LevelQueueResult.Error.NOT_SKYBLOCK,
                        LevelQueueResult.Error.NOT_CONNECTED,
                        LevelQueueResult.Error.NO_MATCHES
                }, LevelQueueResult.Error.values());
    }
}
