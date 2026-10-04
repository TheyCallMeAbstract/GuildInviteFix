package com.ginv.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression guard over the build-generated theme sheets: the D6 unit-free
 * invariant and the 8-digit alpha-first hex rule sweep every {@code office-*}
 * sheet (as the directory globs grow, all six palettes are covered), each theme
 * additionally has a byte-for-byte golden, and the light palettes carry
 * light-ink spot checks while the dark palettes carry the converse.
 * {@code generateThemeSheets} runs as a dependency of {@code test}, so these
 * files always reflect the current template.
 */
class ThemeSheetInvariantsTest {

    private static final Path GENERATED_DIR =
            Path.of("build", "generated", "themeSheets", "assets", "guildinvitefix", "lss");

    private static final Pattern UNIT_BEARING = Pattern.compile(
            "^\\s*(width|height|max-width|max-height|padding|padding-.*|gap|gap-.*|font-size):");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]+");

    /** Dark palettes and the body ink each must resolve to. */
    private static final Map<String, String> DARK_INK = Map.of(
            "carbon", "#FFEDEDED",
            "mint", "#FFE4ECE8",
            "plum", "#FFECE7F2",
            "bee", "#FFF5F0DF");
    /** Light palettes and the body ink each must resolve to. */
    private static final Map<String, String> LIGHT_INK = Map.of(
            "paper", "#FF1B1E24",
            "latte", "#FF2B2622");
    /** The light-authored ink / hairline literals no dark palette may leak. */
    private static final List<String> LIGHT_LITERALS =
            List.of("#FF1B1E24", "#FF2B2622", "#1F000000", "#33000000");
    /** The unselected-tab-ink reclaim rule every palette must own (base weight 1002). */
    private static final String RECLAIM_SELECTOR =
            "#ginv_root .ginv_tab:not(.__selected__) .__tab_text__ {";

    private static List<Path> generatedSheets() throws IOException {
        try (Stream<Path> stream = Files.list(GENERATED_DIR)) {
            List<Path> sheets = stream
                    .filter(p -> p.getFileName().toString().matches("office-.*\\.lss"))
                    .sorted()
                    .toList();
            assertFalse(sheets.isEmpty(),
                    "no generated office-*.lss under " + GENERATED_DIR.toAbsolutePath());
            return sheets;
        }
    }

    @Test
    void everyGeneratedSheetIsUnitFree() throws IOException {
        for (Path sheet : generatedSheets()) {
            List<String> lines = Files.readAllLines(sheet, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (UNIT_BEARING.matcher(lines.get(i)).find()) {
                    fail(sheet.getFileName() + ":" + (i + 1)
                            + " is unit-bearing (D6): " + lines.get(i));
                }
            }
        }
    }

    @Test
    void everyGeneratedColorIsEightDigitAlphaFirst() throws IOException {
        for (Path sheet : generatedSheets()) {
            List<String> lines = Files.readAllLines(sheet, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = HEX.matcher(lines.get(i));
                while (matcher.find()) {
                    String hex = matcher.group();
                    assertTrue(hex.matches("#[0-9A-Fa-f]{8}"),
                            sheet.getFileName() + ":" + (i + 1) + " malformed color " + hex);
                }
            }
        }
    }

    @Test
    void paperSheetShipsAndIsLightInvariant() throws IOException {
        assertLightInvariant("paper");
    }

    @Test
    void latteSheetShipsAndIsLightInvariant() throws IOException {
        assertLightInvariant("latte");
    }

    @Test
    void darkSheetsShipAndAreDarkInvariant() throws IOException {
        for (Map.Entry<String, String> entry : DARK_INK.entrySet()) {
            String id = entry.getKey();
            String sheet = readGenerated("office-" + id + ".lss");
            // The base :not()-owned unselected-tab state must be reclaimed by our
            // higher-specificity, palette-token rule for every palette.
            assertTrue(sheet.contains(RECLAIM_SELECTOR),
                    id + " is missing the unselected-tab-ink reclaim selector");
            // Dark palette: body ink resolved to that theme's own dark token
            // (dusk is itself dark, but each sheet must carry its own value).
            assertTrue(sheet.contains("text-color: " + entry.getValue() + ";"),
                    id + " body ink did not resolve to its dark text token");
            // No light-authored ink or black hairline may leak into a live
            // declaration. The template's header docs intentionally keep dusk
            // reference values, so only declarations are checked here.
            for (String line : sheet.split("\n")) {
                if (line.stripLeading().startsWith("//")) continue;
                for (String light : LIGHT_LITERALS) {
                    assertFalse(line.contains(light),
                            id + " leaked light literal " + light + " into: " + line);
                }
            }
        }
    }

    private void assertLightInvariant(String id) throws IOException {
        String fileName = "office-" + id + ".lss";
        Path sheetPath = GENERATED_DIR.resolve(fileName);
        assertTrue(Files.exists(sheetPath), "missing " + sheetPath.toAbsolutePath()
                + " — the " + id + " picker entry requires a generated delta");
        String sheet = Files.readString(sheetPath, StandardCharsets.UTF_8);
        // The base :not()-owned unselected-tab state must be reclaimed by our
        // higher-specificity, palette-token rule (not left to the light base).
        assertTrue(sheet.contains(RECLAIM_SELECTOR),
                id + " is missing the unselected-tab-ink reclaim selector");
        // Light palette: dark body ink and black hairlines must have resolved,
        // and no dusk ink may leak into a live (non-comment) declaration. The
        // template's header docs intentionally keep the dusk reference values,
        // so only declarations are checked here.
        assertTrue(sheet.contains("text-color: " + LIGHT_INK.get(id) + ";"),
                id + " body ink did not resolve to the light text token");
        assertTrue(sheet.contains("1, #1F000000)"),
                id + " hairlines did not resolve to the light line token");
        for (String line : sheet.split("\n")) {
            if (line.stripLeading().startsWith("//")) continue;
            assertFalse(line.contains("#FFE7EAF2"),
                    id + " leaked the dusk text token (#FFE7EAF2) into: " + line);
        }
    }

    private static String readGenerated(String fileName) throws IOException {
        Path sheet = GENERATED_DIR.resolve(fileName);
        assertTrue(Files.exists(sheet), "missing " + sheet.toAbsolutePath());
        return Files.readString(sheet, StandardCharsets.UTF_8);
    }

    @Test
    void duskMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-dusk.lss");
    }

    @Test
    void carbonMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-carbon.lss");
    }

    @Test
    void mintMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-mint.lss");
    }

    @Test
    void plumMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-plum.lss");
    }

    @Test
    void paperMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-paper.lss");
    }

    @Test
    void latteMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-latte.lss");
    }

    @Test
    void beeMatchesTheCommittedGolden() throws IOException {
        assertMatchesGolden("office-bee.lss");
    }

    private void assertMatchesGolden(String fileName) throws IOException {
        Path generated = GENERATED_DIR.resolve(fileName);
        assertTrue(Files.exists(generated), "missing " + generated.toAbsolutePath());
        byte[] actual = Files.readAllBytes(generated);
        byte[] golden;
        try (InputStream in = getClass().getResourceAsStream("/theme-golden/" + fileName)) {
            assertTrue(in != null, "missing test resource /theme-golden/" + fileName);
            golden = in.readAllBytes();
        }
        assertArrayEquals(golden, actual, fileName + " drifted from the committed golden");
    }
}
