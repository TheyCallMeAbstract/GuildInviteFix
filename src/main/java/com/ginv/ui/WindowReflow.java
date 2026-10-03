package com.ginv.ui;

/**
 * Pure geometry for the popped-out menu window's constrained edge resize.
 *
 * <p>{@code GinvMenuWindow} captures the window's opening configuration as a
 * base ({@code minW x minH}, aspect {@code minW/minH}) and, while the user
 * drags an edge, asks this helper for the anchored rectangle to apply. The
 * result is always on the base's aspect line and never below either minimum,
 * so the fixed-design layout can be zoomed upward but never squeezed into a
 * collapsed state.
 *
 * <p>No Minecraft or LDLib2 types appear here on purpose, so the math is
 * unit-testable headlessly ({@code WindowReflowTest}).
 *
 * <p>The edge bits mirror {@code ModularUIWindow}'s private constants:
 * {@code LEFT=1, RIGHT=2, TOP=4, BOTTOM=8}.
 */
final class WindowReflow {

    static final int LEFT = 1;
    static final int RIGHT = 1 << 1;
    static final int TOP = 1 << 2;
    static final int BOTTOM = 1 << 3;

    private WindowReflow() {
    }

    /**
     * Computes the anchored {@code {x, y, w, h}} for an in-flight resize.
     *
     * <p>The driver dimension is the width for horizontal edges (including a
     * corner, which has both) and the height for vertical-only edges. The
     * other dimension is derived from the driver via {@code aspect}; the
     * driver is then raised to the smallest value on the aspect line that
     * satisfies both minima, so the pair can never dip below the base.
     *
     * @param edges  bit mask of grabbed edges
     * @param grabX  window x at grab time
     * @param grabY  window y at grab time
     * @param grabW  window width at grab time
     * @param grabH  window height at grab time
     * @param dx     cursor delta x since the grab, in window pixels
     * @param dy     cursor delta y since the grab, in window pixels
     * @param minW   minimum width (the opening width)
     * @param minH   minimum height (the opening height)
     * @param aspect {@code minW/minH}, the locked width:height ratio
     * @return {@code {x, y, width, height}} to apply
     */
    static int[] resize(int edges, int grabX, int grabY, int grabW, int grabH,
                        int dx, int dy, int minW, int minH, double aspect) {
        if (aspect <= 0 || minW <= 0 || minH <= 0) {
            // Degenerate base: hand back the grab rect, clamped to whatever
            // minima we were given, so a caller still gets a usable window.
            return new int[]{grabX, grabY, Math.max(grabW, minW), Math.max(grabH, minH)};
        }

        boolean horizontal = (edges & (LEFT | RIGHT)) != 0;
        boolean vertical = (edges & (TOP | BOTTOM)) != 0;

        int w;
        int h;
        if (horizontal) {
            // Corner (both axes) uses the width driver.
            int driver = (edges & LEFT) != 0 ? grabW - dx : grabW + dx;
            int minDriver = Math.max(minW, (int) Math.ceil(minH * aspect));
            w = Math.max(driver, minDriver);
            h = (int) Math.round(w / aspect);
        } else if (vertical) {
            int driver = (edges & TOP) != 0 ? grabH - dy : grabH + dy;
            int minDriver = Math.max(minH, (int) Math.ceil(minW / aspect));
            h = Math.max(driver, minDriver);
            w = (int) Math.round(h * aspect);
        } else {
            return new int[]{grabX, grabY, grabW, grabH};
        }

        int x = (edges & LEFT) != 0 ? grabX + (grabW - w) : grabX;
        int y = (edges & TOP) != 0 ? grabY + (grabH - h) : grabY;
        return new int[]{x, y, w, h};
    }
}
