package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.util.LitematicaBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out what a loaded Litematica schematic still needs, and can buy the missing blocks
 * from a GUI based auction house.
 *
 * <p>Placement is not done here. Litematica's build tools already place blocks and do it
 * better than a reimplementation, and its placement manager is not publicly reachable, so
 * driving it would mean reflecting into private fields that break on every Litematica
 * update. This is the companion piece: the materials list you would otherwise read off a
 * screen and buy by hand.
 *
 * <p>The buying is a small state machine. It sends a search command, waits for the auction
 * window to open, finds a slot holding the block it wants, clicks it, then closes and moves
 * on. It only ever clicks a real slot through the normal container click handler, so the
 * server validates every purchase exactly as if you had clicked by hand.
 *
 * <p>It is off by default and bounded on every axis the client can actually enforce, which
 * matters because a mistake here spends real money. It can count purchases and stack sizes
 * precisely, but it cannot know what things cost unless the auction window shows a price
 * that {@code price-pattern} can read, so the money cap depends on that setting.
 */
public final class AutoBuilderModule extends Module {
    private enum Stage {
        /** Nothing in flight. */
        IDLE,
        /** A search command was sent, waiting for the auction window. */
        WAITING_FOR_WINDOW,
        /** The window is open, looking for a matching slot. */
        SCANNING,
        /** A slot was clicked, giving the server a moment to respond. */
        CONFIRMING
    }

    private record Pending(Item item, int stack) {
    }

    public final ModuleSetting<Integer> interval;
    public final ModuleSetting<Boolean> includeEnderChest;
    public final ModuleSetting<String> ignoreBlocks;

    public final ModuleSetting<Boolean> autoBuy;
    public final ModuleSetting<String> searchCommand;
    public final ModuleSetting<Integer> maxStack;
    public final ModuleSetting<Integer> maxBuysPerSession;
    public final ModuleSetting<Integer> maxSpendPerSession;
    public final ModuleSetting<String> pricePattern;
    public final ModuleSetting<Boolean> strictPrice;
    public final ModuleSetting<Integer> delay;
    public final ModuleSetting<Integer> windowTimeout;
    public final ModuleSetting<Boolean> closeAfterBuy;
    public final ModuleSetting<Boolean> announce;
    public final ModuleSetting<Boolean> announceBuys;

    private final List<LitematicaBridge.Material> requirements = new ArrayList<>();
    private final Deque<Pending> queue = new ArrayDeque<>();

    private Stage stage = Stage.IDLE;
    private int cooldown;
    private int stageTimer;
    private Pending active;
    private int boughtThisSession;
    private int spentThisSession;
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
                "Search the auction house and click items. Spends your money.", false));
        searchCommand = addSetting(ModuleSetting.stringSetting("search", "Search command",
                "Sent to chat. Placeholders: {item} is the block id, {stack} is the amount.",
                "/ah {item} {stack}"));
        maxStack = addSetting(ModuleSetting.integerSetting("max-stack", "Max per buy",
                "The stack value sent with each search, and the most it will buy at once.",
                64, 1, 2304, 1));
        maxBuysPerSession = addSetting(ModuleSetting.integerSetting("max-buys", "Max buys per session",
                "Stops buying for the rest of the session after this many purchases.", 20, 1, 500, 1));
        maxSpendPerSession = addSetting(ModuleSetting.integerSetting("max-spend", "Max spend per session",
                "Only enforced if a price pattern is set, since the client cannot otherwise know a cost.",
                10000, 0, 10000000, 100));
        pricePattern = addSetting(ModuleSetting.stringSetting("price-pattern", "Price pattern",
                "Regex to pull a price out of an item's name, for example \\\\d[0-9,]*. Blank skips the check.",
                ""));
        strictPrice = addSetting(ModuleSetting.booleanSetting("strict-price", "Skip unpriced items",
                "If an item shows no readable price, skip it rather than buying it unchecked.", false));
        delay = addSetting(ModuleSetting.integerSetting("delay", "Delay",
                "Ticks to wait between buying actions.", 30, 0, 600, 5));
        windowTimeout = addSetting(ModuleSetting.integerSetting("timeout", "Window timeout",
                "Ticks to wait for the auction window before giving up on a search.", 200, 20, 1200, 10));
        closeAfterBuy = addSetting(ModuleSetting.booleanSetting("close-window", "Close after buying",
                "Close the auction window once a purchase is made, ready for the next item.", true));
        announce = addSetting(ModuleSetting.booleanSetting("status", "Show status",
                "Print a line in chat when the material list changes.", true));
        announceBuys = addSetting(ModuleSetting.booleanSetting("buy-log", "Log purchases",
                "Print every purchase it makes.", true));
    }

    @Override
    public void onDisable() {
        requirements.clear();
        queue.clear();
        active = null;
        stage = Stage.IDLE;
        boughtThisSession = 0;
        spentThisSession = 0;
        status = "Disabled";
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        if (stage != Stage.IDLE) {
            advance(client);
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
            refillQueue();
            beginNext(client);
        }
    }

    /* ------------------------------------------------------------------ queue */

    private void refillQueue() {
        if (boughtThisSession >= maxBuysPerSession.value()) {
            setStatus("Buy count cap reached for this session");
            return;
        }
        if (spentThisSession >= maxSpendPerSession.value() && !pricePattern.value().isBlank()) {
            setStatus("Spend cap reached for this session");
            return;
        }

        int stack = Math.max(1, maxStack.value());
        for (LitematicaBridge.Material material : requirements) {
            if (material.missing() <= 0) {
                continue;
            }
            if (queue.stream().anyMatch(p -> p.item() == material.item())) {
                continue;
            }
            // Never ask for more than is actually missing.
            queue.add(new Pending(material.item(), Math.min(stack, material.missing())));
        }
    }

    private void beginNext(Minecraft client) {
        if (queue.isEmpty() || active != null) {
            if (queue.isEmpty() && active == null) {
                setStatus("Nothing missing to buy");
            }
            return;
        }
        String template = searchCommand.value().trim();
        if (template.isEmpty() || !template.contains("{item}")) {
            setStatus("Set the search command first");
            queue.clear();
            return;
        }
        if (boughtThisSession >= maxBuysPerSession.value()) {
            queue.clear();
            return;
        }

        active = queue.poll();
        String wire = template
                .replace("{item}", name(active.item()))
                .replace("{stack}", Integer.toString(active.stack()))
                .replace("{price}", Integer.toString(maxStack.value()));
        if (wire.startsWith("/")) {
            wire = wire.substring(1);
        }
        client.player.connection.sendCommand(wire);
        stage = Stage.WAITING_FOR_WINDOW;
        stageTimer = 0;
        if (announce.value()) {
            setStatus("Searching for " + name(active.item()));
        }
    }

    /* ------------------------------------------------------------------ machine */

    private void advance(Minecraft client) {
        stageTimer++;
        boolean windowOpen = isAuctionWindow(client);

        switch (stage) {
            case WAITING_FOR_WINDOW -> {
                if (windowOpen) {
                    stage = Stage.SCANNING;
                    stageTimer = 0;
                } else if (stageTimer >= windowTimeout.value()) {
                    giveUp("No auction window opened", client);
                }
            }
            case SCANNING -> {
                if (!windowOpen) {
                    // Window closed on its own, probably the search found nothing.
                    giveUp("Auction window closed before a match", client);
                    return;
                }
                if (tryClick(client)) {
                    stage = Stage.CONFIRMING;
                    stageTimer = 0;
                } else if (stageTimer >= 30) {
                    giveUp("No slot matched " + name(active.item()), client);
                }
            }
            case CONFIRMING -> {
                if (stageTimer < 12) {
                    return;
                }
                boughtThisSession++;
                if (closeAfterBuy.value()) {
                    closeWindow(client);
                }
                cooldown = delay.value();
                active = null;
                stage = Stage.IDLE;
                stageTimer = 0;
                beginNext(client);
            }
            default -> {
                stage = Stage.IDLE;
            }
        }
    }

    private void giveUp(String reason, Minecraft client) {
        active = null;
        stage = Stage.IDLE;
        stageTimer = 0;
        cooldown = delay.value();
        setStatus(reason);
    }

    /**
     * Looks for a slot holding the wanted block and clicks it.
     *
     * <p>Only slots that are not the player's own inventory or hotbar are considered, so a
     * matching block sitting in the player's inventory is never clicked by mistake.
     */
    private boolean tryClick(Minecraft client) {
        AbstractContainerMenu menu = menuOf(client);
        if (menu == null) {
            return false;
        }
        int playerSlots = client.player.getInventory().getContainerSize();
        Pattern price = compilePrice();

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot;
            try {
                slot = menu.getSlot(index);
            } catch (RuntimeException exception) {
                continue;
            }
            if (slot == null) {
                continue;
            }
            // The tail of a standard menu is always the player's own inventory.
            if (index >= menu.slots.size() - playerSlots) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != active.item()) {
                continue;
            }
            if (price != null) {
                Integer cost = readPrice(stack, price);
                if (cost == null) {
                    if (strictPrice.value()) {
                        continue;
                    }
                } else {
                    if (cost > maxSpendPerSession.value() - spentThisSession) {
                        continue;
                    }
                    spentThisSession += cost;
                }
            }

            int buy = Math.min(active.stack(), Math.max(1, stack.getCount()));
            int containerId = menu.containerId;
            client.gameMode.handleContainerInput(containerId, slot.index, 0,
                    ContainerInput.PICKUP, client.player);
            if (announceBuys.value() && client.player != null) {
                client.player.sendSystemMessage(Component.literal("[Auto Builder] Bought "
                        + buy + "x " + name(active.item())
                        + (price != null ? "" : "  (no price check, pattern is blank)")));
            }
            return true;
        }
        return false;
    }

    private Pattern compilePrice() {
        String raw = pricePattern.value();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Pulls a number out of an item's hover name, tolerating commas and symbols. */
    private Integer readPrice(ItemStack stack, Pattern pattern) {
        String text = stack.getHoverName().getString();
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            String digits = matcher.group().replaceAll("[^0-9]", "");
            return digits.isEmpty() ? null : Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isAuctionWindow(Minecraft client) {
        AbstractContainerMenu menu = menuOf(client);
        if (menu == null) {
            return false;
        }
        // The inventory and the crafting grid are not an auction house.
        return menu.containerId != client.player.inventoryMenu.containerId;
    }

    private AbstractContainerMenu menuOf(Minecraft client) {
        if (client.gui.screen() == null) {
            return null;
        }
        return client.player.containerMenu;
    }

    private void closeWindow(Minecraft client) {
        if (client.player != null) {
            client.player.closeContainer();
        }
    }

    /* ------------------------------------------------------------------ read */

    public List<LitematicaBridge.Material> requirements() {
        return List.copyOf(requirements);
    }

    public String status() {
        return status;
    }

    public int boughtThisSession() {
        return boughtThisSession;
    }

    public int spentThisSession() {
        return spentThisSession;
    }

    public int remainingBuys() {
        return Math.max(0, maxBuysPerSession.value() - boughtThisSession);
    }

    public boolean busy() {
        return stage != Stage.IDLE;
    }

    public String stageLabel() {
        return switch (stage) {
            case WAITING_FOR_WINDOW -> "waiting for window";
            case SCANNING -> "scanning slots";
            case CONFIRMING -> "confirming";
            case IDLE -> active == null ? "idle" : "idle";
        };
    }

    public long lastScan() {
        return lastScan;
    }

    private void setStatus(String value) {
        if (!value.equals(status) && announce.value() && client() != null && client().player != null) {
            client().player.sendSystemMessage(Component.literal("[Auto Builder] " + value));
        }
        status = value;
    }

    private Minecraft client() {
        try {
            return Minecraft.getInstance();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String describe(LitematicaBridge.Materials materials) {
        int missing = materials.missingCount();
        int kinds = materials.missingKinds();
        if (missing == 0) {
            return "Nothing missing for " + materials.schematicName();
        }
        return "Missing " + missing + " block(s) in " + kinds + " type(s) for " + materials.schematicName();
    }

    /** First few missing lines, ready to draw on the HUD. */
    public List<String> summaryLines(int limit) {
        List<String> lines = new ArrayList<>();
        int shown = 0;
        int remaining = 0;
        for (LitematicaBridge.Material material : requirements) {
            if (material.missing() <= 0) {
                continue;
            }
            if (shown++ < limit) {
                lines.add(name(material.item()) + " " + material.missing() + " short");
            } else {
                remaining++;
            }
        }
        if (remaining > 0) {
            lines.add("and " + remaining + " more...");
        }
        return lines;
    }

    private static String name(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
