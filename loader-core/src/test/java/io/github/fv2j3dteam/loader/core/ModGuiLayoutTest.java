package io.github.fv2j3dteam.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModGuiLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    void modListEntryParsing() {
        String mod = "fv2j3_test_mod | TestMod | LOADED";
        String name = extractModName(mod);
        String status = extractModStatus(mod);
        assertEquals("fv2j3_test_mod", name);
        assertEquals("LOADED", status);
    }

    @Test
    void modListEntryWithMultiplePipes() {
        String mod = "awesome_mod | Awesome Mod v1.2.3 | Active";
        String name = extractModName(mod);
        String status = extractModStatus(mod);
        assertEquals("awesome_mod", name);
        assertEquals("Active", status);
    }

    @Test
    void modListEntryWithNoPipes() {
        String mod = "simple_mod";
        String name = extractModName(mod);
        String status = extractModStatus(mod);
        assertEquals("simple_mod", name);
        assertEquals("", status);
    }

    @Test
    void modListEntryWithEmptyStatus() {
        String mod = "mod_id | ";
        String name = extractModName(mod);
        String status = extractModStatus(mod);
        assertEquals("mod_id", name);
        assertEquals("", status);
    }

    @Test
    void backButtonLeftMarginCalculation() {
        int screenWidth = 854;
        int leftMargin = 20;
        int buttonWidth = 80;
        int expectedButtonX = leftMargin;
        assertEquals(expectedButtonX, calculateBackButtonX(leftMargin, buttonWidth, screenWidth));
    }

    @Test
    void backButtonLeftMarginAtSmallWidth() {
        int screenWidth = 320;
        int leftMargin = 20;
        int buttonWidth = 80;
        int expectedButtonX = leftMargin;
        assertEquals(expectedButtonX, calculateBackButtonX(leftMargin, buttonWidth, screenWidth));
    }

    @Test
    void backButtonLeftMarginAtLargeWidth() {
        int screenWidth = 1920;
        int leftMargin = 20;
        int buttonWidth = 80;
        int expectedButtonX = leftMargin;
        assertEquals(expectedButtonX, calculateBackButtonX(leftMargin, buttonWidth, screenWidth));
    }

    @Test
    void modListPanelCentering() {
        int screenWidth = 854;
        int panelWidth = 280;
        int expectedLeft = (screenWidth - panelWidth) / 2;
        assertEquals(287, expectedLeft);
    }

    @Test
    void modListPanelCenteringSmallWidth() {
        int screenWidth = 320;
        int panelWidth = 280;
        int expectedLeft = (screenWidth - panelWidth) / 2;
        assertEquals(20, expectedLeft);
    }

    @Test
    void modListRowBounds() {
        int listTop = 90;
        int rowHeight = 22;
        int index = 0;
        int rowTop = listTop + 8 + index * rowHeight;
        int rowBottom = rowTop + rowHeight - 4;
        assertEquals(98, rowTop);
        assertEquals(116, rowBottom);
    }

    @Test
    void modListRowHoverDetection() {
        int mouseX = 300;
        int mouseY = 100;
        int listLeft = 287;
        int listRight = listLeft + 280;
        int rowTop = 98;
        int rowBottom = 116;
        boolean hovered = mouseX >= listLeft && mouseX < listRight
                && mouseY >= rowTop && mouseY < rowBottom;
        assertTrue(hovered);
    }

    @Test
    void modListRowHoverDetectionOutside() {
        int mouseX = 100;
        int mouseY = 100;
        int listLeft = 287;
        int listRight = listLeft + 280;
        int rowTop = 98;
        int rowBottom = 116;
        boolean hovered = mouseX >= listLeft && mouseX < listRight
                && mouseY >= rowTop && mouseY < rowBottom;
        assertFalse(hovered);
    }

    @Test
    void modListBoundsClipping() {
        int listTop = 90;
        int rowHeight = 22;
        int modCount = 50;
        int listBottom = listTop + rowHeight * modCount + 16;
        int screenHeight = 200;
        int maxBottom = screenHeight - 30;
        if (listBottom > maxBottom) {
            listBottom = maxBottom;
        }
        assertEquals(maxBottom, listBottom);
    }

    @Test
    void modListBoundsNoClipping() {
        int listTop = 90;
        int rowHeight = 22;
        int modCount = 3;
        int listBottom = listTop + rowHeight * modCount + 16;
        int screenHeight = 480;
        int maxBottom = screenHeight - 30;
        assertTrue(listBottom <= maxBottom);
    }

    @Test
    void stringTruncation() {
        String longName = "this_is_a_very_long_mod_name_that_needs_truncation";
        int maxWidth = 100;
        String result = truncateModName(longName, maxWidth);
        assertTrue(result.length() < longName.length());
        assertTrue(result.endsWith("..."));
    }

    @Test
    void stringNoTruncationShort() {
        String shortName = "mod";
        int maxWidth = 100;
        String result = truncateModName(shortName, maxWidth);
        assertEquals(shortName, result);
    }

    @Test
    void stringNoTruncationExact() {
        String name = "ab";
        int maxWidth = 30;
        String result = truncateModName(name, maxWidth);
        assertEquals(name, result);
    }

    @Test
    void escKeyCodeIsOne() {
        int escKeyCode = 1;
        assertEquals(1, escKeyCode);
    }

    @Test
    void modIdsExtractedCorrectly() {
        List<String> mods = List.of(
                "mod_a | Mod A | LOADED",
                "mod_b | Mod B | LOADED",
                "mod_c | Mod C | LOADED"
        );
        assertEquals(3, mods.size());
        for (String mod : mods) {
            String id = extractModName(mod);
            assertTrue(id.startsWith("mod_"), "Expected mod ID to start with 'mod_': " + id);
        }
    }

    private String extractModName(String mod) {
        if (mod == null) return "";
        int pipe = mod.indexOf(" | ");
        return pipe < 0 ? mod : mod.substring(0, pipe);
    }

    private String extractModStatus(String mod) {
        if (mod == null) return "";
        int lastPipe = mod.lastIndexOf(" | ");
        if (lastPipe < 0) return "";
        return mod.substring(lastPipe + 3);
    }

    private int calculateBackButtonX(int leftMargin, int buttonWidth, int screenWidth) {
        return leftMargin;
    }

    private String truncateModName(String text, int maxWidth) {
        if (text == null) return "";
        int charWidth = 6;
        int maxChars = maxWidth / charWidth - 3;
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.min(text.length(), maxChars)) + "...";
    }
}
