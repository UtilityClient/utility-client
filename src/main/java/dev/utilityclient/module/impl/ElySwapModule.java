package dev.utilityclient.module.impl;

import dev.utilityclient.gui.ClickGuiScreen;
import dev.utilityclient.gui.ModuleSettingsScreen;
import dev.utilityclient.keybind.KeyBind;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.HotbarMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * One key to swap between an elytra and a chestplate, both in your hotbar.
 *
 * <p>Press it and the elytra comes into your hand, press it again and the chestplate comes
 * back. It works out which way round to go from what is in your hand, so it does not matter
 * what you happened to be holding when you started.
 *
 * <p>This only changes the hotbar selection. It picks up nothing, moves nothing and never
 * writes to an item stack, which is the same approach the pearl and wind charge macros use
 * and for the same reason: selecting a slot is the one inventory action the server already
 * agrees with and that cannot lose an item.
 *
 * <p>That is a deliberate change from an earlier version of this module, which tried to
 * actually equip the elytra by clicking the chest armour slot. Equipping cannot be done with
 * a quick move, because the game only auto equips when the armour slot is empty, and doing it
 * properly means putting an item on the cursor. The cursor version worked by accident of
 * timing and would leave an item hanging there if anything went wrong. Selecting the hotbar
 * slot is instant, cannot fail halfway, and cannot eat anything.
 *
 * <p>Both the elytra and the chestplate need to be in the hotbar, since a slot further back
 * cannot be selected without dragging the item along first.
 */
public final class ElySwapModule extends Module {
    public final ModuleSetting<KeyBind> activateKey;
    public final ModuleSetting<Boolean> alsoUseModuleKey;
    public final ModuleSetting<String> returnBehaviour;
    public final ModuleSetting<Integer> returnDelay;
    public final ModuleSetting<Boolean> showStatus;

    /** One instance per module, so this module's return cannot clobber Mace Swap's. */
    private final HotbarMemory memory = new HotbarMemory();

    public ElySwapModule() {
        super("ely-swap", "Ely Swap",
                "Swaps between the elytra and chestplate in your hotbar with one key.",
                ModuleCategory.MOVEMENT, false, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "The key that swaps, while the module stays switched on.", new KeyBind()));
        alsoUseModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind swap as well, not just switch it on.", true));
        returnBehaviour = addSetting(ModuleSetting.modeSetting("return", "Switch back",
                "How to go back to your previous item. \"On next press\" is the simplest and "
                        + "matches the pearl macros: one press to swap, one to swap back.",
                "On next press", "On next press", "On right click", "After a delay"));
        returnDelay = addSetting(ModuleSetting.integerSetting("return-delay", "Return delay",
                "Used when switch back is set to \"After a delay\". 1 tick is 50 milliseconds, "
                        + "20 is one second.", 1, 1, 40, 1));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it swaps or cannot find one.", true));
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            memory.forget();
            dev.utilityclient.util.UseState.clear();
            return;
        }
        // Changing the hotbar selection while a real menu is open would fight with whatever
        // the player is clicking. Our own menus are excluded, see realMenuOpen.
        if (realMenuOpen(client)) {
            dev.utilityclient.util.UseState.clear();
            return;
        }

        // A pending delayed return is handled first. Cancelling a pending return is also the
        // first thing a fresh swap does, so pressing again before it fires goes to the other
        // item rather than bouncing back to where you started.
        if (memory.tickReturn()) {
            if (memory.returnIfUnmoved(client) && showStatus.value()) {
                say(client, "Switched back to your previous item.");
            }
            return;
        }

        if (!keyPressed(client)) {
            return;
        }

        // Right click is the switch back when that is the chosen behaviour, and the activate
        // key is the swap in every case. Reading the use state rather than the key binding is
        // deliberate: the game consumes that binding itself before the tick runs, so polling
        // it would never see anything.
        if (rightClickWantsReturn(client) && memory.returnIfUnmoved(client)) {
            if (showStatus.value()) {
                say(client, "Switched back to your previous item.");
            }
            return;
        }

        swap(client);
    }

    @Override
    public void onDisable() {
        // Never leave a pending return armed, or switching the module off would fire a swap
        // later on.
        memory.forget();
        dev.utilityclient.util.UseState.clear();
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

    private boolean rightClickWantsReturn(Minecraft client) {
        return "On right click".equals(returnBehaviour.value())
                && dev.utilityclient.util.UseState.consume();
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
        if (alsoUseModuleKey.value() && keyBind().consumePress(client)) {
            return true;
        }
        warnAboutMissingKey(client);
        return false;
    }

    /* ---------------------------------------------------------------- swap */

    private void swap(Minecraft client) {
        // The return is resolved before anything else. Working it out after looking for the
        // target item meant a press meant to go back could fail with "no elytra in your
        // hotbar", because the search had already run and failed by then. Going back should
        // never depend on still owning the item you swapped to.
        if (memory.armed() && "On next press".equals(returnBehaviour.value())) {
            if (memory.returnIfUnmoved(client)) {
                if (showStatus.value()) {
                    say(client, "Switched back to your previous item.");
                }
                return;
            }
        }

        int current = client.player.getInventory().getSelectedSlot();
        boolean holdingElytra = isElytra(client.player.getInventory().getSelectedItem());

        // Holding the elytra means the chestplate is what you want back, and the other way
        // round. This is the same "do the opposite" rule the equipped version used, so the
        // key keeps meaning the same thing.
        int target = holdingElytra
                ? findChestplate(client)
                : findElytra(client);
        String what = holdingElytra ? "chestplate" : "elytra";

        if (target < 0) {
            if (showStatus.value()) {
                say(client, "No " + what + " in your hotbar.");
            }
            return;
        }

        // Swapping again drops any pending return, so a fresh swap is not immediately undone.
        memory.forget();
        if (!memory.swapTo(client, target)) {
            // Already holding it. The player chose this, so do not start a return that would
            // move them off a slot they are deliberately on.
            return;
        }
        if ("After a delay".equals(returnBehaviour.value())) {
            memory.returnAfter(returnDelay.value());
        }

        if (showStatus.value()) {
            say(client, "Swapped to " + what + ".");
        }
    }

    private static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ELYTRA;
    }

    private static int findElytra(Minecraft client) {
        return findHotbarSlot(client, Items.ELYTRA);
    }

    /**
     * The best chest armour in the hotbar, so swapping back does not downgrade you. A
     * netherite piece beats diamond, and so on down the list.
     */
    private static int findChestplate(Minecraft client) {
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

    private static int findHotbarSlot(Minecraft client, Item wanted) {
        int hotbar = Math.min(9, client.player.getInventory().getContainerSize());
        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == wanted) {
                return slot;
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
        if (isElytra(stack)) {
            return "elytra";
        }
        for (Item candidate : List.of(Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE,
                Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE,
                Items.COPPER_CHESTPLATE, Items.LEATHER_CHESTPLATE)) {
            if (!stack.isEmpty() && stack.getItem() == candidate) {
                return "chestplate";
            }
        }
        return stack.isEmpty() ? "empty hand" : "other item";
    }

    /** Kept for the HUD, which reports what is worn rather than held. */
    public String chestLabel() {
        return heldLabel().toLowerCase(Locale.ROOT);
    }

    public boolean keyBound() {
        return activateKey.value().bound();
    }
}
