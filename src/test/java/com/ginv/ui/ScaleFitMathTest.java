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
    void fitFloorsAtOneOnATinyViewport() {
        // The 75% floor is gone: 100% is the smallest menu scale.
        assertEquals(1.0, GinvMenuScreen.fitScaleFor(10, 10, 1.0), 1e-9);
        assertEquals(1.0, GinvMenuScreen.fitScaleFor(10, 10, 2.0), 1e-9);
    }

    @Test
    void fitCapsAtDoubleOnAHugeViewport() {
        assertEquals(2.0, GinvMenuScreen.fitScaleFor(100_000, 100_000, 1.0), 1e-9);
    }

    @Test
    void fitTakesTheSmallerAxisRatioAboveTheFloor() {
        // 340×266 design: width ratio 0.96, height ratio 0.94 → height wins, but
        // 0.94 is below the 1.0 floor, so the result clamps to 1.0.
        assertEquals(1.0, GinvMenuScreen.fitScaleFor(340, 266, 1.0), 1e-9);
        // A viewport twice the design on both axes fits at 1.88 (height-bound).
        assertEquals(1.88, GinvMenuScreen.fitScaleFor(680, 532, 1.0), 1e-9);
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
            assertEquals(1.0, fit, 1e-9);
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
    void popupWidthUsesTheDesignWidthWhenTheViewportHasRoom() {
        // 1920×1080-ish viewport: plenty of room, keep the 340 design width.
        assertEquals(340f, GinvMenuScreen.popupShellWidth(960, 540, 340, 266, 1.45f), 1e-4);
    }

    @Test
    void popupWidthCapsTheAspectOnAShortWideViewport() {
        // Height-capped to 200×0.94 = 188; width must not exceed 188×1.45 = 272.6.
        assertEquals(272.6f, GinvMenuScreen.popupShellWidth(2000, 200, 340, 266, 1.45f), 1e-3);
    }

    @Test
    void popupWidthRespectsANarrowViewport() {
        // Width-bound: 0.96 of 150 = 144, below both the design width and the
        // aspect cap, so the viewport wins.
        assertEquals(144f, GinvMenuScreen.popupShellWidth(150, 1080, 340, 266, 1.45f), 1e-4);
    }

    @Test
    void popupWidthFallsBackToTheDesignOnADegenerateViewport() {
        assertEquals(340f, GinvMenuScreen.popupShellWidth(0, 0, 340, 266, 1.45f), 1e-4);
        assertEquals(340f, GinvMenuScreen.popupShellWidth(800, -1, 340, 266, 1.45f), 1e-4);
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
