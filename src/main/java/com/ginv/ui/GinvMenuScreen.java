package com.ginv.ui;

import com.ginv.command.GinvCommand;
import com.ginv.data.GinvDataStore;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * The three-section guild invite menu (Settings / Lists / Monitor).
 *
 * <p>Popup mode renders over a transparent screen background and closes on an
 * outside click; screen mode adds a dimmed backdrop and is modal (ESC only).
 * Row lists rebuild when {@link GinvDataStore#version()} or the tab list changes;
 * the status line refreshes every tick.
 */
public class GinvMenuScreen extends ModularUIScreen {

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_HINT = 0x999999;
    private static final int COLOR_ACTIVE_WHITE = 0x55FF55;
    private static final int COLOR_ACTIVE_BLACK = 0xFF5555;
    private static final int COLOR_BUTTON_IDLE = 0xBBBBBB;
    private static final int COLOR_OK = 0x55FF55;
    private static final int COLOR_ERROR = 0xFF5555;

    private final boolean popup;
    private final UIElement panel;

    private final Label statusLabel;
    private final Label feedbackLabel;
    private final TextField minDelayField;
    private final TextField maxDelayField;
    private final Switch whitelistSwitch;
    private final ScrollerView listsScroll;
    private final ScrollerView monitorScroll;

    private int lastVersion;
    private List<String> lastOnline = List.of();

    /** Everything the constructor needs, assembled statically before the screen exists. */
    private record Layout(
            UIElement root,
            UIElement panel,
            Label statusLabel,
            Label feedbackLabel,
            TextField minDelayField,
            TextField maxDelayField,
            Switch whitelistSwitch,
            ScrollerView listsScroll,
            ScrollerView monitorScroll
    ) {
    }

    public GinvMenuScreen(boolean popup) {
        this(buildLayout(popup), popup);
    }

    private GinvMenuScreen(Layout layout, boolean popup) {
        super(new ModularUI(UI.of(layout.root())), Component.literal("Guild Invite Fix"));
        this.popup = popup;
        this.panel = layout.panel();
        this.statusLabel = layout.statusLabel();
        this.feedbackLabel = layout.feedbackLabel();
        this.minDelayField = layout.minDelayField();
        this.maxDelayField = layout.maxDelayField();
        this.whitelistSwitch = layout.whitelistSwitch();
        this.listsScroll = layout.listsScroll();
        this.monitorScroll = layout.monitorScroll();

        this.lastVersion = GinvDataStore.version();
        this.lastOnline = onlineSnapshot();

        if (popup) {
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

    private static Layout buildLayout(boolean popup) {
        UIElement root = new UIElement();
        root.setId("ginv_root");
        root.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
            layout.alignItems(AlignItems.CENTER);
        });
        if (!popup) {
            root.getStyle().backgroundTexture(new ColorRectTexture(0xA6000000));
        }

        UIElement panel = new UIElement();
        panel.setId("ginv_panel");
        panel.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.width(340);
            layout.maxWidthPercent(96);
            layout.height(252);
            layout.maxHeightPercent(94);
            layout.paddingAll(5);
            layout.gapAll(3);
        });
        panel.getStyle().backgroundTexture(Sprites.BORDER);
        root.addChild(panel);

        // Title bar ------------------------------------------------------
        UIElement titleBar = new UIElement();
        titleBar.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(4);
            layout.height(14);
            layout.widthPercent(100);
        });
        Label titleLabel = new Label();
        titleLabel.setText("Guild Invite Fix");
        titleLabel.textStyle(style -> style.textColor(COLOR_TEXT));
        titleLabel.layout(layout -> layout.flexGrow(1));
        Button closeButton = new Button();
        closeButton.setText("X");
        closeButton.layout(layout -> layout.width(16));
        closeButton.getStyle().tooltips("Close (Esc)");
        closeButton.setOnClick(event -> Minecraft.getInstance().setScreen(null));
        titleBar.addChildren(titleLabel, closeButton);
        panel.addChild(titleBar);

        // Tab view -------------------------------------------------------
        TabView tabView = new TabView();
        tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        panel.addChild(tabView);

        // --- Settings tab ---
        Label feedbackLabel = new Label();
        feedbackLabel.setText("");
        feedbackLabel.textStyle(style -> style.textColor(COLOR_OK));

        TextField minDelayField = new TextField().setNumbersOnlyInt(50, 60_000);
        minDelayField.setText(String.valueOf(GinvDataStore.minDelayMs()));
        minDelayField.layout(layout -> layout.width(52));

        TextField maxDelayField = new TextField().setNumbersOnlyInt(50, 60_000);
        maxDelayField.setText(String.valueOf(GinvDataStore.maxDelayMs()));
        maxDelayField.layout(layout -> layout.width(52));

        Button applyButton = new Button();
        applyButton.setText("Apply");
        applyButton.setOnClick(event -> {
            try {
                int min = Integer.parseInt(minDelayField.getValue().trim());
                int max = Integer.parseInt(maxDelayField.getValue().trim());
                GinvDataStore.setDelays(min, max);
                minDelayField.setText(String.valueOf(GinvDataStore.minDelayMs()));
                maxDelayField.setText(String.valueOf(GinvDataStore.maxDelayMs()));
                feedbackLabel.setText("Applied.");
                feedbackLabel.textStyle(style -> style.textColor(COLOR_OK));
            } catch (NumberFormatException e) {
                feedbackLabel.setText("Invalid delay.");
                feedbackLabel.textStyle(style -> style.textColor(COLOR_ERROR));
            }
        });

        UIElement delayRow = new UIElement();
        delayRow.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(4);
            layout.widthPercent(100);
            layout.height(14);
        });
        Label delayLabel = new Label();
        delayLabel.setText("Delay (ms):");
        delayLabel.textStyle(style -> style.textColor(COLOR_TEXT));
        delayRow.addChildren(delayLabel, minDelayField, dashLabel(), maxDelayField, applyButton);

        Switch whitelistSwitch = new Switch();
        whitelistSwitch.setOn(GinvDataStore.whitelistOnly(), false);
        whitelistSwitch.registerValueListener(value ->
                GinvDataStore.setWhitelistOnly(Boolean.TRUE.equals(value)));
        whitelistSwitch.getStyle().tooltips("Only invite whitelisted players");

        UIElement whitelistRow = new UIElement();
        whitelistRow.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(4);
            layout.widthPercent(100);
            layout.height(14);
        });
        Label whitelistLabel = new Label();
        whitelistLabel.setText("Whitelist-only mode");
        whitelistLabel.textStyle(style -> style.textColor(COLOR_TEXT));
        whitelistLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });
        whitelistRow.addChildren(whitelistLabel, whitelistSwitch);

        Label hintLabel = new Label();
        hintLabel.setText("Blacklist always blocks; whitelist-only limits invites.");
        hintLabel.textStyle(style -> {
            style.textColor(COLOR_HINT);
            style.fontSize(9);
        });

        UIElement settingsContent = tabColumn();
        settingsContent.addChildren(delayRow, whitelistRow, hintLabel, feedbackLabel);
        tabView.addTab(new Tab().setText("Settings"), settingsContent);

        // --- Lists tab ---
        TextField nameField = new TextField().setAnyString();
        nameField.textFieldStyle(style -> style.placeholder(Component.literal("Player name")));
        nameField.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });

        Button addButton = new Button();
        addButton.setText("Add");
        addButton.setOnClick(event -> {
            String raw = nameField.getValue() == null ? "" : nameField.getValue().trim();
            if (raw.isEmpty()) return;
            if (GinvDataStore.touch(raw)) {
                nameField.setText("");
            } else {
                nameField.setText(raw);
            }
        });

        UIElement addRow = new UIElement();
        addRow.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(3);
            layout.widthPercent(100);
            layout.height(14);
        });
        addRow.addChildren(nameField, addButton);

        ScrollerView listsScroll = new ScrollerView();
        listsScroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        fillListsScroll(listsScroll);

        UIElement listsContent = tabColumn();
        listsContent.addChildren(addRow, listsScroll);
        tabView.addTab(new Tab().setText("Lists"), listsContent);

        // --- Monitor tab ---
        Label statusLabel = new Label();
        statusLabel.setText(statusText());
        statusLabel.textStyle(style -> {
            style.textColor(COLOR_MUTED);
            style.fontSize(9);
        });

        ScrollerView monitorScroll = new ScrollerView();
        monitorScroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });
        fillMonitorScroll(monitorScroll);

        UIElement monitorContent = tabColumn();
        monitorContent.addChildren(statusLabel, monitorScroll);
        tabView.addTab(new Tab().setText("Monitor"), monitorContent);

        return new Layout(root, panel, statusLabel, feedbackLabel,
                minDelayField, maxDelayField, whitelistSwitch, listsScroll, monitorScroll);
    }

    private static UIElement tabColumn() {
        UIElement column = new UIElement();
        column.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapRow(4);
        });
        return column;
    }

    private static Label dashLabel() {
        Label dash = new Label();
        dash.setText("-");
        dash.textStyle(style -> style.textColor(COLOR_MUTED));
        return dash;
    }

    // ------------------------------------------------------- player rows

    private static UIElement buildPlayerRow(String name, GinvDataStore.PlayerSnapshot snapshot,
                                            boolean withCount) {
        GinvDataStore.ListState state = snapshot == null
                ? GinvDataStore.ListState.NONE
                : snapshot.listState();

        UIElement row = new UIElement();
        row.setId("ginv_row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(3);
            layout.widthPercent(100);
            layout.height(13);
        });

        UIElement head = new UIElement();
        head.layout(layout -> {
            layout.width(10);
            layout.height(10);
        });
        head.getStyle().backgroundTexture(new PlayerHeadTexture(name));

        Label nameLabel = new Label();
        nameLabel.setText(name);
        nameLabel.textStyle(style -> style.textColor(COLOR_TEXT));
        nameLabel.layout(layout -> {
            layout.flexGrow(1);
            layout.minWidth(0);
        });

        row.addChildren(head, nameLabel);

        if (withCount && snapshot != null) {
            String count = "×" + snapshot.invites();
            if (snapshot.lastInviteMs() > 0) {
                count += " · " + ago(snapshot.lastInviteMs());
            }
            Label countLabel = new Label();
            countLabel.setText(count);
            countLabel.textStyle(style -> style.textColor(COLOR_MUTED));
            row.addChildren(countLabel);
        }

        row.addChildren(
                listButton("W", state == GinvDataStore.ListState.WHITE, COLOR_ACTIVE_WHITE,
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
                listButton("B", state == GinvDataStore.ListState.BLACK, COLOR_ACTIVE_BLACK,
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

    private static Button listButton(String text, boolean active, int activeColor,
                                     String tooltip, Runnable action) {
        Button button = new Button();
        button.setText(text);
        button.text.textStyle(style -> style.textColor(active ? activeColor : COLOR_BUTTON_IDLE));
        button.layout(layout -> layout.width(14));
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
        label.textStyle(style -> style.textColor(COLOR_HINT));
        return label;
    }

    // --------------------------------------------------------- rebuilding

    private static List<String> collectNames() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(GinvDataStore.trackedNames());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name != null && !name.startsWith("!")) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static void fillListsScroll(ScrollerView scroll) {
        List<String> names = collectNames();
        scroll.clearAllScrollViewChildren();
        if (names.isEmpty()) {
            scroll.addScrollViewChildren(emptyRow("No players."));
            return;
        }
        for (String name : names) {
            scroll.addScrollViewChildren(
                    buildPlayerRow(name, GinvDataStore.snapshot(name), false));
        }
    }

    private static void fillMonitorScroll(ScrollerView scroll) {
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
            scroll.addScrollViewChildren(
                    buildPlayerRow(snapshot.name(), snapshot, true));
        }
    }

    private static List<String> onlineSnapshot() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        List<String> names = new ArrayList<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name != null && !name.startsWith("!")) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String statusText() {
        return "Pending: " + GinvCommand.getPendingCount()
                + " · " + (GinvCommand.isFrozen() ? "FROZEN" : "RUNNING")
                + " · whitelist-only " + (GinvDataStore.whitelistOnly() ? "ON" : "OFF");
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();

        int version = GinvDataStore.version();
        List<String> online = onlineSnapshot();
        if (version != lastVersion || !online.equals(lastOnline)) {
            lastVersion = version;
            lastOnline = online;
            fillListsScroll(listsScroll);
            fillMonitorScroll(monitorScroll);
        }

        statusLabel.setText(statusText());

        boolean whitelistOnly = GinvDataStore.whitelistOnly();
        boolean switchOn = Boolean.TRUE.equals(whitelistSwitch.getValue());
        if (whitelistOnly != switchOn) {
            whitelistSwitch.setOn(whitelistOnly, false);
        }
    }
}
