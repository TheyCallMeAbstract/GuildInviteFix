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

    /** The menu window tracked for focus/single-instance purposes. */
    @Nullable
    private static GinvMenuWindow tracked;

    /** Which in-game mode this window was popped out of (popup vs screen). */
    private final boolean popupOrigin;

    private long lastDragPressAt;
    private double lastDragX;
    private double lastDragY;
    private boolean swallowNextRelease;

    public GinvMenuWindow(ModularUI modularUI, String title, boolean popupOrigin) {
        super(modularUI, title);
        this.popupOrigin = popupOrigin;
    }

    /**
     * The mode the window was popped out of, so the re-dock button can return
     * the menu to exactly the screen it left (popup overlay or full screen).
     */
    public boolean popupOrigin() {
        return popupOrigin;
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

        lastDragPressAt = now;
        lastDragX = x;
        lastDragY = y;
        return super.beginGesture();
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
