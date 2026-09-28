package dev.utilityclient.module.impl;

import dev.utilityclient.gui.ClickGuiScreen;
import dev.utilityclient.gui.ModuleSettingsScreen;
import dev.utilityclient.keybind.KeyBind;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.HotbarMemory;
import dev.utilityclient.util.UseState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Puts an elytra, or a chestplate, straight into your hand.
 *
 * <p>Your activate key selects the item in the hotbar, and a right click puts back whatever you
 * were holding. That is the whole module: it moves the hotbar selection and nothing else, so
 * nothing is picked up, moved or consumed, and no item stack is ever written to.
 *
 * <p>Only hotbar slots are considered. Selecting an item further back would mean dragging it
 * along first, which is a different and much more intrusive thing to do, so an item that is not
 * in the hotbar is reported as not found rather than relocated.
 *
 * <p>The swap and the return are both hotbar selections, sent locally and then to the server,
 * so both sides agree on what is in hand.
 */
public final class ElySwapModule extends Module {
    public final ModuleSetting<KeyBind> activateKey;
    public final ModuleSetting<Boolean> useModuleKey;
    public final ModuleSetting<String> target;
    public final ModuleSetting<Boolean> returnOnRightClick;
    public final ModuleSetting<Integer> returnDelay;
    public final ModuleSetting<Boolean> skipIfHeld;
    public final ModuleSetting<Boolean> showStatus;

    public ElySwapModule() {
        super("ely-swap", "Ely Swap",
                "Swaps an elytra or chestplate into your hand with one key.",
                ModuleCategory.MOVEMENT, false, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "The key that swaps, while the module stays switched on.", new KeyBind()));
        useModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind swap as well.", true));
        target = addSetting(ModuleSetting.modeSetting("target", "Swap to",
                "Which item to put in your hand. Chestplate picks the best one you own, so "
                        + "swapping back never downgrades your armour.",
                "Elytra", "Elytra", "Chestplate"));
        returnOnRightClick = addSetting(ModuleSetting.booleanSetting("return-on-right-click",
                "Switch back on right click",
                "After swapping, a right click puts your original item back in hand. The game "
                        + "reports the right click through a mixin, so this fires even when the "
                        + "game has already used the item.", true));
        returnDelay = addSetting(ModuleSetting.integerSetting("return-delay", "Auto return delay",
                "If you never right click, switch back on your own after this many ticks. Set to "
                        + "0 to never return by itself.", 0, 0, 40, 1));
        skipIfHeld = addSetting(ModuleSetting.booleanSetting("skip-if-held", "Skip if already held",
                "Do nothing if the item you want is already in hand.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it swaps or cannot find one.", true));
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            HotbarMemory.forget();
            UseState.clear();
            return;
        }
        // Swapping the selection while a real menu is open would fight with whatever the
        // player is clicking. Our own menus are excluded, see realMenuOpen.
        if (realMenuOpen(client)) {
            UseState.clear();
            return;
        }

        // The return is checked first, so a right click lands even if the activate key is
        // also held this tick.
        if (returnOnRightClick.value() && UseState.consume()) {
            if (HotbarMemory.returnIfUnmoved(client) && showStatus.value()) {
                say(client, "Switched back to your previous item.");
            }
            return;
        }
        UseState.clear();

        if (HotbarMemory.ticksUntilReturn() > 0 && HotbarMemory.tickReturn()) {
            if (HotbarMemory.returnIfUnmoved(client) && showStatus.value()) {
                say(client, "Switched back to your previous item.");
            }
            return;
        }

        if (!keyPressed(client)) {
            return;
        }
        swap(client);
    }

    @Override
    public void onDisable() {
        HotbarMemory.forget();
        UseState.clear();
    }

    /**
     * Reads the activate key, falling back to the module keybind so the module is never a
     * no-op. The fallback is announced once rather than failing silently.
     */
    private boolean keyPressed(Minecraft client) {
        if (activateKey.value().bound()) {
            if (activateKey.value().consumePress(client)) {
                return true;
            }
            activateKey.value().sync(client);
            return false;
        }
        if (useModuleKey.value() && keyBind().consumePress(client)) {
            return true;
        }
        warnAboutMissingKey(client);
        return false;
    }

    /**
     * True when a real game menu is open. The ClickGUI and the settings screens are not a
     * problem, since which item you are holding does not interfere with them.
     */
    private static boolean realMenuOpen(Minecraft client) {
        return client.gui.screen() != null
                && !(client.gui.screen() instanceof ClickGuiScreen)
                && !(client.gui.screen() instanceof ModuleSettingsScreen);
    }

    /* ---------------------------------------------------------------- swap */

    private void swap(Minecraft client) {
        boolean wantsElytra = !"Chestplate".equals(target.value());
        int current = client.player.getInventory().getSelectedSlot();
        int slot = wantsElytra
                ? findSlot(client, Items.ELYTRA, List.of())
                : findBestChestplateSlot(client);
        String what = wantsElytra ? "elytra" : "chestplate";

        if (slot < 0) {
            if (showStatus.value()) {
                say(client, "No " + what + " in your hotbar.");
            }
            return;
        }

        if (slot == current && skipIfHeld.value()) {
            return;
        }

        // A second swap while a return is pending means the player wants to stay on the
        // item, so the old timer is dropped rather than firing against the new swap.
        HotbarMemory.forget();
        if (!HotbarMemory.swapTo(client, slot)) {
            return;
        }
        if (returnDelay.value() > 0) {
            HotbarMemory.returnAfter(returnDelay.value());
        }

        if (showStatus.value()) {
            say(client, "Swapped to " + what + ".");
        }
    }

    /** First hotbar slot holding the wanted item. */
    private static int findSlot(Minecraft client, Item wanted, List<Item> ignored) {
        int hotbar = Math.min(9, client.player.getInventory().getContainerSize());
        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.isEmpty() || ignored.contains(stack.getItem())) {
                continue;
            }
            if (stack.getItem() == wanted) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Picks the best chest armour in the hotbar, so swapping back does not downgrade you. A
     * netherite piece beats diamond, and so on down the list.
     */
    private int findBestChestplateSlot(Minecraft client) {
        List<Item> order = List.of(
                Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE,
                Items.COPPER_CHESTPLATE,
                Items.LEATHER_CHESTPLATE
        );

        int hotbar = Math.min(9, client.player.getInventory().getContainerSize());
        for (Item candidate : order) {
            for (int slot = 0; slot < hotbar; slot++) {
                ItemStack stack = client.player.getInventory().getItem(slot);
                if (!stack.isEmpty() && stack.getItem() == candidate) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /* ---------------------------------------------------------------- chat */

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Ely Swap] " + message));
        }
    }

    private boolean warnedAboutKey;

    /** Explains the missing activate key once, instead of failing silently forever. */
    private void warnAboutMissingKey(Minecraft client) {
        if (warnedAboutKey) {
            return;
        }
        warnedAboutKey = true;
        say(client, "No activate key set, using the module keybind. Bind an activate key in "
                + "the settings to use your own key instead.");
    }

    /* ---------------------------------------------------------------- read */

    /** What is in hand, for the HUD. */
    public String heldLabel() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return "-";
        }
        ItemStack stack = client.player.getInventory().getSelectedItem();
        if (stack.isEmpty()) {
            return "empty hand";
        }
        if (stack.getItem() == Items.ELYTRA) {
            return "elytra";
        }
        if (stack.getItem() == Items.NETHERITE_CHESTPLATE
                || stack.getItem() == Items.DIAMOND_CHESTPLATE
                || stack.getItem() == Items.IRON_CHESTPLATE
                || stack.getItem() == Items.CHAINMAIL_CHESTPLATE
                || stack.getItem() == Items.GOLDEN_CHESTPLATE
                || stack.getItem() == Items.COPPER_CHESTPLATE
                || stack.getItem() == Items.LEATHER_CHESTPLATE) {
            return "chestplate";
        }
        return "other item";
    }

    public boolean keyBound() {
        return activateKey.value().bound();
    }
}
