package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.NoDroppedItemsModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class DroppedItemRenderMixin {
    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void utilityclient$hideDroppedItems(Entity entity, Frustum frustum, double cameraX,
                                                 double cameraY, double cameraZ,
                                                 CallbackInfoReturnable<Boolean> callback) {
        Module module = ModuleManager.get().find("no-dropped-items");
        if (module instanceof NoDroppedItemsModule droppedItems && droppedItems.shouldHide(entity, cameraX, cameraY, cameraZ)) {
            callback.setReturnValue(false);
        }
    }
}
