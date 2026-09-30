package com.ginv.ui;

import com.ginv.command.GinvCommand;
import com.ginv.command.LevelQueueResult;
import com.ginv.data.GinvDataStore;
import com.ginv.utils.GuildDirectory;
import com.ginv.utils.GuildLevels;
import com.ginv.utils.SkyBlockDetector;
import com.lowdragmc.lowdraglib2.client.window.OsWindow;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
 * status bar. Both contexts are themed with LDLib2's MODERN stylesheet.
 *
 * <p><b>Scale:</b> every authored pixel goes through {@link #u(double)}.
 * In-screen, values multiply by the persisted {@code uiScale} on top of MC's
 * own GUI scale. In the OS window they divide by the game's GUI scale and the
 * window is opened at {@code base × uiScale / contentScale} physical pixels,
 * so the pop-out keeps one physical size and its proportions at any MC scale
 * option. Scale changes and GUI-scale changes rebuild the current context.
 *
 * <p>Row lists rebuild when {@link GinvDataStore#version()}, the tab list (or
 * its levels) or the target set change; the status line, banner and
 * preconditions refresh every tick.
 */
public class GinvMenuScreen extends ModularUIScreen {

    // --- design tokens (see the control-panel design doc) ---
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0x9CA3AF;
    private static final int COLOR_HINT = 0x8F96A0;
    private static final int COLOR_ACCENT = 0xFF71A4F4;
    private static final int COLOR_SUCCESS = 0xFF22C55E;
    private static final int COLOR_DANGER = 0xFFEF4444;
    private static final int COLOR_WARN = 0xFFF59E0B;
    /** Active-state background tints for the W/B list buttons (text stays white). */
    private static final int TINT_ACTIVE_WHITE = 0xFF1B5E20;
    private static final int TINT_ACTIVE_BLACK = 0xFF7F1D1D;
    private static final int COLOR_BUTTON_IDLE = 0xBBBBBB;
    /** Chrome surfaces, sampled to sit with the MODERN theme's dark panels. */
    private static final int CHROME_BG = 0xFF18181B;
    private static final int WINDOW_BG = 0xFF1E1F22;
    /** Windows convention: the close button's hover fill. */
    private static final int CLOSE_HOVER = 0xFFE81123;
    private static final int CLOSE_PRESSED = 0xFFB00D1F;

    /** Menu scale presets offered by the Settings segmented control. */
    private static final double[] SCALE_PRESETS = {0.75, 1.0, 1.25, 1.5, 2.0};
    /** Pop-out window size in authored pixels at uiScale 1 / contentScale 1. */
    private static final int BASE_WINDOW_WIDTH = 420;
    private static final int BASE_WINDOW_HEIGHT = 300;

    private static final IGuiTexture CHROME_IDLE = new ColorRectTexture(0x00000000);
    private static final IGuiTexture CHROME_HOVER = new ColorRectTexture(0x22FFFFFF);
    private static final IGuiTexture CHROME_PRESSED = new ColorRectTexture(0x33FFFFFF);

    // --- scale context (client thread only; set on every buildLayout entry) ---

    /** {@code uiScale} for the layout currently being built. */
    private static double uiScaleContext = 1.0;
    /** Whether the layout currently being built targets the OS window. */
    private static boolean windowedContext = false;

    /**
     * Authored pixels → canvas units for the active context.
     *
     * <p>In-screen: {@code x × S} on top of MC's GUI scale (MC owns that
     * canvas). Windowed: {@code x × S / guiScale}, which cancels the window's
     * guiScale-dependent canvas so proportions and physical size stay put
     * while the window itself is sized in physical pixels.
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

    /** Remembered tab index, restored on rebuild (client thread only). */
    private static int savedTab = 0;

    /** The LDLib2 theme both the screen and the pop-out window are styled with. */
    private static Stylesheet modernSheet() {
        return StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MODERN);
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
        super(new ModularUI(UI.of(layout.root(), modernSheet())), Component.literal("Guild Invite Fix"));
        this.popup = popup;

        if (popup) {
            UIElement panel = layout.panel();
            layout.root().addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0) return;
                float left = panel.getPositionX();
                float top = panel.getPositionY();
                boolean inside = event.x >= left && event.x <= left + panel.getSizeWidth()
                        && event.y >= top && event.y <= top + panel.getSizeHeight();
                if (!inside) {
                    onClose();
                }
            });
        }
    }

    // --------------------------------------------------------------- build

    private static Layout buildLayout(boolean popup, boolean windowed) {
        uiScaleContext = GinvDataStore.uiScale();
        windowedContext = windowed;

        // Created before the title bar so the pop-out button can capture it for
        // the "no second window available" feedback path.
        Label feedbackLabel = new Label();
        feedbackLabel.setId("ginv_feedback");
        feedbackLabel.setText("");
        feedbackLabel.textStyle(style -> style.fontSize(u(9)).textColor(COLOR_SUCCESS));

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
            // The OS window supplies the frame; the root fills it edge to edge.
            root.getStyle().backgroundTexture(new ColorRectTexture(WINDOW_BG));
        } else if (!popup) {
            root.getStyle().backgroundTexture(new ColorRectTexture(0xA6000000));
        }

        UIElement panel = new UIElement();
        panel.setId("ginv_panel");
        panel.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(u(5));
            layout.gapAll(u(3));
            if (windowed) {
                // Fills the OS window between title bar and status bar.
                layout.widthPercent(100);
                layout.flexGrow(1);
            } else {
                layout.width(u(340));
                layout.maxWidthPercent(96);
                layout.height(u(266));
                layout.maxHeightPercent(94);
            }
        });
        panel.getStyle().backgroundTexture(Sprites.BORDER);
        root.addChild(panel);

        // Title bar ------------------------------------------------------
        UIElement titleBar = new UIElement();
        titleBar.setId("ginv_titlebar");
        titleBar.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            if (windowed) {
                layout.height(u(15));
                layout.paddingHorizontal(u(6));
                layout.paddingVertical(u(1));
                layout.gapAll(u(2));
            } else {
                layout.height(u(14));
                layout.gapColumn(u(4));
            }
        });
        if (windowed) {
            titleBar.getStyle().backgroundTexture(new ColorRectTexture(CHROME_BG));
        }

        Label titleLabel = new Label();
        titleLabel.setText("Guild Invite Fix");
        titleLabel.textStyle(style -> style.fontSize(u(10)).textColor(COLOR_TEXT));
        titleLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        titleBar.addChild(titleLabel);

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
                pinButton.setId("ginv_pin");
                pinButton.setOnClick(event -> {
                    GinvMenuWindow window = GinvMenuWindow.active();
                    if (window != null) {
                        boolean onTop = !window.isAlwaysOnTop();
                        window.setAlwaysOnTop(onTop);
                        GinvDataStore.setAlwaysOnTop(onTop);
                    }
                });
                titleBar.addChild(pinButton);
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
            closeButton.setId("ginv_win_close");
            closeButton.setOnClick(event -> {
                GinvMenuWindow window = GinvMenuWindow.active();
                if (window != null) window.onCloseRequested();
            });

            titleBar.addChildren(dockButton, maximizeButton, closeButton);
            root.titleBar = titleBar;
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
            popOutButton.setOnClick(event -> popOut(popup, feedbackLabel));

            Button closeButton = chromeButton(Icons.WINDOW_CLOSE, "Close (Esc)", true);
            closeButton.setId("ginv_close");
            closeButton.setOnClick(event -> Minecraft.getInstance().setScreen(null));

            titleBar.addChildren(popOutButton, closeButton);
            root.titleBar = titleBar;
            root.closeButton = closeButton;
        }
        panel.addChild(titleBar);

        // Tabs: Control | Lists | Monitor | Settings ----------------------
        TabView tabView = new TabView();
        tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        panel.addChild(tabView);

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
        statusLabel.textStyle(style -> style.fontSize(u(9)).textColor(COLOR_MUTED));

        UIElement statusBar = new UIElement();
        statusBar.setId("ginv_statusbar");
        statusBar.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            layout.height(u(13));
            layout.paddingHorizontal(u(4));
            layout.gapAll(u(4));
        });
        statusBar.getStyle().backgroundTexture(new ColorRectTexture(CHROME_BG));
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
        banner.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            layout.height(u(16));
            layout.paddingHorizontal(u(6));
            layout.gapAll(u(6));
        });
        banner.getStyle().backgroundTexture(new ColorRectTexture(0x22FFFFFF));
        UIElement dot = new UIElement();
        dot.layout(layout -> {
            layout.width(u(6));
            layout.height(u(6));
        });
        dot.getStyle().backgroundTexture(DynamicTexture.of(() ->
                new ColorRectTexture(GinvCommand.isFrozen() ? COLOR_DANGER : COLOR_SUCCESS)));
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
        bannerLabel.textStyle(style -> style.textColor(
                root.lastFrozen ? COLOR_DANGER : COLOR_SUCCESS));
        content.addChild(banner);

        // Hero factory STOP: full-width, red STOP ⇄ green RESUME.
        Button hero = new Button();
        hero.setId("ginv_hero");
        hero.layout(layout -> {
            layout.widthPercent(100);
            layout.height(u(24));
        });
        hero.textStyle(style -> style.fontSize(u(11)).textShadow(true));
        hero.buttonStyle(style -> style
                .baseTexture(DynamicTexture.of(() -> new ColorRectTexture(
                        GinvCommand.isFrozen() ? COLOR_SUCCESS : COLOR_DANGER)))
                .hoverTexture(DynamicTexture.of(() -> new ColorRectTexture(
                        shade(GinvCommand.isFrozen() ? COLOR_SUCCESS : COLOR_DANGER, 1.15))))
                .pressedTexture(DynamicTexture.of(() -> new ColorRectTexture(
                        shade(GinvCommand.isFrozen() ? COLOR_SUCCESS : COLOR_DANGER, 0.8)))));
        hero.setOnClick(event -> {
            GinvCommand.toggleFreeze();
            int pending = GinvCommand.getPendingCount();
            if (GinvCommand.isFrozen()) {
                feedback(feedbackLabel, "Queue frozen. " + pending + " invite(s) pending.", COLOR_DANGER);
            } else {
                feedback(feedbackLabel, "Queue resumed. " + pending + " invite(s) pending.", COLOR_SUCCESS);
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
                feedback(feedbackLabel, "No valid names.", COLOR_DANGER);
                return;
            }
            GinvCommand.queueAndSchedule(parsed);
            feedback(feedbackLabel, "Queued " + parsed.size() + " invites.", COLOR_SUCCESS);
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
                feedback(feedbackLabel, "Enter a level.", COLOR_DANGER);
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
            int color = switch (result.error()) {
                case NONE -> COLOR_SUCCESS;
                case NO_MATCHES -> COLOR_WARN;
                default -> COLOR_DANGER;
            };
            feedback(feedbackLabel, text, color);
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
            feedback(feedbackLabel, "Targets cleared.", COLOR_SUCCESS);
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
        applyButton.layout(layout -> layout.height(u(16)));
        applyButton.textStyle(style -> style.fontSize(u(10)));
        applyButton.setOnClick(event -> {
            try {
                int min = Integer.parseInt(minDelayField.getValue().trim());
                int max = Integer.parseInt(maxDelayField.getValue().trim());
                GinvDataStore.setDelays(min, max);
                minDelayField.setText(String.valueOf(GinvDataStore.minDelayMs()));
                maxDelayField.setText(String.valueOf(GinvDataStore.maxDelayMs()));
                feedback(feedbackLabel, "Applied.", COLOR_SUCCESS);
            } catch (NumberFormatException e) {
                feedback(feedbackLabel, "Invalid delay.", COLOR_DANGER);
            }
        });

        Label delayLabel = bodyLabel("Delay (ms):");
        UIElement delayRow = row(u(16));
        delayRow.addChildren(delayLabel, minDelayField, dashLabel(), maxDelayField, applyButton);

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
        UIElement whitelistRow = row(u(16));
        whitelistRow.addChildren(whitelistLabel, whitelistSwitch);

        Label hintLabel = caption("Blacklist always blocks; whitelist-only limits invites.");

        // Independent menu scale: segmented presets, persisted, rebuild-on-change.
        Label scaleLabel = bodyLabel("Menu scale");
        scaleLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        ToggleGroupElement scaleGroup = new ToggleGroupElement();
        scaleGroup.layout(layout -> layout.height(u(14)));
        for (double preset : SCALE_PRESETS) {
            Toggle toggle = new Toggle();
            toggle.setId("ginv_scale_" + Math.round(preset * 100));
            toggle.setText(scalePresetLabel(preset));
            toggle.layout(layout -> layout.height(u(14)));
            toggle.toggleLabel(label -> label.textStyle(style -> style.fontSize(u(10))));
            toggle.setOn(Math.abs(GinvDataStore.uiScale() - preset) < 1e-6, false);
            toggle.setOnToggleChanged(isOn -> {
                if (Boolean.TRUE.equals(isOn)) {
                    GinvDataStore.setUiScale(preset);
                    applyUiScale();
                }
            });
            scaleGroup.addChild(toggle);
        }
        UIElement scaleRow = row(u(14));
        scaleRow.addChildren(scaleLabel, scaleGroup);

        UIElement content = tabColumn();
        content.addChildren(delayRow, whitelistRow, scaleRow, hintLabel);
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
        label.textStyle(style -> style
                .fontSize(u(10))
                .textColor(COLOR_ACCENT)
                .textShadow(true));
        return label;
    }

    private static Label caption(String text) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.fontSize(u(9)).textColor(COLOR_MUTED));
        return label;
    }

    private static Label bodyLabel(String text) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.fontSize(u(10)).textColor(COLOR_TEXT));
        return label;
    }

    private static Label dashLabel() {
        Label dash = new Label();
        dash.setText("-");
        dash.textStyle(style -> style.fontSize(u(10)).textColor(COLOR_MUTED));
        return dash;
    }

    private static void feedback(Label label, String text, int color) {
        label.setText(text);
        label.textStyle(style -> style.textColor(color));
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
        nameLabel.textStyle(style -> style.fontSize(u(10)).textColor(COLOR_TEXT));
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
            countLabel.textStyle(style -> style.fontSize(u(9)).textColor(COLOR_MUTED));
            row.addChildren(countLabel);
        }

        if (withQueue) {
            row.addChildren(listButton("⚡", false, COLOR_BUTTON_IDLE,
                    "Queue an invite for this player now",
                    () -> GinvCommand.queueAndSchedule(List.of(name))));
        }

        row.addChildren(
                listButton("W", state == GinvDataStore.ListState.WHITE, TINT_ACTIVE_WHITE,
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
                listButton("B", state == GinvDataStore.ListState.BLACK, TINT_ACTIVE_BLACK,
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
                listButton("X", false, COLOR_BUTTON_IDLE,
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
            badge.textStyle(style -> style.fontSize(u(9)).textColor(COLOR_MUTED));
        } else {
            badge.setText("[" + level.value() + "]");
            badge.textStyle(style -> style.fontSize(u(9)).textColor(level.color()));
        }
        badge.layout(layout -> layout.width(u(16)));
        badge.getStyle().tooltips("Guild level");
        return badge;
    }

    private static Button listButton(String text, boolean active, int activeTint,
                                     String tooltip, Runnable action) {
        Button button = new Button();
        button.setText(text);
        if (active) {
            // Active states get a background tint so they read at a glance on
            // the themed blue buttons; idle leaves the theme alone.
            button.buttonStyle(style -> style
                    .baseTexture(new ColorRectTexture(activeTint))
                    .hoverTexture(new ColorRectTexture(activeTint))
                    .pressedTexture(new ColorRectTexture(activeTint)));
        }
        button.text.textStyle(style -> style
                .fontSize(u(10))
                .textColor(active ? COLOR_TEXT : COLOR_BUTTON_IDLE));
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
        label.textStyle(style -> style.fontSize(u(9)).textColor(COLOR_HINT));
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
            nameLabel.textStyle(style -> style.fontSize(u(10)).textColor(COLOR_TEXT));
            nameLabel.layout(layout -> {
                layout.flexGrow(1);
                layout.minWidth(0);
            });

            row.addChildren(head, nameLabel, levelBadge(levels.get(name)),
                    listButton("X", false, COLOR_BUTTON_IDLE,
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
     * Rebuilds whichever context is open after {@code uiScale} changed:
     * the OS window in place (geometry kept), or the in-game screen.
     */
    private static void applyUiScale() {
        if (GinvMenuWindow.active() != null) {
            rebuildActiveWindow();
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
     */
    private static void rebuildActiveWindow() {
        GinvMenuWindow old = GinvMenuWindow.active();
        if (old == null) return;
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
                new ModularUI(UI.of(layout.root(), modernSheet())), "Guild Invite Fix", popup);
        fresh.setDragArea(layout.root().titleBar);

        if (!fresh.open(x, y, width, height, false)) {
            return; // no second window — keep the old one as-is
        }
        GinvMenuWindow.track(fresh);
        if (onTop && OsWindow.supportsAlwaysOnTop()) {
            fresh.setAlwaysOnTop(true);
        }
        if (maximized) {
            fresh.toggleMaximized();
        }
        old.close();
    }

    /** Pop-out size in physical pixels: authored base × uiScale / contentScale.
     *  Clamped to the platform minimum so a small scale / high content scale can
     *  never hand the window a size it refuses (the "pop-out unavailable" path). */
    private static int openWidth() {
        return Math.max(ModularUIWindow.MIN_WIDTH,
                (int) Math.round(BASE_WINDOW_WIDTH * GinvDataStore.uiScale() / contentScale()));
    }

    private static int openHeight() {
        return Math.max(ModularUIWindow.MIN_HEIGHT,
                (int) Math.round(BASE_WINDOW_HEIGHT * GinvDataStore.uiScale() / contentScale()));
    }

    // ------------------------------------------------------------- pop out

    /**
     * Lifts a freshly built copy of the menu into its own OS window, then
     * closes the in-game screen.
     *
     * <p>A fresh copy (not the screen's live UI) because closing the screen
     * would fire {@code onRemoved()} on a shared instance and dispose its
     * style engine. If the platform refuses a second window we stay in-game
     * and say so. The chrome buttons wire themselves to
     * {@link GinvMenuWindow#active()}, so no post-construction rewiring.
     */
    private static void popOut(boolean popup, Label feedbackLabel) {
        Minecraft mc = Minecraft.getInstance();
        Layout windowed = buildLayout(popup, true);

        GinvMenuWindow window = new GinvMenuWindow(
                new ModularUI(UI.of(windowed.root(), modernSheet())), "Guild Invite Fix", popup);
        window.setDragArea(windowed.root().titleBar);
        GinvMenuWindow.track(window);

        if (window.open(Integer.MIN_VALUE, Integer.MIN_VALUE, openWidth(), openHeight(), false)) {
            if (GinvDataStore.alwaysOnTop() && OsWindow.supportsAlwaysOnTop()) {
                window.setAlwaysOnTop(true);
            }
            mc.setScreen(null);
        } else {
            // Include the computed dims — if the platform refused them the
            // numbers localize the regression in the uitest screenshot.
            feedback(feedbackLabel, "Pop-out unavailable (" + openWidth() + "×" + openHeight()
                    + ") — staying in-game.", COLOR_DANGER);
        }
    }

    /** Icon-only title-bar button: transparent at rest, tinted on hover. */
    private static Button chromeButton(IGuiTexture icon, String tooltip, boolean closeStyle) {
        Button button = new Button();
        button.noText().addPreIcon(icon);
        button.layout(layout -> {
            layout.width(u(16));
            layout.height(u(12));
        });
        button.getStyle().tooltips(tooltip);
        button.buttonStyle(style -> style
                .baseTexture(CHROME_IDLE)
                .hoverTexture(closeStyle ? new ColorRectTexture(CLOSE_HOVER) : CHROME_HOVER)
                .pressedTexture(closeStyle ? new ColorRectTexture(CLOSE_PRESSED) : CHROME_PRESSED));
        return button;
    }

    /** Multiplies a color's RGB channels (clamped); alpha is kept. */
    private static int shade(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.clamp((int) Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Math.clamp((int) Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Math.clamp((int) Math.round((argb & 0xFF) * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
        boolean popup;
        boolean windowed;

        // change tokens
        int lastVersion;
        List<String> lastOnline = List.of();
        List<String> lastTargets = List.of();
        boolean lastFrozen;
        boolean lastCanQueue;
        String lastBanner = "";
        String lastCaption = "";
        String lastHeader = "";
        double lastGuiScale = 1;

        @Override
        public void screenTick() {
            super.screenTick();

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

            // Banner dot/hero textures read isFrozen() live; text needs a nudge.
            if (frozen != lastFrozen) {
                lastFrozen = frozen;
                heroStopButton.setText(frozen ? "RESUME INVITES" : "STOP INVITES");
                bannerLabel.textStyle(style -> style.textColor(frozen ? COLOR_DANGER : COLOR_SUCCESS));
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

            statusLabel.setText(statusText());

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
