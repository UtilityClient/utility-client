package dev.utilityclient.module.impl;

import dev.utilityclient.module.ModuleCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One key throw for an ender pearl. See {@link HotbarThrowModule} for the shared behaviour:
 * swap to the item, make one right click, wait for the stack to actually shrink, then hand
 * the previous item back.
 */
public final class KeyPearlModule extends HotbarThrowModule {
    public KeyPearlModule() {
        super("key-pearl", "Key Pearl",
                "Uses this module's keybind to throw a pearl and swap straight back to what you were holding.",
                ModuleCategory.MOVEMENT, false);
    }

    @Override
    protected Item throwable() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected String itemName() {
        return "ender pearl";
    }
}
