package dev.utilityclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the exact moment the game starts an attack.
 *
 * <p>Mace Swap needs to know when the player swung, and the obvious approach of polling
 * {@code options.keyAttack} does not work: the game itself calls
 * {@code consumeClick()} on that binding while handling input, which happens before the end
 * of the client tick. Whatever reads it afterwards sees nothing, because the click has
 * already been taken.
 *
 * <p>Hooking the method that actually begins an attack sidesteps that entirely, and it is
 * also more accurate, because it fires for attacks triggered any way rather than only the
 * ones that come from the key.
 */
@Mixin(Minecraft.class)
public final class AttackMixin {
    /**
     * Set on the frame an attack begins and cleared by whoever reads it, so an attack is
     * never missed between ticks and never counted twice.
     */
    public static boolean attackStarted;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void utilityClient$onStartAttack(CallbackInfoReturnable<Boolean> callback) {
        attackStarted = true;
    }

    /** Reads and clears the flag, so each attack is reported exactly once. */
    public static boolean consumeAttack() {
        boolean was = attackStarted;
        attackStarted = false;
        return was;
    }

    /** Clears a stale flag, for example after a world change. */
    public static void clear() {
        attackStarted = false;
    }
}
