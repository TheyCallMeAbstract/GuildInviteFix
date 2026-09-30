package com.ginv.command;

/**
 * Outcome of a level-based queue attempt, shared by {@code /glvl}'s chat
 * feedback and the menu Control tab's status-bar feedback — one source of
 * truth, two renderers.
 *
 * @param error           why the attempt failed, or {@link Error#NONE}
 * @param queued          how many invites were queued (success only)
 * @param skippedNoLevel  tab players with a prefix but no number (or NPCs are
 *                        excluded entirely — same counts the command reports)
 * @param skippedLowLevel tab players below the requested threshold
 */
public record LevelQueueResult(Error error, int queued, int skippedNoLevel, int skippedLowLevel) {

    /** Typed failure kinds, so neither caller has to parse messages. */
    public enum Error {
        NONE, NOT_SKYBLOCK, NOT_CONNECTED, NO_MATCHES
    }

    public boolean ok() {
        return error == Error.NONE;
    }

    /** A failure with no scan results behind it. */
    public static LevelQueueResult fail(Error error) {
        return new LevelQueueResult(error, 0, 0, 0);
    }
}
