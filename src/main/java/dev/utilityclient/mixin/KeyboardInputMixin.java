package dev.utilityclient.mixin;

import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleManager;
import dev.utilityclient.module.impl.AutoWalkModule;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the auto walk direction after the game has built the movement input, so the held
 * key behaves exactly as if the player kept it down.
 *
 * <p>{@code keyPresses} is declared on the {@link ClientInput} superclass rather than on
 * {@link KeyboardInput}, so it is read and written through a cast instead of a shadow.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void utilityclient$autoWalk(CallbackInfo callback) {
        Module module = ModuleManager.get().find("auto-walk");
        if (module instanceof AutoWalkModule walk && walk.enabled()) {
            ClientInput input = (ClientInput) (Object) this;
            input.keyPresses = walk.apply(input.keyPresses);
        }
    }
}
