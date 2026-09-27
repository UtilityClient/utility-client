package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.CustomBreakAnimationModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public abstract class CustomBreakAnimationMixin {
    @ModifyArg(
            method = "submitBlockDestroyAnimation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitBreakingBlockModel(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;I)V"
            ),
            index = 2
    )
    private int utilityclient$customBreakStage(int vanillaStage) {
        Module module = ModuleManager.get().find("custom-break-animation");
        if (module instanceof CustomBreakAnimationModule breakAnimation) {
            return breakAnimation.stageForRender(vanillaStage);
        }
        return vanillaStage;
    }
}
