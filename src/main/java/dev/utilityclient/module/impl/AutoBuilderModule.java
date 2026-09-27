package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.LitematicaBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out what a loaded Litematica schematic still needs, and optionally buys the missing
 * blocks from the server's auction house.
 *
 * <p>Placement is deliberately not done here. Litematica's build tools already place blocks
 * and do it far better than a reimplementation would, and its placement manager is not
 * publicly reachable, so driving it would mean reflecting into private fields that would
 * break the moment Litematica updated. This module is the companion piece instead: the
 * materials list you would otherwise have to read off a screen and buy by hand.
 *
 * <p>The buying half is off by default and bounded on every axis, because it spends the
 * player's money. A session spend cap, a per item price cap and a cap on how many items are
 * bought in one pass all apply, and every purchase is announced in chat. If the auction
 * house command is not set, nothing is ever sent.
 */
public final class AutoBuilderModule extends Module {
    private record Purchase(Item item, int count, int price, String command) {
    }

    public final ModuleSetting<Boolean> autoBuy;
    public final ModuleSetting<String> buyCommand;
    public final ModuleSetting<Integer> maxPriceEach;
    public final ModuleSetting<Integer> maxSpendPerSession;
    public final ModuleSetting<Integer> delay;
    public final ModuleSetting<Integer> maxBuysPerPass;
    public final ModuleSetting<Boolean> includeEnderChest;
    public final ModuleSetting<String> ignoreBlocks;
    public final ModuleSetting<Integer> interval;
    public final ModuleSetting<Boolean> announce;
    public final ModuleSetting<Boolean> announceBuys;

    private final List<LitematicaBridge.Material> requirements = new ArrayList<>();
    private int cooldown;
    private int spentThisSession;
    private int lastBuyCount;
    private String status = "No schematic loaded";
    private long lastScan;

    public AutoBuilderModule() {
        super("auto-builder", "Auto Builder",
                "Works out what a Litematica schematic is missing and can buy it from the auction house.",
                ModuleCategory.MISC, false, true, true);

        interval = addSetting(ModuleSetting.integerSetting("interval", "Scan interval",
                "Ticks between rescans of the loaded schematic.", 40, 10, 200, 10));
        includeEnderChest = addSetting(ModuleSetting.booleanSetting("ender-chest",
                "Count ender chest", "Treat items in your ender chest as already owned.", true));
        ignoreBlocks = addSetting(ModuleSetting.stringSetting("ignore", "Ignore blocks",
                "Comma separated block ids to skip, for example: oak_planks,torch.", ""));

        autoBuy = addSetting(ModuleSetting.booleanSetting("auto-buy", "Auto buy",
                "Buy missing blocks from the auction house. Spends your money.", false));
        buyCommand = addSetting(ModuleSetting.stringSetting("buy-command", "Buy command",
                "Template sent to chat. Placeholders: {item} {count} {price}.",
                "/ah buy {item} {count} {price}"));
        maxPriceEach = addSetting(ModuleSetting.integerSetting("max-price", "Max price each",
                "Never send a buy for more than this per item.", 10000, 1, 1000000, 100));
        maxSpendPerSession = addSetting(ModuleSetting.integerSetting("max-spend", "Max spend per session",
                "Stops buying for the rest of the session once this much has been spent.", 100000, 0, 10000000, 1000));
        delay = addSetting(ModuleSetting.integerSetting("delay", "Delay",
                "Ticks to wait between buy commands.", 40, 0, 600, 5));
        maxBuysPerPass = addSetting(ModuleSetting.integerSetting("max-buys", "Max buys per pass",
                "How many items it will try to buy in one scan.", 1, 1, 20, 1));
        announce = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print the missing materials summary in chat when it changes.", true));
        announceBuys = addSetting(ModuleSetting.booleanSetting("buy-log", "Log purchases",
                "Print every purchase it makes.", true));
    }

    @Override
    public void onDisable() {
        requirements.clear();
        spentThisSession = 0;
        lastBuyCount = 0;
        status = "Disabled";
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = interval.value();
        lastScan = System.currentTimeMillis();

        LitematicaBridge.Materials materials = LitematicaBridge.collect(client, ignoreBlocks.value());
        if (materials == null || materials.schematicName() == null) {
            requirements.clear();
            status = "No schematic loaded";
            return;
        }

        requirements.clear();
        requirements.addAll(materials.requirements());
        status = describe(materials);

        if (autoBuy.value()) {
            buyPass(client);
        }
    }

    /* ------------------------------------------------------------------ buy */

    private void buyPass(Minecraft client) {
        if (spentThisSession >= maxSpendPerSession.value()) {
            setStatus("Spend cap reached for this session");
            return;
        }

        String template = buyCommand.value().trim();
        if (template.isEmpty() || !template.contains("{item}")) {
            setStatus("Set the buy command first");
            return;
        }

        List<Purchase> planned = new ArrayList<>();
        int budget = maxSpendPerSession.value() - spentThisSession;

        for (LitematicaBridge.Material requirement : requirements) {
            if (planned.size() >= maxBuysPerPass.value()) {
                break;
            }
            int missing = requirement.missing();
            if (missing <= 0) {
                continue;
            }
            // Price per block, capped. Anything above the cap is skipped rather than
            // clamped, so a wrongly priced listing is never bought by accident.
            int price = maxPriceEach.value();
            if (price > budget) {
                price = budget;
            }
            if (price <= 0) {
                break;
            }
            String command = buildCommand(template, requirement.item(), missing, price);
            if (command == null) {
                continue;
            }
            planned.add(new Purchase(requirement.item(), missing, price, command));
            budget -= price;
            if (budget <= 0) {
                break;
            }
        }

        if (planned.isEmpty()) {
            lastBuyCount = 0;
            setStatus("Nothing missing to buy");
            return;
        }

        for (Purchase purchase : planned) {
            if (client.player == null) {
                return;
            }
            // Some servers use /ah, some use a plugin prefix, so the template is sent
            // verbatim. It must not start with a slash for sendCommand.
            String wire = purchase.command().startsWith("/")
                    ? purchase.command().substring(1)
                    : purchase.command();
            client.player.connection.sendCommand(wire);
            spentThisSession += purchase.price();
            lastBuyCount++;
            if (announceBuys.value() && client.player != null) {
                client.player.sendSystemMessage(Component.literal(
                        "[Auto Builder] Bought " + purchase.count() + "x "
                                + name(purchase.item()) + " for " + purchase.price()));
            }
            cooldown = delay.value();
        }
    }

    private String buildCommand(String template, Item item, int count, int price) {
        String name = name(item);
        if (name.isEmpty()) {
            return null;
        }
        return template
                .replace("{item}", name.replace(' ', '_'))
                .replace("{count}", Integer.toString(count))
                .replace("{price}", Integer.toString(price));
    }

    /**
     * Registry id, for example "stone" or "oak_planks". Used rather than the display name
     * because command templates need something without spaces, and because display names
     * are localised, which would break a template on a French server.
     */
    private static String name(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    /* ------------------------------------------------------------------ read */

    public List<LitematicaBridge.Material> requirements() {
        return List.copyOf(requirements);
    }

    public String status() {
        return status;
    }

    public int spentThisSession() {
        return spentThisSession;
    }

    public int remainingBudget() {
        return Math.max(0, maxSpendPerSession.value() - spentThisSession);
    }

    public boolean scannable() {
        return lastScan > 0L;
    }

    private void setStatus(String value) {
        if (announce.value() && !value.equals(status)) {
            status = value;
        } else {
            status = value;
        }
    }

    private String describe(LitematicaBridge.Materials materials) {
        int missing = materials.missingCount();
        int kinds = materials.missingKinds();
        if (missing == 0) {
            return "Nothing missing for " + materials.schematicName();
        }
        return "Missing " + missing + " block(s) in " + kinds
                + " type(s) for " + materials.schematicName();
    }

    /** Convenience for the HUD: the first few missing lines, ready to draw. */
    public List<String> summaryLines(int limit) {
        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (LitematicaBridge.Material requirement : requirements) {
            if (requirement.missing() <= 0) {
                continue;
            }
            if (shown++ >= limit) {
                lines.add("and " + (countMissingKinds() - limit) + " more...");
                break;
            }
            lines.add(name(requirement.item()) + " " + requirement.missing() + " short");
        }
        return lines;
    }

    private int countMissingKinds() {
        int count = 0;
        for (LitematicaBridge.Material requirement : requirements) {
            if (requirement.missing() > 0) {
                count++;
            }
        }
        return count;
    }

    /** Re-exported so the settings screen and HUD do not need the Litematica types. */
    public static boolean isAir(ItemStack stack) {
        return stack.isEmpty() || stack.getItem() == Items.AIR;
    }

    public static Map<String, Integer> asMap(List<LitematicaBridge.Material> list) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (LitematicaBridge.Material requirement : list) {
            out.put(name(requirement.item()), requirement.missing());
        }
        return out;
    }

    static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
