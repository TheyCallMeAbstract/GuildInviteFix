package com.ginv.theme;

import com.ginv.ui.theme.GinvTheme;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the {@link GinvTheme} contract from Stage 2: lower-case ids that
 * round-trip through {@code parse}, the DUSK default for unknown/blank/null
 * input, the light-palette flags, and the generated delta identifier path.
 */
class GinvThemeTest {

    @Test
    void everyIdRoundTripsThroughParse() {
        for (GinvTheme theme : GinvTheme.values()) {
            assertSame(theme, GinvTheme.parse(theme.id()));
            assertSame(theme, GinvTheme.parse(theme.id().toUpperCase(Locale.ROOT)));
            assertSame(theme, GinvTheme.parse("  " + theme.id() + "  "));
        }
    }

    @Test
    void idsAreLowerCase() {
        assertEquals("dusk", GinvTheme.DUSK.id());
        assertEquals("carbon", GinvTheme.CARBON.id());
        assertEquals("mint", GinvTheme.MINT.id());
        assertEquals("plum", GinvTheme.PLUM.id());
        assertEquals("paper", GinvTheme.PAPER.id());
        assertEquals("latte", GinvTheme.LATTE.id());
        assertEquals("bee", GinvTheme.BEE.id());
    }

    @Test
    void unknownBlankAndNullFallBackToDusk() {
        assertSame(GinvTheme.DUSK, GinvTheme.parse(null));
        assertSame(GinvTheme.DUSK, GinvTheme.parse(""));
        assertSame(GinvTheme.DUSK, GinvTheme.parse("   "));
        assertSame(GinvTheme.DUSK, GinvTheme.parse("nonsense"));
    }

    @Test
    void lightFlagsOnlyPaperAndLatte() {
        assertFalse(GinvTheme.DUSK.light());
        assertFalse(GinvTheme.CARBON.light());
        assertFalse(GinvTheme.MINT.light());
        assertFalse(GinvTheme.PLUM.light());
        assertTrue(GinvTheme.PAPER.light());
        assertTrue(GinvTheme.LATTE.light());
        assertFalse(GinvTheme.BEE.light());
    }

    @Test
    void deltaIdentifierIsTheGeneratedOfficeSheetPath() {
        assertEquals("guildinvitefix:lss/office-dusk.lss",
                GinvTheme.DUSK.deltaIdentifier().toString());
        assertEquals("guildinvitefix:lss/office-paper.lss",
                GinvTheme.PAPER.deltaIdentifier().toString());
        assertEquals("lss/office-carbon.lss", GinvTheme.CARBON.deltaIdentifier().getPath());
        assertEquals("guildinvitefix", GinvTheme.MINT.deltaIdentifier().getNamespace());
    }

    @Test
    void deltaSheetNeverThrowsWhenTheSheetIsAbsent() {
        for (GinvTheme theme : GinvTheme.values()) {
            assertNotNull(theme.deltaSheet(), theme + " deltaSheet must degrade, not throw");
        }
    }
}
