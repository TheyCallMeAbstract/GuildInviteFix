package com.ginv.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure resize math behind the popped-out window's aspect-locked,
 * minimum-floored edge drag. Mirrors {@code ScaleFitMathTest} style: no
 * client, just the geometry.
 *
 * <p>Base case: the 340×266 shell at (100,100), so
 * {@code aspect = 340.0/266.0} and both minima equal the grab bounds.
 */
class WindowReflowTest {

    private static final double ASPECT = 340.0 / 266.0;
    private static final int MIN_W = 340;
    private static final int MIN_H = 266;

    private static int[] grab(int edges, int dx, int dy) {
        return WindowReflow.resize(edges, 100, 100, 340, 266, dx, dy, MIN_W, MIN_H, ASPECT);
    }

    @Test
    void rightEdgeInwardStopsAtTheBase() {
        assertArrayEquals(new int[]{100, 100, 340, 266}, grab(WindowReflow.RIGHT, -500, 0));
    }

    @Test
    void rightEdgeOutwardGrowsProportionally() {
        assertArrayEquals(new int[]{100, 100, 440, 344}, grab(WindowReflow.RIGHT, 100, 0));
    }

    @Test
    void bottomEdgeOutwardGrowsProportionally() {
        assertArrayEquals(new int[]{100, 100, 468, 366}, grab(WindowReflow.BOTTOM, 0, 100));
    }

    @Test
    void leftEdgeInwardStopsAtTheBase() {
        assertArrayEquals(new int[]{100, 100, 340, 266}, grab(WindowReflow.LEFT, 500, 0));
    }

    @Test
    void leftEdgeOutwardKeepsTheRightEdgeAnchored() {
        // x = 100 + (340 - 440).
        assertArrayEquals(new int[]{0, 100, 440, 344}, grab(WindowReflow.LEFT, -100, 0));
    }

    @Test
    void topEdgeOutwardKeepsTheBottomEdgeAnchored() {
        // y = 100 + (266 - 366); x is untouched (top edge only).
        assertArrayEquals(new int[]{100, 0, 468, 366}, grab(WindowReflow.TOP, 0, -100));
    }

    @Test
    void cornerUsesTheWidthDriver() {
        // Bottom-right outward: the width drives, the height follows the aspect,
        // and the top-left corner stays put.
        assertArrayEquals(new int[]{100, 100, 440, 344},
                grab(WindowReflow.RIGHT | WindowReflow.BOTTOM, 100, 100));
    }

    @Test
    void degenerateAspectReturnsTheGrabRect() {
        assertArrayEquals(new int[]{100, 100, 340, 266},
                WindowReflow.resize(WindowReflow.RIGHT, 100, 100, 340, 266, 100, 100,
                        MIN_W, MIN_H, 0));
        assertArrayEquals(new int[]{100, 100, 340, 266},
                WindowReflow.resize(WindowReflow.RIGHT, 100, 100, 340, 266, 100, 100,
                        MIN_W, MIN_H, -1));
    }

    @Test
    void everyResizeSatisfiesTheMinimumsAndTheAspect() {
        int[][] cases = {
                grab(WindowReflow.RIGHT, -500, 0),
                grab(WindowReflow.RIGHT, 100, 0),
                grab(WindowReflow.LEFT, -100, 0),
                grab(WindowReflow.BOTTOM, 0, 100),
                grab(WindowReflow.TOP, 0, -100),
                grab(WindowReflow.RIGHT | WindowReflow.BOTTOM, 100, 100),
                grab(WindowReflow.LEFT | WindowReflow.TOP, -100, -100),
                grab(WindowReflow.RIGHT | WindowReflow.BOTTOM, -500, -500),
        };
        for (int[] rect : cases) {
            int w = rect[2];
            int h = rect[3];
            assertTrue(w >= MIN_W, () -> "width " + w + " below minimum " + MIN_W);
            assertTrue(h >= MIN_H, () -> "height " + h + " below minimum " + MIN_H);
            assertTrue(Math.abs((double) w / h - ASPECT) <= 0.02,
                    () -> w + "x" + h + " ratio deviates from " + ASPECT);
        }
    }

    @Test
    void derivedDimensionNeverDropsBelowTheMinimumOnRounding() {
        // A base whose minima are not exactly on the aspect line: the driver
        // clamp must still leave both dimensions at or above their minimum.
        double aspect = 1.3;
        int[] rect = WindowReflow.resize(WindowReflow.BOTTOM, 0, 0, 300, 231, 0, 0,
                300, 231, aspect);
        assertEquals(300, rect[2]);
        assertTrue(rect[3] >= 231, () -> "height " + rect[3]);
    }
}
