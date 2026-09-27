package dev.utilityclient.gui;

import dev.utilityclient.config.ConfigManager;
import dev.utilityclient.license.LicenseManager;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.util.SystemActions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client wide options that are not tied to a single module: licence details, GUI scale,
 * accent colour, keybind maintenance and the config file location.
 */
public final class ClientSettingsScreen extends Screen {
    private static final int PANEL = 0xFF16161F;
    private static final int ROW = 0xFF101017;
    private static final int ROW_HOVER = 0xFF1C1C26;
    private static final int BORDER = 0xFF2B2B36;
    private static final int TEXT = 0xFFF7F3FF;
    private static final int MUTED = 0xFF9A96A8;
    private static final int DIM = 0xFF615D6D;
    private static final int GREEN = 0xFF42E88A;
    private static final int RED = 0xFFFF5C72;

    private static final int WIDTH = 400;
    private static final int HEIGHT = 330;
    private static final int ROW_HEIGHT = 38;

    private final List<Row> rows = new ArrayList<>();
    private int windowX;
    private int windowY;

    public ClientSettingsScreen() {
        super(Component.literal("Settings"));
    }

    private record Row(String label, String value, int accentValue, Action action) {
    }

    private enum Action {
        NONE, SCALE, ACCENT, RESET_KEYBINDS, OPEN_FOLDER, CHANGE_KEY, REVOKE_KEY
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        windowX = (width - WIDTH) / 2;
        windowY = (height - HEIGHT) / 2;
        rows.clear();

        LicenseManager.Status status = LicenseManager.status();
        rows.add(new Row("Licence", status.tier().displayName(),
                status.valid() ? GREEN : RED, Action.CHANGE_KEY));
        rows.add(new Row("Key", LicenseManager.maskedKey(), 0, Action.CHANGE_KEY));
        if (status.tier() != LicenseManager.Tier.NONE && status.tier() != LicenseManager.Tier.PERMANENT) {
            rows.add(new Row("Expires in", LicenseManager.remainingLabel(), 0, Action.NONE));
        }
        rows.add(new Row("GUI scale", scaleLabel(), 0, Action.SCALE));
        rows.add(new Row("Accent", Theme.name(), Theme.accent(), Action.ACCENT));
        rows.add(new Row("Reset all keybinds", "Clear", 0, Action.RESET_KEYBINDS));
        rows.add(new Row("Config folder", "Open", 0, Action.OPEN_FOLDER));
        if (!status.valid() && !status.tier().equals(LicenseManager.Tier.NONE)) {
            rows.add(new Row("Remove saved key", "Clear", RED, Action.REVOKE_KEY));
        }

        int accent = Theme.accent();
        graphics.fill(0, 0, width, height, 0xE8070810);
        panel(graphics, windowX, windowY, WIDTH, HEIGHT, 14, PANEL, BORDER);

        int x = windowX + 22;
        int y = windowY + 22;
        roundRect(graphics, x, y, 28, 28, 9, accent);
        graphics.text(font, "U", x + 9, y + 10, 0xFF120018, false);
        graphics.text(font, "Settings", x + 38, y + 4, TEXT, false);
        graphics.text(font, "Client options", x + 38, y + 16, DIM, false);
        y += 44;
        graphics.fill(x, y, x + WIDTH - 44, y + 1, BORDER);
        y += 12;

        // Licence summary line
        graphics.text(font, "Key server", x, y, DIM, false);
        String endpoint = LicenseManager.endpointConfigured()
                ? LicenseManager.endpoint()
                : "not configured yet";
        graphics.text(font, trim(endpoint, WIDTH - 130), x + 66, y,
                LicenseManager.endpointConfigured() ? MUTED : RED, false);
        if (!LicenseManager.endpointConfigured()) {
            graphics.text(font, "Set -Dutilityclient.api=... to point at your worker.",
                    x, y + 11, DIM, false);
        }
        y += LicenseManager.endpointConfigured() ? 22 : 34;
        graphics.fill(x, y, x + WIDTH - 44, y + 1, BORDER);
        y += 10;

        int rowWidth = WIDTH - 44;
        for (Row row : rows) {
            if (y + ROW_HEIGHT > windowY + HEIGHT - 40) {
                break;
            }
            boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, ROW_HEIGHT);
            roundPanel(graphics, x, y, rowWidth, ROW_HEIGHT, 8, hovered ? ROW_HOVER : ROW, BORDER);
            graphics.text(font, row.label(), x + 12, y + (ROW_HEIGHT - 9) / 2, TEXT, false);

            int valueColor = row.accentValue() != 0 ? row.accentValue()
                    : (row.action() == Action.NONE ? MUTED : accent);
            int right = x + rowWidth - 12;
            if (row.action() != Action.NONE) {
                String value = row.value();
                int valueWidth = font.width(value) + 14;
                roundRect(graphics, right - valueWidth, y + 9, valueWidth, 20, 6, 0xFF23232E);
                graphics.text(font, value, right - valueWidth + 7, y + 14, valueColor, false);
            } else {
                graphics.text(font, row.value(), right - font.width(row.value()), y + (ROW_HEIGHT - 9) / 2,
                        MUTED, false);
            }
            y += ROW_HEIGHT + 5;
        }

        String back = "Esc to go back";
        graphics.text(font, back, windowX + (WIDTH - font.width(back)) / 2,
                windowY + HEIGHT - 26, DIM, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return false;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int x = windowX + 22;
        int rowWidth = WIDTH - 44;

        int y = windowY + 78;
        y += LicenseManager.endpointConfigured() ? 22 : 34;
        y += 10;
        for (Row row : rows) {
            if (y + ROW_HEIGHT > windowY + HEIGHT - 40) {
                break;
            }
            if (inside(mouseX, mouseY, x, y, rowWidth, ROW_HEIGHT)) {
                apply(row.action());
                return true;
            }
            y += ROW_HEIGHT + 5;
        }
        return true;
    }

    private void apply(Action action) {
        switch (action) {
            case SCALE -> {
                int current = minecraft.options.guiScale().get();
                int next = switch (current) {
                    case 1 -> 2;
                    case 2 -> 3;
                    case 3 -> 4;
                    case 4 -> 1;
                    default -> 2;
                };
                minecraft.options.guiScale().set(next);
                minecraft.options.save();
            }
            case ACCENT -> Theme.cycle();
            case RESET_KEYBINDS -> {
                for (Module module : ModuleManager.get().modules()) {
                    module.keyBind().clear();
                }
                ConfigManager.save();
                say("Cleared every module keybind.");
            }
            case OPEN_FOLDER -> {
                Path folder = ConfigManager.path().getParent();
                boolean opened = SystemActions.openFolder(folder);
                say(opened ? "Opened the config folder." : "Copied the config path to the clipboard.");
            }
            case CHANGE_KEY -> minecraft.gui.setScreen(new LicenseScreen());
            case REVOKE_KEY -> {
                LicenseManager.clear();
                say("Saved key removed from this computer.");
            }
            case NONE -> {
            }
        }
    }

    private void say(String message) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }

    private String scaleLabel() {
        int value = minecraft.options.guiScale().get();
        return value <= 0 ? "Auto" : value + "x";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(new ClickGuiScreen());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(new ClickGuiScreen());
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
