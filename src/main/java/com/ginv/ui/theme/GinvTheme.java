package com.ginv.ui.theme;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Single source of truth for the {@code /gmenu} theme: which LDLib2 base sheet
 * to render on, which subtree-local delta sheet to attach, and whether the
 * palette is light.
 *
 * <p>The delta identifier mirrors the build-time generated
 * {@code office-<id>.lss} (see the Gradle {@code generateThemeSheets} task).
 * A missing delta is never fatal: {@link #deltaSheet()} degrades to
 * {@link Stylesheet#EMPTY} and {@link #hasDelta()} reports the gap so the
 * picker can hide palettes that are not shipped yet.
 *
 * <p>{@link #id()} is the lower-case persisted value; unknown/null/blank
 * input to {@link #parse(String)} falls back to {@link #DUSK}.
 */
public enum GinvTheme {
    DUSK,
    CARBON,
    MINT,
    PLUM,
    PAPER,
    LATTE,
    BEE;

    /** Lower-case id: persisted in {@code settings.json} and used in file names. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Human label for the Settings theme picker ({@code "Dusk"}, {@code "Paper"} …). */
    public String displayName() {
        String id = id();
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** Whether the palette paints dark ink on light surfaces. */
    public boolean light() {
        return this == PAPER || this == LATTE;
    }

    /**
     * The LDLib2 base sheet for this palette. Plain, never the {@code _MERGED}
     * id: the screen's delta is attached subtree-local on top of it.
     */
    public Stylesheet baseSheet() {
        return StylesheetManager.INSTANCE.getStylesheetSafe(baseIdentifier());
    }

    /**
     * Safe lookup of the generated subtree delta for this palette. Returns
     * {@link Stylesheet#EMPTY} when the sheet is absent — callers fall back to
     * the DUSK base rather than failing.
     */
    public Stylesheet deltaSheet() {
        return StylesheetManager.INSTANCE.getStylesheetSafe(deltaIdentifier());
    }

    /** Whether the generated delta currently exists (the picker's shipping gate). */
    public boolean hasDelta() {
        return StylesheetManager.INSTANCE.hasStylesheet(deltaIdentifier());
    }

    /** {@code guildinvitefix:lss/office-<id>.lss}: the generated delta path. */
    public Identifier deltaIdentifier() {
        return Identifier.fromNamespaceAndPath("guildinvitefix", "lss/office-" + id() + ".lss");
    }

    private Identifier baseIdentifier() {
        return switch (this) {
            case DUSK -> StylesheetManager.DUSK;
            case CARBON -> StylesheetManager.CARBON;
            case MINT -> StylesheetManager.MINT;
            case PLUM -> StylesheetManager.PLUM;
            case PAPER -> StylesheetManager.PAPER;
            case LATTE -> StylesheetManager.LATTE;
            case BEE -> StylesheetManager.DUSK;
        };
    }

    /** Case-insensitive lookup; {@code null}, blank and unknown ids fall back to DUSK. */
    public static GinvTheme parse(String raw) {
        if (raw == null) return DUSK;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return DUSK;
        for (GinvTheme theme : values()) {
            if (theme.id().equals(trimmed)) return theme;
        }
        return DUSK;
    }
}
