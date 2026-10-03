package com.ginv.ui;

import com.ginv.GuildInviteFix;
import com.ginv.command.GinvCommand;
import com.ginv.command.LevelQueueResult;
import com.ginv.data.GinvDataStore;
import com.ginv.data.ListDuration;
import com.ginv.ui.theme.GinvTheme;
import com.ginv.ui.widget.GinvPageHost;
import com.ginv.ui.widget.GinvSettingsForm;
import com.ginv.ui.widget.GinvTable;
import com.ginv.utils.GuildDirectory;
import com.ginv.utils.GuildLevels;
import com.ginv.utils.SkyBlockDetector;
import com.lowdragmc.lowdraglib2.client.window.OsWindow;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.window.ModularUIWindow;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * The guild invite menu: Control / Lists / Settings.
 *
 * <p>The menu opens as a popup overlay: a transparent screen background that
 * closes on an outside click. There is no separate dimmed/full-screen mode.
 * The title bar's pop-out button rebuilds the menu inside an OS window
 * (LDLib2 {@code ModularUIWindow}) with full program-window chrome — title
 * bar with re-dock, maximize/restore, always-on-top pin, edge resize and a
 * status bar. Both contexts are themed by the persisted {@link GinvTheme}
 * (LDLib2 base sheet + the subtree-local {@code office-<id>.lss} mod delta
 * attached on the root); an unknown id or a missing delta degrades to DUSK.
 *
 * <p><b>Scale:</b> every authored pixel goes through {@link #u(double)}.
 * In-screen, values multiply by the persisted {@code uiScale} (or the
 * autoscale fit, see {@link #fitUiScale()}) on top of MC's own GUI scale. In
 * the OS window they divide by the game's GUI scale, and the window opens at
 * the <b>measured</b> bounds of the live in-screen panel (≥200×150) so the
 * pop-out is WYSIWYG; the legacy {@code base × uiScale / contentScale}
 * formula is the documented fallback only. Scale changes and GUI-scale
 * changes rebuild the current context.
 *
 * <p>Row lists rebuild when {@link GinvDataStore#version()}, the tab list (or
 * its levels) or the target set change; the status line, banner and
 * preconditions refresh every tick.
 */
public class GinvMenuScreen extends ModularUIScreen {

    /** Menu scale presets offered by the View popover. 100% is the floor. */
    private static final double[] SCALE_PRESETS = {1.0, 1.25, 1.5, 2.0};
    /** Pop-out window size in authored pixels at uiScale 1 / contentScale 1. */
    private static final int BASE_WINDOW_WIDTH = 420;
    private static final int BASE_WINDOW_HEIGHT = 300;
    /** Shell design size in authored pixels: the autoscale back-solve target. */
    private static final float SHELL_MAX_WIDTH = 340;
    private static final float SHELL_MAX_HEIGHT = 266;
    /**
     * Popup page max aspect (width : height). The shell's width is clamped to
     * the effective (viewport-capped) height × this ratio so the popup never
     * stretches into a letterbox on a short/wide viewport. See
     * {@link #popupShellWidth}.
     */
    private static final float SHELL_MAX_ASPECT = 1.45f;
    /** Vanilla Minecraft font, used for player-row text (head + name + level). */
    private static final Identifier VANILLA_FONT = Identifier.withDefaultNamespace("default");

    /** Spacing scale in authored units, scaled by u(): S1..S5 = 2/4/6/8/12. */
    private static final double SPACE_1 = 2;
    private static final double SPACE_2 = 4;
    private static final double SPACE_3 = 6;
    private static final double SPACE_4 = 8;
    private static final double SPACE_5 = 12;

    /** Borderless row-action sprites (whitelist page / blacklist page / bolt). */
    private static final IGuiTexture WHITELIST_ICON =
            SpriteTexture.of("guildinvitefix:textures/gui/whitelist.png");
    private static final IGuiTexture BLACKLIST_ICON =
            SpriteTexture.of("guildinvitefix:textures/gui/blacklist.png");
    private static final IGuiTexture BOLT_ICON =
            SpriteTexture.of("guildinvitefix:textures/gui/bolt.png");

    // --- scale context (client thread only; set on every buildLayout entry) ---

    /** {@code uiScale} for the layout currently being built. */
    private static double uiScaleContext = 1.0;
    /** Whether the layout currently being built targets the OS window. */
    private static boolean windowedContext = false;
    /** Windowed {@code uiScale}: the popped screen's scale × its GUI scale. */
    private static double windowedUiScale = Double.NaN;

    /**
     * Authored pixels → canvas units for the active context.
     *
     * <p>In-screen: {@code x × S} on top of MC's GUI scale (MC owns that
     * canvas). Windowed: {@code x × S_w / guiScale}, which cancels the window's
     * guiScale-dependent canvas so proportions and physical size stay put
     * while the window itself is sized in physical pixels. {@code S_w} is
     * {@link #windowedScale()}: the measured screen scale carrying its GUI
     * scale, so the layout exactly fills the panel × GUI-scale window —
     * without that factor the pop-out rendered at 1/guiScale of the window
     * (hero 85px → 24px at uiScale 1.775 / guiScale 2).
     */
    private static float u(double x) {
        if (!windowedContext) return (float) (x * uiScaleContext);
        return (float) (x * uiScaleContext / mcGuiScale());
    }

    private static double mcGuiScale() {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return scale > 0 ? scale : 1;
    }

    /** Main-window framebuffer pixels per screen pixel; 1 when unknown. */
    private static double contentScale() {
        var window = Minecraft.getInstance().getWindow();
        int screen = window.getScreenWidth();
        if (screen <= 0) return 1;
        double scale = window.getWidth() / (double) screen;
        return scale > 0 ? scale : 1;
    }

    /**
     * Authored scale for a windowed rebuild: the popped screen's scale that
     * the panel was measured with, carrying that screen's GUI scale. The
     * WYSIWYG window opens at panel × GUI scale and {@link #u(double)} divides
     * windowed values by the GUI scale again — dropping the factor here is
     * exactly what squashed the pop-out (fixes hero 85px → 24px). Frozen in
     * {@link #popOut} so later guiScale rebuilds keep the same proportions.
     */
    private static double windowedScale() {
        if (!Double.isNaN(windowedUiScale)) return windowedUiScale;
        return windowedScaleFor(GinvDataStore.uiScale(), mcGuiScale());
    }

    /** Pure part of {@link #windowedScale()} (headless-testable, T14). */
    static double windowedScaleFor(double screenScale, double guiScale) {
        return screenScale * guiScale;
    }

    /** Remembered tab index, restored on rebuild (client thread only). */
    private static int savedTab = 0;

    /** The active persisted theme; an unknown id degrades to DUSK inside parse. */
    private static GinvTheme activeTheme() {
        return GinvTheme.parse(GinvDataStore.themeId());
    }

    /** Everything the constructor needs, assembled statically before the screen exists. */
    private record Layout(
            GinvRoot root,
            UIElement panel,
            Stylesheet baseSheet
    ) {
    }

    public GinvMenuScreen() {
        this(buildLayout(false));
    }

    private GinvMenuScreen(Layout layout) {
        super(new ModularUI(UI.of(layout.root(), layout.baseSheet())), Component.literal("BWD"));

        UIElement panel = layout.panel();
        GinvRoot root = layout.root();
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) return;
            float left = panel.getPositionX();
            float top = panel.getPositionY();
            boolean inside = event.x >= left && event.x <= left + panel.getSizeWidth()
                    && event.y >= top && event.y <= top + panel.getSizeHeight();
            // The View popover is anchored inside the panel: a press on it
            // is never an outside press, even if it somehow overflowed the
            // panel.
            if (!inside && root.viewPopover != null
                    && (within(root.viewPopover, event.x, event.y)
                        || inSubtree(root.viewPopover, event.target))) {
                inside = true;
            }
            if (!inside && root.blacklistPopover != null
                    && (within(root.blacklistPopover, event.x, event.y)
                        || inSubtree(root.blacklistPopover, event.target))) {
                inside = true;
            }
            if (!inside) {
                onClose();
            }
        });
    }

    // --------------------------------------------------------------- build

    private static Layout buildLayout(boolean windowed) {
        uiScaleContext = windowed ? windowedScale() : GinvDataStore.uiScale();
        // Autoscale (T8): fit the 340x266 design into the viewport before any
        // u() call. In-game only — the OS window owns its geometry — and never
        // persisted: the stored preset stays untouched until one is clicked.
        if (!windowed && GinvDataStore.autoscale()) {
            uiScaleContext = fitUiScale();
        }
        windowedContext = windowed;

        // Resolve the active theme once per build. A missing generated delta is
        // never fatal: log and fall back to the DUSK base with no delta (design
        // §Error Handling). The chosen base is threaded through Layout so the
        // screen/window constructor uses the same sheet.
        GinvTheme theme = activeTheme();
        Stylesheet delta = theme.deltaSheet();
        Stylesheet base;
        if (delta == Stylesheet.EMPTY) {
            GuildInviteFix.LOGGER.warn(
                    "[Ginv] Theme '{}' has no generated delta sheet; falling back to DUSK base",
                    theme.id());
            base = GinvTheme.DUSK.baseSheet();
            delta = null;
        } else {
            base = theme.baseSheet();
            if (base == Stylesheet.EMPTY) {
                GuildInviteFix.LOGGER.warn(
                        "[Ginv] Theme '{}' base sheet missing; falling back to DUSK base",
                        theme.id());
                base = GinvTheme.DUSK.baseSheet();
            }
        }

        // Created before the title bar so the pop-out button can capture it for
        // the "no second window available" feedback path.
        Label feedbackLabel = new Label();
        feedbackLabel.setId("ginv_feedback");
        feedbackLabel.setText("");
        feedbackLabel.textStyle(style -> style.fontSize(u(9)).adaptiveHeight(true));

        GinvRoot root = new GinvRoot();
        root.setId("ginv_root");
        root.windowed = windowed;
        root.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            if (!windowed) {
                layout.justifyContent(AlignContent.CENTER);
                layout.alignItems(AlignItems.CENTER);
            }
        });
        if (windowed) {
            // The OS window supplies the frame; the sheet paints the dark
            // chrome surface via the ginv-windowed class (B1/D4).
            root.addClass("ginv-windowed");
        }
        // Theme delta rides the subtree only (D2), attached before UI.of so the
        // style engine picks it up on registration. Null delta = the missing-
        // sheet fallback above (DUSK base, no delta).
        if (delta != null) {
            root.addLocalStylesheet(delta);
        }

        // Shell: transparent centered card owning the responsive max-width
        // and the 340x266 design height (both Java tokens, inline so the
        // 75–200% preset still scales them); the sheet owns max-height:94%.
        // The definite height is load-bearing: the panel's body/pane chain
        // tree resolves percentage heights against it (a content-sized shell
        // starves the TabView and the fixed regions overlap).
        UIElement shell = new UIElement();
        shell.addClass("ginv-shell");
        // Popup aspect clamp: cap the shell width to the effective (viewport-
        // capped) height × SHELL_MAX_ASPECT so a short/wide viewport can't
        // letterbox the card. Windowed geometry is owned by the OS window.
        float popupMaxWidth = SHELL_MAX_WIDTH;
        if (!windowed) {
            var window = Minecraft.getInstance().getWindow();
            popupMaxWidth = popupShellWidth(
                    (float) (window.getGuiScaledWidth() / uiScaleContext),
                    (float) (window.getGuiScaledHeight() / uiScaleContext),
                    SHELL_MAX_WIDTH, SHELL_MAX_HEIGHT, SHELL_MAX_ASPECT);
        }
        final float shellMaxWidth = popupMaxWidth;
        shell.layout(layout -> {
            layout.widthPercent(100);
            layout.gapAll(u(SPACE_2));
            if (!windowed) {
                layout.maxWidth(u(shellMaxWidth));
                layout.maxHeightPercent(94);
                layout.height(u(SHELL_MAX_HEIGHT));
            } else {
                layout.maxWidthPercent(100);
                layout.maxHeightPercent(100);
            }
        });

        UIElement panel = new UIElement();
        panel.setId("ginv_panel");
        root.panel = panel;
        // Geometry is Java-owned (D6); office-dusk.lss keeps flex/paint (#ginv_panel).
        panel.layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(u(SPACE_4));
            layout.gapAll(u(SPACE_3));
        });
        shell.addChild(panel);
        root.addChild(shell);

        // Top bar ----------------------------------------------------------
        UIElement topBar = new UIElement();
        topBar.setId("ginv_topbar");
        topBar.addClass("ginv-topbar");
        // Geometry is Java-owned (D6): the screen bar is 14u with 3u side
        // padding and 3u gaps; the windowed bar is 15u with 6/1u padding and
        // 2u gaps. Paint stays in office-dusk.lss (.ginv-topbar / .ginv-windowed).
        topBar.layout(layout -> {
            layout.widthPercent(100);
            if (windowed) {
                layout.height(u(16));
                layout.minHeight(u(16));
                layout.paddingHorizontal(u(SPACE_3));
                layout.paddingVertical(u(1));
                layout.gapAll(u(SPACE_1));
            } else {
                layout.height(u(16));
                layout.minHeight(u(16));
                layout.paddingHorizontal(u(SPACE_3));
                layout.gapAll(u(SPACE_2));
            }
        });

        // Left: app icon + wordmark.
        UIElement appIcon = new UIElement();
        appIcon.layout(layout -> {
            layout.width(u(10));
            layout.height(u(10));
        });
        appIcon.getStyle().backgroundTexture(SpriteTexture.of("guildinvitefix:ginvfixicon.png"));
        topBar.addChild(appIcon);

        Label titleLabel = new Label();
        titleLabel.setText("BWD");
        titleLabel.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        titleLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        topBar.addChild(titleLabel);

        // SkyBlock chip: centered over the bar via a click-through overlay,
        // not parked in the right cluster — the overlay spans the bar so the
        // chip's centre is the bar's centre regardless of how wide the left
        // (icon + title) and right (View + chrome) clusters end up. Only the
        // chip is interactive; the layer itself ignores hit-testing.
        UIElement skyLayer = new UIElement();
        skyLayer.addClass("ginv-topbar-center");
        skyLayer.setAllowHitTest(false);
        SkyBlockStatusElement skyChip = new SkyBlockStatusElement();
        skyChip.layout(layout -> {
            layout.paddingLeft(u(SPACE_5));
            layout.paddingRight(u(SPACE_2));
            layout.paddingVertical(u(SPACE_1));
            layout.gapAll(u(SPACE_2));
        });
        skyChip.getStateLabel().textStyle(style -> style.fontSize(u(9)).adaptiveHeight(true));
        skyChip.setSkyOn(SkyBlockDetector.isSkyBlock());
        skyLayer.addChild(skyChip);
        topBar.addChild(skyLayer);
        root.skyChip = skyChip;

        Button viewButton = new Button();
        viewButton.setId("ginv_view_menu");
        viewButton.setText("View ▾");
        viewButton.layout(layout -> {
            layout.width(u(30));
            layout.height(u(11));
        });
        viewButton.textStyle(style -> style.fontSize(u(9)).adaptiveHeight(true));
        viewButton.getStyle().tooltips("View options");
        topBar.addChild(viewButton);

        // All window buttons read GinvMenuWindow.active() at click time,
        // so they need no rewiring after the window exists.
        if (windowed) {
            Button pinButton = null;
            if (OsWindow.supportsAlwaysOnTop()) {
                pinButton = chromeButton(
                        DynamicTexture.of(() -> GinvMenuWindow.active() != null
                                && GinvMenuWindow.active().isAlwaysOnTop()
                                ? Icons.MAGNET
                                : Icons.MAGNET.copy().setColor(ColorPattern.GRAY.color)),
                        "Always on top", false);
                pinButton.setId("ginv_always_on_top");
                pinButton.setOnClick(event -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) {
                        boolean onTop = !window.isAlwaysOnTop();
                        window.setAlwaysOnTop(onTop);
                        GinvDataStore.setAlwaysOnTop(onTop);
                    }
                });
                topBar.addChild(pinButton);
            }

            // Re-dock: back into the in-game popup menu.
            Button dockButton = chromeButton(Icons.LEFT, "Back into the game", false);
            dockButton.setId("ginv_redock");
            dockButton.setOnClick(event -> {
                GinvMenuWindow window = GinvMenuWindow.active();
                if (window == null) return;
                Minecraft.getInstance().setScreen(new GinvMenuScreen());
                window.onCloseRequested();
            });

            Button maximizeButton = chromeButton(
                    DynamicTexture.of(() -> GinvMenuWindow.active() != null
                            && GinvMenuWindow.active().isMaximized()
                            ? Icons.WINDOW_RESTORE
                            : Icons.WINDOW_MAXIMIZE),
                    "Maximize", false);
            maximizeButton.setId("ginv_maximize");
            maximizeButton.setOnClick(event -> {
                GinvMenuWindow window = GinvMenuWindow.active();
                if (window != null) window.toggleMaximized();
            });

            Button closeButton = chromeButton(Icons.WINDOW_CLOSE, "Close (Esc)", true);
            closeButton.setId("ginv_close");
            closeButton.setOnClick(event -> {
                GinvMenuWindow window = GinvMenuWindow.active();
                if (window != null) window.onCloseRequested();
            });

            topBar.addChildren(dockButton, maximizeButton, closeButton);
            root.titleBar = topBar;
            root.pinButton = pinButton;
            root.maximizeButton = maximizeButton;
            root.closeButton = closeButton;
        } else {
            Button popOutButton = new Button();
            popOutButton.setId("ginv_popout");
            popOutButton.setText("↗");
            popOutButton.layout(layout -> {
                layout.width(u(16));
                layout.height(u(12));
            });
            popOutButton.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
            popOutButton.getStyle().tooltips("Pop out into its own window");
            popOutButton.setOnClick(event -> popOut(feedbackLabel, panel));

            // Light top bar in-screen (office-dusk.lss #F6F6F6): the stock white
            // glyph would vanish on it, so tint it dark here; the windowed
            // chrome (dark #18181B) keeps the white original above.
            Button closeButton = chromeButton(Icons.WINDOW_CLOSE, "Close (Esc)", true);
            // Dark glyph for the light in-screen bar; office-dusk.lss tints the
            // pre-icon via the ginv-close-onscreen class (B8).
            closeButton.addClass("ginv-close-onscreen");
            closeButton.setId("ginv_close");
            closeButton.setOnClick(event -> Minecraft.getInstance().setScreen(null));

            topBar.addChildren(popOutButton, closeButton);
            root.titleBar = topBar;
            root.closeButton = closeButton;
        }
        panel.addChild(topBar);

        // View popover (D5): an absolute overlay anchored to #ginv_panel
        // (the sheet marks it position: relative), built hidden; opened by the
        // anchor, dismissed by a press outside anchor+popover or by a root
        // geometry change (viewport resize).
        UIElement viewPopover = new UIElement();
        viewPopover.setId("ginv_view_popover");
        viewPopover.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(u(2));
            // Clears the bar: absolute offsets resolve against #ginv_panel's
            // padding box, so the popover top must include the panel padding
            // (8) plus the topbar height (16) plus a hairline gap — otherwise
            // the dropdown opens over the bar and clips the SkyBlock chip.
            layout.top(u(26));
            layout.width(u(210));
            layout.maxHeight(u(220));
            layout.paddingAll(u(SPACE_4));
            layout.gapAll(u(SPACE_3));
        });
        viewPopover.getStyle().zIndex(1);
        viewPopover.setDisplay(false);

        Label autoLabel = bodyLabel("Autoscale");
        autoLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        Switch autoSwitch = new Switch();
        autoSwitch.setId("ginv_autoscale");
        autoSwitch.setOn(GinvDataStore.autoscale(), false);
        autoSwitch.registerValueListener(value -> {
            boolean on = Boolean.TRUE.equals(value);
            GinvDataStore.setAutoscale(on);
            if (on) {
                // The fit is baked into u() at build time — refit next tick.
                root.pendingScaleRebuild = true;
            }
        });
        autoSwitch.getStyle().tooltips("Fit the menu to the screen automatically");
        UIElement autoRow = row(u(14));
        autoRow.addChildren(autoLabel, autoSwitch);
        viewPopover.addChild(autoRow);

        // Menu scale: the Settings-tab presets, moved here wholesale (same
        // ginv_scale_* ids the uitest contract targets).
        viewPopover.addChild(caption("Menu scale"));
        ToggleGroupElement scaleGroup = new ToggleGroupElement();
        scaleGroup.addClass("ginv-scale-group");
        scaleGroup.layout(layout -> {
            layout.flexWrap(FlexWrap.WRAP);
            layout.widthPercent(100);
            layout.paddingAll(u(SPACE_1));
            layout.gapAll(u(SPACE_1));
        });
        for (double preset : SCALE_PRESETS) {
            Toggle toggle = new Toggle();
            toggle.setId("ginv_scale_" + Math.round(preset * 100));
            toggle.setText(scalePresetLabel(preset));
            sizeGroupToggle(toggle);
                toggle.toggleLabel(label -> {
                    label.textStyle(style -> style
                            .fontSize(u(10))
                            .adaptiveHeight(true)
                            // Center the preset text in its segment at every
                            // scale (the mock centers it; Toggle's default is
                            // LEFT, and a fixed-height label would otherwise
                            // ride the top under STRETCH).
                            .textAlignHorizontal(Horizontal.CENTER)
                            .textAlignVertical(Vertical.CENTER));
                    label.layout(layout -> {
                        layout.paddingLeft(u(2));
                        layout.paddingRight(u(2));
                    });
                });
            toggle.setOn(Math.abs(GinvDataStore.uiScale() - preset) < 1e-6, false);
            toggle.setOnToggleChanged(isOn -> {
                if (Boolean.TRUE.equals(isOn)) {
                    GinvDataStore.setAutoscale(false); // an explicit preset wins
                    GinvDataStore.setUiScale(preset);
                    root.pendingScaleRebuild = true;
                }
            });
            scaleGroup.addChild(toggle);
        }
        viewPopover.addChild(scaleGroup);

        // Theme selection lives in the Settings tab only (T8) — it is a
        // persisted preference, not a view option, so the View popover is the
        // scale presets + Autoscale. There is no mode switch: the menu always
        // opens as the popup overlay (the OS window is a separate surface).

        panel.addChild(viewPopover);
        root.viewPopover = viewPopover;
        root.viewAnchor = viewButton;
        root.viewOpen = false;
        viewButton.setOnClick(event -> toggleViewPopover(root));

        // Blacklist-duration popover (right-click a Lists row's blacklist
        // icon): one hidden absolute overlay on #ginv_panel, shown at the
        // cursor and clamped inside the panel. Built before the first
        // fillListsTable so row triggers always find it.
        UIElement blacklistPopover = new UIElement();
        blacklistPopover.setId("ginv_blacklist_duration_popover");
        blacklistPopover.addClass("ginv-popover");
        blacklistPopover.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(u(120));
            layout.paddingAll(u(SPACE_4));
            layout.gapAll(u(SPACE_2));
        });
        blacklistPopover.getStyle().zIndex(1);
        blacklistPopover.setDisplay(false);
        for (ListDuration duration : ListDuration.values()) {
            Button option = new Button();
            option.setId("ginv_blacklist_duration_" + durationIdSuffix(duration));
            option.setText(duration.displayName());
            option.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
            option.layout(layout -> {
                layout.widthPercent(100);
                layout.height(u(16));
                layout.minHeight(u(16));
            });
            option.setOnClick(event -> {
                if (root.blacklistPopoverTarget != null) {
                    GinvDataStore.setListState(root.blacklistPopoverTarget,
                            GinvDataStore.ListState.BLACKLIST, duration);
                }
                hideBlacklistPopover(root);
            });
            blacklistPopover.addChild(option);
        }
        panel.addChild(blacklistPopover);
        root.blacklistPopover = blacklistPopover;
        root.blacklistPopoverOpen = false;

        // Dismissal: a bubbling press that missed both anchor and popover —
        // geometrically or as the event's target subtree — and a genuine root
        // size change (viewport resize). LAYOUT_CHANGED does not bubble, but
        // it DOES fire on the root when a child's geometry changes the root's
        // content size. The popover is now an out-of-flow child of #ginv_panel,
        // so open/close no longer perturbs the root's size and only a real
        // resize/rebuild closes.
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) return;
            if (root.viewOpen
                    && !(within(root.viewAnchor, event.x, event.y)
                        || within(root.viewPopover, event.x, event.y)
                        || inSubtree(root.viewAnchor, event.target)
                        || inSubtree(root.viewPopover, event.target))) {
                hideViewPopover(root);
            }
            if (root.listsFilterOpen
                    && !(within(root.listsFilterMenu, event.x, event.y)
                        || within(root.listsFilterPopover, event.x, event.y)
                        || inSubtree(root.listsFilterMenu, event.target)
                        || inSubtree(root.listsFilterPopover, event.target))) {
                hideListsFilterPopover(root);
            }
            if (root.blacklistPopoverOpen
                    && !(within(root.blacklistPopover, event.x, event.y)
                        || inSubtree(root.blacklistPopover, event.target))) {
                hideBlacklistPopover(root);
            }
        });
        root.addEventListener(UIEvents.LAYOUT_CHANGED, event -> {
            float width = root.getSizeWidth();
            float height = root.getSizeHeight();
            boolean resized = width != root.lastRootWidth || height != root.lastRootHeight;
            root.lastRootWidth = width;
            root.lastRootHeight = height;
            // Child-driven contentSize churn (popover open/close) leaves the
            // root's size untouched — only a real resize/rebuild closes.
            if (resized) {
                if (root.viewOpen) hideViewPopover(root);
                if (root.blacklistPopoverOpen) hideBlacklistPopover(root);
            }
        });

        // Pages: Control | Lists | Settings -------------------------------
        // One encapsulated tabbed card (GinvPageHost): the tab bar and each
        // page body share a single widget/surface, so the bar reads as part of
        // it rather than a detached strip. The body region owns the vertical
        // grow so tall page content scrolls instead of pushing the status bar
        // off-card.
        GinvPageHost pageHost = new GinvPageHost(u(SPACE_4), u(SPACE_3));
        pageHost.addPage("control", "Control", controlTab(root, feedbackLabel));
        pageHost.addPage("lists", "Lists", listsTab(root));
        pageHost.addPage("settings", "Settings", settingsTab(root, feedbackLabel));
        panel.addChild(pageHost);
        root.pageHost = pageHost;
        if (savedTab > 0 && savedTab < pageHost.pages().size()) {
            pageHost.select(savedTab);
        }

        // Status bar -----------------------------------------------------
        // One shared strip for both contexts: live queue status on the left,
        // transient action feedback on the right.
        Label statusLabel = new Label();
        statusLabel.setId("ginv_status");
        statusLabel.setText(statusText());
        statusLabel.textStyle(style -> style.fontSize(u(9)).adaptiveHeight(true));

        UIElement statusBar = new UIElement();
        statusBar.setId("ginv_statusbar");
        statusBar.addClass("ginv-statusbar");
        // Geometry is Java-owned (D6); office-dusk.lss keeps row/flex, paint and
        // the overflow guard (clip: scissor).
        statusBar.layout(layout -> {
            layout.widthPercent(100);
            layout.height(u(16));
            layout.minHeight(u(16));
            layout.paddingHorizontal(u(SPACE_3));
            layout.gapAll(u(SPACE_2));
        });
        statusLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        feedbackLabel.layout(layout -> layout.minWidth(0));
        statusBar.addChildren(statusLabel, feedbackLabel);
        // The footer is the bottom band of the page document, so the page host
        // owns it (a shrink-0 child after the TabView) rather than the panel.
        pageHost.setFooter(statusBar);

        // Hand the live widgets to the refresh loop (GinvRoot.screenTick runs in
        // both screen and windowed contexts) and snapshot the change tokens.
        root.statusLabel = statusLabel;
        root.lastVersion = GinvDataStore.version();
        root.lastOnline = onlineSnapshot();
        root.lastTargets = List.copyOf(GinvCommand.getGinvTargets());
        root.lastGuiScale = windowed ? mcGuiScale() : 1;

        return new Layout(root, panel, base);
    }

    // ------------------------------------------------------- section builders

    /**
     * Control tab — state banner, the hero factory STOP, both queue paths and
     * the current-targets readout (the bare {@code /ginv} feature).
     */
    private static UIElement controlTab(GinvRoot root, Label feedbackLabel) {
        UIElement content = tabColumn();

        // State banner: dot + RUNNING/STOPPED · N pending, live every tick.
        UIElement banner = new UIElement();
        banner.addClass("ginv-banner");
        // Geometry is Java-owned (D6); office-dusk.lss keeps row/flex and paint.
        banner.layout(layout -> {
            layout.widthPercent(100);
            layout.height(u(16));
            layout.minHeight(u(16));
            layout.paddingHorizontal(u(SPACE_3));
            layout.gapAll(u(SPACE_2));
        });
        UIElement dot = new UIElement();
        dot.layout(layout -> {
            layout.width(u(6));
            layout.height(u(6));
        });
        dot.addClass("ginv-dot");
        setDotState(dot, GinvCommand.isFrozen());
        root.bannerDot = dot;
        Label bannerLabel = new Label();
        bannerLabel.setId("ginv_banner");
        bannerLabel.textStyle(style -> style.fontSize(u(10)).textShadow(true).adaptiveHeight(true));
        bannerLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        banner.addChildren(dot, bannerLabel);
        root.bannerLabel = bannerLabel;
        root.lastFrozen = GinvCommand.isFrozen();
        root.lastBanner = bannerText(root.lastFrozen);
        bannerLabel.setText(root.lastBanner);
        setBannerState(bannerLabel, root.lastFrozen);
        content.addChild(banner);

        // Hero factory STOP: full-width, red STOP ⇄ green RESUME.
        Button hero = new Button();
        hero.setId("ginv_hero");
        hero.setText(GinvCommand.isFrozen() ? "RESUME INVITES" : "STOP INVITES");
        hero.layout(layout -> {
            layout.widthPercent(100);
            layout.height(u(24));
        });
        hero.textStyle(style -> style.fontSize(u(11)).textShadow(true).adaptiveHeight(true));
        // Red STOP ⇄ green RESUME surfaces come from the hero state classes in
        // office-dusk.lss; screenTick flips them when freeze toggles (B/§4).
        hero.addClass("ginv-hero");
        setHeroState(hero, GinvCommand.isFrozen());
        hero.setOnClick(event -> {
            GinvCommand.toggleFreeze();
            int pending = GinvCommand.getPendingCount();
            if (GinvCommand.isFrozen()) {
                feedback(feedbackLabel, "Queue frozen. " + pending + " invite(s) pending.", FeedbackKind.ERR);
            } else {
                feedback(feedbackLabel, "Queue resumed. " + pending + " invite(s) pending.", FeedbackKind.OK);
            }
        });
        root.heroStopButton = hero;
        content.addChild(hero);

        // --- queue section ---
        content.addChild(sectionHeader("Queue"));

        TextField nameField = new TextField().setAnyString();
        nameField.setId("ginv_name_input");
        nameField.textFieldStyle(style -> style
                .placeholder(Component.literal("player1 player2 …"))
                .fontSize(u(10)));
        nameField.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
            layout.height(u(16));
        });
        Button queueNames = new Button();
        queueNames.setId("ginv_queue_names");
        queueNames.setText("Queue");
        queueNames.layout(layout -> layout.height(u(16)));
        queueNames.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        queueNames.setOnClick(event -> {
            List<String> parsed = GinvCommand.excludeSelf(GinvCommand.parseTargets(nameField.getValue()));
            if (parsed.isEmpty()) {
                feedback(feedbackLabel, "No valid names.", FeedbackKind.ERR);
                return;
            }
            GinvCommand.queueAndSchedule(parsed);
            for (String name : parsed) {
                GinvDataStore.touch(name);
            }
            feedback(feedbackLabel, "Queued " + parsed.size() + " invites.", FeedbackKind.OK);
            nameField.setText("");
        });
        UIElement nameRow = row(u(16));
        nameRow.addChildren(nameField, queueNames);
        content.addChild(nameRow);

        TextField levelField = new TextField().setNumbersOnlyInt(0, 999);
        levelField.setId("ginv_level_input");
        levelField.setText("0");
        levelField.textFieldStyle(style -> style.fontSize(u(10)));
        levelField.layout(layout -> {
            layout.width(u(44));
            layout.height(u(16));
        });
        Button queueLevel = new Button();
        queueLevel.setId("ginv_queue_level");
        queueLevel.setText("Queue ≥");
        queueLevel.layout(layout -> layout.height(u(16)));
        queueLevel.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        queueLevel.setOnClick(event -> {
            int minLevel;
            try {
                minLevel = Integer.parseInt(levelField.getValue().trim());
            } catch (NumberFormatException e) {
                feedback(feedbackLabel, "Enter a level.", FeedbackKind.ERR);
                return;
            }
            LevelQueueResult result = GinvCommand.queueByLevel(minLevel);
            String text = switch (result.error()) {
                case NOT_SKYBLOCK -> "Not in SkyBlock.";
                case NOT_CONNECTED -> "Not connected.";
                case NO_MATCHES -> "No matches (" + result.skippedNoLevel() + " no-level · "
                        + result.skippedLowLevel() + " below)";
                case NONE -> "Queued " + result.queued() + " · " + result.skippedNoLevel()
                        + " no-level · " + result.skippedLowLevel() + " below";
            };
            FeedbackKind kind = switch (result.error()) {
                case NONE -> FeedbackKind.OK;
                case NO_MATCHES -> FeedbackKind.WARN;
                default -> FeedbackKind.ERR;
            };
            feedback(feedbackLabel, text, kind);
        });
        root.levelQueueButton = queueLevel;
        UIElement levelRow = row(u(16));
        levelRow.addChildren(levelField, queueLevel);
        content.addChild(levelRow);

        root.lastCaption = levelCaption();
        Label rangeLabel = caption(root.lastCaption);
        rangeLabel.setId("ginv_range");
        rangeLabel.setVisible(!root.lastCaption.isEmpty());
        root.rangeLabel = rangeLabel;
        root.lastCanQueue = canQueueByLevel();
        queueLevel.setActive(root.lastCanQueue);
        content.addChild(rangeLabel);

        // --- current targets (bare /ginv, with per-row remove) ---
        Label targetsHeader = sectionTitle(root.lastHeader = "Current targets ("
                + GinvCommand.getGinvTargets().size() + ")");
        targetsHeader.setId("ginv_targets_header");
        targetsHeader.layout(layout -> {
            layout.flexShrink(1);
            layout.minWidth(0);
        });
        root.targetsHeader = targetsHeader;
        Button clearButton = new Button();
        clearButton.setId("ginv_clear");
        clearButton.setText("Clear");
        clearButton.layout(layout -> layout.height(u(16)));
        clearButton.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        clearButton.getStyle().tooltips("Clear all targets and pending invites");
        clearButton.setOnClick(event -> {
            GinvCommand.clearTargets();
            feedback(feedbackLabel, "Targets cleared.", FeedbackKind.OK);
        });
        UIElement targetsHeaderRow = sectionRow(targetsHeader, clearButton, SPACE_2);
        content.addChild(targetsHeaderRow);

        GinvTable targetsTable = new GinvTable(targetColumns(), u(18), u(3), u(14));
        fillTargetsTable(targetsTable, collectLevels());
        root.targetsTable = targetsTable;
        content.addChild(targetsTable);

        return content;
    }

    // ------------------------------------------------------------- columns

    /**
     * Control target rows carry the invite count/last-invite age inline (the
     * per-player stats the removed Monitor tab used to own), so the count lives
     * with the queue that produced it.
     */
    private static List<GinvTable.Column> targetColumns() {
        return List.of(
                new GinvTable.Column("name", "Player", 0, true, AlignItems.FLEX_START),
                new GinvTable.Column("count", "Invited", u(52), false, AlignItems.FLEX_END),
                new GinvTable.Column("action", "", u(14), false, AlignItems.CENTER));
    }

    private static List<GinvTable.Column> listColumns() {
        return List.of(
                new GinvTable.Column("name", "Player", 0, true, AlignItems.FLEX_START),
                new GinvTable.Column("white", "", u(14), false, AlignItems.CENTER),
                new GinvTable.Column("black", "", u(14), false, AlignItems.CENTER),
                new GinvTable.Column("remove", "", u(14), false, AlignItems.CENTER));
    }

    /** Lists tab — search/LVL filter, whitelist/blacklist rows. */
    private static UIElement listsTab(GinvRoot root) {
        // Search + a concealed inclusive LVL range. The level fields allow an
        // empty value (= unset): a numeric validator that rejects "" could
        // never be cleared, so parsing decides instead (ListsFilter.parse).
        TextField searchField = new TextField().setAnyString();
        searchField.setId("ginv_lists_search");
        searchField.textFieldStyle(style -> style
                .placeholder(Component.literal("Search players…"))
                .fontSize(u(10)));
        searchField.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
            layout.height(u(16));
        });

        TextField minLevelField = levelFilterField("ginv_lists_level_min", "min");
        TextField maxLevelField = levelFilterField("ginv_lists_level_max", "max");

        Button filterMenu = new Button();
        filterMenu.setId("ginv_lists_filter_menu");
        filterMenu.setText("LVL ▾");
        filterMenu.layout(layout -> {
            layout.width(u(34));
            layout.height(u(16));
        });
        filterMenu.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        filterMenu.getStyle().tooltips("Filter by guild level");

        // Concealed disclosure, revealed in flow by the LVL button. It reuses
        // the popover paint class for the raised ring (D6: no new theme rule),
        // but pins positionType to RELATIVE inline — an absolute overlay inside
        // the table's ScrollerView pane does not paint above the scroll surface.
        UIElement filterPopover = new UIElement();
        filterPopover.setId("ginv_lists_filter_popover");
        filterPopover.addClass("ginv-popover");
        filterPopover.layout(layout -> {
            layout.positionType(TaffyPosition.RELATIVE);
            layout.widthPercent(100);
            layout.paddingAll(u(SPACE_4));
            layout.gapAll(u(SPACE_2));
        });
        filterPopover.setDisplay(false);
        UIElement levelRow = row(u(16));
        // Center the min-dash-max cluster in the full-width panel.
        levelRow.layout(layout -> layout.justifyContent(AlignContent.CENTER));
        levelRow.addChildren(minLevelField, dashLabel(), maxLevelField);
        filterPopover.addChild(levelRow);

        Button clearFilter = new Button();
        clearFilter.setId("ginv_lists_filter_clear");
        clearFilter.setText("Clear");
        clearFilter.layout(layout -> {
            layout.width(u(40));
            layout.height(u(16));
        });
        clearFilter.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        clearFilter.setOnClick(event -> {
            searchField.setText("");
            minLevelField.setText("");
            maxLevelField.setText("");
        });

        UIElement filterRow = row(u(16));
        filterRow.addChildren(searchField, filterMenu, clearFilter);

        UIElement filterWrapper = new UIElement();
        filterWrapper.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.positionType(TaffyPosition.RELATIVE);
            // Section break: tabColumn gap (6u) + 2u = 8u above the table.
            layout.marginBottom(u(SPACE_1));
        });
        filterWrapper.addChildren(filterRow, filterPopover);

        root.listsSearchField = searchField;
        root.listsMinLevelField = minLevelField;
        root.listsMaxLevelField = maxLevelField;
        root.listsFilterMenu = filterMenu;
        root.listsFilterPopover = filterPopover;
        root.listsFilterOpen = false;
        filterMenu.setOnClick(event -> toggleListsFilterPopover(root));

        GinvTable listsTable = new GinvTable(listColumns(), u(18), u(3), u(14));
        fillListsTable(root, listsTable, collectLevels(), listFilter(root));
        root.listsTable = listsTable;

        UIElement content = tabColumn();
        content.addChildren(filterWrapper, listsTable);
        return content;
    }

    /** A narrow LVL-bound field that permits an empty value (empty = unset). */
    private static TextField levelFilterField(String id, String placeholder) {
        TextField field = new TextField().setAnyString();
        field.setId(id);
        field.textFieldStyle(style -> style
                .placeholder(Component.literal(placeholder))
                .fontSize(u(10)));
        field.layout(layout -> {
            layout.width(u(30));
            layout.height(u(16));
        });
        return field;
    }

    /** Null-safe snapshot of the Lists filter controls (empty = {@link ListsFilter#EMPTY}). */
    private static ListsFilter listFilter(GinvRoot root) {
        if (root.listsSearchField == null
                || root.listsMinLevelField == null
                || root.listsMaxLevelField == null) {
            return ListsFilter.EMPTY;
        }
        return ListsFilter.parse(root.listsSearchField.getValue(),
                root.listsMinLevelField.getValue(), root.listsMaxLevelField.getValue());
    }

    /**
     * Settings tab — sectioned layout: Invites (delay), Filtering
     * (whitelist-only + its note), and Appearance (theme), each under an
     * existing {@code sectionHeader} banner.
     */
    private static UIElement settingsTab(GinvRoot root, Label feedbackLabel) {
        TextField minDelayField = new TextField().setNumbersOnlyInt(50, 60_000);
        minDelayField.setText(String.valueOf(GinvDataStore.minDelayMs()));
        minDelayField.textFieldStyle(style -> style.fontSize(u(10)));
        minDelayField.layout(layout -> {
            layout.flexGrow(1);
            layout.flexBasis(0);
            layout.minWidth(0);
            layout.height(u(16));
        });

        TextField maxDelayField = new TextField().setNumbersOnlyInt(50, 60_000);
        maxDelayField.setText(String.valueOf(GinvDataStore.maxDelayMs()));
        maxDelayField.textFieldStyle(style -> style.fontSize(u(10)));
        maxDelayField.layout(layout -> {
            layout.flexGrow(1);
            layout.flexBasis(0);
            layout.minWidth(0);
            layout.height(u(16));
        });

        Button applyButton = new Button();
        applyButton.setText("Apply");
        applyButton.layout(layout -> {
            layout.width(u(48));
            layout.height(u(16));
            layout.flexShrink(0);
        });
        applyButton.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        applyButton.setOnClick(event -> {
            try {
                int min = Integer.parseInt(minDelayField.getValue().trim());
                int max = Integer.parseInt(maxDelayField.getValue().trim());
                GinvDataStore.setDelays(min, max);
                minDelayField.setText(String.valueOf(GinvDataStore.minDelayMs()));
                maxDelayField.setText(String.valueOf(GinvDataStore.maxDelayMs()));
                feedback(feedbackLabel, "Applied.", FeedbackKind.OK);
            } catch (NumberFormatException e) {
                feedback(feedbackLabel, "Invalid delay.", FeedbackKind.ERR);
            }
        });

        Label delayLabel = bodyLabel("Invite delay (ms):");
        // Plain element, not .ginv-ctl: that class is flex-shrink: 0, which
        // would stop the cluster shrinking at the narrow popup width.
        UIElement delayCtl = new UIElement();
        delayCtl.setId("ginv_setting_delay");
        delayCtl.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(4));
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        delayCtl.addChildren(minDelayField, dashLabel(), maxDelayField, applyButton);

        Switch whitelistSwitch = new Switch();
        whitelistSwitch.setOn(GinvDataStore.whitelistOnly(), false);
        whitelistSwitch.registerValueListener(value ->
                GinvDataStore.setWhitelistOnly(Boolean.TRUE.equals(value)));
        whitelistSwitch.getStyle().tooltips("Only invite whitelisted players");
        root.whitelistSwitch = whitelistSwitch;

        Label whitelistLabel = bodyLabel("Whitelist only:");

        // Blacklist duration: the per-type default expiry the store stamps on a
        // new blacklist entry. Same native Selector + sheet classes as the theme
        // picker, so no new stylesheet surface. Whitelist entries stay permanent.
        Label blacklistTtlLabel = bodyLabel("Blacklist duration:");
        Selector<ListDuration> blacklistTtlSelector = new Selector<>();
        blacklistTtlSelector.setId("ginv_settings_blacklist_ttl");
        blacklistTtlSelector.addClass("ginv-theme-select");
        blacklistTtlSelector.setCandidateUIProvider(GinvMenuScreen::blacklistTtlOptionLabel);
        blacklistTtlSelector.setCandidates(List.of(ListDuration.values()));
        blacklistTtlSelector.setSelected(ListDuration.nearest(GinvDataStore.blacklistTtlMs()), false);
        blacklistTtlSelector.setOnValueChanged(value -> {
            if (value != null) {
                GinvDataStore.setBlacklistTtlMs(value.millis());
            }
        });
        blacklistTtlSelector.getStyle().tooltips(
                "New blacklist entries expire after this. Whitelist entries are always permanent.");
        blacklistTtlSelector.layout(layout -> {
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.flexBasis(0);
            layout.minWidth(0);
            layout.height(u(16));
        });
        blacklistTtlSelector.getStyle().backgroundTexture(null);
        blacklistTtlSelector.dialog.addClass("ginv-theme-dialog");
        blacklistTtlSelector.dialog.getStyle().backgroundTexture(null);

        // Theme selector: the only theme picker (View popover no longer has
        // one). A native LDLib2 Selector dropdown — a component, not a row of
        // toggle buttons — over the palettes whose generated delta ships, so a
        // base swap can never land on a missing palette. A change persists then
        // rebuilds via the proven pendingScaleRebuild path (screen or OS window
        // in place). Geometry is Java-owned (D6); the sheet owns only paint.
        GinvTheme active = activeTheme();
        Label themeLabel = bodyLabel("Theme:");
        List<GinvTheme> shippedThemes = new ArrayList<>();
        for (GinvTheme candidate : GinvTheme.values()) {
            if (candidate.hasDelta()) {
                shippedThemes.add(candidate);
            }
        }
        Selector<GinvTheme> themeSelector = new Selector<>();
        themeSelector.setId("ginv_settings_theme");
        themeSelector.addClass("ginv-theme-select");
        themeSelector.setCandidateUIProvider(GinvMenuScreen::themeOptionLabel);
        themeSelector.setCandidates(shippedThemes);
        themeSelector.setSelected(active, false);
        themeSelector.setOnValueChanged(value -> {
            if (value != null) {
                GinvDataStore.setTheme(value.id());
                root.pendingScaleRebuild = true;
            }
        });
        themeSelector.layout(layout -> {
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.flexBasis(0);
            layout.minWidth(0);
            layout.height(u(16));
        });
        // Drop the component's inline rounded surface so office.lss can square
        // it (INLINE outranks STYLESHEET; a null value clears the slot). The
        // dropdown dialog is reparented to the root on open, so it carries its
        // own class for the sheet to target.
        themeSelector.getStyle().backgroundTexture(null);
        themeSelector.dialog.addClass("ginv-theme-dialog");
        themeSelector.dialog.getStyle().backgroundTexture(null);

        // Two-column grid so every label and every control share an edge.
        // Full-width section banners split it into Invites / Filtering /
        // Appearance; the whitelist note stays inside Filtering.
        GinvSettingsForm form = new GinvSettingsForm(u(16), u(4));
        form.setId("ginv_settings_form");
        form.addFullWidth(sectionHeader("Invites"));
        form.addSetting(delayLabel, delayCtl, true);
        form.addFullWidth(sectionHeader("Filtering"));
        form.addSetting(whitelistLabel, whitelistSwitch, false);
        form.addSetting(blacklistTtlLabel, blacklistTtlSelector, true);
        form.addFullWidth(sectionHeader("Appearance"));
        form.addSetting(themeLabel, themeSelector, true);

        // Menu scale + autoscale + mode moved into the View popover (T8).
        // Keep tabColumn for the page's top padding; the form is its only child.
        UIElement content = tabColumn();
        content.addChild(form);
        return content;
    }

    // --------------------------------------------------------------- widgets

    private static UIElement tabColumn() {
        UIElement column = new UIElement();
        column.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapRow(u(SPACE_3));
            layout.paddingTop(u(SPACE_1));
        });
        return column;
    }

    private static UIElement row(float height) {
        UIElement row = new UIElement();
        row.addClass("ginv-row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(SPACE_2));
            layout.widthPercent(100);
            layout.height(height);
            layout.minHeight(u(16));
        });
        return row;
    }

    /**
     * Segmented Toggle sizing (scale/mode groups): the Toggle is
     * {@code [aspect-1 button][flex-1 label]}, so the label's zero flex-basis
     * collapses each Toggle to its square while it still shares the row.
     * Geometry is Java-owned (D6).
     */
    private static void sizeGroupToggle(Toggle toggle) {
        toggle.layout(layout -> {
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.flexBasis(0);
            layout.minWidth(0);
            layout.height(u(14));
            layout.minHeight(u(14));
            layout.paddingAll(0);
            // The label is adaptiveHeight(true) — a definite cross-size, which
            // flexbox leaves at flex-start (top) under STRETCH. CENTER puts the
            // text box in the middle of the segment at every u() scale (the
            // same drift the hero STOP text had). The click target is the
            // absolute toggle button, so it does not need STRETCH.
            layout.alignItems(AlignItems.CENTER);
        });
        // Toggle's button ships with an inline aspectRatio(1); override it so
        // the transparent click target fills the whole segment cell.
        toggle.toggleButton(button -> button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(0);
            layout.left(0);
            layout.right(0);
            layout.bottom(0);
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.aspectRatioAuto();
        }));
    }

    private static UIElement sectionHeader(String text) {
        return sectionRow(sectionTitle(text), null);
    }

    private static UIElement sectionRow(Label label, UIElement trailing) {
        return sectionRow(label, trailing, 0);
    }

    private static UIElement sectionRow(Label label, UIElement trailing, double marginBottom) {
        UIElement header = new UIElement();
        header.addClass("ginv-section-h");
        header.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(SPACE_2));
            layout.marginTop(u(SPACE_4));
            layout.marginBottom(u(marginBottom));
        });
        UIElement rule = new UIElement();
        rule.addClass("ginv-section-rule");
        rule.layout(layout -> {
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.minWidth(0);
            layout.height(u(1));
            layout.minHeight(u(1));
        });
        header.addChild(label);
        header.addChild(rule);
        if (trailing != null) {
            header.addChild(trailing);
        }
        return header;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label();
        label.setText(text.toUpperCase(Locale.ROOT));
        label.addClass("ginv-section");
        label.textStyle(style -> style.fontSize(u(9)).adaptiveWidth(true).adaptiveHeight(true));
        return label;
    }

    private static Label caption(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-caption");
        label.textStyle(style -> style.fontSize(u(9)).adaptiveHeight(true));
        return label;
    }

    private static Label bodyLabel(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-body-text");
        label.textStyle(style -> style.fontSize(u(10)).adaptiveHeight(true));
        return label;
    }

    /**
     * Theme Selector option: the dropdown row and the collapsed value preview
     * both read {@link GinvTheme#displayName()}. Size is Java-owned (D6); the
     * sheet owns the ink via the shared label rule.
     */
    private static Label themeOptionLabel(GinvTheme candidate) {
        Label label = new Label();
        label.setText(candidate == null ? "---" : candidate.displayName());
        label.addClass("ginv-theme-option");
        label.textStyle(style -> style
                .fontSize(u(10))
                .adaptiveHeight(true)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    /**
     * Blacklist-duration Selector option: the dropdown row and the collapsed
     * value preview both read {@link ListDuration#displayName()}. Reuses the
     * theme-option label class so the sheet owns the ink.
     */
    private static Label blacklistTtlOptionLabel(ListDuration candidate) {
        Label label = new Label();
        label.setText(candidate == null ? "---" : candidate.displayName());
        label.addClass("ginv-theme-option");
        label.textStyle(style -> style
                .fontSize(u(10))
                .adaptiveHeight(true)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Label dashLabel() {
        Label dash = new Label();
        dash.setText("-");
        dash.addClass("ginv-caption");
        dash.textStyle(style -> style
                .fontSize(u(10))
                .adaptiveHeight(true)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        // Fixed width so the glyph is equidistant from the two fields.
        dash.layout(layout -> layout.width(u(10)));
        return dash;
    }

    /** Semantic feedback tint; office-dusk.lss maps each kind to a paper token. */
    private enum FeedbackKind {
        INFO, OK, WARN, ERR
    }

    private static void feedback(Label label, String text, FeedbackKind kind) {
        label.removeClasses("ginv-feedback-info", "ginv-feedback-ok",
                "ginv-feedback-warn", "ginv-feedback-err");
        label.addClass(switch (kind) {
            case INFO -> "ginv-feedback-info";
            case OK -> "ginv-feedback-ok";
            case WARN -> "ginv-feedback-warn";
            case ERR -> "ginv-feedback-err";
        });
        label.setText(ellipsize(text, label));
    }

    /** Banner ink: green while RUNNING, red while STOPPED (sheet-driven). */
    private static void setBannerState(Label label, boolean frozen) {
        label.removeClasses("ginv-banner-ok", "ginv-banner-err");
        label.addClass(frozen ? "ginv-banner-err" : "ginv-banner-ok");
    }

    /** Hero surface: red STOP while running, green RESUME while frozen. */
    private static void setHeroState(Button hero, boolean frozen) {
        hero.removeClasses("ginv-hero-stop", "ginv-hero-resume");
        hero.addClass(frozen ? "ginv-hero-resume" : "ginv-hero-stop");
    }

    /** Banner dot: green while RUNNING, red while STOPPED. */
    private static void setDotState(UIElement dot, boolean frozen) {
        dot.removeClasses("ginv-dot-on", "ginv-dot-off");
        dot.addClass(frozen ? "ginv-dot-off" : "ginv-dot-on");
    }

    /**
     * Cuts {@code text} with an ellipsis until it fits the label's laid-out
     * width; a label not measured yet (width ≤ 0) passes the text through.
     * Width estimate: MC font metrics scaled by the active {@code u(1)}.
     */
    private static String ellipsize(String text, Label label) {
        float available = label.getSizeWidth();
        if (available <= 0 || text == null || text.isEmpty()) return text;
        var font = Minecraft.getInstance().font;
        if (font.width(text) * u(1) <= available) return text;
        String ellipsis = "…";
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + ellipsis) * u(1) > available) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.isEmpty() ? ellipsis : cut + ellipsis;
    }

    // ------------------------------------------------------- table cells

    /**
     * The first table column's cell: a player head followed by the vanilla-font
     * name and the guild-level badge, so the level hugs the player it belongs
     * to instead of the trailing action cluster.
     */
    private static UIElement nameCell(String name, GuildLevels.LevelInfo level) {
        UIElement cell = new UIElement();
        cell.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(3));
            layout.minWidth(0);
        });
        UIElement head = new UIElement();
        head.layout(layout -> {
            layout.width(u(10));
            layout.height(u(10));
            layout.flexShrink(0);
        });
        head.getStyle().backgroundTexture(new PlayerHeadTexture(name));
        Label nameLabel = new Label();
        nameLabel.setText(name);
        nameLabel.addClass("ginv-name");
        nameLabel.textStyle(style -> style.font(VANILLA_FONT).fontSize(u(10))
                .adaptiveWidth(true).adaptiveHeight(true));
        nameLabel.layout(layout -> {
            layout.flexShrink(1);
            layout.minWidth(0);
        });
        Label badge = levelBadge(level);
        badge.addClass("ginv-level");
        cell.addChildren(head, nameLabel, badge);
        return cell;
    }

    /**
     * Target column: invite count and last-invite age, right-aligned. A player
     * with no recorded invites yet (or no persisted snapshot at all) shows a
     * muted dash instead of a misleading {@code ×0}.
     */
    private static UIElement countCell(GinvDataStore.PlayerSnapshot snapshot) {
        String count;
        if (snapshot == null || snapshot.invites() <= 0) {
            count = "—";
        } else {
            count = "×" + snapshot.invites();
            if (snapshot.lastInviteMs() > 0) {
                count += " · " + ago(snapshot.lastInviteMs());
            }
        }
        Label label = new Label();
        label.setText(count);
        label.addClass("ginv-muted");
        label.textStyle(style -> style.font(VANILLA_FONT).fontSize(u(9))
                .textAlignHorizontal(Horizontal.RIGHT).adaptiveHeight(true));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.minWidth(0);
        });
        return label;
    }

    /** Flips a player's list state to {@code target}, or clears it if already set. */
    private static void toggleList(String name, GinvDataStore.ListState target) {
        GinvDataStore.PlayerSnapshot current = GinvDataStore.snapshot(name);
        GinvDataStore.ListState currentState = current == null
                ? GinvDataStore.ListState.NONE
                : current.listState();
        GinvDataStore.setListState(name, currentState == target
                ? GinvDataStore.ListState.NONE
                : target);
    }

    /** {@code [42]} in the server's prefix color, or a muted dash when unknown. */
    private static Label levelBadge(GuildLevels.LevelInfo level) {
        Label badge = new Label();
        if (level == null) {
            badge.setText("—");
            badge.addClass("ginv-muted");
            badge.textStyle(style -> style.font(VANILLA_FONT).fontSize(u(9))
                    .adaptiveWidth(true).adaptiveHeight(true));
        } else {
            badge.setText("[" + level.value() + "]");
            badge.textStyle(style -> style.font(VANILLA_FONT)
                    .fontSize(u(10)).textColor(level.color())
                    .adaptiveWidth(true).adaptiveHeight(true));
        }
        badge.layout(layout -> layout.flexShrink(0));
        badge.getStyle().tooltips("Guild level");
        return badge;
    }

    /**
     * Row action button rendered as a bare icon: no border, transparent at rest
     * (hover/pressed feedback comes from the class). An {@code activeClass} of
     * {@code null} uses {@code ginv-chrome}; the active whitelist/blacklist tint
     * is added <em>instead of</em> chrome — the sheet defines {@code .ginv-chrome}
     * later, so stacking both would drop the green/red fill.
     */
    private static Button iconListButton(String activeClass, IGuiTexture icon,
                                         String tooltip, Runnable action) {
        Button button = new Button();
        button.noText().addPreIcon(icon);
        button.addClass(activeClass != null ? activeClass : "ginv-chrome");
        button.layout(layout -> {
            layout.width(u(14));
            layout.height(u(16));
            layout.flexShrink(0);
        });
        button.getStyle().tooltips(tooltip);
        button.setOnClick(event -> action.run());
        return button;
    }

    private static String ago(long epochMs) {
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }

    // --------------------------------------------------------- rebuilding

    private static List<String> collectNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(GinvDataStore.trackedNames());
        for (GuildDirectory.Entry entry : GuildDirectory.online()) {
            names.add(entry.name());
        }
        return new ArrayList<>(names);
    }

    /** Level lookup for the current tab list, keyed by player name. */
    private static Map<String, GuildLevels.LevelInfo> collectLevels() {
        Map<String, GuildLevels.LevelInfo> levels = new HashMap<>();
        for (GuildDirectory.Entry entry : GuildDirectory.online()) {
            if (entry.level() != null) levels.put(entry.name(), entry.level());
        }
        return levels;
    }

    private static void fillListsTable(GinvRoot root, GinvTable table,
                                       Map<String, GuildLevels.LevelInfo> levels,
                                       ListsFilter filter) {
        table.clearRows();
        List<String> names = collectNames();
        if (names.isEmpty()) {
            table.addEmpty("No players.");
            return;
        }
        List<String> matched = new ArrayList<>();
        for (String name : names) {
            GuildLevels.LevelInfo level = levels.get(name);
            if (filter.matches(name, level == null ? null : level.value())) {
                matched.add(name);
            }
        }
        if (matched.isEmpty()) {
            table.addEmpty("No players match.");
            return;
        }
        for (String name : matched) {
            GinvDataStore.PlayerSnapshot snapshot = GinvDataStore.snapshot(name);
            GinvDataStore.ListState state = snapshot == null
                    ? GinvDataStore.ListState.NONE
                    : snapshot.listState();
            Button blacklistButton = iconListButton(state == GinvDataStore.ListState.BLACKLIST
                            ? "ginv-list-black" : null,
                    BLACKLIST_ICON,
                    "Blacklist player (right-click for duration)",
                    () -> toggleList(name, GinvDataStore.ListState.BLACKLIST));
            blacklistButton.addClass("ginv-list-blacklist-trigger");
            blacklistButton.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 1) {
                    openBlacklistPopover(root, name, event.x, event.y);
                }
            });
            table.addRow(null,
                    nameCell(name, levels.get(name)),
                    iconListButton(state == GinvDataStore.ListState.WHITELIST
                                    ? "ginv-list-white" : null,
                            WHITELIST_ICON,
                            "Whitelist player",
                            () -> toggleList(name, GinvDataStore.ListState.WHITELIST)),
                    blacklistButton,
                    iconListButton(null, BOLT_ICON, "Remove record and unqueue", () -> {
                        GinvDataStore.removePlayer(name);
                        GinvCommand.removeFromQueue(name);
                    }));
        }
    }

    /**
     * Current targets with level badges, per-player invite counts and a
     * per-row remove button. The count/last-invite stats the removed Monitor
     * tab used to own now ride the target row that produced them.
     */
    private static void fillTargetsTable(GinvTable table, Map<String, GuildLevels.LevelInfo> levels) {
        table.clearRows();
        List<String> targets = new ArrayList<>(GinvCommand.getGinvTargets());
        if (targets.isEmpty()) {
            table.addEmpty("No targets queued. Use Queue above.");
            return;
        }
        for (String name : targets) {
            table.addRow("ginv_target_row",
                    nameCell(name, levels.get(name)),
                    countCell(GinvDataStore.snapshot(name)),
                    iconListButton(null, BOLT_ICON, "Remove from queue",
                            () -> GinvCommand.removeFromQueue(name)));
        }
    }

    /**
     * Change token for the tab list: name <b>and</b> guild level, so a level-up
     * rebuilds badges even when nobody joins or leaves.
     */
    private static List<String> onlineSnapshot() {
        List<String> names = new ArrayList<>();
        for (GuildDirectory.Entry entry : GuildDirectory.online()) {
            GuildLevels.LevelInfo level = entry.level();
            names.add(entry.name() + ":" + (level == null ? "-" : level.value()));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String statusText() {
        return "Pending: " + GinvCommand.getPendingCount()
                + " · " + (GinvCommand.isFrozen() ? "FROZEN" : "RUNNING")
                + " · Whitelist only: " + (GinvDataStore.whitelistOnly() ? "ON" : "OFF");
    }

    private static String bannerText(boolean frozen) {
        return (frozen ? "STOPPED" : "RUNNING") + " · " + GinvCommand.getPendingCount() + " pending";
    }

    /** Whether the level queue can run right now (connected and in SkyBlock). */
    private static boolean canQueueByLevel() {
        return SkyBlockDetector.isSkyBlock()
                && Minecraft.getInstance().getConnection() != null;
    }

    /**
     * Caption under the level field: the connection hint or the live tab range.
     * Empty outside SkyBlock — the top-bar SkyBlock chip already carries that
     * verdict, so the disabled queue button needs no redundant "SkyBlock only."
     * note (an empty caption hides the label entirely).
     */
    private static String levelCaption() {
        if (Minecraft.getInstance().getConnection() == null) return "Not connected to a server.";
        if (!SkyBlockDetector.isSkyBlock()) return "";
        GuildLevels.LevelRange range = GuildLevels.tabRange();
        return range == null ? "tab: no leveled players"
                : "tab: " + range.min() + "–" + range.max();
    }

    private static String scalePresetLabel(double preset) {
        return Math.round(preset * 100) + "%";
    }

    // -------------------------------------------------------- scale changes

    /**
     * The largest scale within [1.0, 2.0] that fits the 340x266 design plus
     * margins into the current GUI-scaled viewport. Falls back to the stored
     * scale when the window reports a degenerate size. Never persisted — the
     * stored preset is untouched, so switching autoscale off restores it.
     */
    private static double fitUiScale() {
        var window = Minecraft.getInstance().getWindow();
        return fitScaleFor(window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                GinvDataStore.uiScale());
    }

    /**
     * Pure fit: viewport → scale in {@code [1.0, 2.0]}, or {@code stored}
     * for a degenerate viewport. Side-effect free (T14: the no-persist
     * contract is testable headless through this signature).
     */
    static double fitScaleFor(int width, int height, double stored) {
        if (width <= 0 || height <= 0) return stored;
        double fit = Math.min(width * 0.96 / SHELL_MAX_WIDTH, height * 0.94 / SHELL_MAX_HEIGHT);
        return Math.clamp(fit, 1.0, 2.0);
    }

    /**
     * Pure popup-shell width clamp: the design card width, capped so the page
     * aspect (width : height) never exceeds {@code maxAspect} at the effective
     * (viewport-capped) height. Prevents a short/wide viewport from letterboxing
     * the popup. Degenerate viewports return {@code designW}.
     */
    static float popupShellWidth(float availW, float availH, float designW, float designH,
                                 float maxAspect) {
        if (availW <= 0 || availH <= 0) return designW;
        float effectiveHeight = Math.min(designH, availH * 0.94f);
        return Math.min(designW, Math.min(availW * 0.96f, effectiveHeight * maxAspect));
    }

    /**
     * WYSIWYG pop-out size: panel pixels × guiScale × contentScale, floored
     * at the platform window minimum. Pure (T14: the ±10% contract is
     * testable headless through this signature).
     */
    static int popoutSizeFor(double panelDim, double guiScale, double contentScale,
                             int minDim) {
        return Math.max(minDim, (int) Math.round(panelDim * guiScale * contentScale));
    }

    /** Opens the View popover under its anchor, or closes it if open. */
    private static void toggleViewPopover(GinvRoot root) {
        if (root.viewOpen) {
            hideViewPopover(root);
            return;
        }
        // Position and size are owned by office-dusk.lss (#ginv_view_popover:
        // position:absolute; right:2; top:16) and anchored to #ginv_panel,
        // which the sheet marks position: relative. No root-space left/top.
        root.viewPopover.setDisplay(true);
        root.viewOpen = true;
    }

    private static void hideViewPopover(GinvRoot root) {
        if (!root.viewOpen) return;
        root.viewOpen = false;
        root.viewPopover.setDisplay(false);
    }

    /** Stable id suffix for a {@link ListDuration} popover option. */
    private static String durationIdSuffix(ListDuration duration) {
        return switch (duration) {
            case DAYS_3 -> "days_3";
            case DAYS_7 -> "days_7";
            case DAYS_14 -> "days_14";
            case DAYS_30 -> "days_30";
            case FOREVER -> "forever";
        };
    }

    /**
     * Opens the per-player blacklist-duration popover at the right-click
     * cursor, clamped inside {@code #ginv_panel}. Absolute offsets resolve
     * against the panel's padding box, so the root-space cursor is converted
     * with {@code getContentX/Y}. A not-yet-measured popover (size 0 on the
     * same tick) is clamped against its nominal 120u × five-row box.
     */
    private static void openBlacklistPopover(GinvRoot root, String name, float x, float y) {
        if (root.blacklistPopover == null || root.panel == null) return;
        root.blacklistPopoverTarget = name;
        root.blacklistPopover.setDisplay(true);
        root.blacklistPopoverOpen = true;
        float localX = x - root.panel.getContentX();
        float localY = y - root.panel.getContentY();
        float popW = root.blacklistPopover.getSizeWidth();
        float popH = root.blacklistPopover.getSizeHeight();
        int rows = ListDuration.values().length;
        if (popW <= 0) popW = u(120);
        if (popH <= 0) popH = u(16) * rows + u(SPACE_2) * (rows - 1) + u(SPACE_4) * 2;
        float maxX = Math.max(0, root.panel.getContentWidth() - popW);
        float maxY = Math.max(0, root.panel.getContentHeight() - popH);
        localX = Math.max(0, Math.min(localX, maxX));
        localY = Math.max(0, Math.min(localY, maxY));
        root.blacklistPopover.getLayout().left(localX).top(localY);
        root.blacklistPopover.clearLayoutCache();
    }

    private static void hideBlacklistPopover(GinvRoot root) {
        if (root.blacklistPopover == null || !root.blacklistPopoverOpen) return;
        root.blacklistPopoverOpen = false;
        root.blacklistPopover.setDisplay(false);
    }

    /** Reveals the Lists LVL filter disclosure, or conceals it again. */
    private static void toggleListsFilterPopover(GinvRoot root) {
        if (root.listsFilterOpen) {
            hideListsFilterPopover(root);
            return;
        }
        root.listsFilterPopover.setDisplay(true);
        root.listsFilterOpen = true;
    }

    private static void hideListsFilterPopover(GinvRoot root) {
        if (!root.listsFilterOpen) return;
        root.listsFilterOpen = false;
        root.listsFilterPopover.setDisplay(false);
    }

    /** Screen-space hit test; getPositionX/Y and event coords share that space. */
    private static boolean within(UIElement element, float x, float y) {
        return x >= element.getPositionX()
                && x <= element.getPositionX() + element.getSizeWidth()
                && y >= element.getPositionY()
                && y <= element.getPositionY() + element.getSizeHeight();
    }

    /** True if {@code element} is {@code ancestor} itself or below it. */
    private static boolean inSubtree(UIElement ancestor, UIElement element) {
        for (UIElement current = element; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }

    /**
     * Rebuilds whichever context is open after {@code uiScale} changed:
     * the OS window in place (geometry kept), or the in-game screen.
     */
    private static void applyUiScale() {
        if (GinvMenuWindow.active() != null) {
            // The windowed scale is frozen at pop-out (windowedScale); re-freeze
            // from the new preset and grow/shrink the window with it, so the
            // rebuilt content keeps exactly filling it instead of clipping or
            // gapping inside the old geometry.
            double previous = windowedUiScale;
            windowedUiScale = windowedScaleFor(GinvDataStore.uiScale(), mcGuiScale());
            GinvMenuWindow fresh = rebuildActiveWindow();
            if (fresh == null) {
                windowedUiScale = previous; // rebuild refused — keep old content in sync
            } else if (!Double.isNaN(previous) && previous > 0) {
                double factor = windowedUiScale / previous;
                if (!fresh.isMaximized()) {
                    // Maximized windows are screen-sized, not WYSIWYG-sized —
                    // leave their geometry alone; flex containers absorb the
                    // scale change.
                    var os = fresh.window();
                    os.setSize((int) Math.round(os.getWindowWidth() * factor),
                            (int) Math.round(os.getWindowHeight() * factor));
                }
                // The resize floor tracks the new natural size — even while
                // maximized, so a later restore resizes against the new scale.
                fresh.scaleReflowBase(factor);
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GinvMenuScreen) {
                mc.setScreen(new GinvMenuScreen());
            }
        }
    }

    /**
     * Reopens the menu window at the same position and size with a freshly
     * built layout — used for {@code uiScale} changes and MC GUI-scale
     * changes, both of which bake different values into {@link #u(double)}.
     * The old window closes only after the new one is up, so a refused
     * second window leaves the menu alive.
     *
     * @return the freshly opened window, or {@code null} if there was nothing
     *         to rebuild or the platform refused the second window.
     */
    private static GinvMenuWindow rebuildActiveWindow() {
        GinvMenuWindow old = GinvMenuWindow.active();
        if (old == null) return null;

        int x;
        int y;
        int width;
        int height;
        boolean maximized;
        boolean onTop;
        try {
            var os = old.window();
            x = os.getPositionX();
            y = os.getPositionY();
            width = os.getWindowWidth();
            height = os.getWindowHeight();
            maximized = old.isMaximized();
            onTop = old.isAlwaysOnTop();
        } catch (RuntimeException e) {
            // The window vanished mid-tick — fall back to platform placement.
            x = Integer.MIN_VALUE;
            y = Integer.MIN_VALUE;
            width = openWidth();
            height = openHeight();
            maximized = false;
            onTop = false;
        }

        Layout layout = buildLayout(true);
        GinvMenuWindow fresh = new GinvMenuWindow(
                new ModularUI(UI.of(layout.root(), layout.baseSheet())), "BWD");
        fresh.setDragArea(layout.root().titleBar);
        // The rebuild reopens at the existing geometry, so the resize base
        // (floor + aspect) carries over unchanged; applyUiScale scales it when
        // the window itself is resized.
        fresh.copyReflowBaseFrom(old);

        if (!fresh.open(x, y, width, height, false)) {
            return null; // no second window — keep the old one as-is
        }
        GinvMenuWindow.track(fresh);
        if (onTop && OsWindow.supportsAlwaysOnTop()) {
            fresh.setAlwaysOnTop(true);
        }
        if (maximized) {
            fresh.toggleMaximized();
        }
        old.close();
        return fresh;
    }

    /** Legacy pop-out size in physical pixels: authored base × windowed scale
     *  / contentScale. <b>Fallback only (T10 addendum)</b> — the primary path
     *  measures the live in-screen panel; this formula runs when the bounds
     *  are degenerate or a rebuild reopens an existing window. The windowed
     *  scale carries the GUI scale, so the legacy window is still big enough
     *  for the content {@link #u(double)} renders into it. Clamped to the
     *  platform minimum so a small scale / high content scale can never hand
     *  the window a size it refuses (the "pop-out unavailable" path). */
    private static int openWidth() {
        return Math.max(ModularUIWindow.MIN_WIDTH,
                (int) Math.round(BASE_WINDOW_WIDTH * windowedScale() / contentScale()));
    }

    private static int openHeight() {
        return Math.max(ModularUIWindow.MIN_HEIGHT,
                (int) Math.round(BASE_WINDOW_HEIGHT * windowedScale() / contentScale()));
    }

    // ------------------------------------------------------------- pop out

    /**
     * Lifts a freshly built copy of the menu into its own OS window, then
     * closes the in-game screen.
     *
     * <p>WYSIWYG (T10): the live in-screen {@code panel} is measured
     * <b>before</b> {@code buildLayout(true)} runs — its bounds only
     * exist while this screen's layout is current — and the window opens at
     * that size (clamped to {@code ≥200×150} / ).
     * Degenerate bounds log one line and fall back to the legacy
     * {@link #openWidth()}/{@link #openHeight()} formula.
     *
     * <p>A fresh copy (not the screen's live UI) because closing the screen
     * would fire {@code onRemoved()} on a shared instance and dispose its
     * style engine. If the platform refuses a second window we stay in-game
     * and say so. The chrome buttons wire themselves to
     * {@link GinvMenuWindow#active()}, so no post-construction rewiring.
     */
    private static void popOut(Label feedbackLabel, UIElement panel) {
        Minecraft mc = Minecraft.getInstance();

        // A second GLFW window cannot be created while the game window is
        // fullscreen on some platforms, so drop to windowed first, then open.
        // The game stays windowed afterwards — the menu window is meant to sit
        // alongside it, and silently re-fullscreening on close would be jarring.
        if (mc.getWindow().isFullscreen()) {
            mc.getWindow().toggleFullScreen();
        }

        // Freeze the live screen's scale (× its GUI scale) for the windowed
        // rebuild before buildLayout replaces uiScaleContext — see
        // windowedScale(): this is the factor that fixes the squash.
        windowedUiScale = windowedScaleFor(uiScaleContext, mcGuiScale());

        // Measure first: the windowed rebuild replaces this layout.
        int openW = openWidth();
        int openH = openHeight();
        float panelW = panel.getSizeWidth();
        float panelH = panel.getSizeHeight();
        if (panelW > 0 && panelH > 0 && !Float.isNaN(panelW) && !Float.isNaN(panelH)) {
            openW = popoutSizeFor(panelW, mcGuiScale(), contentScale(),
                    ModularUIWindow.MIN_WIDTH);
            openH = popoutSizeFor(panelH, mcGuiScale(), contentScale(),
                    ModularUIWindow.MIN_HEIGHT);
        } else {
            GuildInviteFix.LOGGER.warn(
                    "[Ginv] Pop-out panel bounds degenerate ({}x{}), using legacy window size",
                    panelW, panelH);
        }

        Layout windowed = buildLayout(true);

        GinvMenuWindow window = new GinvMenuWindow(
                new ModularUI(UI.of(windowed.root(), windowed.baseSheet())), "BWD");
        window.setDragArea(windowed.root().titleBar);
        // The WYSIWYG opening size becomes the resize floor and aspect lock:
        // the window can be zoomed up from this configuration but never below
        // it, so the fixed layout cannot collapse.
        window.setReflowBase(openW, openH);
        GinvMenuWindow.track(window);

        if (window.open(Integer.MIN_VALUE, Integer.MIN_VALUE, openW, openH, false)) {
            if (GinvDataStore.alwaysOnTop() && OsWindow.supportsAlwaysOnTop()) {
                window.setAlwaysOnTop(true);
            }
            mc.setScreen(null);
        } else {
            // Include the computed dims — if the platform refused them the
            // numbers localize the regression in the uitest screenshot.
            feedback(feedbackLabel, "Pop-out unavailable (" + openW + "×" + openH
                    + ") — staying in-game.", FeedbackKind.ERR);
        }
    }

    /** Icon-only title-bar button: transparent at rest, tinted on hover. */
    private static Button chromeButton(IGuiTexture icon, String tooltip, boolean closeStyle) {
        Button button = new Button();
        button.noText().addPreIcon(icon);
        // Transparent-at-rest chrome: hover/pressed tints and the close red
        // come from office-dusk.lss via the ginv-chrome[-close] classes (B8).
        button.addClass("ginv-chrome");
        if (closeStyle) {
            button.addClass("ginv-chrome-close");
        }
        button.layout(layout -> {
            layout.width(u(16));
            layout.height(u(12));
        });
        button.getStyle().tooltips(tooltip);
        return button;
    }

    // ---------------------------------------------------------------- root

    /**
     * Menu root element carrying the refresh loop.
     *
     * <p>{@link #screenTick()} replaces the old {@code GinvMenuScreen.tick()}:
     * in-screen, {@code ScreenMixin.ldlib2$tick} invokes it when the screen ticks;
     * in an OS window, {@code ModularUIWindow} sets {@code tickWhileRending} so
     * widget extraction drives it once per client tick. One implementation, both
     * contexts — so no double refresh either.
     */
    private static final class GinvRoot extends UIElement {
        Label statusLabel;
        Label bannerLabel;
        UIElement bannerDot;
        Label rangeLabel;
        Label targetsHeader;
        Switch whitelistSwitch;
        GinvTable listsTable;
        GinvTable targetsTable;
        /** Lists filter controls; null until {@link #listsTab} builds them. */
        TextField listsSearchField;
        TextField listsMinLevelField;
        TextField listsMaxLevelField;
        /** Lists LVL filter popover (built hidden) and its anchor button. */
        UIElement listsFilterPopover;
        Button listsFilterMenu;
        boolean listsFilterOpen;
        Button heroStopButton;
        Button levelQueueButton;
        /** Pop-out window drag target; also double-clicked to maximize. */
        UIElement titleBar;
        /** Handed to the OS window as its close target in windowed mode. */
        Button closeButton;
        /** Windowed only — absent when the platform has no window buttons. */
        Button maximizeButton;
        /** Windowed only, and only when always-on-top is supported. */
        Button pinButton;
        /** Encapsulated page host, polled for the tab worth restoring on rebuild. */
        GinvPageHost pageHost;
        /** View popover overlay (D5): built hidden, positioned at open time. */
        UIElement viewPopover;
        /** Anchor the popover drops from (the top-bar View button). */
        UIElement viewAnchor;
        boolean viewOpen;
        /** Per-player blacklist-duration popover: built hidden, opened at the cursor. */
        UIElement blacklistPopover;
        /** Player name the blacklist popover will apply its selection to. */
        String blacklistPopoverTarget;
        boolean blacklistPopoverOpen;
        /** Positioning host for the cursor-anchored blacklist popover. */
        UIElement panel;
        /** Root canvas size at the last geometry event — resize vs content churn. */
        float lastRootWidth;
        float lastRootHeight;
        /** Top-bar SkyBlock chip; tick flips its .ginv-sky-* state classes. */
        SkyBlockStatusElement skyChip;
        boolean windowed;
        /**
         * {@code uiScale} changed inside a mouse event; rebuild once next tick
         * so the screen is never swapped before the triggering click releases.
         */
        boolean pendingScaleRebuild;

        // change tokens
        int lastVersion;
        List<String> lastOnline = List.of();
        List<String> lastTargets = List.of();
        ListsFilter lastFilter = ListsFilter.EMPTY;
        String lastListsFilterLabel = "";
        boolean lastFrozen;
        boolean lastSky;
        boolean lastCanQueue;
        String lastBanner = "";
        String lastCaption = "";
        String lastHeader = "";
        double lastGuiScale = 1;

        @Override
        public void screenTick() {
            super.screenTick();

            // A scale preset / autoscale change swaps the whole screen. That
            // must never happen while the click that triggered it is still in
            // flight: the release step re-resolves the control, and the rebuilt
            // screen has the View popover closed, so the control is zero-sized
            // and the release targets nothing. Wait for the mouse button to come
            // up, then swap on the next idle tick. (Checking the button rather
            // than a fixed tick delay keeps this correct whether the harness
            // settles one frame or several between press and release.)
            if (pendingScaleRebuild && !isMouseDown(0)) {
                pendingScaleRebuild = false;
                applyUiScale();
                return;
            }

            // The baked u() values depend on the game GUI scale: rebuild the
            // window when it changes out from under a windowed layout.
            if (windowed) {
                double guiScale = mcGuiScale();
                if (guiScale != lastGuiScale) {
                    lastGuiScale = guiScale;
                    rebuildActiveWindow();
                    return;
                }
            }

            boolean frozen = GinvCommand.isFrozen();
            int pending = GinvCommand.getPendingCount();

            // Banner dot/hero/text states are sheet-driven: flip the
            // semantic classes when freeze toggles.
            if (frozen != lastFrozen) {
                lastFrozen = frozen;
                heroStopButton.setText(frozen ? "RESUME INVITES" : "STOP INVITES");
                setHeroState(heroStopButton, frozen);
                setBannerState(bannerLabel, frozen);
                setDotState(bannerDot, frozen);
            }

            // SkyBlock verdict → chip .ginv-sky-on / .ginv-sky-off flip. The
            // label is static ("SkyBlock"); only the surface + dot recolor.
            boolean sky = SkyBlockDetector.isSkyBlock();
            if (sky != lastSky) {
                lastSky = sky;
                skyChip.setSkyOn(sky);
            }
            String banner = bannerText(frozen);
            if (!banner.equals(lastBanner)) {
                lastBanner = banner;
                bannerLabel.setText(banner);
            }

            int version = GinvDataStore.version();
            List<String> online = onlineSnapshot();
            List<String> targets = List.copyOf(GinvCommand.getGinvTargets());
            boolean dataDirty = version != lastVersion
                    || !online.equals(lastOnline)
                    || !targets.equals(lastTargets);
            // Filter edits refill only Lists; data changes also refill targets.
            ListsFilter filter = listFilter(this);
            boolean filterDirty = !filter.equals(lastFilter);
            if (dataDirty || filterDirty) {
                lastVersion = version;
                lastOnline = online;
                lastTargets = targets;
                lastFilter = filter;
                Map<String, GuildLevels.LevelInfo> levels = collectLevels();
                fillListsTable(this, listsTable, levels, filter);
                if (dataDirty) {
                    fillTargetsTable(targetsTable, levels);
                }
            }
            // The concealed LVL popover advertises an active level bound on its
            // anchor button; search-only keeps the neutral caret.
            boolean levelBound = filter.minLevel() != null || filter.maxLevel() != null;
            String filterLabel = filter.isActive() && levelBound ? "LVL •" : "LVL ▾";
            if (!filterLabel.equals(lastListsFilterLabel)) {
                lastListsFilterLabel = filterLabel;
                listsFilterMenu.setText(filterLabel);
            }
            String header = "Current targets (" + targets.size() + ")";
            if (!header.equals(lastHeader)) {
                lastHeader = header;
                targetsHeader.setText(header.toUpperCase(Locale.ROOT));
            }

            statusLabel.setText(ellipsize(statusText(), statusLabel));

            boolean whitelistOnly = GinvDataStore.whitelistOnly();
            boolean switchOn = Boolean.TRUE.equals(whitelistSwitch.getValue());
            if (whitelistOnly != switchOn) {
                whitelistSwitch.setOn(whitelistOnly, false);
            }

            // Level-queue preconditions and the live tab-range caption.
            boolean canQueue = canQueueByLevel();
            if (canQueue != lastCanQueue) {
                lastCanQueue = canQueue;
                levelQueueButton.setActive(canQueue);
            }
            String caption = levelCaption();
            if (!caption.equals(lastCaption)) {
                lastCaption = caption;
                rangeLabel.setText(caption);
                rangeLabel.setVisible(!caption.isEmpty());
            }

            // Remember the selected page so rebuilds land where the user was.
            savedTab = pageHost.selectedIndex();
        }
    }
}
