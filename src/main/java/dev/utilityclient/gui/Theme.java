package dev.utilityclient.gui;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Accent colours shared by the menu screens, so the sidebar Theme option is a real setting
 * rather than a label that does nothing.
 */
public final class Theme {
    public record Preset(String name, int accent, int accentDim, int accentSoft) {
    }

    public static final Preset[] PRESETS = {
            new Preset("Purple", 0xFFD000FF, 0xFF8E2BB5, 0xFF2A1740),
            new Preset("Cyan", 0xFF00D5FF, 0xFF0A7E99, 0xFF0B2A33),
            new Preset("Rose", 0xFFFF4D8D, 0xFFA82A57, 0xFF33101E),
            new Preset("Lime", 0xFF9BFF3D, 0xFF5C9922, 0xFF1B2A0E),
    };

    private static int index;
    private static boolean loaded;

    private Theme() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("utility-client-theme.txt");
            if (Files.exists(file)) {
                index = Math.floorMod(Integer.parseInt(Files.readString(file).trim()), PRESETS.length);
            }
        } catch (IOException | RuntimeException ignored) {
            // A missing or malformed theme file just means the default accent.
        }
    }

    public static Preset current() {
        return PRESETS[Math.floorMod(index, PRESETS.length)];
    }

    public static int accent() {
        return current().accent();
    }

    public static int accentDim() {
        return current().accentDim();
    }

    public static int accentSoft() {
        return current().accentSoft();
    }

    public static String name() {
        return current().name();
    }

    /** Advances to the next preset and persists it. */
    public static void cycle() {
        index = Math.floorMod(index + 1, PRESETS.length);
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("utility-client-theme.txt");
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(Integer.toString(index));
            }
        } catch (IOException ignored) {
            // The choice still applies for this session even if it cannot be written.
        }
    }
}
