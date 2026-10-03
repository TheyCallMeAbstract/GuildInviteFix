package com.guildinvitefix.testing.layout;

import com.lowdragmc.lowdraglib2.uitest.ElementBounds;

import java.util.List;

/**
 * Pure, reusable geometry assertions for the uitest framework.
 *
 * <p>It powers the gap/symmetry uitest scenario: laid-out widgets are sampled into
 * {@link Box}es and checked for professional front-end spacing (consistent gaps
 * between siblings) and symmetry (balanced padding inside a container). Nothing
 * here touches a client, a screen or an element — every method is static and
 * operates on plain numbers, so the rules can be unit-tested headlessly.
 */
public final class LayoutAssert {

    /** Default allowed floating-point slack, in GUI-scaled pixels. */
    public static final float DEFAULT_TOLERANCE = 0.5f;

    private LayoutAssert() {
    }

    /**
     * An axis-aligned rectangle in the same GUI-scaled screen space as
     * {@link ElementBounds}, with the derived edges/centre precomputed on demand.
     */
    public record Box(float x, float y, float w, float h) {

        /** Maps an element's measured bounds onto a plain geometry box. */
        public static Box of(ElementBounds b) {
            return new Box(b.x(), b.y(), b.width(), b.height());
        }

        /** Right edge: {@code x + w}. */
        public float right() {
            return x + w;
        }

        /** Bottom edge: {@code y + h}. */
        public float bottom() {
            return y + h;
        }

        /** Horizontal centre: {@code x + w / 2}. */
        public float centerX() {
            return x + w / 2f;
        }

        /** Vertical centre: {@code y + h / 2}. */
        public float centerY() {
            return y + h / 2f;
        }

        /** Rectangle area: {@code w * h}. */
        public float area() {
            return w * h;
        }
    }

    /**
     * Horizontal gap between a left box and a right box: {@code right.x - left.right()}.
     * Negative when the boxes overlap horizontally.
     */
    public static float hGap(Box left, Box right) {
        return right.x() - left.right();
    }

    /**
     * Vertical gap between a top box and a bottom box: {@code bottom.y - top.bottom()}.
     * Negative when the boxes overlap vertically.
     */
    public static float vGap(Box top, Box bottom) {
        return bottom.y() - top.bottom();
    }

    /**
     * Whether every value matches the first within {@code tol}. True for fewer
     * than two values.
     */
    public static boolean uniform(List<Float> values, float tol) {
        if (values.size() < 2) {
            return true;
        }
        float first = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            if (Math.abs(values.get(i) - first) > tol) {
                return false;
            }
        }
        return true;
    }

    /** {@link #uniform(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean uniform(List<Float> values) {
        return uniform(values, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's left edge within {@code tol}. */
    public static boolean alignedLeft(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::x), tol);
    }

    /** {@link #alignedLeft(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean alignedLeft(List<Box> boxes) {
        return alignedLeft(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's right edge within {@code tol}. */
    public static boolean alignedRight(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::right), tol);
    }

    /** {@link #alignedRight(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean alignedRight(List<Box> boxes) {
        return alignedRight(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's horizontal centre within {@code tol}. */
    public static boolean alignedCenterX(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::centerX), tol);
    }

    /** {@link #alignedCenterX(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean alignedCenterX(List<Box> boxes) {
        return alignedCenterX(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's top edge within {@code tol}. */
    public static boolean alignedTop(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::y), tol);
    }

    /** {@link #alignedTop(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean alignedTop(List<Box> boxes) {
        return alignedTop(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's bottom edge within {@code tol}. */
    public static boolean alignedBottom(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::bottom), tol);
    }

    /** {@link #alignedBottom(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean alignedBottom(List<Box> boxes) {
        return alignedBottom(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's width within {@code tol}. */
    public static boolean sameWidth(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::w), tol);
    }

    /** {@link #sameWidth(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean sameWidth(List<Box> boxes) {
        return sameWidth(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's height within {@code tol}. */
    public static boolean sameHeight(List<Box> boxes, float tol) {
        return uniform(coords(boxes, Box::h), tol);
    }

    /** {@link #sameHeight(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean sameHeight(List<Box> boxes) {
        return sameHeight(boxes, DEFAULT_TOLERANCE);
    }

    /** Whether every box shares the first box's width and height within {@code tol}. */
    public static boolean sameSize(List<Box> boxes, float tol) {
        if (boxes.size() < 2) {
            return true;
        }
        Box first = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            if (Math.abs(b.w() - first.w()) > tol || Math.abs(b.h() - first.h()) > tol) {
                return false;
            }
        }
        return true;
    }

    /** {@link #sameSize(List, float)} using {@link #DEFAULT_TOLERANCE}. */
    public static boolean sameSize(List<Box> boxes) {
        return sameSize(boxes, DEFAULT_TOLERANCE);
    }

    /**
     * Whether the two rectangles are separated by more than {@code tol} on at
     * least one axis. Touch or overlap within {@code tol} counts as overlapping
     * and returns {@code false}.
     */
    public static boolean noOverlap(Box a, Box b, float tol) {
        return a.right() < b.x() - tol
                || b.right() < a.x() - tol
                || a.bottom() < b.y() - tol
                || b.bottom() < a.y() - tol;
    }

    /** Whether {@code inner} lies inside {@code outer} within {@code tol}. */
    public static boolean contains(Box outer, Box inner, float tol) {
        return inner.x() >= outer.x() - tol
                && inner.y() >= outer.y() - tol
                && inner.right() <= outer.right() + tol
                && inner.bottom() <= outer.bottom() + tol;
    }

    /** Whether {@code inner} has equal left and right padding inside {@code outer}. */
    public static boolean symmetricPaddingLR(Box outer, Box inner, float tol) {
        return Math.abs((inner.x() - outer.x()) - (outer.right() - inner.right())) <= tol;
    }

    /** Whether {@code inner} has equal top and bottom padding inside {@code outer}. */
    public static boolean symmetricPaddingTB(Box outer, Box inner, float tol) {
        return Math.abs((inner.y() - outer.y()) - (outer.bottom() - inner.bottom())) <= tol;
    }

    private static List<Float> coords(List<Box> boxes, java.util.function.ToDoubleFunction<Box> pick) {
        return boxes.stream().map(b -> (Float) (float) pick.applyAsDouble(b)).toList();
    }
}
