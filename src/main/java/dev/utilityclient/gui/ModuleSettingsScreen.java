package dev.utilityclient.gui;

import dev.utilityclient.config.ConfigManager;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.module.impl.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModuleSettingsScreen extends Screen {
    private static final int WINDOW = 0xFF0B0B11;
    private static final int PANEL = 0xFF16161F;
    private static final int BORDER = 0xFF2B2B36;
    private static int PURPLE;
    private static int PURPLE_DIM;
    private static int PURPLE_SOFT;
    private static final int TEXT = 0xFFF7F3FF;
    private static final int MUTED = 0xFF9A96A8;
    private static final int DIM = 0xFF615D6D;
    private static final int TRACK = 0xFF2A2A35;
    private static final int GREEN = 0xFF42E88A;
    private static final int RED = 0xFFFF5577;

    private final Module module;
    private final Set<String> expandedColors = new HashSet<>();
    private Layout layout = new Layout();

    private boolean listening;
    private String message = "";
    private int messageColor = MUTED;
    private int scroll;

    private String dragSettingId;
    private boolean dragHue;

    // Free text setting currently being edited, for the STRING type.
    private String editingSettingId;
    private String textBuffer = "";

    public ModuleSettingsScreen(Module module) {
        super(Component.literal(module.displayName() + " settings"));
        this.module = module;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        PURPLE = Theme.accent();
        PURPLE_DIM = Theme.accentDim();
        PURPLE_SOFT = Theme.accentSoft();
        layout = buildLayout();
        graphics.fill(0, 0, width, height, 0xE8070810);
        roundPanel(graphics, layout.windowX, layout.windowY, layout.windowWidth, layout.windowHeight, 14, WINDOW, BORDER);

        drawHeader(graphics, mouseX, mouseY);
        drawModuleCard(graphics, mouseX, mouseY);
        drawKeybindRow(graphics, mouseX, mouseY);
        drawSettings(graphics, mouseX, mouseY);
        drawScrollbar(graphics);
        drawFooter(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = layout.windowX + 22;
        int y = layout.windowY + 20;
        boolean hovered = inside(mouseX, mouseY, x, y, 70, 26);
        roundPanel(graphics, x, y, 70, 26, 7, hovered ? PURPLE_SOFT : 0xFF1A1A24, hovered ? PURPLE : BORDER);
        graphics.text(font, "< Back", x + 12, y + 9, hovered ? TEXT : MUTED, false);
        graphics.text(font, "MODULE SETTINGS", x + 104, y + 9, PURPLE, false);
        graphics.fill(x, y + 44, layout.windowX + layout.windowWidth - 22, y + 45, BORDER);
    }

    private void drawModuleCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = layout.windowX + 22;
        int y = layout.windowY + 66;
        int w = layout.windowWidth - 44;
        roundPanel(graphics, x, y, w, 76, 10, PANEL, module.enabled() ? PURPLE : BORDER);
        graphics.text(font, module.displayName(), x + 16, y + 14, TEXT, false);
        graphics.text(font, module.description(), x + 16, y + 36, MUTED, false);

        if (module instanceof HudModule) {
            boolean moveHovered = inside(mouseX, mouseY, layout.moveX, y + 26, 78, 26);
            roundRect(graphics, layout.moveX, y + 26, 78, 26, 7, moveHovered ? 0xFF20342C : 0xFF1A1A24);
            graphics.text(font, "MOVE", layout.moveX + 20, y + 34, moveHovered ? GREEN : MUTED, false);
        }
        drawToggle(graphics, layout.toggleX, y + 28, module.enabled());
    }

    private void drawKeybindRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = layout.windowX + 22;
        int y = layout.windowY + 154;
        int w = layout.windowWidth - 44;
        boolean hovered = inside(mouseX, mouseY, layout.keybindX, layout.keybindY, 150, 34);
        boolean blink = listening && (Util.getMillis() / 400L) % 2L == 0L;

        roundPanel(graphics, x, y, w, 58, 10, listening ? 0xFF1E1430 : PANEL,
                listening ? PURPLE : (hovered ? PURPLE_DIM : BORDER));
        graphics.text(font, "Keybind", x + 14, y + 12, TEXT, false);
        graphics.text(font, "Any keyboard key or mouse button.", x + 14, y + 33, MUTED, false);

        String value = listening ? "Listening..." : module.keyBind().displayName();
        int valueColor = listening ? (blink ? TEXT : PURPLE) : (module.keyBind().bound() ? PURPLE : DIM);
        roundRect(graphics, layout.keybindX, layout.keybindY, 150, 34, 8,
                listening ? (blink ? 0xFF2A1740 : TRACK) : 0xFF23232E);
        graphics.text(font, value,
                layout.keybindX + (150 - font.width(value)) / 2,
                layout.keybindY + 12, valueColor, false);

        boolean clearHovered = inside(mouseX, mouseY, layout.clearX, layout.keybindY, 32, 34);
        roundRect(graphics, layout.clearX, layout.keybindY, 32, 34, 8, clearHovered ? 0xFF3A1B27 : TRACK);
        graphics.text(font, "x", layout.clearX + 13, layout.keybindY + 12, clearHovered ? RED : MUTED, false);
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (layout.rows.isEmpty()) {
            graphics.text(font, "This module has no other settings.", layout.windowX + 22,
                    layout.contentTop + 12, DIM, false);
            return;
        }

        graphics.enableScissor(layout.windowX + 1, layout.contentTop, layout.windowX + layout.windowWidth - 1,
                layout.contentBottom);
        for (SettingRow row : layout.rows) {
            if (row.color != null) {
                drawColorRow(graphics, mouseX, mouseY, row);
            } else if (row.setting.type() == ModuleSetting.Type.STRING) {
                drawTextRow(graphics, mouseX, mouseY, row);
            } else {
                drawValueRow(graphics, mouseX, mouseY, row);
            }
        }
        graphics.disableScissor();
    }

    /**
     * Free text setting. Clicking focuses the box, typing edits it, Enter commits it and
     * Esc abandons the edit. Used for things like a server specific chat command where a
     * fixed list of options would be wrong.
     */
    private void drawTextRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, SettingRow row) {
        boolean hovered = inside(mouseX, mouseY, row.x, row.y, row.width, row.height);
        roundPanel(graphics, row.x, row.y, row.width, row.height, 9,
                hovered ? 0xFF1E1A28 : PANEL, hovered ? PURPLE_DIM : BORDER);
        graphics.text(font, row.setting.name(), row.x + 14, row.y + 12, TEXT, false);

        boolean editing = editingSettingId != null && editingSettingId.equals(row.setting.id());
        int fieldX = row.x + 14;
        int fieldWidth = row.width - 28;
        int fieldY = row.y + 30;
        int fieldHeight = 20;

        roundPanel(graphics, fieldX, fieldY, fieldWidth, fieldHeight, 6,
                editing ? 0xFF101018 : 0xFF14141C, editing ? PURPLE : BORDER);

        String value = editing ? textBuffer : String.valueOf(row.setting.value());
        if (value.isEmpty()) {
            value = "not set";
        }
        // Show the tail, so a long command keeps its most meaningful part visible.
        String shown = value;
        while (shown.length() > 2 && font.width(shown) > fieldWidth - 16) {
            shown = shown.substring(1);
        }
        if (!shown.equals(value)) {
            shown = "..." + shown;
        }
        graphics.text(font, shown, fieldX + 8, fieldY + 6,
                editing ? TEXT : (value.equals("not set") ? DIM : MUTED), false);

        if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = fieldX + 8 + font.width(shown) + 1;
            if (caretX < fieldX + fieldWidth - 4) {
                graphics.fill(caretX, fieldY + 5, caretX + 1, fieldY + fieldHeight - 5, PURPLE);
            }
        }
        graphics.text(font, editing ? "Enter saves, Esc cancels" : row.setting.description(),
                row.x + 14, row.y + row.height - 14, editing ? PURPLE_DIM : DIM, false);
    }

    private void drawValueRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, SettingRow row) {
        boolean hovered = inside(mouseX, mouseY, row.x, row.y, row.width, row.height);
        roundPanel(graphics, row.x, row.y, row.width, row.height, 9,
                hovered ? 0xFF1E1A28 : PANEL, hovered ? PURPLE_DIM : BORDER);
        graphics.text(font, row.setting.name(), row.x + 14, row.y + 12, TEXT, false);
        graphics.text(font, row.setting.description(), row.x + 14, row.y + 32, MUTED, false);

        int controlX = row.x + row.width - 150;
        roundRect(graphics, controlX, row.y + 18, 26, 28, 7, TRACK);
        graphics.text(font, "-", controlX + 10, row.y + 27, MUTED, false);
        roundRect(graphics, controlX + 34, row.y + 18, 82, 28, 7, 0xFF23232E);
        String value = row.setting.displayValue();
        graphics.text(font, value, controlX + 34 + (82 - font.width(value)) / 2, row.y + 27,
                row.setting.type() == ModuleSetting.Type.BOOLEAN && Boolean.TRUE.equals(row.setting.value())
                        ? PURPLE : TEXT, false);
        roundRect(graphics, controlX + 124, row.y + 18, 26, 28, 7, TRACK);
        graphics.text(font, "+", controlX + 134, row.y + 27, MUTED, false);
    }

    @SuppressWarnings("unchecked")
    private void drawColorRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, SettingRow row) {
        ModuleSetting<Integer> setting = (ModuleSetting<Integer>) row.setting;
        boolean hovered = inside(mouseX, mouseY, row.x, row.y, row.width, row.height);
        roundPanel(graphics, row.x, row.y, row.width, row.height, 9,
                hovered ? 0xFF1E1A28 : PANEL, hovered ? PURPLE_DIM : BORDER);
        graphics.text(font, setting.name(), row.x + 14, row.y + 11, TEXT, false);
        graphics.text(font, setting.displayValue(), row.x + 14, row.y + 31, MUTED, false);

        boolean swatchHovered = inside(mouseX, mouseY, row.swatchX, row.swatchY, 82, 14);
        roundRect(graphics, row.swatchX, row.swatchY, 82, 14, 5, 0xFF23232E);
        roundRect(graphics, row.swatchX + 1, row.swatchY + 1, 80, 12, 4, 0xFF000000 | setting.colorValue());
        graphics.text(font, expandedColors.contains(setting.id()) ? "close" : "pick",
                row.swatchX + (expandedColors.contains(setting.id()) ? 30 : 34), row.swatchY + 3,
                swatchHovered ? TEXT : MUTED, false);

        if (row.color == null) {
            return;
        }

        ColorPicker picker = row.color;
        float[] hsb = ModuleSetting.rgbToHsb(setting.colorValue());
        int steps = 28;
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < steps; j++) {
                float saturation = (i + 0.5F) / steps;
                float brightness = 1.0F - (j + 0.5F) / steps;
                int argb = 0xFF000000 | ModuleSetting.hueToRgb(hsb[0], saturation, brightness);
                int px = picker.squareX + (i * picker.squareSize) / steps;
                int py = picker.squareY + (j * picker.squareSize) / steps;
                int pw = Math.max(1, ((i + 1) * picker.squareSize) / steps - (i * picker.squareSize) / steps + 1);
                int ph = Math.max(1, ((j + 1) * picker.squareSize) / steps - (j * picker.squareSize) / steps + 1);
                graphics.fill(px, py, px + pw, py + ph, argb);
            }
        }
        int cursorX = picker.squareX + (int) (hsb[1] * picker.squareSize) - 1;
        int cursorY = picker.squareY + (int) ((1.0F - hsb[2]) * picker.squareSize) - 1;
        graphics.outline(cursorX, cursorY, 3, 3, 0xFFFFFFFF);
        graphics.outline(cursorX - 1, cursorY - 1, 5, 5, 0xFF000000);

        for (int i = 0; i < picker.hueWidth; i++) {
            int argb = 0xFF000000 | ModuleSetting.hueToRgb((i + 0.5F) / picker.hueWidth, 1.0F, 1.0F);
            graphics.fill(picker.hueX + i, picker.hueY, picker.hueX + i + 1, picker.hueY + picker.squareSize, argb);
        }
        int markerX = picker.hueX + Math.min(picker.hueWidth - 3, (int) (hsb[0] * picker.hueWidth));
        graphics.fill(markerX, picker.hueY - 3, markerX + 3, picker.hueY + picker.squareSize + 3, 0xFF000000);
        graphics.fill(markerX + 1, picker.hueY - 2, markerX + 2, picker.hueY + picker.squareSize + 2, TEXT);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (layout.maxScroll <= 0) {
            return;
        }
        int x = layout.windowX + layout.windowWidth - 8;
        int available = layout.contentBottom - layout.contentTop;
        int thumbHeight = Math.max(24, available * available / (available + layout.maxScroll));
        int thumbY = layout.contentTop + (available - thumbHeight) * scroll / Math.max(1, layout.maxScroll);
        roundRect(graphics, x, layout.contentTop, 3, available, 2, 0xFF23232E);
        roundRect(graphics, x, thumbY, 3, thumbHeight, 2, PURPLE_DIM);
    }

    private void drawFooter(GuiGraphicsExtractor graphics) {
        int y = layout.windowY + layout.windowHeight - 22;
        if (!message.isEmpty()) {
            graphics.text(font, message, layout.windowX + 22, y, messageColor, false);
            return;
        }
        String hint = listening
                ? "Press a key or mouse button  •  ESC cancels  •  Delete clears"
                : "Click - / + to change settings  •  Click a colour swatch to pick  •  Insert returns";
        graphics.text(font, hint, layout.windowX + 22, y, DIM, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening) {
            module.keyBind().setMouse(event.button());
            finishBinding();
            return true;
        }
        if (event.button() != 0 && event.button() != 1) {
            return false;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (inside(mouseX, mouseY, layout.windowX + 22, layout.windowY + 20, 70, 26)) {
            minecraft.gui.setScreen(new ClickGuiScreen());
            return true;
        }
        if (inside(mouseX, mouseY, layout.toggleX, layout.windowY + 94, 44, 24)) {
            module.toggle();
            ConfigManager.save();
            return true;
        }
        if (module instanceof HudModule
                && inside(mouseX, mouseY, layout.moveX, layout.windowY + 92, 78, 26)) {
            if (event.button() == 0) {
                if (((HudModule) module).beginMove(minecraft)) {
                    minecraft.gui.setScreen(null);
                } else {
                    showMessage("Join a world before moving the HUD.", RED);
                }
            }
            return true;
        }
        if (inside(mouseX, mouseY, layout.clearX, layout.keybindY, 32, 34)) {
            module.keyBind().clear();
            ConfigManager.save();
            showMessage("Keybind cleared.", MUTED);
            return true;
        }
        if (inside(mouseX, mouseY, layout.keybindX, layout.keybindY, 150, 34)) {
            if (event.button() == 1) {
                module.keyBind().clear();
                ConfigManager.save();
                showMessage("Keybind cleared.", MUTED);
            } else {
                listening = true;
                showMessage("", MUTED);
            }
            return true;
        }

        for (SettingRow row : layout.rows) {
            if (mouseY < layout.contentTop || mouseY >= layout.contentBottom) {
                break;
            }
            if (!inside(mouseX, mouseY, row.x, row.y, row.width, row.height)) {
                continue;
            }
            if (row.setting.type() == ModuleSetting.Type.COLOR) {
                if (inside(mouseX, mouseY, row.swatchX, row.swatchY, 82, 14)) {
                    if (!expandedColors.remove(row.setting.id())) {
                        expandedColors.add(row.setting.id());
                    }
                    return true;
                }
                if (row.color != null && applyColorDrag(mouseX, mouseY, row)) {
                    return true;
                }
                return true;
            }
            if (row.setting.type() == ModuleSetting.Type.STRING) {
                editingSettingId = row.setting.id();
                textBuffer = String.valueOf(row.setting.value());
                module.onSettingsChanged();
                return true;
            }
            int controlX = row.x + row.width - 150;
            if (mouseX < controlX + 34) {
                row.setting.adjust(-1);
            } else {
                row.setting.adjust(1);
            }
            module.onSettingsChanged();
            ConfigManager.save();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragSettingId == null) {
            return true;
        }
        for (SettingRow row : layout.rows) {
            if (row.color != null && row.setting.id().equals(dragSettingId)) {
                applyColor((int) event.x(), (int) event.y(), row, dragHue);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragSettingId != null) {
            dragSettingId = null;
            ConfigManager.save();
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean applyColorDrag(int mouseX, int mouseY, SettingRow row) {
        ColorPicker picker = row.color;
        if (picker == null) {
            return false;
        }
        if (inside(mouseX, mouseY, picker.squareX, picker.squareY, picker.squareSize, picker.squareSize)) {
            dragSettingId = row.setting.id();
            dragHue = false;
            applyColor(mouseX, mouseY, row, false);
            return true;
        }
        if (inside(mouseX, mouseY, picker.hueX, picker.hueY, picker.hueWidth, picker.squareSize)) {
            dragSettingId = row.setting.id();
            dragHue = true;
            applyColor(mouseX, mouseY, row, true);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void applyColor(int mouseX, int mouseY, SettingRow row, boolean hue) {
        ModuleSetting<Integer> setting = (ModuleSetting<Integer>) row.setting;
        ColorPicker picker = row.color;
        if (picker == null) {
            return;
        }
        float[] hsb = ModuleSetting.rgbToHsb(setting.colorValue());
        if (hue) {
            float newHue = (mouseX - picker.hueX) / (float) Math.max(1, picker.hueWidth);
            hsb[0] = Math.max(0.0F, Math.min(0.999F, newHue));
        } else {
            float saturation = (mouseX - picker.squareX) / (float) Math.max(1, picker.squareSize);
            float brightness = 1.0F - (mouseY - picker.squareY) / (float) Math.max(1, picker.squareSize);
            hsb[1] = Math.max(0.0F, Math.min(1.0F, saturation));
            hsb[2] = Math.max(0.0F, Math.min(1.0F, brightness));
        }
        setting.setColor(ModuleSetting.hueToRgb(hsb[0], hsb[1], hsb[2]));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            int code = event.key();
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
                showMessage("Keybind unchanged.", MUTED);
                return true;
            }
            if (code == GLFW.GLFW_KEY_DELETE || code == GLFW.GLFW_KEY_BACKSPACE) {
                module.keyBind().clear();
                finishBinding();
                return true;
            }
            if (!isModifierKey(code)) {
                module.keyBind().setKeyboard(code);
                finishBinding();
            }
            return true;
        }
        if (editingSettingId != null) {
            int code = event.key();
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                // Abandon the edit and put the stored value back in the box.
                textBuffer = valueOf(editingSettingId);
                editingSettingId = null;
                showMessage("Edit cancelled.", MUTED);
                return true;
            }
            if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
                commitText(editingSettingId);
                editingSettingId = null;
                return true;
            }
            if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (!textBuffer.isEmpty()) {
                    textBuffer = textBuffer.substring(0, textBuffer.length() - 1);
                }
                return true;
            }
            if (code == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                pasteIntoBuffer();
                return true;
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_INSERT || event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(new ClickGuiScreen());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingSettingId != null) {
            if (event.isAllowedChatCharacter() && textBuffer.length() < 120) {
                textBuffer += event.codepointAsString();
            }
            return true;
        }
        return super.charTyped(event);
    }

    private String valueOf(String settingId) {
        for (SettingRow row : layout.rows) {
            if (row.setting.id().equals(settingId)) {
                return String.valueOf(row.setting.value());
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private void commitText(String settingId) {
        for (SettingRow row : layout.rows) {
            if (!row.setting.id().equals(settingId)) {
                continue;
            }
            ((ModuleSetting<String>) row.setting).set(textBuffer);
            module.onSettingsChanged();
            ConfigManager.save();
            showMessage(row.setting.name() + " saved.", MUTED);
            return;
        }
    }

    private void pasteIntoBuffer() {
        try {
            String clipboard = minecraft.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                textBuffer = (textBuffer + clipboard.replaceAll("\\s+", " ")).substring(0,
                        Math.min(120, textBuffer.length() + clipboard.length()));
            }
        } catch (RuntimeException ignored) {
            // Clipboard can throw on some platforms, typing still works.
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (inside((int) mouseX, (int) mouseY, layout.windowX + 1, layout.contentTop,
                layout.windowWidth - 2, Math.max(1, layout.contentBottom - layout.contentTop))) {
            scroll = Math.max(0, Math.min(layout.maxScroll, scroll - (int) (deltaY * 18)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        if (dragSettingId != null) {
            dragSettingId = null;
            ConfigManager.save();
        }
        minecraft.gui.setScreen(new ClickGuiScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void finishBinding() {
        listening = false;
        ConfigManager.save();
        showMessage("Keybind set to " + module.keyBind().displayName() + ".", GREEN);
    }

    private void showMessage(String text, int color) {
        message = text;
        messageColor = color;
    }

    private boolean isModifierKey(int code) {
        return code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT
                || code == GLFW.GLFW_KEY_LEFT_CONTROL || code == GLFW.GLFW_KEY_RIGHT_CONTROL
                || code == GLFW.GLFW_KEY_LEFT_ALT || code == GLFW.GLFW_KEY_RIGHT_ALT
                || code == GLFW.GLFW_KEY_LEFT_SUPER || code == GLFW.GLFW_KEY_RIGHT_SUPER
                || code == GLFW.GLFW_KEY_CAPS_LOCK || code == GLFW.GLFW_KEY_NUM_LOCK
                || code == GLFW.GLFW_KEY_SCROLL_LOCK;
    }

    // ----------------------------------------------------------------- layout

    private Layout buildLayout() {
        Layout result = new Layout();
        result.windowWidth = Math.min(600, Math.max(440, width - 36));
        result.windowHeight = Math.min(560, Math.max(360, height - 36));
        result.windowX = (width - result.windowWidth) / 2;
        result.windowY = (height - result.windowHeight) / 2;

        int x = result.windowX + 22;
        int rowWidth = result.windowWidth - 44;
        result.toggleX = x + rowWidth - 66;
        result.moveX = x + rowWidth - 150;
        result.keybindX = x + rowWidth - 32 - 8 - 150;
        result.clearX = x + rowWidth - 32;
        result.keybindY = result.windowY + 166;
        result.contentTop = result.windowY + 224;
        result.contentBottom = result.windowY + result.windowHeight - 36;

        int y = result.contentTop + 4 - scroll;
        int totalHeight = 0;
        for (ModuleSetting<?> setting : module.settings()) {
            boolean color = setting.type() == ModuleSetting.Type.COLOR;
            boolean expanded = color && expandedColors.contains(setting.id());
            int rowHeight = 64;
            ColorPicker picker = null;
            if (expanded) {
                int square = pickerSquareSize(rowWidth);
                int squareX = x + 14;
                int squareY = y + 48;
                int hueX = squareX + square + 12;
                int hueWidth = Math.max(20, Math.min(120, x + rowWidth - 14 - hueX));
                picker = new ColorPicker(squareX, squareY, square, hueX, squareY, hueWidth);
                rowHeight = 56 + square + 12;
            }
            result.rows.add(new SettingRow(setting, x, y, rowWidth, rowHeight,
                    x + rowWidth - 96, y + 27, picker));
            y += rowHeight + 8;
            totalHeight += rowHeight + 8;
        }

        int visibleHeight = Math.max(1, result.contentBottom - result.contentTop);
        result.maxScroll = Math.max(0, totalHeight - visibleHeight);
        scroll = Math.max(0, Math.min(result.maxScroll, scroll));
        return result;
    }

    private int pickerSquareSize(int rowWidth) {
        return Math.max(60, Math.min(110, rowWidth - 190));
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, boolean enabled) {
        roundRect(graphics, x, y, 42, 20, 10, enabled ? PURPLE : TRACK);
        graphics.fill(enabled ? x + 24 : x + 4, y + 4, enabled ? x + 38 : x + 18, y + 16, TEXT);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void roundRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - 1 - i, x + width - inset, y + height - i, color);
        }
    }

    private void roundPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            int radius, int fill, int border) {
        roundRect(graphics, x, y, width, height, radius, border);
        roundRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(1, radius - 1), fill);
    }

    private record ColorPicker(int squareX, int squareY, int squareSize, int hueX, int hueY, int hueWidth) {
    }

    private record SettingRow(ModuleSetting<?> setting, int x, int y, int width, int height,
                              int swatchX, int swatchY, ColorPicker color) {
    }

    private static final class Layout {
        private int windowX;
        private int windowY;
        private int windowWidth;
        private int windowHeight;
        private int contentTop;
        private int contentBottom;
        private int keybindX;
        private int keybindY;
        private int clearX;
        private int moveX;
        private int toggleX;
        private int maxScroll;
        private final List<SettingRow> rows = new ArrayList<>();
    }
}
