package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * Trades for you once you have opened a villager yourself.
 *
 * <p>This is deliberately confined to the trade window. It never walks you to a villager,
 * never opens a trade on its own and never trades while the screen is closed, so it cannot
 * move your character or buy anything you were not already standing in front of. Each trade
 * goes through the normal container click the game uses when you click the result slot.
 *
 * <p>The number of trades per villager is capped and a delay is applied, so a single
 * activation can never dump your whole stock of items into one trade.
 */
public final class AutoTradeModule extends Module {
    public final ModuleSetting<Integer> delay;
    public final ModuleSetting<Integer> maxTrades;
    public final ModuleSetting<Boolean> tradeFirstOfferOnly;
    public final ModuleSetting<Boolean> showStatus;

    private int cooldown;
    private int traded;
    private int lastContainerId = -1;

    public AutoTradeModule() {
        super("auto-trade", "Auto Villager Trader",
                "Trades with the villager you are already looking at.", ModuleCategory.MISC, false, true, true);
        delay = addSetting(ModuleSetting.integerSetting("delay", "Delay",
                "Ticks to wait between trades.", 10, 1, 60, 1));
        maxTrades = addSetting(ModuleSetting.integerSetting("max-trades", "Max trades",
                "How many trades to make per villager before it stops.", 64, 1, 512, 1));
        tradeFirstOfferOnly = addSetting(ModuleSetting.booleanSetting("first-offer-only",
                "First offer only", "Stop after the first trade instead of moving through the list.", true));
        showStatus = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when the trade limit is reached.", true));
    }

    @Override
    public void onDisable() {
        cooldown = 0;
        traded = 0;
        lastContainerId = -1;
    }

    @Override
    public void tick(Minecraft client) {
        if (cooldown > 0) {
            cooldown--;
        }

        // Only ever act on a trade window the player opened themselves.
        if (!(client.gui.screen() instanceof MerchantScreen) || client.player == null
                || client.player.containerMenu == null) {
            reset();
            return;
        }
        if (!(client.player.containerMenu instanceof MerchantMenu menu)) {
            reset();
            return;
        }
        if (menu.getOffers().isEmpty()) {
            reset();
            return;
        }

        // A new villager, or a reopened window, starts a fresh allowance.
        if (menu.containerId != lastContainerId) {
            lastContainerId = menu.containerId;
            traded = 0;
            cooldown = 0;
        }

        if (traded >= maxTrades.value()) {
            if (showStatus.value() && cooldown <= 0) {
                tell(client, "Auto trader stopped after " + maxTrades.value()
                        + " trades. Open the villager again to reset.");
                cooldown = 40;
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }

        client.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.PICKUP, client.player);
        traded++;
        cooldown = delay.value();

        if (tradeFirstOfferOnly.value() && traded >= 1) {
            traded = maxTrades.value();
        }
    }

    private void reset() {
        cooldown = 0;
        traded = 0;
        lastContainerId = -1;
    }

    private void tell(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }
}
