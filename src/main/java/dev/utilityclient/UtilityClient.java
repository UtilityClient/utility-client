package dev.utilityclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilityclient.config.ConfigManager;
import dev.utilityclient.gui.ClickGuiScreen;
import dev.utilityclient.gui.LicenseScreen;
import dev.utilityclient.gui.Theme;
import dev.utilityclient.license.LicenseManager;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.BlockHighlightModule;
import dev.utilityclient.module.impl.CustomBreakAnimationModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class UtilityClient implements ClientModInitializer {
    public static final String MOD_ID = "utilityclient";
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(id("general"));

    private static KeyMapping openGuiKey;
    private static long sessionStart;
    private static boolean configLoaded;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static long sessionSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - sessionStart) / 1000L);
    }

    @Override
    public void onInitializeClient() {
        sessionStart = System.currentTimeMillis();
        Theme.load();
        LicenseManager.load();
        ModuleManager.init();

        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.utilityclient.open_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_INSERT,
                KEY_CATEGORY
        ));

        HudElementRegistry.addLast(id("hud"), (graphics, deltaTracker) -> HudOverlay.render(graphics));

        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) ->
                !ModuleManager.get().isEnabled("block-highlight"));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Module module = ModuleManager.get().find("block-highlight");
            if (module instanceof BlockHighlightModule highlight) {
                highlight.render(context, context.levelState().blockOutlineRenderState);
            }
            Module breakModule = ModuleManager.get().find("custom-break-animation");
            if (breakModule instanceof CustomBreakAnimationModule breakAnimation) {
                breakAnimation.render(context);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!configLoaded && client.options != null) {
                ConfigManager.load();
                configLoaded = true;
            }
            LicenseManager.tick();

            // The menu is only reachable with a working licence. Modules themselves stay
            // registered either way so an expired key can be renewed without a restart.
            while (openGuiKey.consumeClick()) {
                client.gui.setScreen(LicenseManager.isLicensed()
                        ? new ClickGuiScreen()
                        : new LicenseScreen());
            }
            if (LicenseManager.isLicensed()) {
                ModuleManager.get().tick(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("utility").executes(context -> {
                    context.getSource().getClient().gui.setScreen(LicenseManager.isLicensed()
                            ? new ClickGuiScreen()
                            : new LicenseScreen());
                    return 1;
                }))
        );

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("utilitykey").executes(context -> {
                    context.getSource().getClient().gui.setScreen(new LicenseScreen());
                    return 1;
                }))
        );
    }
}
