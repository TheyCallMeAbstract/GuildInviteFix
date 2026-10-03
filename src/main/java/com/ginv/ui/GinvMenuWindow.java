package com.ginv.ui;

import com.lowdragmc.lowdraglib2.client.window.OsWindowEvent;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.window.ModularUIWindow;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The menu's operating-system window.
 *
 * <p>{@link ModularUIWindow} already provides moving, edge resizing, maximize
 * and the close-request hook; this subclass adds the two Windows behaviors it
 * deliberately does not ship:
 *
 * <ul>
 *   <li><b>Double-click the title bar</b> to maximize/restore. The press that
 *       starts a move is consumed by the window before the UI sees it, so the
 *       double-click has to be recognized at the gesture edge — detected here
 *       by time and distance, with the follow-up release swallowed.</li>
 *   <li><b>Esc closes the window</b>, but the first press goes to a focused
 *       text field instead of closing out from under it — the same caution
 *       LDLib2's own debugger documents for its F12 binding.</li>
 * </ul>
 *
 * <p>Also holds the single-instance reference used by {@code /gmenu} to focus
 * an already-open menu window rather than opening a second one.
 */
public class GinvMenuWindow extends ModularUIWindow {

    /** Maximum gap between the two presses of a title-bar double-click. */
    private static final long DOUBLE_CLICK_MS = 350;
    /** Maximum cursor travel between the two presses of a double-click. */
    private static final double DOUBLE_CLICK_SLOP = 3;

    // Edge bits, mirroring ModularUIWindow's private constants. The parent
    // dispatches beginGesture()/applyGesture() virtually, so a subclass can
    // mirror the resize state it cannot read.
    private static final int WINDOW_EDGE_LEFT = 1;
    private static final int WINDOW_EDGE_RIGHT = 1 << 1;
    private static final int WINDOW_EDGE_TOP = 1 << 2;
    private static final int WINDOW_EDGE_BOTTOM = 1 << 3;

    /** The menu window tracked for focus/single-instance purposes. */
    @Nullable
    private static GinvMenuWindow tracked;

    private long lastDragPressAt;
    private double lastDragX;
    private double lastDragY;
    private boolean swallowNextRelease;

    // --- constrained-resize mirror (set from GinvMenuScreen.popOut) ---
    /** Opening width; also the resize floor. */
    private int reflowMinW;
    /** Opening height; also the resize floor. */
    private int reflowMinH;
    /** Locked width:height ratio, {@code reflowMinW / reflowMinH}. */
    private double reflowAspect;
    /** Grabbed edge mask for the current resize gesture; 0 when not resizing. */
    private int reflowEdges;
    private int reflowGrabX;
    private int reflowGrabY;
    private int reflowGrabW;
    private int reflowGrabH;
    private double reflowGrabGlobalX;
    private double reflowGrabGlobalY;
    /** Whether a usable base has been set; the constraint is inert without one. */
    private boolean reflowBaseSet;

    public GinvMenuWindow(ModularUI modularUI, String title) {
        super(modularUI, title);
    }

    // ----------------------------------------------------------- single instance

    /** Remembers the just-opened window so {@link #focusExisting()} can find it. */
    public static void track(GinvMenuWindow window) {
        tracked = window;
    }

    /** The tracked window if it is still open, otherwise {@code null}. */
    @Nullable
    public static GinvMenuWindow active() {
        return tracked != null && tracked.isOpen() ? tracked : null;
    }

    /**
     * Brings an already-open menu window to the front.
     *
     * @return {@code true} if there was one, so the caller can skip opening
     *         another copy (screen or window)
     */
    public static boolean focusExisting() {
        var window = active();
        if (window == null) return false;
        window.window().focus();
        return true;
    }

    // -------------------------------------------------------- resize constraint

    /**
     * Sets the window's opening configuration as the resize base: both minima
     * and the locked aspect ratio. Called by {@code GinvMenuScreen.popOut}
     * right after the window is built, so a resize can only zoom the window
     * upward from a known-good layout. A degenerate pair leaves the
     * constraint inert ({@code applyGesture} then defers to {@code super}).
     */
    public void setReflowBase(int w, int h) {
        if (w > 0 && h > 0) {
            reflowMinW = w;
            reflowMinH = h;
            reflowAspect = (double) w / h;
            reflowBaseSet = true;
        } else {
            reflowBaseSet = false;
        }
    }

    /**
     * Scales the resize base by {@code ratio}, keeping the aspect ratio, so
     * the floor tracks a programmatic window resize (a scale-preset change).
     * No-op until {@link #setReflowBase} has run or for a non-positive ratio.
     */
    public void scaleReflowBase(double ratio) {
        if (!reflowBaseSet || ratio <= 0) return;
        reflowMinW = (int) Math.round(reflowMinW * ratio);
        reflowMinH = (int) Math.round(reflowMinH * ratio);
    }

    /**
     * Carries this window's resize base over to a freshly rebuilt one
     * ({@code GinvMenuScreen.rebuildActiveWindow} replaces the instance while
     * keeping the geometry, so the new window must keep the floor too).
     */
    void copyReflowBaseFrom(GinvMenuWindow other) {
        reflowMinW = other.reflowMinW;
        reflowMinH = other.reflowMinH;
        reflowAspect = other.reflowAspect;
        reflowBaseSet = other.reflowBaseSet;
    }

    // -------------------------------------------------------- title-bar gesture

    /**
     * Recognizes a double-click on the title bar before the press becomes a
     * move gesture, then maximizes or restores instead.
     *
     * <p>Edge presses stay with the resize logic; only presses over the drag
     * area are candidates, and only when they land close in time and space to
     * the previous one.
     */
    @Override
    protected boolean beginGesture() {
        reflowEdges = 0;
        var current = window();
        double x = current.getCursorX();
        double y = current.getCursorY();
        int width = current.getWindowWidth();
        int height = current.getWindowHeight();
        boolean atEdge = x <= RESIZE_BORDER || x >= width - RESIZE_BORDER
                || y <= RESIZE_BORDER || y >= height - RESIZE_BORDER;
        long now = System.currentTimeMillis();

        if (!atEdge && isOverDragArea()
                && now - lastDragPressAt <= DOUBLE_CLICK_MS
                && Math.abs(x - lastDragX) <= DOUBLE_CLICK_SLOP
                && Math.abs(y - lastDragY) <= DOUBLE_CLICK_SLOP) {
            swallowNextRelease = true;
            toggleMaximized();
            return true; // the press is consumed; no move starts
        }

        // Mirror the parent's resize gesture. The parent keeps its edge mask
        // and grab rect private, so capture our own copy from the same inputs;
        // applyGesture() then feeds them to the aspect-locked reflow. Only
        // when not maximized — a maximized window reports no edges, and that
        // is what keeps maximize/restore out of the constraint.
        if (!current.isMaximized()) {
            int edges = 0;
            if (x <= RESIZE_BORDER) edges |= WINDOW_EDGE_LEFT;
            if (x >= width - RESIZE_BORDER) edges |= WINDOW_EDGE_RIGHT;
            if (y <= RESIZE_BORDER) edges |= WINDOW_EDGE_TOP;
            if (y >= height - RESIZE_BORDER) edges |= WINDOW_EDGE_BOTTOM;
            if (edges != 0) {
                var global = current.queryGlobalCursor();
                reflowEdges = edges;
                reflowGrabX = current.getPositionX();
                reflowGrabY = current.getPositionY();
                reflowGrabW = width;
                reflowGrabH = height;
                reflowGrabGlobalX = global[0];
                reflowGrabGlobalY = global[1];
            }
        }

        lastDragPressAt = now;
        lastDragX = x;
        lastDragY = y;
        return super.beginGesture();
    }

    /**
     * Applies an in-flight resize through {@link WindowReflow}: the window
     * zooms on its opening aspect line, anchored at the opposite edge, and
     * never drops below the opening size. Moves, and every window without a
     * reflow base, keep the stock {@code super} behavior.
     */
    @Override
    protected void applyGesture() {
        if (reflowBaseSet && reflowEdges != 0) {
            var current = window();
            var global = current.queryGlobalCursor();
            int dx = (int) Math.round(global[0] - reflowGrabGlobalX);
            int dy = (int) Math.round(global[1] - reflowGrabGlobalY);
            int[] rect = WindowReflow.resize(reflowEdges, reflowGrabX, reflowGrabY,
                    reflowGrabW, reflowGrabH, dx, dy,
                    reflowMinW, reflowMinH, reflowAspect);
            current.setPosition(rect[0], rect[1]);
            current.setSize(rect[2], rect[3]);
            return;
        }
        super.applyGesture();
    }

    // -------------------------------------------------------------------- input

    /**
     * Esc and the double-click's orphaned release, caught at the window edge
     * before the UI sees them — key events otherwise go only to the focused
     * element, so a global chord cannot be an ordinary listener.
     */
    @Override
    protected void handleEvent(OsWindowEvent event) {
        if (event instanceof OsWindowEvent.MouseButton mouse
                && mouse.button() == GLFW.GLFW_MOUSE_BUTTON_1
                && mouse.action() == GLFW.GLFW_RELEASE
                && swallowNextRelease) {
            swallowNextRelease = false;
            return;
        }
        if (event instanceof OsWindowEvent.Key key
                && key.action() == GLFW.GLFW_PRESS
                && key.key() == GLFW.GLFW_KEY_ESCAPE) {
            var ui = getModularUI();
            if (isEditing()) {
                // First Esc: leave the text field. Second: close.
                ui.clearFocus();
                return;
            }
            onCloseRequested();
            return;
        }
        super.handleEvent(event);
    }

    /** Whether a text field currently holds focus (the {@code __focused__} marker). */
    private boolean isEditing() {
        for (var element : getModularUI().getAllElements()) {
            if (element instanceof TextField && element.hasClass("__focused__")) {
                return true;
            }
        }
        return false;
    }
}
