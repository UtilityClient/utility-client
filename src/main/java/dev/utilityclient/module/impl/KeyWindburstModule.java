package dev.utilityclient.module.impl;

import dev.utilityclient.module.ModuleCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One key throw for a wind charge. Same behaviour as Key Pearl, different item, so both live
 * in {@link HotbarThrowModule} rather than duplicating the timing logic.
 */
public final class KeyWindburstModule extends HotbarThrowModule {
    public KeyWindburstModule() {
        super("key-windburst", "Key Windburst",
                "Uses this module's keybind to throw a wind charge and swap straight back to what you were holding.",
                ModuleCategory.MOVEMENT, false);
    }

    @Override
    protected Item throwable() {
        return Items.WIND_CHARGE;
    }

    @Override
    protected String itemName() {
        return "wind charge";
    }
}
