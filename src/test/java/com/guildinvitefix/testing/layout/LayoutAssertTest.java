package com.guildinvitefix.testing.layout;

import com.guildinvitefix.testing.layout.LayoutAssert.Box;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless geometry checks for {@link LayoutAssert} — the gap/symmetry rules
 * the uitest framework uses to judge spacing. Boxes are built directly, so no
 * client or element is involved.
 */
class LayoutAssertTest {

    @Test
    void boxOfMapsElementBounds() {
        ElementBounds bounds = new ElementBounds(10f, 20f, 30f, 40f, new Vector2f(25f, 40f),
                new Vector2f[]{new Vector2f(10f, 20f), new Vector2f(40f, 20f),
                        new Vector2f(40f, 60f), new Vector2f(10f, 60f)});

        Box box = Box.of(bounds);

        assertEquals(10f, box.x(), 1e-6f);
        assertEquals(20f, box.y(), 1e-6f);
        assertEquals(30f, box.w(), 1e-6f);
        assertEquals(40f, box.h(), 1e-6f);
    }

    @Test
    void boxDerivedEdgesCentreAndArea() {
        Box box = new Box(10f, 20f, 30f, 40f);

        assertEquals(40f, box.right(), 1e-6f);
        assertEquals(60f, box.bottom(), 1e-6f);
        assertEquals(25f, box.centerX(), 1e-6f);
        assertEquals(40f, box.centerY(), 1e-6f);
        assertEquals(1200f, box.area(), 1e-6f);
    }

    @Test
    void horizontalAndVerticalGaps() {
        assertEquals(10f, LayoutAssert.hGap(new Box(0, 0, 10, 10), new Box(20, 0, 10, 10)), 1e-6f);
        assertEquals(5f, LayoutAssert.vGap(new Box(0, 0, 10, 10), new Box(0, 15, 10, 10)), 1e-6f);
    }

    @Test
    void gapsAreNegativeOnOverlap() {
        assertEquals(-5f, LayoutAssert.hGap(new Box(0, 0, 20, 10), new Box(15, 0, 20, 10)), 1e-6f);
        assertEquals(-5f, LayoutAssert.vGap(new Box(0, 0, 10, 20), new Box(0, 15, 10, 20)), 1e-6f);
    }

    @Test
    void uniformEqualUnequal() {
        assertTrue(LayoutAssert.uniform(List.of(3f, 3.2f, 2.8f), 0.5f));
        assertFalse(LayoutAssert.uniform(List.of(3f, 4f), 0.5f));
    }

    @Test
    void uniformSingleAndEmptyAreTriviallyTrue() {
        assertTrue(LayoutAssert.uniform(List.of(42f), 0.1f));
        assertTrue(LayoutAssert.uniform(List.of(), 0.1f));
    }

    @Test
    void alignedLeftAndRight() {
        List<Box> left = List.of(new Box(0, 0, 10, 10), new Box(0.3f, 20, 10, 10));
        assertTrue(LayoutAssert.alignedLeft(left, 0.5f));

        List<Box> right = List.of(new Box(0, 0, 10, 10), new Box(5, 20, 5, 10));
        assertTrue(LayoutAssert.alignedRight(right, 0.5f));
        assertFalse(LayoutAssert.alignedLeft(List.of(new Box(0, 0, 10, 10), new Box(2, 0, 10, 10)), 0.5f));
        assertFalse(LayoutAssert.alignedRight(List.of(new Box(0, 0, 10, 10), new Box(0, 0, 12, 10)), 0.5f));
    }

    @Test
    void alignedCenterTopAndBottom() {
        List<Box> centre = List.of(new Box(0, 0, 10, 10), new Box(0, 20, 10, 10));
        assertTrue(LayoutAssert.alignedCenterX(centre, 0.5f));

        List<Box> top = List.of(new Box(0, 0, 10, 10), new Box(20, 0.2f, 10, 10));
        assertTrue(LayoutAssert.alignedTop(top, 0.5f));

        List<Box> bottom = List.of(new Box(0, 0, 10, 10), new Box(20, 0, 10, 10));
        assertTrue(LayoutAssert.alignedBottom(bottom, 0.5f));
        assertFalse(LayoutAssert.alignedCenterX(List.of(new Box(0, 0, 10, 10), new Box(20, 0, 10, 10)), 0.5f));
        assertFalse(LayoutAssert.alignedTop(List.of(new Box(0, 0, 10, 10), new Box(0, 3, 10, 10)), 0.5f));
        assertFalse(LayoutAssert.alignedBottom(List.of(new Box(0, 0, 10, 10), new Box(0, 0, 10, 13)), 0.5f));
    }

    @Test
    void sameWidthHeightAndSize() {
        List<Box> equal = List.of(new Box(0, 0, 10, 20), new Box(30, 0, 10, 20));
        assertTrue(LayoutAssert.sameWidth(equal, 0.5f));
        assertTrue(LayoutAssert.sameHeight(equal, 0.5f));
        assertTrue(LayoutAssert.sameSize(equal, 0.5f));

        List<Box> mismatched = List.of(new Box(0, 0, 10, 20), new Box(30, 0, 12, 20));
        assertFalse(LayoutAssert.sameWidth(mismatched, 0.5f));
        assertTrue(LayoutAssert.sameHeight(mismatched, 0.5f));
        assertFalse(LayoutAssert.sameSize(mismatched, 0.5f));
    }

    @Test
    void noOverlapTrueWhenSeparated() {
        assertTrue(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(20, 0, 10, 10), 0.5f));
        assertTrue(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(0, 20, 10, 10), 0.5f));
    }

    @Test
    void noOverlapFalseWhenTouchingOrNested() {
        assertFalse(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(10, 0, 10, 10), 0.5f));
        assertFalse(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(2, 2, 4, 4), 0.5f));
        assertFalse(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(10.4f, 0, 10, 10), 0.5f));
        assertTrue(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(10.6f, 0, 10, 10), 0.5f));
    }

    @Test
    void containsInsideAndEqual() {
        Box outer = new Box(0, 0, 100, 100);
        assertTrue(LayoutAssert.contains(outer, new Box(10, 10, 50, 50), 0.5f));
        assertTrue(LayoutAssert.contains(outer, outer, 0.5f));
    }

    @Test
    void containsFalseOnOverflow() {
        Box outer = new Box(0, 0, 100, 100);
        assertFalse(LayoutAssert.contains(outer, new Box(-5, 0, 10, 10), 0.5f));
        assertFalse(LayoutAssert.contains(outer, new Box(0, 0, 101, 50), 0.5f));
    }

    @Test
    void symmetricPaddingLeftRight() {
        Box outer = new Box(0, 0, 100, 100);
        assertTrue(LayoutAssert.symmetricPaddingLR(outer, new Box(10, 0, 80, 100), 0.5f));
        assertFalse(LayoutAssert.symmetricPaddingLR(outer, new Box(10, 0, 70, 100), 0.5f));
    }

    @Test
    void symmetricPaddingTopBottom() {
        Box outer = new Box(0, 0, 100, 100);
        assertTrue(LayoutAssert.symmetricPaddingTB(outer, new Box(0, 10, 100, 80), 0.5f));
        assertFalse(LayoutAssert.symmetricPaddingTB(outer, new Box(0, 10, 100, 70), 0.5f));
    }

    @Test
    void defaultToleranceOverloadsAndBoundary() {
        assertEquals(0.5f, LayoutAssert.DEFAULT_TOLERANCE, 1e-6f);

        assertTrue(LayoutAssert.uniform(List.of(1f, 1.5f)));
        assertFalse(LayoutAssert.uniform(List.of(1f, 1.6f)));
        assertTrue(LayoutAssert.alignedLeft(List.of(new Box(0, 0, 10, 10), new Box(0.5f, 5, 10, 10))));
        assertTrue(LayoutAssert.sameSize(List.of(new Box(0, 0, 10, 10), new Box(5, 5, 10.5f, 9.5f))));
        assertFalse(LayoutAssert.sameWidth(List.of(new Box(0, 0, 10, 10), new Box(0, 0, 11, 10))));
        assertTrue(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(10.6f, 0, 10, 10), LayoutAssert.DEFAULT_TOLERANCE));
        assertFalse(LayoutAssert.noOverlap(new Box(0, 0, 10, 10), new Box(10.5f, 0, 10, 10), LayoutAssert.DEFAULT_TOLERANCE));
    }
}
