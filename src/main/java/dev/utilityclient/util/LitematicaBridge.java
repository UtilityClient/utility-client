package dev.utilityclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads loaded schematics out of Litematica without compiling against it.
 *
 * <p>Litematica is not a hard dependency of this client, and its internals move between
 * versions. Everything here goes through reflection and every call is guarded, so if a
 * Litematica update moves a class the builder simply reports "no schematic loaded" instead
 * of taking the game down with it.
 *
 * <p>Only public API is touched: {@code SchematicHolder.getAllSchematics()} to list loaded
 * files, then per region {@code getSubRegionContainer(String)} and {@code get(x, y, z)} to
 * read block states.
 */
public final class LitematicaBridge {
    private LitematicaBridge() {
    }

    /** One material line: how many the schematic wants, and how many the player holds. */
    public record Material(Item item, int needed, int held) {
        public int missing() {
            return Math.max(0, needed - held);
        }
    }

    public record Materials(String schematicName, int totalBlocks, List<Material> materials) {
        public List<Material> requirements() {
            return materials;
        }

        public int missingCount() {
            int total = 0;
            for (Material material : materials) {
                total += material.missing();
            }
            return total;
        }

        public int missingKinds() {
            int count = 0;
            for (Material material : materials) {
                if (material.missing() > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    /* ---------------------------------------------------------------- cache */

    private static final List<Class<?>> RESOLVED = new ArrayList<>();
    private static boolean unavailable;
    private static String unavailableReason = "";

    private static Class<?> classOf(String name) {
        if (unavailable) {
            return null;
        }
        for (Class<?> cached : RESOLVED) {
            if (cached.getSimpleName().equals(name.substring(name.lastIndexOf('.') + 1))) {
                return cached;
            }
        }
        try {
            Class<?> found = Class.forName(name);
            RESOLVED.add(found);
            return found;
        } catch (Throwable throwable) {
            unavailable = true;
            unavailableReason = "Litematica class " + name + " not found. Is Litematica installed?";
            return null;
        }
    }

    private static Object invoke(Object target, Class<?> owner, String name, Class<?>[] types, Object[] args) {
        try {
            Method method = owner.getMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static String status() {
        return unavailable ? unavailableReason : "ok";
    }

    /* ---------------------------------------------------------------- collect */

    public static Materials collect(Minecraft client, String ignoreCsv) {
        if (client.player == null) {
            return null;
        }
        Set<String> ignore = parseIgnore(ignoreCsv);

        Object holder = getSchematicHolder();
        if (holder == null) {
            return null;
        }
        Collection<?> schematics = listSchematics(holder);
        if (schematics == null || schematics.isEmpty()) {
            return null;
        }

        Object schematic = schematics.iterator().next();
        String name = String.valueOf(invoke(schematic, schematic.getClass(), "getName",
                new Class<?>[0], new Object[0]));
        if (name.startsWith("null")) {
            name = "schematic";
        }

        Map<Item, Integer> needed = new LinkedHashMap<>();
        int total = tally(schematic, needed, ignore);
        if (total == 0) {
            return new Materials(name, 0, List.of());
        }

        Map<Item, Integer> held = countHeld(client);
        List<Material> materials = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            materials.add(new Material(entry.getKey(), entry.getValue(),
                    held.getOrDefault(entry.getKey(), 0)));
        }
        return new Materials(name, total, materials);
    }

    private static Set<String> parseIgnore(String csv) {
        Set<String> out = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            String clean = part.trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty()) {
                out.add(clean);
            }
        }
        return out;
    }

    private static Object getSchematicHolder() {
        Class<?> holderClass = classOf("fi.dy.masa.litematica.data.SchematicHolder");
        if (holderClass == null) {
            return null;
        }
        return invoke(null, holderClass, "getInstance", new Class<?>[0], new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private static Collection<?> listSchematics(Object holder) {
        Object result = invoke(holder, holder.getClass(), "getAllSchematics",
                new Class<?>[0], new Object[0]);
        return result instanceof Collection<?> collection ? collection : null;
    }

    /** Walks every sub region and tallies non air blocks into {@code needed}. */
    private static int tally(Object schematic, Map<Item, Integer> needed, Set<String> ignore) {
        Object areas = invoke(schematic, schematic.getClass(), "getAreaPositions",
                new Class<?>[0], new Object[0]);
        if (!(areas instanceof Map<?, ?> map) || map.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Object regionName : map.keySet()) {
            String key = String.valueOf(regionName);
            Object container = invoke(schematic, schematic.getClass(), "getSubRegionContainer",
                    new Class<?>[]{String.class}, new Object[]{key});
            if (container == null) {
                continue;
            }
            Object size = invoke(container, container.getClass(), "getSize",
                    new Class<?>[0], new Object[0]);
            if (!(size instanceof net.minecraft.core.Vec3i vec)) {
                continue;
            }
            total += tallyRegion(container, vec, needed, ignore);
        }
        return total;
    }

    private static int tallyRegion(Object container, net.minecraft.core.Vec3i size,
                                   Map<Item, Integer> needed, Set<String> ignore) {
        Method getter = blockGetter(container.getClass());
        if (getter == null) {
            return 0;
        }

        int counted = 0;
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    Object state;
                    try {
                        state = getter.invoke(container, x, y, z);
                    } catch (Throwable throwable) {
                        return counted;
                    }
                    if (!(state instanceof net.minecraft.world.level.block.state.BlockState blockState)) {
                        continue;
                    }
                    if (blockState.isAir()) {
                        continue;
                    }
                    Item item = blockState.getBlock().asItem();
                    if (item == net.minecraft.world.item.Items.AIR) {
                        continue;
                    }
                    if (!ignore.isEmpty() && matchesIgnore(item, ignore)) {
                        continue;
                    }
                    needed.merge(item, 1, Integer::sum);
                    counted++;
                }
            }
        }
        return counted;
    }

    private static Method blockGetter(Class<?> containerClass) {
        try {
            Method method = containerClass.getMethod("get", int.class, int.class, int.class);
            method.setAccessible(true);
            return method;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static boolean matchesIgnore(Item item, Set<String> ignore) {
        // Registry id rather than the display name, so ignore lists work the same on any
        // language client.
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(item).getPath().toLowerCase(Locale.ROOT);
        if (ignore.contains(name)) {
            return true;
        }
        // Also allow the namespaced form, so either spelling works.
        String full = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(item).toString().toLowerCase(Locale.ROOT);
        return ignore.contains(full);
    }

    /* ---------------------------------------------------------------- inventory */

    private static Map<Item, Integer> countHeld(Minecraft client) {
        Map<Item, Integer> held = new LinkedHashMap<>();
        if (client.player == null) {
            return held;
        }
        net.minecraft.world.entity.player.Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            held.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return held;
    }

    /** Convenience for callers that want a position list rather than a tally. */
    public static List<BlockPos> regionPositions(Object schematic, String region) {
        Object result = invoke(schematic, schematic.getClass(), "getAreaPositions",
                new Class<?>[0], new Object[0]);
        if (result instanceof Map<?, ?> map) {
            Object value = map.get(region);
            if (value instanceof BlockPos pos) {
                return List.of(pos);
            }
        }
        return List.of();
    }
}
