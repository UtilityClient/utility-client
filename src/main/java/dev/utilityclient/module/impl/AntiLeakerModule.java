package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a small resource pack that paints the given blocks with the netherite block
 * texture, so blocks that give away what someone is doing underground can be told apart
 * at a glance. It is a plain client side texture pack, no packets are touched.
 */
public final class AntiLeakerModule extends Module {
    private static final String PACK_NAME = "utilityclient-anti-leaker";
    private static final String PACK_FILE = "utilityclient-anti-leaker.zip";
    private static final String SOURCE_TEXTURE = "assets/minecraft/textures/block/netherite_block.png";

    /**
     * Blocks painted with the netherite block texture. Deepslate, tuff, gravel and
     * cobbled deepslate are the blocks that report a nearby miner, and every deepslate ore
     * is included so ores stop standing out on their own.
     */
    private static final String[] TARGETS = {
            "deepslate",
            "deepslate_top",
            "cobbled_deepslate",
            "polished_deepslate",
            "deepslate_bricks",
            "cracked_deepslate_bricks",
            "deepslate_tiles",
            "cracked_deepslate_tiles",
            "chiseled_deepslate",
            "tuff",
            "tuff_bricks",
            "chiseled_tuff",
            "polished_tuff",
            "chiseled_tuff_bricks",
            "gravel",
            "bedrock",
            "deepslate_coal_ore",
            "deepslate_iron_ore",
            "deepslate_copper_ore",
            "deepslate_gold_ore",
            "deepslate_redstone_ore",
            "deepslate_emerald_ore",
            "deepslate_lapis_ore",
            "deepslate_diamond_ore"
    };

    /**
     * The ordinary stone family, which is just as common to mine through as deepslate.
     */
    private static final String[] STONE_FAMILY = {
            "stone",
            "andesite",
            "diorite",
            "granite",
            "cobblestone",
            "mossy_cobblestone",
            "smooth_stone",
            "stone_bricks",
            "mossy_stone_bricks",
            "cracked_stone_bricks",
            "chiseled_stone_bricks",
            "polished_andesite",
            "polished_diorite",
            "polished_granite",
            "calcite",
            "tuff_calcite"
    };

    public final ModuleSetting<Boolean> showStatus;
    public final ModuleSetting<Boolean> deepslateOres;
    public final ModuleSetting<Boolean> stoneFamily;

    public AntiLeakerModule() {
        super("anti-leaker", "Anti Leaker",
                "Repaints leaky blocks with the netherite texture so they can be spotted.",
                ModuleCategory.DONUTSMP, false, true, false);
        deepslateOres = addSetting(ModuleSetting.booleanSetting("ores", "Include deepslate ores",
                "Also paint every deepslate ore so ores do not stand out on their own.", true));
        stoneFamily = addSetting(ModuleSetting.booleanSetting("stone-family", "Include stone family",
                "Also paint stone, andesite, diorite, granite, cobblestone, stone bricks and calcite.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when the pack is applied.", true));
    }

    @Override
    public void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return;
        }
        apply(client, true);
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return;
        }
        apply(client, false);
    }

    /**
     * Writes the pack if needed and switches it in or out of the enabled list.
     */
    private void apply(Minecraft client, boolean enable) {
        List<String> enabled = client.options.resourcePacks;
        if (enabled == null) {
            return;
        }
        try {
            boolean present = enabled.contains(PACK_NAME);
            if (enable) {
                Path pack = buildPack();
                if (!present) {
                    enabled.add(0, PACK_NAME);
                } else {
                    // keep it at the top so our textures win over other packs
                    enabled.remove(PACK_NAME);
                    enabled.add(0, PACK_NAME);
                }
                client.options.save();
                client.reloadResourcePacks();
                if (showStatus.value()) {
                    tell(client, "Anti Leaker on: " + targets().size() + " textures painted, pack format "
                            + packFormat() + ". Your resource packs reloaded.");
                }
            } else if (present) {
                enabled.remove(PACK_NAME);
                client.options.save();
                client.reloadResourcePacks();
                if (showStatus.value()) {
                    tell(client, "Anti Leaker off: pack removed and resource packs reloaded.");
                }
            } else if (showStatus.value()) {
                tell(client, "Anti Leaker off.");
            }
        } catch (Exception exception) {
            System.err.println("[Utility Client] Anti Leaker failed: " + exception);
            tell(client, "Anti Leaker could not apply the pack: " + exception.getMessage());
        }
    }

    /**
     * The module can be switched on from the config before a world is joined, so the local
     * player may not exist yet.
     */
    private void tell(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private int packFormat() {
        return SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;
    }

    private List<String> targets() {
        List<String> result = new ArrayList<>();
        for (String target : TARGETS) {
            if (target.contains("_ore") && !deepslateOres.value()) {
                continue;
            }
            result.add(target);
        }
        if (stoneFamily.value()) {
            result.addAll(List.of(STONE_FAMILY));
        }
        return result;
    }

    /**
     * Writes the pack next to the game so the resource pack screen can see it.
     */
    private Path buildPack() throws IOException {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        Files.createDirectories(directory);
        Path pack = directory.resolve(PACK_FILE);

        try (OutputStream output = Files.newOutputStream(pack);
             ZipOutputStream zip = new ZipOutputStream(output)) {

            int major = SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;
            int minor = SharedConstants.RESOURCE_PACK_FORMAT_MINOR;
            // Modern Minecraft wants min_format and max_format as {major, minor} objects
            // placed directly in the pack object. The old nested supported_formats form is
            // only understood for pack formats up to 64 and makes the pack unusable after.
            writeEntry(zip, "pack.mcmeta",
                    "{\"pack\":{"
                            + "\"description\":\"Utility Client Anti Leaker\","
                            + "\"pack_format\":" + major + ","
                            + "\"min_format\":{\"major\":" + major + ",\"minor\":" + minor + "},"
                            + "\"max_format\":{\"major\":" + major + ",\"minor\":" + minor + "}"
                            + "}}");

            byte[] texture = readSourceTexture();
            // Collected through a set so a face variant that is also listed explicitly,
            // such as deepslate_top, is never written to the zip twice.
            Set<String> painted = new LinkedHashSet<>();
            for (String target : targets()) {
                painted.addAll(withFaceVariants(target));
            }
            for (String name : painted) {
                zip.putNextEntry(new ZipEntry("assets/minecraft/textures/block/" + name + ".png"));
                zip.write(texture);
                zip.closeEntry();
            }
        }
        return pack;
    }

    /**
     * A block model may point its top and bottom at a separate texture, so those are
     * painted as well when the game has that texture.
     */
    private List<String> withFaceVariants(String target) {
        List<String> names = new ArrayList<>();
        names.add(target);
        for (String suffix : new String[]{"_top", "_bottom", "_side"}) {
            String candidate = target + suffix;
            if (hasTexture(candidate)) {
                names.add(candidate);
            }
        }
        return names;
    }

    private static final java.util.Set<String> KNOWN_TEXTURES = loadKnownTextures();

    private static java.util.Set<String> loadKnownTextures() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String target : TARGETS) {
            names.add(target);
        }
        for (String target : STONE_FAMILY) {
            names.add(target);
        }
        return names;
    }

    private boolean hasTexture(String name) {
        return KNOWN_TEXTURES.contains(name)
                || AntiLeakerModule.class.getClassLoader()
                .getResource("assets/minecraft/textures/block/" + name + ".png") != null;
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private byte[] readSourceTexture() throws IOException {
        try (InputStream stream = AntiLeakerModule.class
                .getClassLoader()
                .getResourceAsStream(SOURCE_TEXTURE)) {
            if (stream == null) {
                throw new IOException("Could not read " + SOURCE_TEXTURE + " from the game jar");
            }
            return stream.readAllBytes();
        }
    }
}
