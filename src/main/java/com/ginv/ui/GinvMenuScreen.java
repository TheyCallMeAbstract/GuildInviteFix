package com.ginv.ui;

import com.ginv.GuildInviteFix;
import com.ginv.command.GinvCommand;
import com.ginv.command.LevelQueueResult;
import com.ginv.data.GinvDataStore;
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
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The guild invite menu: Control / Lists / Monitor / Settings.
 *
 * <p>Popup mode renders over a transparent screen background and closes on an
 * outside click; screen mode adds a dimmed backdrop and is modal (ESC only).
 * The title bar's pop-out button rebuilds the menu inside an OS window
 * (LDLib2 {@code ModularUIWindow}) with full program-window chrome — title
 * bar with re-dock, maximize/restore, always-on-top pin, edge resize and a
 * status bar. Both contexts are themed with LDLib2's DUSK stylesheet plus
 * the subtree-local {@code office.lss} mod delta attached on the root.
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

    /** Menu scale presets offered by the Settings segmented control. */
    private static final double[] SCALE_PRESETS = {0.75, 1.0, 1.25, 1.5, 2.0};
    /** Pop-out window size in authored pixels at uiScale 1 / contentScale 1. */
    private static final int BASE_WINDOW_WIDTH = 420;
    private static final int BASE_WINDOW_HEIGHT = 300;
    /** Shell design size in authored pixels: the autoscale back-solve target. */
    private static final float SHELL_MAX_WIDTH = 340;
    private static final float SHELL_MAX_HEIGHT = 266;

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

    /** The LDLib2 base theme both the screen and the pop-out window are styled with (D1). */
    private static Stylesheet paperSheet() {
        return StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.DUSK);
    }

    /**
     * Paper-based mod delta for this screen only (D2): attached subtree-local
     * on {@code ginv_root}, never globally, so every other screen keeps DUSK
     * untouched. A missing/broken sheet degrades to {@link Stylesheet#EMPTY}
     * via the safe accessor.
     */
    private static Stylesheet officeSheet() {
        return StylesheetManager.INSTANCE.getStylesheetSafe(
                Identifier.fromNamespaceAndPath("guildinvitefix", "lss/office.lss"));
    }

    /** Everything the constructor needs, assembled statically before the screen exists. */
    private record Layout(
            GinvRoot root,
            UIElement panel
    ) {
    }

    /** Which in-game mode this screen instance shows (for pop-out/re-dock). */
    private final boolean popup;

    public GinvMenuScreen(boolean popup) {
        this(buildLayout(popup, false), popup);
    }

    private GinvMenuScreen(Layout layout, boolean popup) {
        super(new ModularUI(UI.of(layout.root(), paperSheet())), Component.literal("Guild Invite Fix"));
        this.popup = popup;

        if (popup) {
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
                if (!inside) {
                    onClose();
                }
            });
        }
    }

    // --------------------------------------------------------------- build

    private static Layout buildLayout(boolean popup, boolean windowed) {
        uiScaleContext = windowed ? windowedScale() : GinvDataStore.uiScale();
        // Autoscale (T8): fit the 340x266 design into the viewport before any
        // u() call. In-game only — the OS window owns its geometry — and never
        // persisted: the stored preset stays untouched until one is clicked.
        if (!windowed && GinvDataStore.autoscale()) {
            uiScaleContext = fitUiScale();
        }
        windowedContext = windowed;

        // Created before the title bar so the pop-out button can capture it for
        // the "no second window available" feedback path.
        Label feedbackLabel = new Label();
        feedbackLabel.setId("ginv_feedback");
        feedbackLabel.setText("");
        feedbackLabel.textStyle(style -> style.fontSize(u(9)));

        GinvRoot root = new GinvRoot();
        root.setId("ginv_root");
        root.popup = popup;
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
        } else if (!popup) {
            // Screen mode dims the world behind the panel; popup stays
            // transparent. Both surfaces come from office.lss (B1/D4).
            root.addClass("ginv-screen");
        }
        // Office light theme rides the subtree only (D2), attached before
        // UI.of so the style engine picks it up on registration.
        root.addLocalStylesheet(officeSheet());

        // Shell: transparent centered card owning the responsive max-width
        // and the 340x266 design height (both Java tokens, inline so the
        // 75–200% preset still scales them); the sheet owns max-height:94%.
        // The definite height is load-bearing: the panel's body/pane chain
        // tree resolves percentage heights against it (a content-sized shell
        // starves the TabView and the fixed regions overlap).
        UIElement shell = new UIElement();
        shell.addClass("ginv-shell");
        shell.layout(layout -> {
            if (!windowed) {
                layout.maxWidth(u(SHELL_MAX_WIDTH));
                layout.height(u(SHELL_MAX_HEIGHT));
            }
        });

        UIElement panel = new UIElement();
        panel.setId("ginv_panel");
        // Sizing, padding, gap and surface all come from office.lss
        // (#ginv_panel); the shell owns the responsive width/height caps.
        panel.layout(layout -> layout.widthPercent(100));
        shell.addChild(panel);
        root.addChild(shell);

        // Top bar ----------------------------------------------------------
        UIElement topBar = new UIElement();
        topBar.setId("ginv_topbar");
        topBar.addClass("ginv-topbar");
        // Height/padding/gap/paint come from office.lss (.ginv-topbar, with the
        // dark windowed override under #ginv_root.ginv-windowed).

        // Left: app icon + wordmark.
        UIElement appIcon = new UIElement();
        appIcon.layout(layout -> {
            layout.width(u(10));
            layout.height(u(10));
        });
        appIcon.getStyle().backgroundTexture(SpriteTexture.of("guildinvitefix:textures/gui/icon.png"));
        topBar.addChild(appIcon);

        Label titleLabel = new Label();
        titleLabel.setText("Guild Invite Fix");
        titleLabel.textStyle(style -> style.fontSize(u(10)));
        titleLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        topBar.addChild(titleLabel);

        // Right cluster order: SkyBlock chip → View anchor → chrome buttons.
        SkyBlockStatusElement skyChip = new SkyBlockStatusElement();
        skyChip.layout(layout -> {
            layout.paddingLeft(u(12));
            layout.paddingRight(u(4));
            layout.paddingVertical(u(2));
            layout.gapAll(u(3));
        });
        skyChip.getStateLabel().textStyle(style -> style.fontSize(u(9)));
        skyChip.setSkyOn(SkyBlockDetector.isSkyBlock());
        topBar.addChild(skyChip);
        root.skyChip = skyChip;

        Button viewButton = new Button();
        viewButton.setId("ginv_view_menu");
        viewButton.setText("View ▾");
        viewButton.layout(layout -> {
            layout.width(u(30));
            layout.height(u(11));
        });
        viewButton.textStyle(style -> style.fontSize(u(9)));
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

            // Re-dock: back into the exact screen mode the window came from.
            Button dockButton = chromeButton(Icons.LEFT, "Back into the game", false);
            dockButton.setId("ginv_redock");
            dockButton.setOnClick(event -> {
                GinvMenuWindow window = GinvMenuWindow.active();
                if (window == null) return;
                boolean origin = window.popupOrigin();
                Minecraft.getInstance().setScreen(new GinvMenuScreen(origin));
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
            popOutButton.textStyle(style -> style.fontSize(u(10)));
            popOutButton.getStyle().tooltips("Pop out into its own window");
            popOutButton.setOnClick(event -> popOut(popup, feedbackLabel, panel));

            // Light top bar in-screen (office.lss #F6F6F6): the stock white
            // glyph would vanish on it, so tint it dark here; the windowed
            // chrome (dark #18181B) keeps the white original above.
            Button closeButton = chromeButton(Icons.WINDOW_CLOSE, "Close (Esc)", true);
            // Dark glyph for the light in-screen bar; office.lss tints the
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
            layout.width(u(210));
            layout.paddingAll(u(6));
            layout.gapAll(u(4));
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
        scaleGroup.layout(layout -> layout.flexWrap(FlexWrap.WRAP));
        for (double preset : SCALE_PRESETS) {
            Toggle toggle = new Toggle();
            toggle.setId("ginv_scale_" + Math.round(preset * 100));
            toggle.setText(scalePresetLabel(preset));
            toggle.layout(layout -> layout.height(u(14)));
            toggle.toggleLabel(label -> label.textStyle(style -> style.fontSize(u(10))));
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

        // Mode: Popup | Screen. In-game only — the OS window is windowed by
        // definition, so the control is omitted there rather than no-op.
        if (!windowed) {
            viewPopover.addChild(caption("Mode"));
            ToggleGroupElement modeGroup = new ToggleGroupElement();
            modeGroup.addClass("ginv-mode-group");
            for (int i = 0; i < 2; i++) {
                boolean wantPopup = i == 0;
                Toggle toggle = new Toggle();
                toggle.setId(wantPopup ? "ginv_mode_popup" : "ginv_mode_screen");
                toggle.setText(wantPopup ? "Popup" : "Screen");
                toggle.layout(layout -> layout.height(u(14)));
                toggle.toggleLabel(label -> label.textStyle(style -> style.fontSize(u(10))));
                toggle.setOn(popup == wantPopup, false);
                toggle.setOnToggleChanged(isOn -> {
                    if (Boolean.TRUE.equals(isOn)) {
                        switchMenuMode(root, wantPopup);
                    }
                });
                modeGroup.addChild(toggle);
            }
            viewPopover.addChild(modeGroup);
        }

        panel.addChild(viewPopover);
        root.viewPopover = viewPopover;
        root.viewAnchor = viewButton;
        root.viewOpen = false;
        viewButton.setOnClick(event -> toggleViewPopover(root));

        // Dismissal: a bubbling press that missed both anchor and popover —
        // geometrically or as the event's target subtree — and a genuine root
        // size change (viewport resize). LAYOUT_CHANGED does not bubble, but
        // it DOES fire on the root when a child's geometry changes the root's
        // content size. The popover is now an out-of-flow child of #ginv_panel,
        // so open/close no longer perturbs the root's size and only a real
        // resize/rebuild closes.
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0 || !root.viewOpen) return;
            if (within(root.viewAnchor, event.x, event.y)
                    || within(root.viewPopover, event.x, event.y)
                    || inSubtree(root.viewAnchor, event.target)
                    || inSubtree(root.viewPopover, event.target)) {
                return;
            }
            hideViewPopover(root);
        });
        root.addEventListener(UIEvents.LAYOUT_CHANGED, event -> {
            float width = root.getSizeWidth();
            float height = root.getSizeHeight();
            boolean resized = width != root.lastRootWidth || height != root.lastRootHeight;
            root.lastRootWidth = width;
            root.lastRootHeight = height;
            // Child-driven contentSize churn (popover open/close) leaves the
            // root's size untouched — only a real resize/rebuild closes.
            if (resized && root.viewOpen) hideViewPopover(root);
        });

        // Tabs: Control | Lists | Monitor | Settings ----------------------
        // Body region owns the vertical grow (office.lss .ginv-body) so tall
        // tab content scrolls instead of pushing the status bar off-card.
        TabView tabView = new TabView();
        UIElement body = new UIElement();
        body.addClass("ginv-body");
        body.addChild(tabView);
        panel.addChild(body);

        List<Tab> tabs = new ArrayList<>();
        addTab(tabView, tabs, "Control", controlTab(root, feedbackLabel));
        addTab(tabView, tabs, "Lists", listsTab(root));
        addTab(tabView, tabs, "Monitor", monitorTab(root));
        addTab(tabView, tabs, "Settings", settingsTab(root, feedbackLabel));
        root.tabs = tabs;
        if (savedTab > 0 && savedTab < tabs.size()) {
            tabView.selectTab(tabs.get(savedTab));
        }

        // Status bar -----------------------------------------------------
        // One shared strip for both contexts: live queue status on the left,
        // transient action feedback on the right.
        Label statusLabel = new Label();
        statusLabel.setId("ginv_status");
        statusLabel.setText(statusText());
        statusLabel.textStyle(style -> style.fontSize(u(9)));

        UIElement statusBar = new UIElement();
        statusBar.setId("ginv_statusbar");
        // Row/height/padding/gap/paint come from office.lss (.ginv-statusbar).
        statusBar.addClass("ginv-statusbar");
        // Surface comes from office.lss (#ginv_statusbar); no inline paint.
        statusLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        feedbackLabel.layout(layout -> layout.minWidth(0));
        statusBar.addChildren(statusLabel, feedbackLabel);
        panel.addChild(statusBar);

        // Hand the live widgets to the refresh loop (GinvRoot.screenTick runs in
        // both screen and windowed contexts) and snapshot the change tokens.
        root.statusLabel = statusLabel;
        root.lastVersion = GinvDataStore.version();
        root.lastOnline = onlineSnapshot();
        root.lastTargets = List.copyOf(GinvCommand.getGinvTargets());
        root.lastGuiScale = windowed ? mcGuiScale() : 1;

        return new Layout(root, panel);
    }

    private static void addTab(TabView tabView, List<Tab> tabs, String name, UIElement content) {
        Tab tab = new Tab().setText(name);
        String slug = name.toLowerCase(Locale.ROOT);
        // Stable ids/classes so the uitest scenarios can target tabs and panes.
        tab.setId("ginv_tab_" + slug);
        tab.addClass("ginv_tab");
        content.setId("ginv_pane_" + slug);
        content.addClass("ginv_pane");
        tabs.add(tab);
        tabView.addTab(tab, content);
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
        // Row/height/padding/gap/paint come from office.lss (.ginv-banner).
        banner.addClass("ginv-banner");
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
        bannerLabel.textStyle(style -> style.fontSize(u(10)).textShadow(true));
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
        hero.textStyle(style -> style.fontSize(u(11)).textShadow(true));
        // Red STOP ⇄ green RESUME surfaces come from the hero state classes in
        // office.lss; screenTick flips them when freeze toggles (B/§4).
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
        content.addChild(sectionTitle("Queue"));

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
        queueNames.textStyle(style -> style.fontSize(u(10)));
        queueNames.setOnClick(event -> {
            Set<String> parsed = GinvCommand.parseTargets(nameField.getValue());
            if (parsed.isEmpty()) {
                feedback(feedbackLabel, "No valid names.", FeedbackKind.ERR);
                return;
            }
            GinvCommand.queueAndSchedule(parsed);
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
        queueLevel.textStyle(style -> style.fontSize(u(10)));
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
        root.rangeLabel = rangeLabel;
        root.lastCanQueue = canQueueByLevel();
        queueLevel.setActive(root.lastCanQueue);
        content.addChild(rangeLabel);

        // --- current targets (bare /ginv, with per-row remove) ---
        UIElement targetsHeaderRow = row(u(16));
        Label targetsHeader = sectionTitle(root.lastHeader = "Current targets ("
                + GinvCommand.getGinvTargets().size() + ")");
        targetsHeader.setId("ginv_targets_header");
        targetsHeader.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        root.targetsHeader = targetsHeader;
        Button clearButton = new Button();
        clearButton.setId("ginv_clear");
        clearButton.setText("Clear");
        clearButton.layout(layout -> layout.height(u(16)));
        clearButton.textStyle(style -> style.fontSize(u(10)));
        clearButton.getStyle().tooltips("Clear all targets and pending invites");
        clearButton.setOnClick(event -> {
            GinvCommand.clearTargets();
            feedback(feedbackLabel, "Targets cleared.", FeedbackKind.OK);
        });
        targetsHeaderRow.addChildren(targetsHeader, clearButton);
        content.addChild(targetsHeaderRow);

        ScrollerView targetsScroll = new ScrollerView();
        targetsScroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        fillTargetsScroll(targetsScroll, collectLevels());
        root.targetsScroll = targetsScroll;
        content.addChild(targetsScroll);

        return content;
    }

    /** Lists tab — add by name, whitelist/blacklist rows, now with queue buttons. */
    private static UIElement listsTab(GinvRoot root) {
        TextField nameField = new TextField().setAnyString();
        nameField.textFieldStyle(style -> style
                .placeholder(Component.literal("Player name"))
                .fontSize(u(10)));
        nameField.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
            layout.height(u(16));
        });

        Button addButton = new Button();
        addButton.setText("Add");
        addButton.layout(layout -> layout.height(u(16)));
        addButton.textStyle(style -> style.fontSize(u(10)));
        addButton.setOnClick(event -> {
            String raw = nameField.getValue() == null ? "" : nameField.getValue().trim();
            if (raw.isEmpty()) return;
            if (GinvDataStore.touch(raw)) {
                nameField.setText("");
            } else {
                nameField.setText(raw);
            }
        });

        UIElement addRow = row(u(16));
        addRow.addChildren(nameField, addButton);

        ScrollerView listsScroll = new ScrollerView();
        listsScroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        fillListsScroll(listsScroll, collectLevels());
        root.listsScroll = listsScroll;

        UIElement content = tabColumn();
        content.addChildren(addRow, listsScroll);
        return content;
    }

    /** Monitor tab — per-player invite counts and last-invite times. */
    private static UIElement monitorTab(GinvRoot root) {
        ScrollerView monitorScroll = new ScrollerView();
        monitorScroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        fillMonitorScroll(monitorScroll, collectLevels());
        root.monitorScroll = monitorScroll;

        UIElement content = tabColumn();
        content.addChild(monitorScroll);
        return content;
    }

    /** Settings tab — delays, whitelist-only, and the independent menu scale. */
    private static UIElement settingsTab(GinvRoot root, Label feedbackLabel) {
        TextField minDelayField = new TextField().setNumbersOnlyInt(50, 60_000);
        minDelayField.setText(String.valueOf(GinvDataStore.minDelayMs()));
        minDelayField.textFieldStyle(style -> style.fontSize(u(10)));
        minDelayField.layout(layout -> {
            layout.width(u(52));
            layout.height(u(16));
        });

        TextField maxDelayField = new TextField().setNumbersOnlyInt(50, 60_000);
        maxDelayField.setText(String.valueOf(GinvDataStore.maxDelayMs()));
        maxDelayField.textFieldStyle(style -> style.fontSize(u(10)));
        maxDelayField.layout(layout -> {
            layout.width(u(52));
            layout.height(u(16));
        });

        Button applyButton = new Button();
        applyButton.setText("Apply");
        applyButton.layout(layout -> {
            layout.height(u(16));
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        applyButton.textStyle(style -> style.fontSize(u(10)));
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

        Label delayLabel = bodyLabel("Delay (ms):");
        delayLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        UIElement delayCtl = new UIElement();
        delayCtl.addClass("ginv-ctl");
        delayCtl.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(4));
            layout.width(u(165));
        });
        delayCtl.addChildren(minDelayField, dashLabel(), maxDelayField, applyButton);
        UIElement delayRow = row(u(16));
        delayRow.addChildren(delayLabel, delayCtl);

        Switch whitelistSwitch = new Switch();
        whitelistSwitch.setOn(GinvDataStore.whitelistOnly(), false);
        whitelistSwitch.registerValueListener(value ->
                GinvDataStore.setWhitelistOnly(Boolean.TRUE.equals(value)));
        whitelistSwitch.getStyle().tooltips("Only invite whitelisted players");
        root.whitelistSwitch = whitelistSwitch;

        Label whitelistLabel = bodyLabel("Whitelist-only mode");
        whitelistLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        UIElement whitelistCtl = new UIElement();
        whitelistCtl.addClass("ginv-ctl");
        whitelistCtl.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_END);
            layout.width(u(165));
        });
        whitelistCtl.addChild(whitelistSwitch);
        UIElement whitelistRow = row(u(16));
        whitelistRow.addChildren(whitelistLabel, whitelistCtl);

        Label hintLabel = caption("Blacklist always blocks; whitelist-only limits invites.");

        // Menu scale + autoscale + mode moved into the View popover (T8).
        UIElement content = tabColumn();
        content.addChildren(delayRow, whitelistRow, hintLabel);
        return content;
    }

    // --------------------------------------------------------------- widgets

    private static UIElement tabColumn() {
        UIElement column = new UIElement();
        column.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapRow(u(4));
        });
        return column;
    }

    private static UIElement row(float height) {
        UIElement row = new UIElement();
        row.addClass("ginv-row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(4));
            layout.widthPercent(100);
            layout.height(height);
        });
        return row;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-section");
        label.textStyle(style -> style.fontSize(u(10)));
        return label;
    }

    private static Label caption(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-caption");
        label.textStyle(style -> style.fontSize(u(9)));
        return label;
    }

    private static Label bodyLabel(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-body-text");
        label.textStyle(style -> style.fontSize(u(10)));
        return label;
    }

    private static Label dashLabel() {
        Label dash = new Label();
        dash.setText("-");
        dash.addClass("ginv-caption");
        dash.textStyle(style -> style.fontSize(u(10)));
        return dash;
    }

    /** Semantic feedback tint; office.lss maps each kind to a paper token. */
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

    // ------------------------------------------------------- player rows

    /**
     * One player row: head, name, guild-level badge, optional invite count,
     * optional queue-now button, then the W/B/X list buttons.
     */
    private static UIElement buildPlayerRow(String name, GinvDataStore.PlayerSnapshot snapshot,
                                            boolean withCount, GuildLevels.LevelInfo level,
                                            boolean withQueue) {
        GinvDataStore.ListState state = snapshot == null
                ? GinvDataStore.ListState.NONE
                : snapshot.listState();

        UIElement row = new UIElement();
        row.setId("ginv_row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(u(3));
            layout.widthPercent(100);
            layout.height(u(16));
        });

        UIElement head = new UIElement();
        head.layout(layout -> {
            layout.width(u(10));
            layout.height(u(10));
        });
        head.getStyle().backgroundTexture(new PlayerHeadTexture(name));

        Label nameLabel = new Label();
        nameLabel.setText(name);
        nameLabel.textStyle(style -> style.fontSize(u(10)));
        nameLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });

        row.addChildren(head, nameLabel, levelBadge(level));

        if (withCount && snapshot != null) {
            String count = "×" + snapshot.invites();
            if (snapshot.lastInviteMs() > 0) {
                count += " · " + ago(snapshot.lastInviteMs());
            }
            Label countLabel = new Label();
            countLabel.setText(count);
            countLabel.addClass("ginv-muted");
            countLabel.textStyle(style -> style.fontSize(u(9)));
            row.addChildren(countLabel);
        }

        if (withQueue) {
            row.addChildren(listButton("⚡", null,
                    "Queue an invite for this player now",
                    () -> GinvCommand.queueAndSchedule(List.of(name))));
        }

        row.addChildren(
                listButton("W", state == GinvDataStore.ListState.WHITE
                                ? "ginv-list-white" : null,
                        "Whitelist player", () -> {
                            GinvDataStore.PlayerSnapshot current = GinvDataStore.snapshot(name);
                            GinvDataStore.ListState currentState = current == null
                                    ? GinvDataStore.ListState.NONE
                                    : current.listState();
                            GinvDataStore.setListState(name,
                                    currentState == GinvDataStore.ListState.WHITE
                                            ? GinvDataStore.ListState.NONE
                                            : GinvDataStore.ListState.WHITE);
                        }),
                listButton("B", state == GinvDataStore.ListState.BLACK
                                ? "ginv-list-black" : null,
                        "Blacklist player (always blocks invites)", () -> {
                            GinvDataStore.PlayerSnapshot current = GinvDataStore.snapshot(name);
                            GinvDataStore.ListState currentState = current == null
                                    ? GinvDataStore.ListState.NONE
                                    : current.listState();
                            GinvDataStore.setListState(name,
                                    currentState == GinvDataStore.ListState.BLACK
                                            ? GinvDataStore.ListState.NONE
                                            : GinvDataStore.ListState.BLACK);
                        }),
                listButton("X", null,
                        "Remove record and unqueue", () -> {
                            GinvDataStore.removePlayer(name);
                            GinvCommand.removeFromQueue(name);
                        })
        );

        return row;
    }

    /** {@code [42]} in the server's prefix color, or a muted dash when unknown. */
    private static Label levelBadge(GuildLevels.LevelInfo level) {
        Label badge = new Label();
        if (level == null) {
            badge.setText("—");
            badge.addClass("ginv-muted");
            badge.textStyle(style -> style.fontSize(u(9)));
        } else {
            badge.setText("[" + level.value() + "]");
            badge.textStyle(style -> style.fontSize(u(9)).textColor(level.color()));
        }
        badge.layout(layout -> layout.width(u(16)));
        badge.getStyle().tooltips("Guild level");
        return badge;
    }

    private static Button listButton(String text, String activeClass,
                                     String tooltip, Runnable action) {
        Button button = new Button();
        button.setText(text);
        if (activeClass != null) {
            // Active W/B states read as a saturated tint via a semantic class;
            // idle buttons keep the sheet's default surface and ink.
            button.addClass(activeClass);
        }
        button.text.textStyle(style -> style.fontSize(u(10)));
        button.layout(layout -> {
            layout.width(u(14));
            layout.height(u(16));
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

    private static Label emptyRow(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-empty");
        label.textStyle(style -> style.fontSize(u(9)));
        return label;
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

    private static void fillListsScroll(ScrollerView scroll, Map<String, GuildLevels.LevelInfo> levels) {
        List<String> names = collectNames();
        scroll.clearAllScrollViewChildren();
        if (names.isEmpty()) {
            scroll.addScrollViewChildren(emptyRow("No players."));
            return;
        }
        for (String name : names) {
            scroll.addScrollViewChildren(buildPlayerRow(name, GinvDataStore.snapshot(name),
                    false, levels.get(name), true));
        }
    }

    private static void fillMonitorScroll(ScrollerView scroll, Map<String, GuildLevels.LevelInfo> levels) {
        List<GinvDataStore.PlayerSnapshot> snapshots = new ArrayList<>(GinvDataStore.snapshots());
        snapshots.sort(Comparator
                .comparingInt(GinvDataStore.PlayerSnapshot::invites).reversed()
                .thenComparing(GinvDataStore.PlayerSnapshot::name,
                        String.CASE_INSENSITIVE_ORDER));
        scroll.clearAllScrollViewChildren();
        if (snapshots.isEmpty()) {
            scroll.addScrollViewChildren(emptyRow("No invites recorded yet."));
            return;
        }
        for (GinvDataStore.PlayerSnapshot snapshot : snapshots) {
            scroll.addScrollViewChildren(buildPlayerRow(snapshot.name(), snapshot,
                    true, levels.get(snapshot.name()), false));
        }
    }

    /** Current targets with level badges and a per-row remove button. */
    private static void fillTargetsScroll(ScrollerView scroll, Map<String, GuildLevels.LevelInfo> levels) {
        List<String> targets = new ArrayList<>(GinvCommand.getGinvTargets());
        scroll.clearAllScrollViewChildren();
        if (targets.isEmpty()) {
            scroll.addScrollViewChildren(emptyRow("No targets queued. Use Queue above."));
            return;
        }
        for (String name : targets) {
            UIElement row = new UIElement();
            row.setId("ginv_target_row");
            row.layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.gapColumn(u(3));
                layout.widthPercent(100);
                layout.height(u(16));
            });

            UIElement head = new UIElement();
            head.layout(layout -> {
                layout.width(u(10));
                layout.height(u(10));
            });
            head.getStyle().backgroundTexture(new PlayerHeadTexture(name));

            Label nameLabel = new Label();
            nameLabel.setText(name);
            nameLabel.textStyle(style -> style.fontSize(u(10)));
            nameLabel.layout(layout -> {
                layout.flexGrow(1);
                layout.minWidth(0);
            });

            row.addChildren(head, nameLabel, levelBadge(levels.get(name)),
                    listButton("X", null,
                            "Remove from queue", () -> GinvCommand.removeFromQueue(name)));
            scroll.addScrollViewChildren(row);
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
                + " · whitelist-only " + (GinvDataStore.whitelistOnly() ? "ON" : "OFF");
    }

    private static String bannerText(boolean frozen) {
        return (frozen ? "STOPPED" : "RUNNING") + " · " + GinvCommand.getPendingCount() + " pending";
    }

    /** Whether the level queue can run right now (connected and in SkyBlock). */
    private static boolean canQueueByLevel() {
        return SkyBlockDetector.isSkyBlock()
                && Minecraft.getInstance().getConnection() != null;
    }

    /** Caption under the level field: precondition hint or the live tab range. */
    private static String levelCaption() {
        if (Minecraft.getInstance().getConnection() == null) return "Not connected to a server.";
        if (!SkyBlockDetector.isSkyBlock()) return "SkyBlock only.";
        GuildLevels.LevelRange range = GuildLevels.tabRange();
        return range == null ? "tab: no leveled players"
                : "tab: " + range.min() + "–" + range.max();
    }

    private static String scalePresetLabel(double preset) {
        return Math.round(preset * 100) + "%";
    }

    // -------------------------------------------------------- scale changes

    /**
     * The largest scale within [0.75, 2.0] that fits the 340x266 design plus
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
     * Pure fit: viewport → scale in {@code [0.75, 2.0]}, or {@code stored}
     * for a degenerate viewport. Side-effect free (T14: the no-persist
     * contract is testable headless through this signature).
     */
    static double fitScaleFor(int width, int height, double stored) {
        if (width <= 0 || height <= 0) return stored;
        double fit = Math.min(width * 0.96 / SHELL_MAX_WIDTH, height * 0.94 / SHELL_MAX_HEIGHT);
        return Math.clamp(fit, 0.75, 2.0);
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
        // Position and size are owned by office.lss (#ginv_view_popover:
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

    /**
     * Rebuilds the in-game screen in the chosen mode and closes the popover.
     * No-op outside an open {@link GinvMenuScreen} (the OS window has no mode).
     */
    private static void switchMenuMode(GinvRoot root, boolean toPopup) {
        hideViewPopover(root);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GinvMenuScreen menu && menu.popup != toPopup) {
            mc.setScreen(new GinvMenuScreen(toPopup));
        }
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
            } else if (!Double.isNaN(previous) && previous > 0 && !fresh.isMaximized()) {
                // Maximized windows are screen-sized, not WYSIWYG-sized — leave
                // their geometry alone; flex containers absorb the scale change.
                double factor = windowedUiScale / previous;
                var os = fresh.window();
                os.setSize((int) Math.round(os.getWindowWidth() * factor),
                        (int) Math.round(os.getWindowHeight() * factor));
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GinvMenuScreen menu) {
                mc.setScreen(new GinvMenuScreen(menu.popup));
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
        boolean popup = old.popupOrigin();

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

        Layout layout = buildLayout(popup, true);
        GinvMenuWindow fresh = new GinvMenuWindow(
                new ModularUI(UI.of(layout.root(), paperSheet())), "Guild Invite Fix", popup);
        fresh.setDragArea(layout.root().titleBar);

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
     * <b>before</b> {@code buildLayout(popup, true)} runs — its bounds only
     * exist while this screen's layout is current — and the window opens at
     * that size (clamped to {@code ≥200×150} / {@link ModularUIWindow#MIN_*}).
     * Degenerate bounds log one line and fall back to the legacy
     * {@link #openWidth()}/{@link #openHeight()} formula.
     *
     * <p>A fresh copy (not the screen's live UI) because closing the screen
     * would fire {@code onRemoved()} on a shared instance and dispose its
     * style engine. If the platform refuses a second window we stay in-game
     * and say so. The chrome buttons wire themselves to
     * {@link GinvMenuWindow#active()}, so no post-construction rewiring.
     */
    private static void popOut(boolean popup, Label feedbackLabel, UIElement panel) {
        Minecraft mc = Minecraft.getInstance();

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

        Layout windowed = buildLayout(popup, true);

        GinvMenuWindow window = new GinvMenuWindow(
                new ModularUI(UI.of(windowed.root(), paperSheet())), "Guild Invite Fix", popup);
        window.setDragArea(windowed.root().titleBar);
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
        // come from office.lss via the ginv-chrome[-close] classes (B8).
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
        ScrollerView listsScroll;
        ScrollerView monitorScroll;
        ScrollerView targetsScroll;
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
        /** Tabs, polled for the selection worth restoring after a rebuild. */
        List<Tab> tabs = List.of();
        /** View popover overlay (D5): built hidden, positioned at open time. */
        UIElement viewPopover;
        /** Anchor the popover drops from (the top-bar View button). */
        UIElement viewAnchor;
        boolean viewOpen;
        /** Root canvas size at the last geometry event — resize vs content churn. */
        float lastRootWidth;
        float lastRootHeight;
        /** Top-bar SkyBlock chip; tick flips its .ginv-sky-* state classes. */
        SkyBlockStatusElement skyChip;
        boolean popup;
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

            // SkyBlock verdict → chip .ginv-sky-on / .ginv-sky-off flip (the
            // element moves both classes and its label text in one call).
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
            if (version != lastVersion || !online.equals(lastOnline) || !targets.equals(lastTargets)) {
                lastVersion = version;
                lastOnline = online;
                lastTargets = targets;
                Map<String, GuildLevels.LevelInfo> levels = collectLevels();
                fillListsScroll(listsScroll, levels);
                fillMonitorScroll(monitorScroll, levels);
                fillTargetsScroll(targetsScroll, levels);
            }
            String header = "Current targets (" + targets.size() + ")";
            if (!header.equals(lastHeader)) {
                lastHeader = header;
                targetsHeader.setText(header);
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
            }

            // Remember the selected tab so rebuilds land where the user was.
            for (int i = 0; i < tabs.size(); i++) {
                if (tabs.get(i).isSelected()) {
                    savedTab = i;
                    break;
                }
            }
        }
    }
}
