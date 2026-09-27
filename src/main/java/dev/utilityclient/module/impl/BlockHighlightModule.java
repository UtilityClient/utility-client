package dev.utilityclient.module.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.utilityclient.module.Module;
import dev.utilityclient.module.ModuleCategory;
import dev.utilityclient.module.ModuleSetting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class BlockHighlightModule extends Module {
    public final ModuleSetting<Integer> color;
    public final ModuleSetting<String> style;
    public final ModuleSetting<Integer> thickness;
    public final ModuleSetting<Integer> fillAlpha;

    public BlockHighlightModule() {
        super("block-highlight", "Block Highlight", "Custom colored highlight for the targeted block.", ModuleCategory.VISUAL, false, false, false);
        color = addSetting(ModuleSetting.colorSetting("color", "Color", "Highlight colour.", 0xD000FF));
        style = addSetting(ModuleSetting.modeSetting("style", "Style", "Border outline or full block fill.",
                "Border", "Border", "Full"));
        thickness = addSetting(ModuleSetting.integerSetting("thickness", "Thickness", "Outline thickness in pixels.", 2, 1, 6, 1));
        fillAlpha = addSetting(ModuleSetting.integerSetting("fill-alpha", "Fill opacity", "Opacity of the full block fill.", 45, 5, 255, 5));
    }

    public void render(LevelRenderContext context, BlockOutlineRenderState outline) {
        if (!enabled() || outline == null) {
            return;
        }

        BlockPos pos = outline.pos();
        if (pos == null) {
            return;
        }

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack pose = context.poseStack();
        int rgb = color.colorValue();
        int opaque = 0xFF000000 | rgb;

        pose.pushPose();
        pose.translate(pos.getX() - camera.x(), pos.getY() - camera.y(), pos.getZ() - camera.z());

        boolean full = "Full".equalsIgnoreCase(style.value());
        if (full) {
            float inset = 0.002F;
            float fill = Math.max(0.0F, Math.min(1.0F, fillAlpha.value() / 255.0F));
            int argb = (Math.round(fill * 255) << 24) | rgb;
            context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(),
                    (renderPose, consumer) -> renderCube(renderPose, consumer, argb,
                            1.0F - inset * 2.0F, 1.0F - inset * 2.0F, 1.0F - inset * 2.0F));
        } else {
            float size = Math.max(0.004F, thickness.value() * 0.006F);
            context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(),
                    (renderPose, consumer) -> renderEdges(renderPose, consumer, opaque, size));
        }

        pose.popPose();
    }

    /**
     * Draws the twelve edges of the block outline as thin filled boxes.
     */
    private static void renderEdges(PoseStack.Pose pose, VertexConsumer consumer, int argb, float size) {
        float min = 0.0F;
        float max = 1.0F;
        float low = size;
        float high = 1.0F - size;

        // Four edges along X
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                float edgeY = y == 0 ? low : high;
                float edgeZ = z == 0 ? low : high;
                box(pose, consumer, argb, min, max, edgeY - low, edgeY + low, edgeZ - low, edgeZ + low);
            }
        }
        // Four edges along Y
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                float edgeX = x == 0 ? low : high;
                float edgeZ = z == 0 ? low : high;
                box(pose, consumer, argb, edgeX - low, edgeX + low, min, max, edgeZ - low, edgeZ + low);
            }
        }
        // Four edges along Z
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                float edgeX = x == 0 ? low : high;
                float edgeY = y == 0 ? low : high;
                box(pose, consumer, argb, edgeX - low, edgeX + low, edgeY - low, edgeY + low, min, max);
            }
        }
    }

    private static void renderCube(PoseStack.Pose pose, VertexConsumer consumer, int argb, float sizeX, float sizeY, float sizeZ) {
        float x1 = (1.0F - sizeX) / 2.0F;
        float y1 = (1.0F - sizeY) / 2.0F;
        float z1 = (1.0F - sizeZ) / 2.0F;
        float x2 = x1 + sizeX;
        float y2 = y1 + sizeY;
        float z2 = z1 + sizeZ;
        box(pose, consumer, argb, x1, x2, y1, y2, z1, z2);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer consumer, int argb,
                            float x1, float x2, float y1, float y2, float z1, float z2) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        // -X
        quad(consumer, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // +X
        quad(consumer, pose, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, r, g, b, a);
        // -Y
        quad(consumer, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        // +Y
        quad(consumer, pose, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, r, g, b, a);
        // -Z
        quad(consumer, pose, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        // +Z
        quad(consumer, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz,
                             int r, int g, int b, int a) {
        consumer.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
        consumer.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
        consumer.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        consumer.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
    }
}
