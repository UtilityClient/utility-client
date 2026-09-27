package dev.utilityclient.gui;

import dev.utilityclient.config.ConfigManager;
import dev.utilityclient.license.LicenseManager;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.util.SystemActions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main menu.
 *
 * <p>Layout: a fixed sidebar with the brand, the module categories, the general pages and a
 * profile card pinned to the bottom, next to a scrolling two column grid of module cards.
 * Left click toggles, right click opens that module's settings.
 */
public final class ClickGuiScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String WEBSITE = "https://utilityclient.pages.dev";

    private static final int SCRIM = 0xE8070810;
    private static final int WINDOW = 0xFF0B0B11;
    private static final int SIDEBAR = 0xFF12121A;
    private static final int CARD_OFF = 0xFF141419;
    private static final int CARD_ON = 0xFF1C1526;
    private static final int CARD_HOVER = 0xFF1B1B24;
    private static final int DIVIDER = 0xFF23232E;
    private static final int BORDER = 0xFF23232E;
    private static final int BORDER_SOFT = 0xFF1E1E28;
    private static final int TEXT = 0xFFF7F3FF;
    private static final int MUTED = 0xFF9A96A8;
    private static final int DIM = 0xFF6B6779;
    private static final int FAINT = 0xFF4A4756;
    private static final int GREEN = 0xFF42E88A;
    private static final int RED = 0xFFFF5C72;
    private static final int TRACK = 0xFF2A2A35;
    private static final int SEARCH_BG = 0xFF141419;
    private static final int KEYCAP = 0xFF1E1E28;

    private ModuleCategory selectedCategory = ModuleCategory.VISUAL;
    private String searchQuery = "";
    private boolean searchFocused;
    private int scroll;
    private Layout layout = new Layout();

    public ClickGuiScreen() {
        super(Component.literal("Utility Client"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        layout = buildLayout();
        int accent = Theme.accent();

        graphics.fill(0, 0, width, height, SCRIM);
        drawWindow(graphics, accent);
        drawSidebar(graphics, mouseX, mouseY, accent);
        drawTopBar(graphics, mouseX, mouseY, accent);
        drawGrid(graphics, mouseX, mouseY, accent);
        drawScrollbar(graphics, accent);
        drawProfile(graphics, mouseX, mouseY, accent);
    }

    /* ------------------------------------------------------------------ window */

    private void drawWindow(GuiGraphicsExtractor graphics, int accent) {
        roundPanel(graphics, layout.windowX, layout.windowY, layout.windowWidth, layout.windowHeight,
                16, WINDOW, BORDER);
        roundRect(graphics, layout.windowX + 1, layout.windowY + 1, layout.sidebarWidth - 2,
                layout.windowHeight - 2, 15, SIDEBAR);
        // Divider between the sidebar and the content area.
        graphics.fill(layout.windowX + layout.sidebarWidth - 1, layout.windowY + 18,
                layout.windowX + layout.sidebarWidth, layout.windowY + layout.windowHeight - 18, DIVIDER);
        // Purple wash bleeding out of the top bar, as in the reference.
        for (int i = 0; i < 26; i++) {
            int alpha = Math.max(0, 8 - i / 3);
            int c = (alpha << 24) | (accent & 0x00FFFFFF);
            graphics.fill(layout.contentX, layout.topBarBottom + i,
                    layout.windowX + layout.windowWidth - 1, layout.topBarBottom + i + 1, c);
        }
    }

    /* ------------------------------------------------------------------ sidebar */

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent) {
        int x = layout.windowX + 18;
        int y = layout.windowY + 20;
        int rowWidth = layout.sidebarWidth - 36;

        // Brand
        roundRect(graphics, x, y, 36, 36, 11, accent);
        graphics.text(font, "U", x + 12, y + 13, 0xFF120018, false);
        graphics.text(font, "UTILITY", x + 46, y + 6, TEXT, false);
        graphics.text(font, "CLIENT", x + 46, y + 19, accent, false);
        graphics.text(font, "v0.1.0", x + 46, y + 33, DIM, false);

        y += 58;
        graphics.fill(x, y, x + rowWidth, y + 1, BORDER_SOFT);
        y += 18;

        graphics.text(font, "MODULES", x, y, FAINT, false);
        y += 18;
        for (CategoryRow row : layout.moduleRows) {
            drawNavItem(graphics, x, y, rowWidth, row.icon, row.label, row.category == selectedCategory,
                    count(row.category), mouseX, mouseY, accent);
            y += 32;
        }

        y += 10;
        graphics.text(font, "GENERAL", x, y, FAINT, false);
        y += 18;
        for (GeneralRow row : layout.generalRows) {
            drawNavItem(graphics, x, y, rowWidth, row.icon, row.label, false, 0, mouseX, mouseY, accent);
            y += 32;
        }
    }

    private void drawNavItem(GuiGraphicsExtractor graphics, int x, int y, int rowWidth, Icon icon,
                             String label, boolean selected, int count, int mouseX, int mouseY, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, 28);
        if (selected) {
            roundRect(graphics, x, y, rowWidth, 28, 7, Theme.accentSoft());
            graphics.fill(x, y + 5, x + 3, y + 23, accent);
        } else if (hovered) {
            roundRect(graphics, x, y, rowWidth, 28, 7, 0xFF1A1A22);
        }
        int iconColor = selected ? accent : (hovered ? TEXT : MUTED);
        drawIcon(graphics, icon, x + 17, y + 14, iconColor);
        graphics.text(font, label, x + 32, y + 10, selected ? accent : (hovered ? TEXT : MUTED), false);
        if (count > 0) {
            String value = String.valueOf(count);
            graphics.text(font, value, x + rowWidth - font.width(value) - 10, y + 10,
                    selected ? accent : FAINT, false);
        }
    }

    /* ------------------------------------------------------------------ top bar */

    private void drawTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent) {
        int x = layout.contentX;
        int y = layout.windowY;

        graphics.text(font, "Hello, " + playerName(), x + 22, y + 17, TEXT, false);

        String time = LocalTime.now().format(TIME_FORMAT);
        graphics.text(font, time,
                x + (layout.contentWidth - font.width(time)) / 2, y + 17, accent, false);

        // Accent rule under the top bar.
        graphics.fill(x + 1, layout.topBarBottom - 2, x + layout.contentWidth - 1, layout.topBarBottom - 1, accent);
        graphics.fill(x + 1, layout.topBarBottom - 1, x + layout.contentWidth - 1, layout.topBarBottom,
                Theme.accentDim());

        int searchX = x + layout.contentWidth - 214;
        int searchY = y + 10;
        int searchWidth = 196;
        boolean hovered = inside(mouseX, mouseY, searchX, searchY, searchWidth, 28);
        roundPanel(graphics, searchX, searchY, searchWidth, 28, 9, SEARCH_BG,
                searchFocused ? accent : (hovered ? Theme.accentDim() : BORDER));
        drawSearchIcon(graphics, searchX + 14, searchY + 14, searchFocused ? accent : MUTED);
        graphics.text(font, searchQuery.isEmpty() ? "Search modules..." : searchQuery,
                searchX + 30, searchY + 10, searchQuery.isEmpty() ? DIM : TEXT, false);
        // Keycap
        int capWidth = 26;
        int capX = searchX + searchWidth - capWidth - 6;
        roundRect(graphics, capX, searchY + 6, capWidth, 16, 4, KEYCAP);
        graphics.text(font, "K", capX + 4, searchY + 10, MUTED, false);
        graphics.text(font, "^", capX + 13, searchY + 10, FAINT, false);
    }

    private void drawSearchIcon(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
        strokeCircle(graphics, cx - 1, cy - 1, 4, 1, color);
        line(graphics, cx + 2, cy + 2, cx + 5, cy + 5, color, 1);
    }

    /* ------------------------------------------------------------------ grid */

    private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent) {
        int top = layout.gridTop;
        int bottom = layout.windowY + layout.windowHeight - 12;
        graphics.enableScissor(layout.contentX + 1, top, layout.windowX + layout.windowWidth - 11, bottom);

        if (layout.cards.isEmpty()) {
            String title = searchQuery.isEmpty()
                    ? "Nothing in " + selectedCategory.displayName()
                    : "No modules match \"" + searchQuery + "\"";
            graphics.text(font, title,
                    layout.contentX + (layout.contentWidth - font.width(title)) / 2,
                    top + 46, MUTED, false);
            String hint = searchQuery.isEmpty()
                    ? "This category has no modules. Pick another one on the left."
                    : "Try a shorter search, or clear the box with Backspace.";
            graphics.text(font, hint,
                    layout.contentX + (layout.contentWidth - font.width(hint)) / 2,
                    top + 62, FAINT, false);
        }

        for (CardLayout card : layout.cards) {
            drawCard(graphics, mouseX, mouseY, card, accent);
        }
        graphics.disableScissor();
    }

    private void drawCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CardLayout card, int accent) {
        boolean hovered = inside(mouseX, mouseY, card.x, card.y, card.width, card.height);
        boolean on = card.module.enabled();

        // Outer bloom on enabled cards.
        if (on) {
            roundRect(graphics, card.x - 2, card.y - 2, card.width + 4, card.height + 4, 12, 0x22D000FF);
        }
        int fill = on ? CARD_ON : (hovered ? CARD_HOVER : CARD_OFF);
        int border = on ? accent : (hovered ? BORDER : BORDER_SOFT);
        roundPanel(graphics, card.x, card.y, card.width, card.height, 10, fill, border);

        graphics.text(font, card.module.displayName(), card.x + 13, card.y + 12, TEXT, false);

        int nameEnd = card.x + 13 + font.width(card.module.displayName());
        if (card.module.hasInfo()) {
            drawInfoIcon(graphics, nameEnd + 9, card.y + 17, on ? accent : MUTED);
            nameEnd += 18;
        }
        if (card.module.flagged()) {
            drawDiamond(graphics, nameEnd + 9, card.y + 17, 4, RED);
        }

        // Keybind line
        graphics.text(font, "KeyBind:", card.x + 13, card.y + 33, DIM, false);
        int capX = card.x + 13 + font.width("KeyBind: ") + 3;
        String key = card.module.keyBind().displayName();
        int capWidth = Math.max(14, font.width(key) + 8);
        roundRect(graphics, capX, card.y + 30, capWidth, 14, 4,
                card.module.keyBind().bound() ? KEYCAP : 0xFF17171D);
        graphics.text(font, key, capX + 4, card.y + 33,
                card.module.keyBind().bound() ? MUTED : FAINT, false);

        drawToggle(graphics, card.x + card.width - 52, card.y + 15, on, accent);
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, boolean on, int accent) {
        roundRect(graphics, x, y, 40, 19, 9, on ? accent : TRACK);
        int knob = on ? x + 23 : x + 3;
        if (on) {
            roundRect(graphics, knob - 2, y - 2, 20, 23, 11, 0x33000000);
        }
        fillCircle(graphics, knob + 6, y + 9, 7, on ? 0xFFFFFFFF : 0xFF6E6A7C);
    }

    private void drawInfoIcon(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
        strokeCircle(graphics, cx, cy, 4, 1, color);
        graphics.fill(cx, cy - 3, cx + 1, cy - 1, color);
        graphics.fill(cx, cy + 0, cx + 1, cy + 3, color);
    }

    private void drawDiamond(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            graphics.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int accent) {
        if (layout.maxScroll <= 0) {
            return;
        }
        int x = layout.windowX + layout.windowWidth - 7;
        int top = layout.gridTop + 2;
        int bottom = layout.windowY + layout.windowHeight - 16;
        int available = bottom - top;
        if (available <= 8) {
            return;
        }
        int thumb = Math.max(22, available * available / (available + layout.maxScroll));
        int thumbY = top + (available - thumb) * scroll / Math.max(1, layout.maxScroll);
        roundRect(graphics, x, thumbY, 4, thumb, 2, 0xFF3A3A48);
        if (thumbY + thumb < bottom) {
            roundRect(graphics, x, thumbY + thumb + 6, 4, 2, 1, Theme.accentDim());
        }
    }

    /* ------------------------------------------------------------------ profile */

    private void drawProfile(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent) {
        int x = layout.windowX + 18;
        int y = layout.windowY + layout.windowHeight - 60;
        int w = layout.sidebarWidth - 36;
        boolean hovered = inside(mouseX, mouseY, x, y, w, 44);
        roundPanel(graphics, x, y, w, 44, 10, hovered ? 0xFF1E1E28 : 0xFF18181F, BORDER);

        LicenseManager.Status status = LicenseManager.status();
        String initial = playerName().isEmpty() ? "?" : playerName().substring(0, 1).toUpperCase(Locale.ROOT);
        roundRect(graphics, x + 10, y + 9, 26, 26, 8, status.valid() ? 0xFF2B7A4B : 0xFF3A3A48);
        graphics.text(font, initial, x + 10 + (26 - font.width(initial)) / 2, y + 18, TEXT, false);

        graphics.text(font, playerName(), x + 44, y + 10, TEXT, false);
        graphics.text(font, status.tier().displayName(), x + 44, y + 24,
                status.valid() ? MUTED : (status.tier() == LicenseManager.Tier.NONE ? RED : RED), false);

        int dotX = x + w - 20;
        int dotY = y + 22;
        int dotColor = status.valid() ? GREEN : (status.checking() ? accent : RED);
        fillCircle(graphics, dotX, dotY, 6, (dotColor & 0x00FFFFFF) | 0x33000000);
        fillCircle(graphics, dotX, dotY, 3, dotColor);
    }

    private String playerName() {
        try {
            return minecraft.getUser().getName();
        } catch (RuntimeException exception) {
            return "Player";
        }
    }

    /* ------------------------------------------------------------------ icons */

    private enum Icon {
        COMBAT, MOVEMENT, DONUT, VISUAL, MISC, SETTINGS, CONFIGS, THEME, SOCIALS
    }

    private void drawIcon(GuiGraphicsExtractor graphics, Icon icon, int cx, int cy, int color) {
        switch (icon) {
            case COMBAT -> {
                // Two blades crossing.
                for (int i = -5; i <= 5; i++) {
                    graphics.fill(cx + i - 1, cy + i, cx + i, cy + i + 1, color);
                    graphics.fill(cx - i, cy + i - 1, cx - i + 1, cy + i, color);
                }
            }
            case MOVEMENT -> {
                fillCircle(graphics, cx, cy - 4, 2, color);
                graphics.fill(cx, cy - 2, cx + 1, cy + 2, color);
                line(graphics, cx, cy + 1, cx - 4, cy + 5, color, 1);
                line(graphics, cx, cy + 1, cx + 4, cy + 5, color, 1);
                line(graphics, cx, cy - 1, cx + 4, cy - 3, color, 1);
            }
            case DONUT -> {
                strokeCircle(graphics, cx, cy, 5, 2, color);
                strokeCircle(graphics, cx, cy, 2, 1, color);
            }
            case VISUAL -> {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dx = -6; dx <= 6; dx++) {
                        double v = (dx * dx) / 36.0 + (dy * dy) / 16.0;
                        if (v <= 1.0 && v >= 0.35) {
                            graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                        }
                    }
                }
                fillCircle(graphics, cx, cy, 2, color);
            }
            case MISC -> {
                line(graphics, cx - 4, cy + 4, cx + 3, cy - 3, color, 2);
                strokeCircle(graphics, cx + 3, cy - 4, 2, 1, color);
            }
            case SETTINGS -> {
                strokeCircle(graphics, cx, cy, 4, 1, color);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3.0;
                    line(graphics, cx, cy,
                            cx + (int) Math.round(Math.cos(a) * 6),
                            cy + (int) Math.round(Math.sin(a) * 6), color, 1);
                }
            }
            case CONFIGS -> {
                roundRect(graphics, cx - 4, cy - 6, 9, 12, 2, 0x00000000);
                for (int i = 0; i < 4; i++) {
                    graphics.fill(cx - 3, cy - 4 + i * 3, cx - 3 + (i == 3 ? 3 : 6), cy - 3 + i * 3, color);
                }
            }
            case THEME -> {
                strokeCircle(graphics, cx, cy, 5, 1, color);
                for (int dy = -5; dy <= 5; dy++) {
                    int half = (int) Math.round(Math.sqrt(Math.max(0, 25 - dy * dy)));
                    graphics.fill(cx - half, cy + dy, cx, cy + dy + 1, color);
                }
            }
            case SOCIALS -> {
                fillCircle(graphics, cx - 3, cy - 3, 2, color);
                fillCircle(graphics, cx + 3, cy - 3, 2, color);
                for (int dy = 0; dy <= 4; dy++) {
                    int half = (int) Math.round(Math.sqrt(Math.max(0, 9 - dy * dy)));
                    graphics.fill(cx - 6 + half, cy - 1 + dy, cx - 6 + 7 - half, cy + dy, color);
                    graphics.fill(cx + 6 - half, cy - 1 + dy, cx + 6 - 7 + half, cy + dy, color);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ input */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 && event.button() != 1) {
            return false;
        }
        boolean rightClick = event.button() == 1;
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int searchX = layout.contentX + layout.contentWidth - 214;
        int searchY = layout.windowY + 10;
        if (inside(mouseX, mouseY, searchX, searchY, 196, 28)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        for (CategoryRow row : layout.moduleRows) {
            if (inside(mouseX, mouseY, row.x, row.y, row.width, row.height)) {
                selectedCategory = row.category;
                scroll = 0;
                return true;
            }
        }
        for (GeneralRow row : layout.generalRows) {
            if (inside(mouseX, mouseY, row.x, row.y, row.width, row.height)) {
                openGeneral(row.page);
                return true;
            }
        }

        int profileY = layout.windowY + layout.windowHeight - 60;
        if (inside(mouseX, mouseY, layout.windowX + 18, profileY, layout.sidebarWidth - 36, 44)) {
            minecraft.gui.setScreen(new LicenseScreen());
            return true;
        }

        for (CardLayout card : layout.cards) {
            if (inside(mouseX, mouseY, card.x, card.y, card.width, card.height)
                    && mouseY >= layout.gridTop && mouseY < layout.windowY + layout.windowHeight - 12) {
                if (rightClick) {
                    minecraft.gui.setScreen(new ModuleSettingsScreen(card.module));
                } else {
                    card.module.toggle();
                    ConfigManager.save();
                }
                return true;
            }
        }
        return true;
    }

    private void openGeneral(GeneralPage page) {
        switch (page) {
            case SETTINGS -> minecraft.gui.setScreen(new ClientSettingsScreen());
            case CONFIGS -> minecraft.gui.setScreen(new ConfigScreen());
            case THEME -> {
                Theme.cycle();
                minecraft.player.sendSystemMessage(Component.literal("Accent set to " + Theme.name() + "."));
            }
            case SOCIALS -> {
                if (!SystemActions.openLink(WEBSITE) && minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal("Copied " + WEBSITE + " to the clipboard."));
                }
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused && event.isAllowedChatCharacter() && searchQuery.length() < 28) {
            searchQuery += event.codepointAsString();
            scroll = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int code = event.key();
        if (searchFocused && code == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            }
            scroll = 0;
            return true;
        }
        if (code == GLFW.GLFW_KEY_K && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchFocused = true;
            return true;
        }
        if (code == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused) {
                searchFocused = false;
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (inside((int) mouseX, (int) mouseY, layout.contentX, layout.gridTop,
                layout.contentWidth - 10, layout.windowHeight - (layout.gridTop - layout.windowY) - 12)) {
            scroll = Math.max(0, Math.min(layout.maxScroll, scroll - (int) (deltaY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /* ------------------------------------------------------------------ layout */

    private enum GeneralPage { SETTINGS, CONFIGS, THEME, SOCIALS }

    private record CategoryRow(ModuleCategory category, Icon icon, String label, int x, int y, int width, int height) {
    }

    private record GeneralRow(GeneralPage page, Icon icon, String label, int x, int y, int width, int height) {
    }

    private record CardLayout(Module module, int x, int y, int width, int height) {
    }

    private static final class Layout {
        private int windowX;
        private int windowY;
        private int windowWidth;
        private int windowHeight;
        private int sidebarWidth;
        private int contentX;
        private int contentWidth;
        private int topBarBottom;
        private int gridTop;
        private int maxScroll;
        private final List<CategoryRow> moduleRows = new ArrayList<>();
        private final List<GeneralRow> generalRows = new ArrayList<>();
        private final List<CardLayout> cards = new ArrayList<>();
    }

    private Layout buildLayout() {
        Layout result = new Layout();
        result.windowWidth = Math.min(880, Math.max(560, width - 40));
        result.windowHeight = Math.min(560, Math.max(360, height - 40));
        result.windowX = (width - result.windowWidth) / 2;
        result.windowY = (height - result.windowHeight) / 2;
        result.sidebarWidth = Math.min(232, Math.max(196, result.windowWidth / 4));
        result.contentX = result.windowX + result.sidebarWidth;
        result.contentWidth = result.windowWidth - result.sidebarWidth;
        result.topBarBottom = result.windowY + 48;
        result.gridTop = result.windowY + 60;

        int rowX = result.windowX + 18;
        int rowWidth = result.sidebarWidth - 36;
        int rowY = result.windowY + 96;

        // Order here is the order they appear in the sidebar.
        CategoryRow[] categories = {
                new CategoryRow(ModuleCategory.COMBAT, Icon.COMBAT, "Combat", 0, 0, 0, 0),
                new CategoryRow(ModuleCategory.MOVEMENT, Icon.MOVEMENT, "Movement", 0, 0, 0, 0),
                new CategoryRow(ModuleCategory.DONUTSMP, Icon.DONUT, "DonutSMP", 0, 0, 0, 0),
                new CategoryRow(ModuleCategory.VISUAL, Icon.VISUAL, "Visuals", 0, 0, 0, 0),
                new CategoryRow(ModuleCategory.MISC, Icon.MISC, "Misc", 0, 0, 0, 0),
        };
        for (int i = 0; i < categories.length; i++) {
            CategoryRow base = categories[i];
            result.moduleRows.add(new CategoryRow(base.category(), base.icon(), base.label(),
                    rowX, rowY + i * 32, rowWidth, 28));
        }

        int generalY = rowY + categories.length * 32 + 10 + 18;
        GeneralRow[] general = {
                new GeneralRow(GeneralPage.SETTINGS, Icon.SETTINGS, "Settings", 0, 0, 0, 0),
                new GeneralRow(GeneralPage.CONFIGS, Icon.CONFIGS, "Configs", 0, 0, 0, 0),
                new GeneralRow(GeneralPage.THEME, Icon.THEME, "Theme", 0, 0, 0, 0),
                new GeneralRow(GeneralPage.SOCIALS, Icon.SOCIALS, "Socials", 0, 0, 0, 0),
        };
        for (int i = 0; i < general.length; i++) {
            GeneralRow base = general[i];
            result.generalRows.add(new GeneralRow(base.page(), base.icon(), base.label(),
                    rowX, generalY + i * 32, rowWidth, 28));
        }

        int gap = 10;
        int cardWidth = (result.contentWidth - 46 - gap) / 2;
        int cardHeight = 58;
        int cardX = result.contentX + 22;
        List<Module> modules = visibleModules();
        for (int i = 0; i < modules.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int x = cardX + col * (cardWidth + gap);
            int y = result.gridTop + row * (cardHeight + gap) - scroll;
            result.cards.add(new CardLayout(modules.get(i), x, y, cardWidth, cardHeight));
        }
        int rows = (modules.size() + 1) / 2;
        int contentHeight = rows * cardHeight + Math.max(0, rows - 1) * gap;
        int visibleHeight = result.windowHeight - 72;
        result.maxScroll = Math.max(0, contentHeight - visibleHeight);
        scroll = Math.max(0, Math.min(result.maxScroll, scroll));
        return result;
    }

    private List<Module> visibleModules() {
        String query = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<Module> visible = new ArrayList<>();
        for (Module module : ModuleManager.get().modules()) {
            if (selectedCategory != null && module.category() != selectedCategory) {
                continue;
            }
            if (!query.isEmpty()
                    && !module.displayName().toLowerCase(Locale.ROOT).contains(query)
                    && !module.description().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            visible.add(module);
        }
        return visible;
    }

    private int count(ModuleCategory category) {
        int count = 0;
        for (Module module : ModuleManager.get().modules()) {
            if (module.category() == category) {
                count++;
            }
        }
        return count;
    }

    /* ------------------------------------------------------------------ primitives */

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void roundRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0 || color == 0x00000000) {
            if (w > 0 && h > 0) {
                graphics.fill(x, y, x + w, y + h, color);
            }
            return;
        }
        graphics.fill(x + r, y, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            graphics.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            graphics.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    private void roundPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            int radius, int fill, int border) {
        roundRect(graphics, x, y, w, h, radius, border);
        roundRect(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), fill);
    }

    private void fillCircle(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            if (half <= 0 && Math.abs(dy) > r) {
                continue;
            }
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private void strokeCircle(GuiGraphicsExtractor graphics, int cx, int cy, int r, int thickness, int color) {
        int outer = r;
        int inner = Math.max(0, r - thickness);
        for (int dy = -outer; dy <= outer; dy++) {
            int outerHalf = (int) Math.round(Math.sqrt(Math.max(0.0, outer * outer - dy * dy)));
            int innerHalf = Math.abs(dy) > inner ? 0
                    : (int) Math.round(Math.sqrt(Math.max(0.0, inner * inner - dy * dy)));
            if (outerHalf <= innerHalf) {
                continue;
            }
            graphics.fill(cx - outerHalf, cy + dy, cx - innerHalf, cy + dy + 1, color);
            graphics.fill(cx + innerHalf, cy + dy, cx + outerHalf + 1, cy + dy + 1, color);
        }
    }

    private void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int half = Math.max(0, thickness / 2);
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
