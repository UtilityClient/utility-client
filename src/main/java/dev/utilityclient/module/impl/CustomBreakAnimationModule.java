package dev.utilityclient.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class CustomBreakAnimationModule extends Module {
    public final ModuleSetting<String> style;
    public final ModuleSetting<Integer> smoothing;
    public final ModuleSetting<Integer> color;
    public final ModuleSetting<Boolean> showIndicator;
    public final ModuleSetting<Integer> indicatorWidth;

    private float displayedProgress;
    private BlockPos lastTarget;
    private float smoothProgress;
    private boolean hasSmoothProgress;

    public CustomBreakAnimationModule() {
        super("custom-break-animation", "Custom Break Animation",
                "A customizable client-side mining progress animation.", ModuleCategory.VISUAL, false, true, false);
        style = addSetting(ModuleSetting.modeSetting("style", "Style", "Choose the progress animation preset.",
                "Classic", "Classic", "Neon", "Minimal", "Pulse", "Smooth"));
        smoothing = addSetting(ModuleSetting.integerSetting("smoothing", "Smoothing", "How quickly the animation catches up.", 12, 1, 20, 1));
        color = addSetting(ModuleSetting.colorSetting("color", "Color", "Colour of the progress indicator.", 0xD000FF));
        showIndicator = addSetting(ModuleSetting.booleanSetting("show-indicator", "Progress indicator",
                "Draw a progress bar above the block being broken.", true));
        indicatorWidth = addSetting(ModuleSetting.integerSetting("indicator-width", "Indicator width",
                "How wide the progress bar is drawn.", 120, 60, 200, 10));
    }

    @Override
    public void tick(Minecraft client) {
        BlockPos target = targetPos(client);
        float target_progress = targetProgress(client);
        if (target == null || target_progress <= 0.0F) {
            smoothProgress += (0.0F - smoothProgress) * 0.35F;
            if (smoothProgress < 0.01F) {
                smoothProgress = 0.0F;
            }
            displayedProgress = 0.0F;
            hasSmoothProgress = false;
            lastTarget = null;
            return;
        }

        lastTarget = target;
        float amount = Math.min(1.0F, 0.08F * smoothing.value());
        smoothProgress += (target_progress - smoothProgress) * amount;
        if (smoothProgress < 0.0F) {
            smoothProgress = 0.0F;
        }
        if (!hasSmoothProgress) {
            smoothProgress = target_progress;
            hasSmoothProgress = true;
        }
        displayedProgress = shape(smoothProgress);
    }

    public boolean isBreaking(Minecraft client) {
        return targetProgress(client) > 0.0F;
    }

    public float progress() {
        return Math.max(0.0F, Math.min(1.0F, displayedProgress));
    }

    public String style() {
        return style.value();
    }

    public BlockPos target() {
        return lastTarget;
    }

    /**
     * Remaps the vanilla crack stage so the on-block texture also follows the preset.
     */
    public int stageForRender(int vanillaStage) {
        if (!enabled()) {
            return Math.max(0, Math.min(9, vanillaStage));
        }
        float value = Math.max(0.0F, Math.min(1.0F, (vanillaStage + 1) / 10.0F));
        return Math.max(0, Math.min(9, Math.round(shape(value) * 9.0F)));
    }

    private float shape(float value) {
        return switch (style.value().toLowerCase(Locale.ROOT)) {
            case "smooth" -> 1.0F - (float) Math.pow(1.0F - value, 2.2);
            case "neon" -> Math.min(1.0F, value * 1.35F);
            case "pulse" -> Math.min(1.0F, value * value * 1.15F);
            case "minimal" -> value * 0.85F;
            default -> Math.min(1.0F, value * 1.15F);
        };
    }

    /**
     * Draws a camera facing progress bar above the block that is being broken.
     */
    public void render(LevelRenderContext context) {
        if (!enabled() || !showIndicator.value() || lastTarget == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        float progress = progress();
        if (progress <= 0.0F) {
            return;
        }

        Vec3 look = client.player.getLookAngle();
        Vec3 right = look.cross(Vec3.Y_AXIS);
        if (right.lengthSqr() < 1.0E-6F) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();

        float halfWidth = indicatorWidth.value() / 300.0F;
        float halfHeight = halfWidth * 0.09F;
        double baseX = lastTarget.getX() + 0.5D;
        double baseY = lastTarget.getY() + 0.85D;
        double baseZ = lastTarget.getZ() + 0.5D;

        int rgb = color.colorValue();
        int accent = 0xFF000000 | rgb;
        int background = 0xB0000000;

        float width = halfWidth;
        float height = halfHeight;
        float filledRight = -width + (width * 2.0F) * progress;
        float x = (float) baseX;
        float y = (float) baseY;
        float z = (float) baseZ;
        Vec3 rightVector = right;
        Vec3 upVector = up;

        PoseStack pose = context.poseStack();
        pose.pushPose();
        // debugQuads keeps the QUADS topology but disables back face culling, so the bar is
        // drawn whichever way it ends up facing. debugFilledBox culls, which can make a
        // single flat quad disappear completely.
        context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(),
                (renderPose, consumer) -> {
                    barQuad(consumer, renderPose, x, y, z, rightVector, upVector,
                            -width, width, -height, height, background);
                    barQuad(consumer, renderPose, x, y, z, rightVector, upVector,
                            -width, filledRight, -height, height, accent);
                    frame(consumer, renderPose, x, y, z, rightVector, upVector, width, height, accent);
                });
        pose.popPose();
    }

    private static void barQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                float x, float y, float z, Vec3 right, Vec3 up,
                                float left, float rightEdge, float bottom, float top, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        vertex(consumer, pose, x, y, z, right, up, left, bottom, r, g, b, a);
        vertex(consumer, pose, x, y, z, right, up, rightEdge, bottom, r, g, b, a);
        vertex(consumer, pose, x, y, z, right, up, rightEdge, top, r, g, b, a);
        vertex(consumer, pose, x, y, z, right, up, left, top, r, g, b, a);
    }

    private static void frame(VertexConsumer consumer, PoseStack.Pose pose,
                              float x, float y, float z, Vec3 right, Vec3 up,
                              float halfWidth, float halfHeight, int argb) {
        float t = halfHeight * 0.35F;
        barQuad(consumer, pose, x, y, z, right, up, -halfWidth, halfWidth, -halfHeight, -halfHeight + t, argb);
        barQuad(consumer, pose, x, y, z, right, up, -halfWidth, halfWidth, halfHeight - t, halfHeight, argb);
        barQuad(consumer, pose, x, y, z, right, up, -halfWidth, -halfWidth + t, -halfHeight, halfHeight, argb);
        barQuad(consumer, pose, x, y, z, right, up, halfWidth - t, halfWidth, -halfHeight, halfHeight, argb);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, Vec3 right, Vec3 up,
                               float rightOffset, float upOffset, int r, int g, int b, int a) {
        float vx = x + (float) (right.x() * rightOffset + up.x() * upOffset);
        float vy = y + (float) (right.y() * rightOffset + up.y() * upOffset);
        float vz = z + (float) (right.z() * rightOffset + up.z() * upOffset);
        consumer.addVertex(pose, vx, vy, vz).setColor(r, g, b, a);
    }

    private static BlockPos targetPos(Minecraft client) {
        if (client.player == null || !(client.hitResult instanceof BlockHitResult hit)) {
            return null;
        }
        return hit.getBlockPos();
    }

    private static float targetProgress(Minecraft client) {
        if (client.player == null || client.gameMode == null || !client.gameMode.isDestroying()
                || !(client.hitResult instanceof BlockHitResult)) {
            return 0.0F;
        }
        int stage = client.gameMode.getDestroyStage();
        if (stage < 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (stage + 1) / 10.0F));
    }
}
