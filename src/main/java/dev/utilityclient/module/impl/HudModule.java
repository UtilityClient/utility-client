package dev.utilityclient.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilityclient.config.ConfigManager;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class HudModule extends Module {
    public final ModuleSetting<Boolean> showFps;
    public final ModuleSetting<Boolean> showCoordinates;
    public final ModuleSetting<Boolean> showSession;
    public final ModuleSetting<Integer> positionX;
    public final ModuleSetting<Integer> positionY;

    private boolean moving;
    private int startX;
    private int startY;
    private int ghostX;
    private int ghostY;
    private boolean wasLeftDown;

    public HudModule() {
        super("hud", "HUD", "FPS, coordinates, facing and session time.", ModuleCategory.MISC, true, false, false);
        showFps = addSetting(ModuleSetting.booleanSetting("show-fps", "Show FPS", "Display the current frame rate.", true));
        showCoordinates = addSetting(ModuleSetting.booleanSetting("show-coordinates", "Show coordinates", "Display XYZ position.", true));
        showSession = addSetting(ModuleSetting.booleanSetting("show-session", "Show session time", "Display play time for this session.", true));
        positionX = addSetting(ModuleSetting.integerSetting("position-x", "HUD X", "Horizontal HUD position.", 6, 0, 8000, 1));
        positionY = addSetting(ModuleSetting.integerSetting("position-y", "HUD Y", "Vertical HUD position.", 6, 0, 8000, 1));
    }

    public boolean moving() {
        return moving;
    }

    public int ghostX() {
        return moving ? ghostX : positionX.value();
    }

    public int ghostY() {
        return moving ? ghostY : positionY.value();
    }

    /**
     * Leaves the menu and lets the player drag the HUD with the mouse.
     */
    public boolean beginMove(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }
        startX = clampX(client, positionX.value());
        startY = clampY(client, positionY.value());
        ghostX = startX;
        ghostY = startY;
        wasLeftDown = false;
        moving = true;
        client.mouseHandler.releaseMouse();
        return true;
    }

    public void tickMoveMode(Minecraft client) {
        if (!moving) {
            return;
        }
        if (client.player == null || client.level == null || client.gui.screen() != null) {
            cancelMove(client);
            return;
        }

        if (client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
        }

        ghostX = (int) client.mouseHandler.getScaledXPos(client.getWindow());
        ghostY = (int) client.mouseHandler.getScaledYPos(client.getWindow());

        long handle = client.getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean cancel = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_ESCAPE);

        if (rightDown || cancel) {
            cancelMove(client);
            wasLeftDown = leftDown;
            return;
        }
        if (leftDown && !wasLeftDown) {
            placeMove(client, ghostX, ghostY);
        }
        wasLeftDown = leftDown;
    }

    private void placeMove(Minecraft client, int x, int y) {
        positionX.set(clampX(client, x));
        positionY.set(clampY(client, y));
        moving = false;
        wasLeftDown = false;
        client.mouseHandler.grabMouse();
        ConfigManager.save();
    }

    private int clampX(Minecraft client, int x) {
        return Math.max(4, Math.min(client.getWindow().getGuiScaledWidth() - 60, x));
    }

    private int clampY(Minecraft client, int y) {
        return Math.max(4, Math.min(client.getWindow().getGuiScaledHeight() - 20, y));
    }

    public void cancelMove(Minecraft client) {
        if (!moving) {
            return;
        }
        positionX.set(startX);
        positionY.set(startY);
        moving = false;
        wasLeftDown = false;
        if (client.gui.screen() == null && !client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.grabMouse();
        }
    }

    @Override
    public void onDisable() {
        if (moving) {
            cancelMove(Minecraft.getInstance());
        }
    }
}
