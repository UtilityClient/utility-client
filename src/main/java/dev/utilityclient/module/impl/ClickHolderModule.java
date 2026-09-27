package dev.utilityclient.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import dev.utilityclient.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Holds a mouse button down for you.
 *
 * <p>This is deliberately not a clicker. The button is pressed once when the holder switches
 * on and then simply kept in the held state, which is exactly what happens when you hold the
 * mouse yourself: the game gets one press and then keeps acting on the held state. No timed
 * or repeating clicks are produced.
 */
public final class ClickHolderModule extends Module {
    public final ModuleSetting<Boolean> left;
    public final ModuleSetting<Boolean> right;

    private boolean holdingLeft;
    private boolean holdingRight;

    public ClickHolderModule() {
        super("click-holder", "Click Holder", "Keeps a mouse button held down for you.", ModuleCategory.MISC, false, true, false);
        left = addSetting(ModuleSetting.booleanSetting("left", "Hold left click",
                "Keeps attack and mining held down.", true));
        right = addSetting(ModuleSetting.booleanSetting("right", "Hold right click",
                "Keeps use, place and eat held down.", false));
    }

    @Override
    public void onEnable() {
        holdingLeft = false;
        holdingRight = false;
    }

    @Override
    public void onDisable() {
        release();
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.options == null || client.level == null) {
            release();
            return;
        }
        if (client.gui.screen() != null) {
            // Never hold a click while a menu is open, so inventory clicks still work.
            release();
            return;
        }
        apply(client.options.keyAttack, left.value(), true);
        apply(client.options.keyUse, right.value(), false);
    }

    private void apply(KeyMapping mapping, boolean wanted, boolean leftSide) {
        boolean holding = leftSide ? holdingLeft : holdingRight;
        if (wanted) {
            mapping.setDown(true);
            if (!holding) {
                // One press so the game starts the action, then the held state keeps it going.
                InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).utilityclient$getKey();
                if (key != null && key.getValue() >= 0) {
                    KeyMapping.click(key);
                }
            }
        } else if (holding) {
            mapping.setDown(false);
        }
        if (leftSide) {
            holdingLeft = wanted;
        } else {
            holdingRight = wanted;
        }
    }

    private void release() {
        Minecraft client = Minecraft.getInstance();
        if (client.options != null) {
            if (holdingLeft) {
                client.options.keyAttack.setDown(false);
            }
            if (holdingRight) {
                client.options.keyUse.setDown(false);
            }
        }
        holdingLeft = false;
        holdingRight = false;
    }
}
