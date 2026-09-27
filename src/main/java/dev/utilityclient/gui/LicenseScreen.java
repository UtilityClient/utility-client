package dev.utilityclient.gui;

import dev.utilityclient.license.LicenseManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Key entry screen. Shown instead of the main menu until a valid key is present.
 */
public final class LicenseScreen extends Screen {
    private static final int PANEL = 0xFF16161F;
    private static final int FIELD = 0xFF0F0F17;
    private static final int BORDER = 0xFF2B2B36;
    private static int PURPLE;
    private static int PURPLE_DIM;
    private static final int TEXT = 0xFFF7F3FF;
    private static final int MUTED = 0xFF9A96A8;
    private static final int DIM = 0xFF615D6D;
    private static final int GREEN = 0xFF42E88A;
    private static final int RED = 0xFFFF5C72;

    private static final int WIDTH = 380;
    private static final int HEIGHT = 300;

    private String input = "";
    private boolean focused = true;
    private int buttonX;
    private int buttonY;
    private int buttonWidth = 150;
    private int buttonHeight = 30;

    public LicenseScreen() {
        super(Component.literal("Utility Client - Activate"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        PURPLE = Theme.accent();
        PURPLE_DIM = Theme.accentDim();
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        graphics.fill(0, 0, width, height, 0xE8070810);
        panel(graphics, x, y, WIDTH, HEIGHT, 14, PANEL, BORDER);

        int cx = x + 28;
        int contentWidth = WIDTH - 56;

        // Brand
        roundRect(graphics, cx, y + 26, 34, 34, 10, PURPLE);
        graphics.text(font, "U", cx + 11, y + 36, 0xFF120018, false);
        graphics.text(font, "Utility Client", cx + 46, y + 30, TEXT, false);
        graphics.text(font, "v0.1.0", cx + 46, y + 44, DIM, false);

        LicenseManager.Status status = LicenseManager.status();
        graphics.text(font, status.valid() ? "Licensed" : "Enter your key", cx, y + 84, TEXT, false);
        graphics.text(font, status.valid()
                        ? "This copy is active. You can close this screen."
                        : "Paste the key you were given to use the client.",
                cx, y + 100, MUTED, false);

        int fieldY = y + 122;
        boolean fieldHovered = inside(mouseX, mouseY, cx, fieldY, contentWidth, 30);
        roundPanel(graphics, cx, fieldY, contentWidth, 30, 8, FIELD, focused ? PURPLE_DIM : BORDER);
        if (fieldHovered && !focused) {
            roundRect(graphics, cx, fieldY, contentWidth, 30, 8, 0x00000000);
        }
        String shown = input.isEmpty() ? "UC-XXXX-XXXX-XXXX-XXXX" : input;
        graphics.text(font, shown, cx + 12, fieldY + 10, input.isEmpty() ? DIM : TEXT, false);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = cx + 12 + font.width(input.isEmpty() ? "" : input) + 1;
            graphics.fill(caretX, fieldY + 8, caretX + 1, fieldY + 22, PURPLE);
        }

        buttonX = x + WIDTH / 2 - buttonWidth / 2;
        buttonY = fieldY + 44;
        boolean buttonHovered = inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
        int buttonColor = status.checking() ? 0xFF4A2A5C : (buttonHovered ? 0xFFE62BFF : PURPLE);
        roundRect(graphics, buttonX, buttonY, buttonWidth, buttonHeight, 8, buttonColor);
        String label = status.checking() ? "Checking..." : "Activate";
        graphics.text(font, label,
                buttonX + (buttonWidth - font.width(label)) / 2,
                buttonY + (buttonHeight - 9) / 2, TEXT, false);

        int messageY = buttonY + buttonHeight + 12;
        int messageColor = status.valid() ? GREEN : (status.expired() ? RED : DIM);
        if (status.expired()) {
            messageColor = RED;
        }
        graphics.text(font, trim(status.detail(), contentWidth), cx, messageY, messageColor, false);

        // License summary, only worth showing when there is something to summarise.
        int infoY = messageY + 20;
        if (status.tier() != LicenseManager.Tier.NONE) {
            graphics.text(font, "Plan: " + status.tier().displayName(), cx, infoY, MUTED, false);
            graphics.text(font, "Key: " + LicenseManager.maskedKey(), cx, infoY + 12, MUTED, false);
            graphics.text(font, status.tier() == LicenseManager.Tier.PERMANENT
                            ? "Never expires"
                            : "Expires: " + LicenseManager.expiryLabel() + " (" + LicenseManager.remainingLabel() + ")",
                    cx, infoY + 24, MUTED, false);
        } else {
            graphics.text(font, "Plans: 1 day, 1 week, 1 month, lifetime.", cx, infoY, DIM, false);
            graphics.text(font, "Run the game offline and a saved licence keeps working for 72 hours.",
                    cx, infoY + 12, DIM, false);
        }

        if (status.valid()) {
            int doneX = x + WIDTH / 2 - 50;
            int doneY = y + HEIGHT - 44;
            boolean doneHovered = inside(mouseX, mouseY, doneX, doneY, 100, 26);
            roundPanel(graphics, doneX, doneY, 100, 26, 8, 0xFF1E1E28, doneHovered ? PURPLE_DIM : BORDER);
            String done = "Open menu";
            graphics.text(font, done, doneX + (100 - font.width(done)) / 2, doneY + 9, TEXT, false);
        } else {
            String hint = "Enter to activate    .    Esc to close";
            graphics.text(font, hint, x + WIDTH / 2 - font.width(hint) / 2, y + HEIGHT - 32, DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        int cx = x + 28;
        int contentWidth = WIDTH - 56;

        if (inside(mouseX, mouseY, cx, y + 122, contentWidth, 30)) {
            focused = true;
            return true;
        }
        LicenseManager.Status status = LicenseManager.status();
        if (!status.checking() && inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight)) {
            activate();
            return true;
        }
        if (status.valid()) {
            int doneX = x + WIDTH / 2 - 50;
            int doneY = y + HEIGHT - 44;
            if (inside(mouseX, mouseY, doneX, doneY, 100, 26)) {
                minecraft.gui.setScreen(new ClickGuiScreen());
                return true;
            }
        }
        focused = false;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focused && event.isAllowedChatCharacter() && input.length() < 64) {
            char c = event.codepointAsString().charAt(0);
            // Keys are printed without spaces, so silently drop accidental spaces.
            if (c != ' ') {
                input += Character.toUpperCase(c);
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int code = event.key();
        if (code == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) {
                input = input.substring(0, input.length() - 1);
            }
            return true;
        }
        if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
            activate();
            return true;
        }
        if (code == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            pasteFromClipboard();
            return true;
        }
        if (code == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    private void activate() {
        focused = false;
        if (input.isEmpty()) {
            // Re-open the stored key rather than silently doing nothing.
            String stored = LicenseManager.key();
            if (stored.isEmpty()) {
                return;
            }
        }
        LicenseManager.submit(input);
    }

    private void pasteFromClipboard() {
        try {
            String clipboard = minecraft.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                input = clipboard.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            }
        } catch (RuntimeException ignored) {
            // Clipboard access can throw on some platforms; the field is still typeable.
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String cut = value;
        while (cut.length() > 4 && font.width(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void roundRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + w, y + h, color);
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

    private void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int fill, int border) {
        roundRect(graphics, x, y, w, h, radius, border);
        roundRect(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1), fill);
    }

    private void roundPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                            int radius, int fill, int border) {
        panel(graphics, x, y, w, h, radius, fill, border);
    }
}
