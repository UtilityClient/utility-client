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
 * <p>The swap is a single shift click, the same thing you would do by hand, so the game takes
 * the old armour back into your inventory as it goes on. There is no cursor involved, so there
 * is nothing to drop if a step is interrupted, and this module never writes to an item stack.
 *
 * <p>It feels instant because the click is applied locally first and then sent, rather than
 * waiting for the server to tell us what happened. Both halves use the game's own code, so the
 * local result matches what the server decides and the two never disagree.
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
                "Let the module's own on/off keybind swap as well.", true));
        searchInventory = addSetting(ModuleSetting.booleanSetting("search-inventory", "Search inventory",
                "Look through your whole inventory for the spare piece, not just the hotbar. "
                        + "A shift click works from anywhere, so this only changes how far it "
                        + "has to look, never whether it works.", true));
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
        Slot target = wearingElytra ? bestChestplate(client) : firstSlot(client, Items.ELYTRA);
        String what = wearingElytra ? "chestplate" : "elytra";

        if (target == null) {
            if (showStatus.value()) {
                say(client, wearingElytra
                        ? "No chestplate to put back on."
                        : "No elytra in your " + (searchInventory.value() ? "inventory" : "hotbar") + ".");
            }
            return;
        }

        // Two halves, both the game's own code. The local click is what makes the swap feel
        // instant, the packet is what makes the server agree. An earlier build sent only the
        // packet, so nothing appeared to happen until the server replied, which read as the
        // module simply being broken.
        int slot = target.index;
        client.player.inventoryMenu.clicked(slot, 0, ContainerInput.QUICK_MOVE, client.player);
        client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId,
                slot, 0, ContainerInput.QUICK_MOVE, client.player);

        if (showStatus.value()) {
            say(client, "Swapped to " + what + ".");
        }
    }

    /**
     * Finds the menu slot holding the wanted item.
     *
     * <p>This walks the menu's own slots and uses the index the menu gives, rather than
     * converting an inventory index by hand. The two numberings do not match, the hotbar being
     * offset by 36, and getting that wrong is completely silent: click the wrong index and the
     * game does nothing at all, which is what an earlier build did.
     */
    private Slot firstSlot(Minecraft client, Item wanted) {
        int limit = searchInventory.value() ? Integer.MAX_VALUE : 9;
        for (Slot slot : client.player.inventoryMenu.slots) {
            if (slot.index >= limit) {
                continue;
            }
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
    private Slot bestChestplate(Minecraft client) {
        List<Item> order = List.of(
                Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE,
                Items.COPPER_CHESTPLATE,
                Items.LEATHER_CHESTPLATE
        );

        int limit = searchInventory.value() ? Integer.MAX_VALUE : 9;
        for (Item candidate : order) {
            for (Slot slot : client.player.inventoryMenu.slots) {
                if (slot.index >= limit) {
                    continue;
                }
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
