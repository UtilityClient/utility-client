package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shared behaviour for the one key throw macros: swap to a throwable item, make a single
 * right click, wait for the stack to actually shrink, then hand the previous item back.
 *
 * <p>It is a convenience, not automation. Nothing here aims, times the throw against the
 * world, or picks a target. It makes the one click you would have made and puts your
 * previous item back afterwards.
 *
 * <p>Two things are deliberate. The return waits for the stack count in the thrown slot to
 * actually drop, because returning on a fixed timer can hand back a different item before
 * the throw registered. And nothing here ever writes to an item stack, so a mistake can cost
 * you a wrong hotbar slot but never your inventory.
 */
public abstract class HotbarThrowModule extends Module {
    protected enum Stage {
        IDLE,
        SWITCHING,
        WAITING_FOR_THROW
    }

    public final ModuleSetting<dev.utilityclient.keybind.KeyBind> activateKey;
    public final ModuleSetting<Integer> switchDelay;
    public final ModuleSetting<Integer> returnDelay;
    public final ModuleSetting<Integer> timeout;
    public final ModuleSetting<Boolean> searchInventory;
    public final ModuleSetting<Boolean> showStatus;
    public final ModuleSetting<Boolean> alsoUseModuleKey;

    protected Stage stage = Stage.IDLE;
    private int returnSlot = -1;
    private int throwSlot = -1;
    private int countBefore;
    private int timer;

    protected HotbarThrowModule(String id, String displayName, String description,
                                ModuleCategory category, boolean byDefault) {
        super(id, displayName, description, category, byDefault, true, false);

        activateKey = addSetting(ModuleSetting.keybindSetting("activate-key", "Activate key",
                "The key that throws, while the module stays switched on.",
                new dev.utilityclient.keybind.KeyBind()));

        switchDelay = addSetting(ModuleSetting.integerSetting("switch-delay", "Switch delay",
                "Ticks between selecting the item and throwing it.", 1, 0, 20, 1));
        returnDelay = addSetting(ModuleSetting.integerSetting("return-delay", "Return delay",
                "Extra ticks after the throw registers before swapping back.", 0, 0, 20, 1));
        timeout = addSetting(ModuleSetting.integerSetting("timeout", "Timeout",
                "Gives up and swaps your item back if the throw never registers.", 40, 5, 200, 5));
        searchInventory = addSetting(ModuleSetting.booleanSetting("search-inventory", "Search inventory",
                "Look through your whole inventory, not just the hotbar. Slower.", false));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it cannot find one.", true));
        alsoUseModuleKey = addSetting(ModuleSetting.booleanSetting("module-key", "Also use module key",
                "Let the module's own on/off keybind throw as well, not just switch it.", false));
    }

    @Override
    public void onDisable() {
        // Switched off mid throw: hand the item back rather than leaving the player holding
        // something they did not choose.
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
                // The activate key is the real trigger, so the module can sit switched on.
                // The module's own keybind is only consulted when that extra option is on,
                // and even then it has already been consumed by the toggle by this point.
                boolean pressed = false;
                if (activateKey.value().bound() && activateKey.value().consumePress(client)) {
                    pressed = true;
                } else {
                    activateKey.value().sync(client);
                }
                if (!pressed && alsoUseModuleKey.value() && keyBind().consumePress(client)) {
                    pressed = true;
                }
                if (pressed) {
                    begin(client);
                }
            }
            case SWITCHING -> {
                if (timer-- > 0) {
                    return;
                }
                doThrow(client);
            }
            case WAITING_FOR_THROW -> {
                timer++;
                if (hasLeftHand(client)) {
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
        int found = findSlot(client);
        if (found < 0) {
            if (showStatus.value()) {
                say(client, "No " + itemName().toLowerCase(java.util.Locale.ROOT)
                        + " in your " + (searchInventory.value() ? "inventory" : "hotbar") + ".");
            }
            return;
        }

        int current = client.player.getInventory().getSelectedSlot();
        if (current == found) {
            if (showStatus.value()) {
                say(client, "You already have one in hand.");
            }
            return;
        }

        throwSlot = found;
        returnSlot = current;
        countBefore = countIn(client, found);
        timer = switchDelay.value();
        stage = Stage.SWITCHING;
        select(client, found);
    }

    private void doThrow(Minecraft client) {
        if (throwSlot < 0 || client.player == null) {
            reset();
            return;
        }
        select(client, throwSlot);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        stage = Stage.WAITING_FOR_THROW;
        timer = 0;
    }

    private void restore(Minecraft client) {
        if (client.player != null && returnSlot >= 0 && returnSlot < 9) {
            select(client, returnSlot);
        }
    }

    private void select(Minecraft client, int slot) {
        if (slot < 0 || slot > 8) {
            return;
        }
        client.player.getInventory().setSelectedSlot(slot);
        // The inventory change is local only, so the server has to be told separately.
        // Without this the server still thinks the old item is held and throws that instead.
        client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private boolean hasLeftHand(Minecraft client) {
        return throwSlot < 0 || countIn(client, throwSlot) < countBefore;
    }

    private static int countIn(Minecraft client, int slot) {
        ItemStack stack = client.player.getInventory().getItem(slot);
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    /* ---------------------------------------------------------------- lookup */

    private int findSlot(Minecraft client) {
        int total = client.player.getInventory().getContainerSize();
        int limit = searchInventory.value() ? total : Math.min(9, total);
        Item wanted = throwable();
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == wanted) {
                return slot;
            }
        }
        return -1;
    }

    /* ---------------------------------------------------------------- hooks */

    /** The item this module throws. */
    protected abstract Item throwable();

    /** Human readable name used in chat messages. */
    protected abstract String itemName();

    /* ---------------------------------------------------------------- state */

    private void reset() {
        stage = Stage.IDLE;
        returnSlot = -1;
        throwSlot = -1;
        countBefore = 0;
        timer = 0;
    }

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[" + displayName() + "] " + message));
        }
    }

    /* ---------------------------------------------------------------- read */

    public boolean active() {
        return stage != Stage.IDLE;
    }

    public String stateLabel() {
        return switch (stage) {
            case SWITCHING -> "switching";
            case WAITING_FOR_THROW -> "waiting for throw";
            case IDLE -> "idle";
        };
    }

    public int heldSlot() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? 0 : client.player.getInventory().getSelectedSlot();
    }
}
