package dev.utilityclient.mixin;

import dev.utilityclient.util.AttackState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the exact moment the game starts an attack.
 *
 * <p>Mace Swap needs to know when the player swung, and the obvious approach of polling
 * {@code options.keyAttack} does not work: the game itself calls {@code consumeClick()} on
 * that binding while handling input, which happens before the end of the client tick.
 * Whatever reads it afterwards sees nothing, because the click has already been taken.
 *
 * <p>Hooking the method that actually begins an attack sidesteps that entirely, and it is
 * also more accurate, because it fires for attacks triggered any way rather than only the
 * ones that come from the key.
 *
 * <p>The flag itself is held in {@link AttackState} rather than here. Mixin rejects a
 * non-private static field, and a private one would still be merged into {@code Minecraft}.
 */
@Mixin(Minecraft.class)
public final class AttackMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void utilityClient$onStartAttack(CallbackInfoReturnable<Boolean> callback) {
        AttackState.mark();
    }
}
