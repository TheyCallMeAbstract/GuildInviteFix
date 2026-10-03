package com.ginv.ui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;
import java.util.Locale;

/**
 * Reusable "Excel-style" column/row table built on LDLib2's Taffy CSS grid.
 *
 * <p>The table owns its header row, a framed scroller body with side rails and
 * row separators, so callers only supply column definitions and row cells.
 * Sizes are already in final canvas units — unlike {@code GinvMenuScreen}, this
 * widget never applies its own {@code u()} scaling. The shared
 * {@code .ginv-list-frame} / {@code .ginv-list-rail} / {@code .ginv-row-sep}
 * classes match the existing player lists in {@code GinvMenuScreen}.
 */
public class GinvTable extends UIElement {

    /**
     * A single column: a stable {@code id}, header text, a Taffy track size in
     * final canvas units, whether that track flexes, and the header alignment.
     */
    public record Column(String id, String header, float track, boolean flexible, AlignItems align) {
    }

    private final List<Column> columns;
    private final float gap;
    private final float rowHeight;
    private final String template;
    private final ScrollerView body;
    private int rowCount;

    public GinvTable(List<Column> columns, float rowHeight, float gap, float headerHeight) {
        this.columns = List.copyOf(columns);
        this.rowHeight = rowHeight;
        this.gap = gap;
        this.template = buildTemplate(this.columns);

        setId("ginv_table");
        addClass("ginv-table");
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.widthPercent(100);
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.minHeight(0);
            layout.gapRow(gap);
        });

        addChild(buildHeader(headerHeight));

        body = new ScrollerView();
        // The table is a fixed grid of columns, so a lateral scrollbar can only
        // ever be an artefact of some child exceeding the viewport by a pixel.
        // Pin the body to vertical scrolling: the wheel then always drives the
        // rows and a stray overflow can never add a horizontal bar.
        body.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO));
        addChild(buildFrame(body));
    }

    public List<Column> columns() {
        return columns;
    }

    public ScrollerView body() {
        return body;
    }

    public void clearRows() {
        body.clearAllScrollViewChildren();
        rowCount = 0;
    }

    public void addRow(String rowId, UIElement... cells) {
        if (cells.length != columns.size()) {
            throw new IllegalArgumentException(
                    "expected " + columns.size() + " cells, got " + cells.length);
        }
        if (rowCount > 0) {
            body.addScrollViewChildren(rowSeparator());
        }
        UIElement row = new UIElement();
        row.addClass("ginv-table-row");
        // Zebra: the 2nd, 4th, … data row carries a faint tint (rowCount is the
        // index of the row about to be added) so wide tables read row-by-row.
        if (rowCount % 2 == 1) {
            row.addClass("ginv-row-alt");
        }
        row.layout(layout -> {
            layout.display(TaffyDisplay.GRID);
            layout.gridTemplateColumns(template);
            layout.gapColumn(gap);
            // Gutter so the head/name and the trailing action buttons are not
            // flush against the frame rails.
            layout.paddingHorizontal(gap);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            layout.height(rowHeight);
        });
        if (rowId != null && !rowId.isBlank()) {
            row.setId(rowId);
        }
        // Tag each cell with a column-scoped class so geometry tests can assert
        // per-column alignment/symmetry across rows (see LayoutRhythmScenario).
        for (int i = 0; i < cells.length; i++) {
            cells[i].addClass("ginv-cell");
            cells[i].addClass("ginv-cell-" + columns.get(i).id());
        }
        row.addChildren(cells);
        body.addScrollViewChildren(row);
        rowCount++;
    }

    public void addEmpty(String text) {
        Label label = new Label();
        label.setText(text);
        label.addClass("ginv-empty");
        body.addScrollViewChildren(label);
    }

    private UIElement buildHeader(float headerHeight) {
        UIElement header = new UIElement();
        header.setId("ginv_table_header");
        header.addClass("ginv-table-head");
        header.layout(layout -> {
            layout.display(TaffyDisplay.GRID);
            layout.gridTemplateColumns(template);
            layout.gapColumn(gap);
            // Match the data rows' gutter so header labels sit over their columns.
            layout.paddingHorizontal(gap);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            layout.height(headerHeight);
        });
        for (Column column : columns) {
            Label label = new Label();
            label.setText(column.header().toUpperCase(Locale.ROOT));
            label.addClass("ginv-table-th");
            AlignItems align = column.align();
            label.textStyle(style -> {
                style.fontSize(headerHeight * 0.6f).adaptiveHeight(true);
                if (align == AlignItems.CENTER) {
                    style.textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER);
                } else if (align == AlignItems.FLEX_END) {
                    style.textAlignHorizontal(Horizontal.RIGHT);
                }
            });
            header.addChild(label);
        }
        return header;
    }

    private UIElement buildFrame(ScrollerView scroll) {
        scroll.addClass("ginv-table-body");
        scroll.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.minHeight(0);
        });
        // The base sheet pads every scroller view-port. That would inset the
        // rows a further step inboard of the header (which has no such padding)
        // and break the column rhythm. The row cells already carry the 3u
        // gutter, so reset the view-port to flush; the reset sits at INLINE
        // specificity and so beats the base class rule without touching the
        // shared sheet (units stay Java-owned, D6).
        scroll.viewPort(viewport -> viewport.lss("padding-all", 0));
        UIElement frame = new UIElement();
        frame.addClass("ginv-list-frame");
        frame.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.minHeight(0);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        frame.addChildren(scroll, rail(false), rail(true));
        return frame;
    }

    private static UIElement rail(boolean rightSide) {
        UIElement rail = new UIElement();
        rail.addClass("ginv-list-rail");
        rail.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(0);
            layout.bottom(0);
            layout.width(1);
            if (rightSide) {
                layout.right(0);
            } else {
                layout.left(0);
            }
        });
        return rail;
    }

    private UIElement rowSeparator() {
        UIElement separator = new UIElement();
        separator.addClass("ginv-row-sep");
        separator.layout(layout -> {
            // Inset from the side rails so the rules read as row dividers, not
            // a full-bleed grid. No explicit width: with the row container's
            // default stretch the rule fills the frame minus these margins, so
            // it can never push the scroll content past the viewport (which is
            // what used to raise a horizontal scrollbar).
            layout.marginHorizontal(gap);
            layout.height(1);
            layout.minHeight(1);
        });
        return separator;
    }

    private static String buildTemplate(List<Column> columns) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            Column column = columns.get(i);
            if (column.flexible()) {
                builder.append("minmax(0, 1fr)");
            } else {
                builder.append(column.track()).append("px");
            }
        }
        return builder.toString();
    }
}
