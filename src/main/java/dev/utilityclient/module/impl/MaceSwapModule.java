package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.AttackState;
import dev.utilityclient.util.HotbarMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Locale;

/**
 * Swaps to a mace the moment you attack.
 *
 * <p>The trigger is the game's own attack binding, so swinging at a mob and starting to
 * break a block both count. The setting picks which mace to reach for when you own more
 * than one: a plain mace, or the one carrying Wind Burst, Density or Breach. It matches the
 * real enchantment registry keys rather than display text, so it works on any language
 * client, and when several maces qualify it takes the highest level of the enchantment you
 * asked for.
 *
 * <p>Only the hotbar selection changes, and the server is told separately, so what you end
 * up holding is the same on both sides. Nothing is picked up, moved or consumed, and this
 * module never writes to an item stack.
 */
public final class MaceSwapModule extends Module {
    public final ModuleSetting<String> prefer;
    public final ModuleSetting<Boolean> onAttack;
    public final ModuleSetting<Boolean> manualKey;
    public final ModuleSetting<Boolean> skipIfHeld;
    public final ModuleSetting<Boolean> switchBack;
    public final ModuleSetting<Integer> returnDelay;
    public final ModuleSetting<Boolean> showStatus;

    /** One instance per module, so this module's return cannot clobber Ely Swap's. */
    private final HotbarMemory memory = new HotbarMemory();

    public MaceSwapModule() {
        super("mace-swap", "Mace Swap",
                "Swaps to a mace when you attack.",
                ModuleCategory.COMBAT, false, true, false);

        prefer = addSetting(ModuleSetting.modeSetting("prefer", "Swap to",
                "Which mace to reach for when you have more than one.",
                "Wind Burst", "Any mace", "Wind Burst", "Density", "Breach"));
        onAttack = addSetting(ModuleSetting.booleanSetting("on-attack", "Swap when you attack",
                "Swap to the mace whenever you swing at something.", true));
        manualKey = addSetting(ModuleSetting.booleanSetting("manual-key", "Module key swaps too",
                "Let the module keybind swap as well, not just switch it on and off.", false));
        skipIfHeld = addSetting(ModuleSetting.booleanSetting("skip-if-held", "Skip if already held",
                "Do nothing if the mace you want is already in hand.", true));
        switchBack = addSetting(ModuleSetting.booleanSetting("switch-back", "Switch back",
                "Put your original item back in hand shortly after swapping, so you are not "
                        + "left holding a mace. Returns only if you have not scrolled away "
                        + "yourself in the meantime.", false));
        returnDelay = addSetting(ModuleSetting.integerSetting("return-delay", "Return delay",
                "How long to hold the mace before switching back, in ticks. 1 is 50 "
                        + "milliseconds, 20 is one second.", 1, 1, 40, 1));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it swaps or cannot find one.", true));
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            memory.forget();
            return;
        }
        // Equipping while a real menu is open would fight with whatever the player is
        // clicking. Our own menus are excluded, see realMenuOpen.
        if (realMenuOpen(client)) {
            return;
        }

        // A pending return is handled before anything else, and cancelling a pending return
        // is the first thing a fresh swap does. Without this, an attack one tick before the
        // timer expired would swap to the mace and then immediately bounce back.
        if (memory.tickReturn()) {
            if (memory.returnIfUnmoved(client) && showStatus.value()) {
                say(client, "Switched back.");
            }
        }

        // The swap itself is armed to run from inside the attack, not here. This tick is
        // already too late: the attack has been resolved by the time it runs, so the hit
        // would land with the old item. Arming it every tick means the swap is always
        // waiting, and firing it from the mixin puts the mace in hand before the hit.
        if (onAttack.value() && !realMenuOpen(client)) {
            AttackState.arm(this::swapFromAttack);
        } else {
            AttackState.clear();
        }

        // The flag is only consulted when the attack trigger is off, which is the case the
        // in-attack hook cannot cover.
        boolean triggered = false;
        if (!onAttack.value()) {
            triggered = AttackState.consume();
        }
        if (!triggered && manualKey.value() && keyBind().consumePress(client)) {
            triggered = true;
        }
        if (!triggered) {
            return;
        }

        swap(client);
    }

    /**
     * Runs the swap at the moment the attack starts.
     *
     * <p>No menu check and no status message. The arming tick already established that the
     * player is in the world with no menu open, and a message printed mid attack would land
     * in the middle of the swing.
     */
    private void swapFromAttack() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || realMenuOpen(client)) {
            return;
        }
        swap(client);
    }

    /**
     * True when a real game menu is open. The ClickGUI and the settings screens are not a
     * problem, since which item you are holding does not interfere with them.
     */
    private static boolean realMenuOpen(Minecraft client) {
        return client.gui.screen() != null
                && !(client.gui.screen() instanceof dev.utilityclient.gui.ClickGuiScreen)
                && !(client.gui.screen() instanceof dev.utilityclient.gui.ModuleSettingsScreen);
    }

    @Override
    public void onDisable() {
        // Drop any pending return, so switching the module off never leaves it firing a
        // swap later on.
        memory.forget();
        AttackState.clear();
    }

    /* ---------------------------------------------------------------- swap */

    private void swap(Minecraft client) {
        String wanted = prefer.value();
        ResourceKey<Enchantment> required = keyFor(wanted);
        String label = labelFor(wanted);

        int current = client.player.getInventory().getSelectedSlot();
        int best = -1;
        int bestLevel = -1;

        // Only the hotbar can be selected without moving items around, so a mace further
        // back is deliberately ignored rather than dragged into your hand.
        int hotbar = Math.min(9, client.player.getInventory().getContainerSize());
        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != Items.MACE) {
                continue;
            }
            int level = levelOf(stack, required);
            if (level < 0) {
                continue;
            }
            if (level > bestLevel) {
                bestLevel = level;
                best = slot;
            }
        }

        if (best < 0) {
            if (showStatus.value()) {
                say(client, required == null
                        ? "No mace in your hotbar."
                        : "No " + label + " mace in your hotbar.");
            }
            return;
        }

        if (best == current && skipIfHeld.value()) {
            return;
        }

        // Swapping again while a return is pending means the player wants the mace for
        // longer, so the old timer is dropped rather than firing against the new swap.
        memory.forget();
        if (!memory.swapTo(client, best)) {
            return;
        }
        if (switchBack.value()) {
            memory.returnAfter(returnDelay.value());
        }

        if (showStatus.value()) {
            say(client, "Swapped to " + label + " mace.");
        }
    }

    /**
     * Level of the wanted enchantment on this mace, or -1 when it does not have it. With no
     * preference, any mace scores zero so the first one found is taken.
     */
    private static int levelOf(ItemStack stack, ResourceKey<Enchantment> required) {
        if (required == null) {
            return 0;
        }
        for (Holder<Enchantment> holder : stack.getEnchantments().keySet()) {
            ResourceKey<Enchantment> key = holder.unwrapKey().orElse(null);
            if (key == null || !key.equals(required)) {
                continue;
            }
            return stack.getEnchantments().getLevel(holder);
        }
        return -1;
    }

    private static ResourceKey<Enchantment> keyFor(String label) {
        return switch (label) {
            case "Wind Burst" -> Enchantments.WIND_BURST;
            case "Density" -> Enchantments.DENSITY;
            case "Breach" -> Enchantments.BREACH;
            // "Any mace" matches everything, so no enchantment is required.
            default -> null;
        };
    }

    private static String labelFor(String label) {
        return switch (label) {
            case "Wind Burst" -> "Wind Burst";
            case "Density" -> "Density";
            case "Breach" -> "Breach";
            default -> "any";
        };
    }

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Mace Swap] " + message));
        }
    }

    /* ---------------------------------------------------------------- read */

    /** What is in hand, for the HUD. */
    public String heldLabel() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return "-";
        }
        ItemStack stack = client.player.getInventory().getSelectedItem();
        if (stack.isEmpty() || stack.getItem() != Items.MACE) {
            return "not holding a mace";
        }
        return describe(stack).toLowerCase(Locale.ROOT);
    }

    private static String describe(ItemStack stack) {
        for (Holder<Enchantment> holder : stack.getEnchantments().keySet()) {
            ResourceKey<Enchantment> key = holder.unwrapKey().orElse(null);
            if (key == null) {
                continue;
            }
            String path = key.identifier().getPath();
            if (path.equals(Enchantments.WIND_BURST.identifier().getPath())) {
                return "Wind Burst";
            }
            if (path.equals(Enchantments.DENSITY.identifier().getPath())) {
                return "Density";
            }
            if (path.equals(Enchantments.BREACH.identifier().getPath())) {
                return "Breach";
            }
        }
        return "Mace";
    }
}
