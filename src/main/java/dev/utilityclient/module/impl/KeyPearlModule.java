package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Press one key to throw a pearl and put your previous item straight back.
 *
 * <p>It is a convenience, not automation. The module never aims, never times the throw and
 * never picks the target. It swaps to the pearl, makes the one right click you would have
 * made, waits for the pearl to actually leave your hand, then swaps back to whatever you
 * were holding. No pearl in your hotbar means it does nothing at all.
 *
 * <p>The swap back waits for the pearl count in that slot to actually drop. Returning the
 * instant the click was sent would put a different item in your hand before the throw had
 * registered, which is exactly the kind of timing bug that eats items.
 */
public final class KeyPearlModule extends Module {
    private enum Stage {
        IDLE,
        SWITCHING,
        WAITING_FOR_THROW
    }

    public final ModuleSetting<Integer> switchDelay;
    public final ModuleSetting<Integer> returnDelay;
    public final ModuleSetting<Integer> timeout;
    public final ModuleSetting<Boolean> searchInventory;
    public final ModuleSetting<Boolean> showStatus;

    private Stage stage = Stage.IDLE;
    private int returnSlot = -1;
    private int pearlSlot = -1;
    private int pearlCountBefore;
    private int timer;

    public KeyPearlModule() {
        super("key-pearl", "Key Pearl",
                "Uses this module's keybind to throw a pearl and swap straight back to what you were holding.",
                ModuleCategory.MOVEMENT, false, true, false);

        switchDelay = addSetting(ModuleSetting.integerSetting("switch-delay", "Switch delay",
                "Ticks between selecting the pearl and throwing it.", 1, 0, 20, 1));
        returnDelay = addSetting(ModuleSetting.integerSetting("return-delay", "Return delay",
                "Extra ticks after the throw registers before swapping back.", 0, 0, 20, 1));
        timeout = addSetting(ModuleSetting.integerSetting("timeout", "Timeout",
                "Gives up and swaps your item back if the throw never registers.", 40, 5, 200, 5));
        searchInventory = addSetting(ModuleSetting.booleanSetting("search-inventory", "Search inventory",
                "Look through your whole inventory, not just the hotbar. Slower.", false));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it cannot find a pearl.", true));
    }

    @Override
    public void onDisable() {
        // Switched off mid throw: hand the player their item back rather than leaving them
        // holding a pearl they did not pick.
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
            case IDLE -> {
                if (keyBind().consumePress(client)) {
                    begin(client);
                }
            }
            case SWITCHING -> {
                if (timer-- > 0) {
                    return;
                }
                throwPear(client);
            }
            case WAITING_FOR_THROW -> {
                timer++;
                if (pearlHasLeftHand(client)) {
                    if (timer < returnDelay.value()) {
                        return;
                    }
                    restore(client);
                    reset();
                    return;
                }
                if (timer >= timeout.value()) {
                    if (showStatus.value()) {
                        say(client, "The throw did not register, putting your item back.");
                    }
                    restore(client);
                    reset();
                }
            }
            default -> reset();
        }
    }

    /* ---------------------------------------------------------------- steps */

    private void begin(Minecraft client) {
        int found = findPearlSlot(client);
        if (found < 0) {
            if (showStatus.value()) {
                say(client, "No ender pearl in your "
                        + (searchInventory.value() ? "inventory" : "hotbar") + ".");
            }
            return;
        }

        int current = client.player.getInventory().getSelectedSlot();
        if (current == found) {
            // Already holding a pearl, so there is nothing to swap and nothing to undo.
            if (showStatus.value()) {
                say(client, "You already have a pearl in hand.");
            }
            return;
        }

        pearlSlot = found;
        returnSlot = current;
        pearlCountBefore = countIn(client, found);
        timer = switchDelay.value();
        stage = Stage.SWITCHING;
        select(client, found);
    }

    private void throwPear(Minecraft client) {
        if (pearlSlot < 0 || client.player == null) {
            reset();
            return;
        }
        select(client, pearlSlot);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        stage = Stage.WAITING_FOR_THROW;
        timer = 0;
    }

    private void restore(Minecraft client) {
        if (client.player == null || returnSlot < 0) {
            return;
        }
        if (returnSlot >= 0 && returnSlot < 9) {
            select(client, returnSlot);
        }
    }

    private void select(Minecraft client, int slot) {
        if (slot < 0 || slot > 8) {
            return;
        }
        client.player.getInventory().setSelectedSlot(slot);
        // The inventory change is local only, so the server has to be told separately.
        // Without this the server still thinks the old item is held and would throw that.
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private boolean pearlHasLeftHand(Minecraft client) {
        if (pearlSlot < 0) {
            return true;
        }
        return countIn(client, pearlSlot) < pearlCountBefore;
    }

    private static int countIn(Minecraft client, int slot) {
        ItemStack stack = client.player.getInventory().getItem(slot);
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    /* ---------------------------------------------------------------- lookup */

    private int findPearlSlot(Minecraft client) {
        int total = client.player.getInventory().getContainerSize();
        int limit = searchInventory.value() ? total : Math.min(9, total);
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == Items.ENDER_PEARL) {
                return slot;
            }
        }
        return -1;
    }

    /* ---------------------------------------------------------------- state */

    private void reset() {
        stage = Stage.IDLE;
        returnSlot = -1;
        pearlSlot = -1;
        pearlCountBefore = 0;
        timer = 0;
    }

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Key Pearl] " + message));
        }
    }

    /* ---------------------------------------------------------------- read */

    public boolean active() {
        return stage != Stage.IDLE;
    }

    public String stateLabel() {
        return switch (stage) {
            case SWITCHING -> "switching to pearl";
            case WAITING_FOR_THROW -> "waiting for throw";
            case IDLE -> "idle";
        };
    }

    public int pearlCount() {
        if (pearlSlot < 0) {
            return 0;
        }
        ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(pearlSlot);
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    public int heldSlot() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? 0 : client.player.getInventory().getSelectedSlot();
    }
}
