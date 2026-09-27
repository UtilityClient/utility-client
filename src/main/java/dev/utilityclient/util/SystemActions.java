package dev.utilityclient.util;

import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Small wrappers for the few desktop actions the menu offers.
 *
 * <p>Minecraft's own helper for opening links is not available on this version, and the
 * clipboard is reached through the keyboard handler, so both paths are wrapped here with a
 * fallback and never throw at the call site.
 */
public final class SystemActions {
    private SystemActions() {
    }

    /**
     * Tries to open a link in the default browser.
     *
     * @return true if the browser was launched, false if the link was copied instead
     */
    public static boolean openLink(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI.create(url));
                    return true;
                }
            }
        } catch (Exception | LinkageError ignored) {
            // Headless, sandboxed or no browser; fall through to the clipboard.
        }
        copyToClipboard(url);
        return false;
    }

    /**
     * Tries to reveal a folder in the system file browser.
     *
     * @return true if a folder was opened, false if the path was copied instead
     */
    public static boolean openFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return false;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(folder.toFile());
                    return true;
                }
            }
        } catch (Exception | LinkageError ignored) {
            // Fall through to the clipboard.
        }
        copyToClipboard(folder.toString());
        return false;
    }

    public static boolean copyToClipboard(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            return true;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }
}
