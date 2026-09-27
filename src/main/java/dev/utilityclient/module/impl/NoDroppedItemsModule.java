package dev.utilityclient.module.impl;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

public final class NoDroppedItemsModule extends Module {
    public final ModuleSetting<Integer> range;

    public NoDroppedItemsModule() {
        super("no-dropped-items", "No Dropped Items", "Hides dropped item entities locally without changing the world.", ModuleCategory.VISUAL, false, false, false);
        range = addSetting(ModuleSetting.integerSetting("range", "Range", "0 hides all items; a positive value hides only nearby items.", 0, 0, 32, 4));
    }

    public boolean shouldHide(Entity entity, double cameraX, double cameraY, double cameraZ) {
        if (!enabled() || !(entity instanceof ItemEntity)) {
            return false;
        }
        int radius = range.value();
        return radius <= 0 || entity.distanceToSqr(cameraX, cameraY, cameraZ) <= (double) radius * radius;
    }
}
