package com.ginv.ui;

import com.ginv.data.GinvDataStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure scale math split out of {@code GinvMenuScreen} (T14): the
 * autoscale viewport fit (floor 0.75, cap 2.0, degenerate-viewport
 * fallback, never persisted) and the WYSIWYG pop-out size formula — both
 * callable without a client.
 */
class ScaleFitMathTest {

    @Test
    void fitFloorsAtThreeQuartersOnATinyViewport() {
        assertEquals(0.75, GinvMenuScreen.fitScaleFor(10, 10, 1.0), 1e-9);
    }

    @Test
    void fitCapsAtDoubleOnAHugeViewport() {
        assertEquals(2.0, GinvMenuScreen.fitScaleFor(100_000, 100_000, 1.0), 1e-9);
    }

    @Test
    void fitTakesTheSmallerAxisRatio() {
        // 340×266 design: width ratio 0.96, height ratio 0.94 → height wins.
        assertEquals(0.94, GinvMenuScreen.fitScaleFor(340, 266, 1.0), 1e-9);
    }

    @Test
    void degenerateViewportFallsBackToTheStoredPreset() {
        assertEquals(1.25, GinvMenuScreen.fitScaleFor(0, 480, 1.25), 1e-9);
        assertEquals(1.25, GinvMenuScreen.fitScaleFor(854, -1, 1.25), 1e-9);
    }

    @Test
    void fitIsPureAndNeverPersistsTheStoredPreset() {
        try {
            GinvDataStore.autoscale(); // probes the FabricLoader-backed store
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "GinvDataStore unavailable: " + t);
        }

        double original = GinvDataStore.uiScale();
        boolean flag = GinvDataStore.autoscale();
        try {
            GinvDataStore.setUiScale(1.25);
            GinvDataStore.setAutoscale(true);
            double fit = GinvMenuScreen.fitScaleFor(340, 266, GinvDataStore.uiScale());
            assertEquals(0.94, fit, 1e-9);
            assertEquals(1.25, GinvDataStore.uiScale(), 1e-9,
                    "the fit must never write the stored preset");
            assertTrue(GinvDataStore.autoscale(),
                    "the fit must never touch the autoscale flag");
        } finally {
            GinvDataStore.setUiScale(original);
            GinvDataStore.setAutoscale(flag);
        }
    }

    @Test
    void popoutSizeIsPanelTimesScalesWithinTenPercent() {
        assertEquals(680, GinvMenuScreen.popoutSizeFor(340, 2.0, 1.0, 0));
        double expected = 340 * 1.75 * 1.25;
        int got = GinvMenuScreen.popoutSizeFor(340, 1.75, 1.25, 0);
        assertTrue(Math.abs(got - expected) <= 0.10 * expected,
                () -> got + " deviates more than 10% from " + expected);
    }

    @Test
    void popoutSizeHonoursThePlatformWindowFloor() {
        assertEquals(200, GinvMenuScreen.popoutSizeFor(10, 2.0, 1.0, 200));
    }

    @Test
    void windowedScaleCarriesTheGuiScaleSoThePopoutFills() {
        // u() divides windowed values by the GUI scale and the WYSIWYG window
        // opens at panel × GUI scale: without carrying the factor here the
        // content rendered at 1/guiScale of the window (hero 85px → 24px).
        assertEquals(3.55, GinvMenuScreen.windowedScaleFor(1.775, 2.0), 1e-9);
        assertEquals(2.0, GinvMenuScreen.windowedScaleFor(1.0, 2.0), 1e-9);
        assertEquals(1.25, GinvMenuScreen.windowedScaleFor(1.25, 1.0), 1e-9);
    }
}
