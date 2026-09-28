package dev.utilityclient.module.impl;

import dev.utilityclient.keybind.KeyBind;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.UseState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * One key to put on your elytra, or take it off again.
 *
 * <p>Press it and the elytra from your hotbar comes into your hand and is equipped with a
 * single right click. Press it again and the best chestplate you own is equipped instead. It
 * works out which way round to go from what you are currently wearing, so it does not matter
 * what you started with.
 *
 * <p>Equipping is done by right clicking, which is what a player does. Nothing here writes to
 * an item stack, moves an item between slots, or touches the cursor, so an interrupted swap
 * can cost you a wrong hotbar selection but never your inventory.
 *
 * <p>This is the same shape as the pearl and wind charge macros: select the item, use it, and
 * optionally put the previous item back. The difference is in how the result is confirmed.
 * Throwing a pearl shrinks the stack, so the macro can watch the count drop. Equipping does
 * not shrink anything, the item simply appears in the chest slot, so this watches for that
 * instead and gives up if it never happens rather than swapping back over a half finished
 * equip.
 */
public final class ElySwapModule extends Module {
    private enum Stage {
        IDLE,
        SWITCHING,
        WAITING_FOR_EQUIP
    }

    public final ModuleSetting<KeyBind> activateKey;
    public final ModuleSetting<String> switchBack;
    public final ModuleSetting<Integer> switchDelay;
    public final ModuleSetting<Integer> returnDelay;
    public final ModuleSetting<Integer> timeout;
    public final ModuleSetting<Boolean> alsoUseModuleKey;
    public final ModuleSetting<Boolean> showStatus;

    private Stage stage = Stage.IDLE;
    private int returnSlot = -1;
    private int targetSlot = -1;
    private Item targetItem;
    private int timer;

    public ElySwapModule() {
        super("ely-swap", "Ely Swap",
                "Swaps between your elytra and your chestplate with one key.",
                ModuleCategory.MOVEMENT, false, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "The key that swaps, while the module stays switched on. Mouse buttons work "
                        + "here too.", new KeyBind()));
        switchBack = addSetting(ModuleSetting.modeSetting("return", "Switch back",
                "Whether to put your previous item back in hand afterwards. \"After a delay\" "
                        + "needs no further input, the others wait for you.",
                "After a delay", "Never", "After a delay", "On next press", "On right click"));
        switchDelay = addSetting(ModuleSetting.integerSetting("switch-delay", "Switch delay",
                "Ticks between selecting the item and right clicking it. 0 does both in the "
                        + "same tick, which is the fastest.", 0, 0, 20, 1));
        returnDelay = addSetting(ModuleSetting.integerSetting("return-delay", "Return delay",
                "Used when switch back is set to \"After a delay\". Extra ticks to hold the "
                        + "item before putting your previous one back.", 2, 0, 20, 1));
        timeout = addSetting(ModuleSetting.integerSetting("timeout", "Timeout",
                "Gives up and puts your item back if the equip never registers, so the module "
                        + "cannot leave you stuck holding the wrong thing.", 20, 5, 100, 5));
        alsoUseModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind swap as well, not just switch it on.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it swaps or cannot find one.", true));
    }

    @Override
    public void onDisable() {
        // Switched off partway through: hand the item back rather than leaving the player
        // holding something they did not choose.
        restore(Minecraft.getInstance());
        reset();
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }
        // A menu being open means the player is doing something else entirely.
        if (client.gui.screen() != null) {
            if (stage != Stage.IDLE) {
                restore(client);
                reset();
            }
            return;
        }

        switch (stage) {
            case IDLE -> idle(client);
            case SWITCHING -> {
                if (timer-- > 0) {
                    return;
                }
                doEquip(client);
            }
            case WAITING_FOR_EQUIP -> waiting(client);
            default -> reset();
        }
    }

    /* ---------------------------------------------------------------- steps */

    private void idle(Minecraft client) {
        // Right click is the switch back when that is the chosen behaviour, and it is checked
        // before the activate key so a right click is never swallowed by a fresh swap.
        if (wantsReturnOnRightClick() && UseState.consume()) {
            if (returnSlot >= 0) {
                select(client, returnSlot);
                reset();
                if (showStatus.value()) {
                    say(client, "Switched back to your previous item.");
                }
                return;
            }
        }
        UseState.clear();

        // A pending return is resolved before looking for a new target, so pressing again to
        // go back can never fail on a missing item.
        if (returnSlot >= 0 && "On next press".equals(switchBack.value())) {
            select(client, returnSlot);
            reset();
            if (showStatus.value()) {
                say(client, "Switched back to your previous item.");
            }
            return;
        }

        boolean pressed = false;
        if (activateKey.value().bound() && activateKey.value().consumePress(client)) {
            pressed = true;
        } else {
            activateKey.value().sync(client);
        }
        if (!pressed && alsoUseModuleKey.value() && keyBind().consumePress(client)) {
            pressed = true;
        }
        if (!pressed) {
            return;
        }
        begin(client);
    }

    private void begin(Minecraft client) {
        // Wearing an elytra means the chestplate is what you want back, and the other way
        // round. Anything else is treated as the chest side so the elytra goes on.
        boolean wearingElytra = wornChest() == Items.ELYTRA;
        Item wanted = wearingElytra ? null : Items.ELYTRA;

        int found = wanted != null ? findSlot(client, wanted) : findChestplate(client);
        if (found < 0) {
            if (showStatus.value()) {
                say(client, wanted != null
                        ? "No elytra in your hotbar."
                        : "No chestplate in your hotbar.");
            }
            return;
        }

        int current = client.player.getInventory().getSelectedSlot();
        targetSlot = found;
        targetItem = wanted != null
                ? wanted
                : client.player.getInventory().getItem(found).getItem();

        if (current == found) {
            // Already in hand. Equip it straight away and put nothing back afterwards, since
            // the player chose to be holding it. returnSlot stays -1 so restore does nothing.
            returnSlot = -1;
            if (switchDelay.value() == 0) {
                doEquip(client);
            } else {
                timer = switchDelay.value();
                stage = Stage.SWITCHING;
            }
            return;
        }

        returnSlot = current;
        timer = switchDelay.value();
        stage = Stage.SWITCHING;
        // Packets go out in the order they are queued, so selecting and then using in the
        // same tick is safe. The server sees the item change before the equip.
        select(client, found);
    }

    /** Selects the item and right clicks it, which is what equips an elytra. */
    private void doEquip(Minecraft client) {
        if (targetSlot < 0 || client.player == null) {
            reset();
            return;
        }
        select(client, targetSlot);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        stage = Stage.WAITING_FOR_EQUIP;
        timer = 0;
    }

    private void waiting(Minecraft client) {
        // Checked before the counter moves, so an equip that already registered does not cost
        // an extra tick of holding the item.
        if (equipped()) {
            if (timer < returnDelay.value()) {
                timer++;
                return;
            }
            finishWithReturn(client, true);
            return;
        }
        timer++;
        if (timer >= timeout.value()) {
            if (showStatus.value()) {
                say(client, "The equip did not register, putting your item back.");
            }
            finishWithReturn(client, false);
        }
    }

    /**
     * Puts the previous item back when the setting says to, then clears the state.
     *
     * @param say whether to report a successful swap
     */
    private void finishWithReturn(Minecraft client, boolean report) {
        if (returnSlot >= 0 && !"Never".equals(switchBack.value())) {
            select(client, returnSlot);
        }
        if (report && showStatus.value()) {
            say(client, "Swapped to " + (targetItem == null ? "chestplate" : "elytra") + ".");
        }
        reset();
    }

    private void restore(Minecraft client) {
        if (client.player != null && returnSlot >= 0 && returnSlot < 9) {
            select(client, returnSlot);
        }
    }

    private boolean wantsReturnOnRightClick() {
        return "On right click".equals(switchBack.value());
    }

    /* ---------------------------------------------------------------- checks */

    /** True when the wanted item is now in the chest armour slot. */
    private boolean equipped() {
        return wornChest() == targetItem;
    }

    private static Item wornChest() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        ItemStack stack = client.player.getItemBySlot(EquipmentSlot.CHEST);
        return stack.isEmpty() ? null : stack.getItem();
    }

    /* ---------------------------------------------------------------- lookup */

    private int findSlot(Minecraft client, Item wanted) {
        int hotbar = Math.min(9, client.player.getInventory().getContainerSize());
        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == wanted) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * The best chest armour in the hotbar, so swapping back does not downgrade you. A
     * netherite piece beats diamond, and so on down the list.
     */
    private int findChestplate(Minecraft client) {
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

    private static void select(Minecraft client, int slot) {
        if (slot < 0 || slot > 8) {
            return;
        }
        client.player.getInventory().setSelectedSlot(slot);
        // The inventory change is local only, so the server has to be told separately.
        // Without this the server still thinks the old item is held and equips that instead.
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private void reset() {
        stage = Stage.IDLE;
        returnSlot = -1;
        targetSlot = -1;
        targetItem = null;
        timer = 0;
    }

    /* ---------------------------------------------------------------- chat */

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Ely Swap] " + message));
        }
    }

    /* ---------------------------------------------------------------- read */

    public boolean active() {
        return stage != Stage.IDLE;
    }

    public String stateLabel() {
        return switch (stage) {
            case SWITCHING -> "switching";
            case WAITING_FOR_EQUIP -> "equipping";
            case IDLE -> "idle";
        };
    }

    /** What is worn on the chest, for the HUD. */
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

    /** Lower cased for the HUD, which mixes this with other labels. */
    public String heldLabel() {
        return chestLabel().toLowerCase(Locale.ROOT);
    }
}
