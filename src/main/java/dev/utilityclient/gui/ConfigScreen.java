package dev.utilityclient.gui;

import dev.utilityclient.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ConfigScreen extends Screen {
    private static final int WINDOW = 0xFF0B0B11;
    private static final int PANEL = 0xFF16161F;
    private static final int BORDER = 0xFF2B2B36;
    private static int PURPLE;
    private static final int TEXT = 0xFFF7F3FF;
    private static final int MUTED = 0xFF9A96A8;
    private static final int DIM = 0xFF615D6D;
    private static final int GREEN = 0xFF42E88A;
    private static final int RED = 0xFFFF5577;

    private String status = "Changes are saved to your Utility Client config.";
    private int statusColor = MUTED;
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;

    public ConfigScreen() {
        super(Component.literal("Config"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        PURPLE = Theme.accent();
        windowWidth = Math.min(560, Math.max(430, width - 36));
        windowHeight = Math.min(390, Math.max(320, height - 36));
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;

        graphics.fill(0, 0, width, height, 0xE8070810);
        roundPanel(graphics, windowX, windowY, windowWidth, windowHeight, 16, WINDOW, BORDER);

        drawHeader(graphics, mouseX, mouseY);
        drawActions(graphics, mouseX, mouseY);
        drawInfo(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = windowX + 22;
        int y = windowY + 20;
        boolean backHovered = inside(mouseX, mouseY, x, y, 74, 28);
        roundPanel(graphics, x, y, 74, 28, 8, backHovered ? 0xFF2A1740 : 0xFF1A1A24,
                backHovered ? PURPLE : BORDER);
        graphics.text(font, "< Back", x + 12, y + 10, backHovered ? TEXT : MUTED, false);
        graphics.text(font, "CONFIG", x + 102, y + 10, PURPLE, false);
        graphics.fill(x, y + 48, windowX + windowWidth - 22, y + 49, BORDER);
    }

    private void drawActions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = windowX + 22;
        int y = windowY + 76;
        int gap = 10;
        int buttonWidth = (windowWidth - 44 - gap * 2) / 3;
        drawAction(graphics, x, y, buttonWidth, 72, "SAVE", "Write current settings", GREEN, mouseX, mouseY);
        drawAction(graphics, x + buttonWidth + gap, y, buttonWidth, 72, "LOAD", "Read saved settings", PURPLE, mouseX, mouseY);
        drawAction(graphics, x + (buttonWidth + gap) * 2, y, buttonWidth, 72, "RESET", "Restore defaults", RED, mouseX, mouseY);
    }

    private void drawAction(GuiGraphicsExtractor graphics, int x, int y, int buttonWidth, int buttonHeight,
                            String title, String subtitle, int accent, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        roundPanel(graphics, x, y, buttonWidth, buttonHeight, 11, hovered ? 0xFF241E2E : PANEL,
                hovered ? accent : BORDER);
        graphics.text(font, title, x + 14, y + 15, accent, false);
        graphics.text(font, subtitle, x + 14, y + 38, MUTED, false);
    }

    private void drawInfo(GuiGraphicsExtractor graphics) {
        int x = windowX + 22;
        int y = windowY + 176;
        roundPanel(graphics, x, y, windowWidth - 44, 74, 11, PANEL, BORDER);
        graphics.text(font, "Configuration file", x + 16, y + 14, TEXT, false);
        String path = ConfigManager.path().toString();
        if (path.length() > 62) {
            path = "..." + path.substring(path.length() - 59);
        }
        graphics.text(font, path, x + 16, y + 36, MUTED, false);
        graphics.text(font, status, x + 16, y + 55, statusColor, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return false;
        }
        int x = (int) event.x();
        int y = (int) event.y();
        if (inside(x, y, windowX + 22, windowY + 20, 74, 28)) {
            minecraft.gui.setScreen(new ClickGuiScreen());
            return true;
        }

        int buttonY = windowY + 76;
        int gap = 10;
        int buttonWidth = (windowWidth - 44 - gap * 2) / 3;
        int firstX = windowX + 22;
        if (inside(x, y, firstX, buttonY, buttonWidth, 72)) {
            ConfigManager.save();
            status = "Configuration saved.";
            statusColor = GREEN;
        } else if (inside(x, y, firstX + buttonWidth + gap, buttonY, buttonWidth, 72)) {
            ConfigManager.load();
            status = "Configuration loaded.";
            statusColor = PURPLE;
        } else if (inside(x, y, firstX + (buttonWidth + gap) * 2, buttonY, buttonWidth, 72)) {
            ConfigManager.reset();
            status = "Defaults restored and saved.";
            statusColor = RED;
        }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(new ClickGuiScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void roundRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        GuiShapes.roundRect(graphics, x, y, width, height, radius, color);
    }

    private void roundPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            int radius, int fill, int border) {
        GuiShapes.roundPanel(graphics, x, y, width, height, radius, fill, border);
    }
}
