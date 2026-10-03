package com.ginv.ui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;

/**
 * Two-column settings grid: a label column and a control column per row.
 *
 * <p>Every row is a flex row with the same min height and gap; the label
 * column takes a 4/10 share and the control column 6/10, both with
 * {@code flexBasis 0} / {@code minWidth 0}, so the split tracks the available
 * width instead of pinning a fixed size. The control column right-aligns its
 * widget, so a stretching control fills the column while a fixed one (e.g. a
 * switch) sits at its right edge.
 *
 * <p>Full-width children inserted between rows — section headers or notes —
 * span the form via {@link #addFullWidth(UIElement)} and stay out of the
 * label/control split.
 *
 * <p>Sizes are final canvas units — unlike {@code GinvMenuScreen}, this widget
 * never applies its own {@code u()} scaling. The {@code ginv-setting-row},
 * {@code ginv-setting-label} and {@code ginv-setting-control} classes are test
 * selectors only and carry no stylesheet rules (D6).
 */
public class GinvSettingsForm extends UIElement {

    /** Label/control column shares of the row width (4:6). */
    private static final float LABEL_SHARE = 4f;
    private static final float CONTROL_SHARE = 6f;

    private final float rowHeight;
    private final float rowGap;

    public GinvSettingsForm(float rowHeight, float rowGap) {
        this.rowHeight = rowHeight;
        this.rowGap = rowGap;
        layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapRow(rowGap);
        });
    }

    /**
     * Appends a label/control row. When {@code stretch} is set the control
     * grows to fill its column; otherwise it sits at the column's right edge.
     */
    public GinvSettingsForm addSetting(Label label, UIElement control, boolean stretch) {
        label.addClass("ginv-setting-label");
        label.layout(layout -> {
            layout.flexBasis(0);
            layout.flexGrow(LABEL_SHARE);
            layout.minWidth(0);
            layout.flexShrink(1);
        });

        UIElement controlColumn = new UIElement();
        controlColumn.addClass("ginv-setting-control");
        controlColumn.layout(layout -> {
            layout.flexBasis(0);
            layout.flexGrow(CONTROL_SHARE);
            layout.minWidth(0);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_END);
            layout.gapColumn(rowGap);
        });
        controlColumn.addChild(control);
        if (stretch) {
            control.layout(layout -> {
                layout.flexGrow(1);
                layout.minWidth(0);
            });
        }

        UIElement row = new UIElement();
        row.addClass("ginv-row");
        row.addClass("ginv-setting-row");
        row.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapColumn(rowGap);
            layout.minHeight(rowHeight);
        });
        row.addChildren(label, controlColumn);

        addChild(row);
        return this;
    }

    /** Appends a full-width child (e.g. a section header or a note). */
    public GinvSettingsForm addFullWidth(UIElement child) {
        child.layout(layout -> layout.widthPercent(100));
        addChild(child);
        return this;
    }

    /** Appends a full-width note (e.g. the hint caption). */
    public GinvSettingsForm addNote(Label note) {
        return addFullWidth(note);
    }
}
