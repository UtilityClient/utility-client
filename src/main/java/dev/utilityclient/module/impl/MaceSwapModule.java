package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.Locale;

/**
 * Click the module in the menu and it puts a mace in your hand.
 *
 * <p>It swaps on enable, not on a timer and not on a key, because the module card click is
 * already the press. The setting picks which mace to reach for when you own more than one:
 * a plain mace, or the one carrying Wind Burst or Density. It matches the real enchantment
 * registry keys rather than display text, so it works on any language client.
 *
 * <p>Only the hotbar selection changes, and the server is told separately, so what you end
 * up holding is the same on both sides. Nothing is picked up, moved or consumed, and this
 * module never writes to an item stack.
 */
public final class MaceSwapModule extends Module {
    public final ModuleSetting<String> prefer;
    public final ModuleSetting<Boolean> skipIfHeld;
    public final ModuleSetting<Boolean> showStatus;

    public MaceSwapModule() {
        super("mace-swap", "Mace Swap",
                "Clicking this in the menu swaps to a mace. No keybind needed.",
                ModuleCategory.COMBAT, false, true, false);

        prefer = addSetting(ModuleSetting.modeSetting("prefer", "Swap to",
                "Which mace to reach for when you have more than one.",
                "Wind Burst", "Any mace", "Wind Burst", "Density", "Breach"));
        skipIfHeld = addSetting(ModuleSetting.booleanSetting("skip-if-held", "Skip if already held",
                "Do nothing if the mace you want is already in hand.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it swaps or cannot find one.", true));
    }

    @Override
    public void onEnable() {
        // The card click is the press, so the swap happens the moment the module turns on.
        swap(Minecraft.getInstance());
    }

    /* ---------------------------------------------------------------- swap */

    private void swap(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        // Equipping while a menu is open would fight with whatever the player is clicking.
        if (client.gui.screen() != null) {
            return;
        }

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
            // Prefer the highest level of the wanted enchantment when several maces match.
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

        client.player.getInventory().setSelectedSlot(best);
        client.player.connection.send(new ServerboundSetCarriedItemPacket(best));

        if (showStatus.value()) {
            say(client, "Swapped to " + label + " mace.");
        }
    }

    /**
     * Level of the wanted enchantment on this mace, or -1 when it does not have it.
     * With no preference, any mace scores zero so the first one found is taken.
     */
    private static int levelOf(ItemStack stack, ResourceKey<Enchantment> required) {
        if (required == null) {
            return 0;
        }
        List<Holder<Enchantment>> keys = List.copyOf(stack.getEnchantments().keySet());
        for (Holder<Enchantment> holder : keys) {
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
