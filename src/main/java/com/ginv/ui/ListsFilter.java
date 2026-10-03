package com.ginv.ui;

import java.util.Locale;

/**
 * The Lists tab's pure name + level filter predicate.
 *
 * <p>Holds the raw search text plus optional inclusive level bounds. The level
 * bounds are {@link Integer} so an empty or malformed field is <b>unset</b>
 * (never zero): {@link #parse} trims and treats blank/non-numeric input as
 * {@code null}. {@link #matches} applies a case-insensitive substring search and
 * excludes unknown-level players whenever any bound is set — showing a no-level
 * player under "LVL ≥ 40" would be misleading. A {@code min > max} range simply
 * matches nothing (no auto-swap).
 */
public record ListsFilter(String query, Integer minLevel, Integer maxLevel) {

    /** No query, no bounds: matches every player. */
    public static final ListsFilter EMPTY = new ListsFilter("", null, null);

    public ListsFilter {
        query = query == null ? "" : query.trim();
    }

    /** Builds a filter from raw field text; blank/non-numeric bounds become null. */
    public static ListsFilter parse(String queryText, String minText, String maxText) {
        return new ListsFilter(queryText, parseBound(minText), parseBound(maxText));
    }

    private static Integer parseBound(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True when {@code name}/{@code level} satisfy the query and any level bounds. */
    public boolean matches(String name, Integer level) {
        if (!query.isEmpty()) {
            if (name == null
                    || !name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (minLevel == null && maxLevel == null) return true;
        if (level == null) return false;
        if (minLevel != null && level < minLevel) return false;
        if (maxLevel != null && level > maxLevel) return false;
        return true;
    }

    /** False only when there is neither a query nor any level bound. */
    public boolean isActive() {
        return !query.isEmpty() || minLevel != null || maxLevel != null;
    }
}
