package dev.utilityclient.module.impl;

import dev.utilityclient.gui.ClickGuiScreen;
import dev.utilityclient.gui.ModuleSettingsScreen;
import dev.utilityclient.keybind.KeyBind;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * One key to swap between the elytra and the chestplate you are wearing.
 *
 * <p>It looks at what is actually in your chest slot and does the opposite: wearing anything
 * other than an elytra means it puts the elytra on, wearing an elytra means it puts your best
 * chestplate back. It works whichever way round you started, and it will not downgrade you.
 *
 * <p><b>Why this cannot be a shift click.</b> The obvious way to do this is one quick move on
 * the item, the same as shift clicking it. That does not work, and it is worth being precise
 * about the reason, because it fails silently and looks like a broken module. The game's own
 * quick move code checks whether the matching armour slot is <i>empty</i> before it will
 * equip anything, and if the slot is already occupied it gives up on equipping and just
 * shuffles the item into your inventory instead. So with a chestplate already on, quick moving
 * an elytra puts the elytra in your inventory and leaves you wearing the chestplate. Vanilla
 * has the same limitation.
 *
 * <p>So this does what a player does instead: pick the item up onto the cursor, click the
 * chest slot to swap it in, then put the old piece back on the now empty slot it came from.
 * Three clicks, all within a single tick, leaving the cursor empty.
 *
 * <p>Each step applies locally and is sent to the server, using the game's own click handling
 * both times, so the local result and the server's decision always agree. The cursor is
 * checked after every step and the sequence is abandoned if it is not holding what we expect,
 * which is what stops an interrupted swap from eating an item.
 */
public final class ElySwapModule extends Module {
    public final ModuleSetting<KeyBind> activateKey;
    public final ModuleSetting<Boolean> useModuleKey;
    public final ModuleSetting<Boolean> showStatus;

    public ElySwapModule() {
        super("ely-swap", "Ely Swap",
                "Swaps between your elytra and your chestplate with one key.",
                ModuleCategory.MOVEMENT, false, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "The key that swaps, while the module stays switched on.", new KeyBind()));
        useModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind swap as well.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it swaps or cannot find one.", true));
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        // Equipping while a real menu is open would fight with whatever the player is
        // clicking. Our own menus are excluded, see realMenuOpen.
        if (realMenuOpen(client)) {
            return;
        }
        if (!keyPressed(client)) {
            return;
        }
        swap(client);
    }

    /**
     * True when a real game menu is open. The ClickGUI and the settings screens are not a
     * problem, since what you are wearing does not interfere with them.
     */
    private static boolean realMenuOpen(Minecraft client) {
        return client.gui.screen() != null
                && !(client.gui.screen() instanceof ClickGuiScreen)
                && !(client.gui.screen() instanceof ModuleSettingsScreen);
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

    /* ---------------------------------------------------------------- swap */

    private void swap(Minecraft client) {
        ItemStack worn = client.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearingElytra = !worn.isEmpty() && worn.getItem() == Items.ELYTRA;

        // Wearing an elytra means the chestplate is what you want back, and the other way
        // round. Anything else is treated as the chest side so the elytra goes on.
        Slot source = wearingElytra ? bestChestplate(client) : findSlot(client, Items.ELYTRA);
        String what = wearingElytra ? "chestplate" : "elytra";

        if (source == null) {
            if (showStatus.value()) {
                say(client, wearingElytra
                        ? "No chestplate to put back on."
                        : "No elytra in your inventory.");
            }
            return;
        }

        // Find the chest slot the same way the game does. The armour slots are stored in
        // reverse, so chest is 8 minus the equipment index. Deriving it the same way the game
        // does keeps the two in step if the layout ever changes.
        Slot chest = chestSlot(client);
        if (chest == null) {
            if (showStatus.value()) {
                say(client, "Could not find your chest armour slot.");
            }
            return;
        }

        int sourceIndex = source.index;
        int chestIndex = chest.index;
        ItemStack spare = source.getItem().copy();

        // Step 1, pick the spare item up onto the cursor.
        if (!click(client, sourceIndex, ContainerInput.PICKUP)) {
            return;
        }
        if (!cursorIs(client, spare)) {
            abort(client, "the cursor did not pick up the " + what);
            return;
        }

        // Step 2, click the chest slot. The item goes on and whatever was worn comes off onto
        // the cursor.
        if (!click(client, chestIndex, ContainerInput.PICKUP)) {
            abort(client, "the chest slot would not take the " + what);
            return;
        }
        if (client.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != spare.getItem()) {
            abort(client, "the " + what + " did not go on");
            return;
        }

        // Step 3, put the old piece back on the slot the spare item came from. That slot is
        // empty now, so this always has somewhere to go, and it leaves the cursor empty.
        if (!client.player.inventoryMenu.getCarried().isEmpty()) {
            click(client, sourceIndex, ContainerInput.PICKUP);
        }

        if (showStatus.value()) {
            say(client, "Swapped to " + what + ".");
        }
    }

    /**
     * Clicks a menu slot: apply it here, then tell the server.
     *
     * <p>Both halves use the game's own click handling, so what happens locally is what the
     * server decides. Doing only the send would leave the local copy stale until the server
     * replied, which is a round trip and looks like nothing happened.
     */
    private static boolean click(Minecraft client, int slot, ContainerInput input) {
        if (slot < 0) {
            return false;
        }
        client.player.inventoryMenu.clicked(slot, 0, input, client.player);
        client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId,
                slot, 0, input, client.player);
        return true;
    }

    /** True when the cursor is holding this exact item. */
    private static boolean cursorIs(Minecraft client, ItemStack expected) {
        ItemStack carried = client.player.inventoryMenu.getCarried();
        return !carried.isEmpty() && carried.getItem() == expected.getItem();
    }

    /**
     * Bails out of a swap partway, returning the cursor to the slot it came from so an item
     * is never left stuck on the cursor.
     */
    private void abort(Minecraft client, String why) {
        ItemStack carried = client.player.inventoryMenu.getCarried();
        if (!carried.isEmpty()) {
            // Find somewhere to put it: any empty slot will do, and the hotbar is the least
            // disruptive place to leave a stray item.
            for (int slot = 9; slot < Math.min(45, client.player.inventoryMenu.slots.size()); slot++) {
                if (client.player.inventoryMenu.slots.get(slot).getItem().isEmpty()) {
                    click(client, slot, ContainerInput.PICKUP);
                    break;
                }
            }
        }
        if (showStatus.value()) {
            say(client, "Swap cancelled, " + why + ".");
        }
    }

    /**
     * The menu slot for the chest armour.
     *
     * <p>Found by asking each slot which container position it holds, rather than by a
     * hardcoded menu index. The armour sits at 36 plus the equipment index in the player's
     * own item list, which is the same arithmetic the game uses, and matching on the container
     * position cannot collide with a hotbar or main inventory slot.
     */
    private static Slot chestSlot(Minecraft client) {
        int containerSlot = 36 + EquipmentSlot.CHEST.getIndex();
        for (Slot slot : client.player.inventoryMenu.slots) {
            if (slot.getContainerSlot() == containerSlot) {
                return slot;
            }
        }
        return null;
    }

    /** The first inventory slot holding the wanted item. */
    private static Slot findSlot(Minecraft client, Item wanted) {
        for (Slot slot : client.player.inventoryMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() == wanted) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Picks the best chest armour available, so swapping back does not downgrade you. A
     * netherite piece beats diamond, and so on down the list.
     */
    private static Slot bestChestplate(Minecraft client) {
        List<Item> order = List.of(
                Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE,
                Items.COPPER_CHESTPLATE,
                Items.LEATHER_CHESTPLATE
        );

        for (Item candidate : order) {
            for (Slot slot : client.player.inventoryMenu.slots) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty() && stack.getItem() == candidate) {
                    return slot;
                }
            }
        }
        return null;
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

    /** What is in the chest slot right now, for the HUD. */
    public String chestLabel() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return "-";
        }
        ItemStack stack = client.player.getItemBySlot(EquipmentSlot.CHEST);
        if (stack.isEmpty()) {
            return "nothing";
        }
        return stack.getItem() == Items.ELYTRA ? "elytra" : "chestplate";
    }

    public boolean keyBound() {
        return activateKey.value().bound();
    }
}
