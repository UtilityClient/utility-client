package dev.utilityclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.FreeCamModule;
import dev.utilityclient.module.impl.FreeLookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera control for the free camera and free look modules.
 *
 * <p>Free Look has to be injected into {@code alignWithEntity} rather than applied at the
 * end, because the third person camera offset is worked out there from the angles the
 * entity reports. Feeding the free look angles in means the camera orbits around the body
 * using the direction you are actually looking, with vanilla's own wall clipping intact.
 *
 * <p>Free Cam overrides the finished camera instead, because it replaces the position as
 * well as the rotation.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /**
     * Stops the third person camera from being pulled towards the player when a wall is in
     * the way. Vanilla raycasts eight points around the camera and shortens the boom; giving
     * back the distance it asked for means the camera passes through blocks instead.
     */
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void utilityclient$cameraNoclip(float distance, CallbackInfoReturnable<Float> callback) {
        FreeLookModule look = activeFreeLook();
        if (look != null && look.noclip.value()) {
            callback.setReturnValue(distance);
        }
    }

    @WrapOperation(
            method = "alignWithEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F")
    )
    private float utilityclient$freeLookYaw(Entity entity, float partialTicks, Operation<Float> original) {
        FreeLookModule look = activeFreeLook();
        if (look != null) {
            return look.yaw();
        }
        return original.call(entity, partialTicks);
    }

    @WrapOperation(
            method = "alignWithEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F")
    )
    private float utilityclient$freeLookPitch(Entity entity, float partialTicks, Operation<Float> original) {
        FreeLookModule look = activeFreeLook();
        if (look != null) {
            return look.pitch();
        }
        return original.call(entity, partialTicks);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void utilityclient$freeCam(CameraRenderState state, float partialTick, CallbackInfo callback) {
        Module freeCam = ModuleManager.get().find("freecam");
        if (!(freeCam instanceof FreeCamModule cam) || !cam.enabled()) {
            return;
        }
        FreeLookModule look = activeFreeLook();
        if (look != null) {
            cam.setLook(look.yaw(), look.pitch());
        }
        cam.captureStart((Camera) (Object) this);
        this.setPosition(cam.cameraX(), cam.cameraY(), cam.cameraZ());
        this.setRotation(cam.yaw(), cam.pitch());
    }

    private static FreeLookModule activeFreeLook() {
        Module module = ModuleManager.get().find("freelook");
        if (module instanceof FreeLookModule look
                && look.enabled()
                && !(ModuleManager.get().find("freecam") instanceof FreeCamModule cam && cam.enabled())) {
            return look;
        }
        return null;
    }
}
