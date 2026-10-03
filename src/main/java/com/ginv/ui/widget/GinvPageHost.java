package com.ginv.ui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.FlexDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tabbed page area as a single widget.
 *
 * <p>A {@code GinvPageHost} owns the internal LDLib2 {@link TabView}, the bar
 * of {@link Tab}s and a stack of encapsulated {@link GinvPage} bodies. The
 * host grows to fill its parent (column, 100% both axes) so the tab bar reads
 * as part of the surrounding card. Callers pass final canvas units for the
 * per-page padding and row gap — no scaling is applied here.
 */
public class GinvPageHost extends UIElement {

    private final TabView tabView;
    private final List<Tab> tabs = new ArrayList<>();
    private final List<GinvPage> pages = new ArrayList<>();
    private final float pagePadding;
    private final float pageGap;
    private UIElement footer;

    public GinvPageHost(float pagePadding, float pageGap) {
        setId("ginv_pages");
        addClass("ginv-pages");
        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.minHeight(0);
        });

        this.tabView = new TabView();
        this.tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
            layout.flexShrink(1);
            layout.minHeight(0);
        });
        // LDLib2's TabView content slot gets flex-grow:1 but no flex-shrink:
        // its LayoutProperties.FLEX_SHRINK defaults to 0 (not CSS's 1), so the
        // slot can never shrink below its content height. A long list then
        // grows the slot — and the page with it, since the page is height:100%
        // of the slot — instead of scrolling inside the table's own
        // ScrollerView. Pin it to the available height. Structural geometry
        // stays Java-owned (D6).
        this.tabView.tabContentContainer(container -> container.layout(layout -> {
            layout.flexShrink(1);
            layout.minHeight(0);
        }));
        addChild(this.tabView);

        this.pagePadding = pagePadding;
        this.pageGap = pageGap;
    }

    /**
     * Appends a page and its tab. The slug is lowercased with
     * {@link Locale#ROOT} and drives the stable {@code ginv_tab_*} /
     * {@code ginv_pane_*} ids used by the UI tests.
     */
    public GinvPage addPage(String slug, String title, UIElement content) {
        String key = slug.toLowerCase(Locale.ROOT);
        Tab tab = new Tab().setText(title);
        tab.setId("ginv_tab_" + key);
        tab.addClass("ginv_tab");

        GinvPage page = new GinvPage(key, title);
        page.setId("ginv_pane_" + key);
        page.addClass("ginv_pane");
        page.addClass("ginv-page-body");
        page.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(pagePadding);
            layout.gapRow(pageGap);
        });
        page.addChild(content);

        tabs.add(tab);
        pages.add(page);
        tabView.addTab(tab, page);
        return page;
    }

    /**
     * Installs (or replaces) the optional footer band, appended after the
     * {@link TabView} so the page document owns the footer and it reads as the
     * bottom band of the same frame. Passing {@code null} clears it. The footer
     * is expected to carry {@code .ginv-statusbar} for {@code flex-shrink: 0}.
     */
    public void setFooter(UIElement footer) {
        if (this.footer != null) {
            removeChild(this.footer);
        }
        this.footer = footer;
        if (footer != null) {
            addChild(footer);
        }
    }

    /** Selects the page at {@code index}; out-of-range indexes are ignored. */
    public void select(int index) {
        if (index < 0 || index >= tabs.size()) return;
        tabView.selectTab(tabs.get(index));
    }

    /** The first selected tab's index, or {@code 0} when none is selected. */
    public int selectedIndex() {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).isSelected()) return i;
        }
        return 0;
    }

    public List<GinvPage> pages() {
        return pages;
    }

    public TabView tabView() {
        return tabView;
    }

    /** One encapsulated page body: a column filling its tab content slot. */
    public static final class GinvPage extends UIElement {
        private final String slug;
        private final String title;

        private GinvPage(String slug, String title) {
            this.slug = slug;
            this.title = title;
        }

        public String slug() {
            return slug;
        }

        public String title() {
            return title;
        }
    }
}
