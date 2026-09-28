package dev.utilityclient.module.impl;

import dev.utilityclient.keybind.KeyBind;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * One key to swap between an elytra and a chestplate.
 *
 * <p>Press once and it puts the elytra on, press again and it puts the chestplate back. It
 * looks at what is actually in your chest slot and moves whichever of the two is sitting
 * unused in your inventory, so it works whichever way round you started.
 *
 * <p>The swap is a single shift click on the item in your inventory, which is the same thing
 * you would do by hand. The game takes the old armour back into your inventory as it goes on.
 * There is no cursor involved, so there is nothing to drop if a step is interrupted, and this
 * module never writes to an item stack itself.
 *
 * <p>Only the elytra is treated as special. If you are wearing a netherite chestplate it will
 * put that back rather than forcing a leather one, which is almost always what you want.
 */
public final class ElySwapModule extends Module {
    public final ModuleSetting<KeyBind> activateKey;
    public final ModuleSetting<Boolean> useModuleKey;
    public final ModuleSetting<Boolean> searchInventory;
    public final ModuleSetting<Boolean> showStatus;

    public ElySwapModule() {
        super("ely-swap", "Ely Swap",
                "Swaps between your elytra and your chestplate with one key.",
                ModuleCategory.MOVEMENT, false, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "The key that swaps, while the module stays switched on.", new KeyBind()));
        useModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind swap as well.", false));
        searchInventory = addSetting(ModuleSetting.booleanSetting("search-inventory", "Search inventory",
                "Look through your whole inventory for the spare piece, not just the hotbar.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it cannot make the swap.", true));
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        // Equipping while a menu is open would fight with whatever the player is clicking.
        if (client.gui.screen() != null) {
            return;
        }

        boolean pressed = false;
        if (activateKey.value().bound() && activateKey.value().consumePress(client)) {
            pressed = true;
        } else {
            activateKey.value().sync(client);
        }
        if (!pressed && useModuleKey.value() && keyBind().consumePress(client)) {
            pressed = true;
        }
        if (!pressed) {
            return;
        }

        swap(client);
    }

    /* ---------------------------------------------------------------- swap */

    private void swap(Minecraft client) {
        ItemStack worn = client.player.getItemBySlot(EquipmentSlot.CHEST);
        Item wornItem = worn.isEmpty() ? null : worn.getItem();

        // Wearing an elytra means the chestplate is what you want back, and the other way
        // round. Anything else, such as a netherite chestplate, is treated as the chest
        // side so the elytra goes on.
        boolean wantsElytra = wornItem != Items.ELYTRA;
        Item wanted = wantsElytra ? Items.ELYTRA : null;

        // Going back to a chestplate: use whatever chest armour is lying around, preferring
        // the one that is already best, but never force a worse piece.
        int slot;
        String what;
        if (wanted != null) {
            slot = findSlot(client, wanted);
            what = "elytra";
        } else {
            slot = findBestChestplateSlot(client);
            what = "chestplate";
        }

        if (slot < 0) {
            if (showStatus.value()) {
                say(client, wantsElytra
                        ? "No elytra in your " + (searchInventory.value() ? "inventory" : "hotbar") + "."
                        : "No chestplate to put back on.");
            }
            return;
        }

        // Shift click. The game equips it and returns whatever was worn to the inventory.
        client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, slot, 0,
                ContainerInput.QUICK_MOVE, client.player);

        if (showStatus.value()) {
            say(client, "Swapped to " + what + ".");
        }
    }

    /**
     * Finds a slot holding the wanted item. The hotbar is searched first when the setting is
     * off, because a shift click works from anywhere so the setting only changes how fast
     * the search runs, never whether it works.
     */
    private int findSlot(Minecraft client, Item wanted) {
        int total = client.player.getInventory().getContainerSize();
        int limit = searchInventory.value() ? total : Math.min(9, total);

        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == wanted) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Picks the best chest armour available, so swapping back does not downgrade you. A
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
        int total = client.player.getInventory().getContainerSize();
        int limit = searchInventory.value() ? total : Math.min(9, total);

        for (Item candidate : order) {
            for (int slot = 0; slot < limit; slot++) {
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
