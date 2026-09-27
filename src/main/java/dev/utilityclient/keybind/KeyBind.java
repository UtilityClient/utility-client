package dev.utilityclient.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * A single key binding that can be driven by a keyboard key or a mouse button.
 * <p>
 * Press detection is polled straight from GLFW on the client thread, so mouse buttons
 * behave exactly like keyboard keys and both work in game and in menus.
 */
public final class KeyBind {
    private InputConstants.Key key;
    private boolean wasDown;

    public KeyBind() {
        this(InputConstants.UNKNOWN);
    }

    public KeyBind(InputConstants.Key key) {
        this.key = key == null ? InputConstants.UNKNOWN : key;
    }

    public InputConstants.Key key() {
        return key;
    }

    public void set(InputConstants.Key key) {
        this.key = key == null ? InputConstants.UNKNOWN : key;
        this.wasDown = false;
    }

    public void setKeyboard(int keyCode) {
        set(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
    }

    public void setMouse(int button) {
        set(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    public void clear() {
        set(InputConstants.UNKNOWN);
    }

    public boolean bound() {
        return key.getValue() >= 0;
    }

    public boolean mouse() {
        return key.getType() == InputConstants.Type.MOUSE;
    }

    public boolean isDown(Minecraft client) {
        if (client == null || !bound()) {
            return false;
        }
        Window window = client.getWindow();
        if (window == null) {
            return false;
        }
        if (mouse()) {
            return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    /**
     * @return true only on the tick where the key or mouse button goes down
     */
    public boolean consumePress(Minecraft client) {
        boolean down = isDown(client);
        boolean pressed = down && !wasDown;
        wasDown = down;
        return pressed;
    }

    /**
     * Updates the internal state without reporting a press, used while a menu is open
     * so that a held key does not toggle a module the moment the menu closes.
     */
    public void sync(Minecraft client) {
        wasDown = isDown(client);
    }

    public String displayName() {
        if (!bound()) {
            return "NONE";
        }
        if (mouse()) {
            return "MOUSE " + (key.getValue() + 1);
        }
        String name = key.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        name = name.replace('_', ' ').trim().toUpperCase(Locale.ROOT);
        return name.isEmpty() ? "NONE" : name;
    }

    public String save() {
        if (!bound()) {
            return "none";
        }
        return (mouse() ? "m" : "k") + key.getValue();
    }

    public void load(String stored) {
        if (stored == null || stored.isEmpty() || "none".equalsIgnoreCase(stored)) {
            clear();
            return;
        }
        try {
            char kind = stored.charAt(0);
            int code = Integer.parseInt(stored.substring(1));
            set(kind == 'm' || kind == 'M'
                    ? InputConstants.Type.MOUSE.getOrCreate(code)
                    : InputConstants.Type.KEYSYM.getOrCreate(code));
        } catch (RuntimeException exception) {
            clear();
        }
    }
}
