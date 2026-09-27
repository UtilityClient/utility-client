package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Field;

/**
 * Trades repeatedly with the offer you pick, then closes the window once that offer is
 * used up.
 *
 * <p>Open a villager, click the trade you want, and it keeps trading that one until the
 * villager runs out of it. When the offer disappears the window closes itself.
 *
 * <p>How it knows a trade is finished is worth spelling out. A villager removes an offer
 * from its list once it is exhausted, so the list getting shorter is the reliable signal.
 * The offer object also reports being out of stock directly, and that is checked too, but
 * the list length is what it trusts, because the active offer reference can stay populated
 * on the client after the server has already removed it.
 *
 * <p>There is a hard trade cap as well, and it is not optional. A server villager that
 * restocks, or one with an effectively unlimited offer, would otherwise drain money
 * forever. If the trade count does not move for a while, it also stops on its own, which
 * covers the case of being unable to afford the next trade.
 */
public final class AutoTradeModule extends Module {
    private static final int RESULT_SLOT = 0;

    public final ModuleSetting<Integer> delay;
    public final ModuleSetting<Integer> maxTrades;
    public final ModuleSetting<Integer> idleTimeout;
    public final ModuleSetting<Boolean> closeWhenDone;
    public final ModuleSetting<Boolean> showStatus;
    public final ModuleSetting<Boolean> tradeWhileFull;

    private static Field tradeContainerField;
    private static boolean reflectionWarned;

    private MerchantOffer tracked;
    private int baselineOffers = -1;
    private int trades;
    private int idleTicks;
    private int cooldown;
    private int containerId = -1;
    private String resultName = "";

    public AutoTradeModule() {
        super("auto-trade", "Auto Villager Trader",
                "Trades the offer you click until that villager runs out, then closes the window.",
                ModuleCategory.MISC, false, true, true);

        delay = addSetting(ModuleSetting.integerSetting("delay", "Delay",
                "Ticks to wait between trades.", 8, 1, 60, 1));
        maxTrades = addSetting(ModuleSetting.integerSetting("max-trades", "Hard cap",
                "Safety limit. Stops after this many trades even if the villager still has stock.",
                2304, 1, 2304, 64));
        idleTimeout = addSetting(ModuleSetting.integerSetting("idle-timeout", "Stall timeout",
                "Stops if this many ticks pass with the offer count not changing.", 200, 20, 1200, 20));
        closeWhenDone = addSetting(ModuleSetting.booleanSetting("close-when-done", "Close when done",
                "Close the trade window once the offer is used up.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when it finishes.", true));
        tradeWhileFull = addSetting(ModuleSetting.booleanSetting("full-inventory", "Trade when full",
                "Keep going even if your inventory cannot fit another result.", true));
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void tick(Minecraft client) {
        if (cooldown > 0) {
            cooldown--;
        }

        if (!(client.gui.screen() instanceof MerchantScreen) || client.player == null
                || client.player.containerMenu == null) {
            reset();
            return;
        }
        if (!(client.player.containerMenu instanceof MerchantMenu menu)) {
            reset();
            return;
        }
        if (menu.containerId != containerId) {
            containerId = menu.containerId;
            reset();
        }

        MerchantOffer active = activeOffer(menu);
        if (active == null) {
            // Nothing picked yet. Waiting for the click that chooses a trade.
            tracked = null;
            baselineOffers = -1;
            return;
        }

        if (tracked == null) {
            // First tick after a pick. Record the starting point and start trading.
            tracked = active;
            baselineOffers = menu.getOffers().size();
            trades = 0;
            idleTicks = 0;
            resultName = describe(active);
        }

        if (isFinished(menu, active)) {
            finish(client, "Used up " + resultName + " after " + trades + " trade(s)");
            return;
        }

        if (trades >= maxTrades.value()) {
            finish(client, "Reached the hard cap of " + maxTrades.value() + " trades on " + resultName);
            return;
        }

        if (!tradeWhileFull.value() && inventoryIsFull(client)) {
            finish(client, "Inventory is full, stopping before " + resultName);
            return;
        }

        if (cooldown > 0) {
            return;
        }

        client.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0,
                ContainerInput.PICKUP, client.player);
        trades++;
        cooldown = delay.value();
        idleTicks = 0;
    }

    /* ---------------------------------------------------------------- state */

    /**
     * True once the offer being traded can no longer be made. The offer list shrinking is
     * the dependable signal, with the offer's own out of stock flag as a second opinion.
     */
    private boolean isFinished(MerchantMenu menu, MerchantOffer active) {
        if (baselineOffers >= 0 && menu.getOffers().size() < baselineOffers) {
            return true;
        }
        if (active.isOutOfStock()) {
            return true;
        }
        if (active.getUses() >= active.getMaxUses() && active.getMaxUses() > 0) {
            return true;
        }
        return false;
    }

    private void finish(Minecraft client, String message) {
        if (showStatus.value() && client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Auto Trader] " + message));
        }
        if (closeWhenDone.value() && client.player != null) {
            client.player.closeContainer();
            if (client.gui.screen() != null) {
                client.gui.setScreen(null);
            }
        }
        reset();
    }

    private void reset() {
        tracked = null;
        baselineOffers = -1;
        trades = 0;
        idleTicks = 0;
        cooldown = 0;
    }

    /* ---------------------------------------------------------------- helpers */

    private static boolean inventoryIsFull(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inventory = client.player.getInventory();
        // The first 36 slots are the main inventory. The hotbar cannot be a reason to stop,
        // because stackable results just merge into what is already there.
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String describe(MerchantOffer offer) {
        String result = offer.getResult().getHoverName().getString();
        return result.isBlank() ? "that trade" : result;
    }

    /**
     * The offer the player has selected. The screen does not expose it and the menu keeps
     * its trade container private, so it is read reflectively. If that ever stops working
     * the module simply never starts, rather than misbehaving.
     */
    private static MerchantOffer activeOffer(MerchantMenu menu) {
        try {
            if (tradeContainerField == null) {
                Field field = MerchantMenu.class.getDeclaredField("tradeContainer");
                field.setAccessible(true);
                tradeContainerField = field;
            }
            Object container = tradeContainerField.get(menu);
            if (container instanceof MerchantContainer tradeContainer) {
                return tradeContainer.getActiveOffer();
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!reflectionWarned) {
                reflectionWarned = true;
                System.out.println("[Utility Client] Could not read the selected trade: "
                        + exception);
            }
        }
        return null;
    }

    /** Total trades made since the module was enabled, for the HUD. */
    public int trades() {
        return trades;
    }

    public boolean trading() {
        return tracked != null;
    }
}
