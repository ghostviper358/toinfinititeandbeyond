package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the Black Hole Arm Cannon's black hole (see CannonBlackHole): a black core inside a bigger
 * see-through purple shell, spinning different ways, with the shell pulsing. It grows in when it
 * appears and shrinks away as it collapses. Uses the black hole squares of the cannon's own
 * texture, so it looks like the little one floating at the end of the cannon, only much bigger.
 */
public class CannonBlackHoleRenderer extends EntityRenderer<CannonBlackHole, EntityRenderState> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "textures/item/black_hole_arm_cannon.png");

    /** Where the CORE and SHELL squares are in the 32x32 texture (see gen_black_hole_arm_cannon_texture.py). */
    private static final float CORE_U = 16 / 32f, CORE_V = 0 / 32f;
    private static final float SHELL_U = 16 / 32f, SHELL_V = 8 / 32f;
    private static final float SQUARE = 8 / 32f;

    /** Half the width of the black core and the purple shell, in blocks. */
    private static final float CORE_SIZE = 1.0f;
    private static final float SHELL_SIZE = 1.7f;
    /** Ticks it takes to grow in at the start and shrink away at the end. */
    private static final float GROW_TICKS = 10.0f;

    public CannonBlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void render(EntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = state.ageInTicks;
        float grow = Mth.clamp(Math.min(age, CannonBlackHole.LIFETIME_TICKS - age) / GROW_TICKS, 0.0f, 1.0f);
        if (grow <= 0.0f) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        poseStack.pushPose();
        poseStack.scale(grow, grow, grow);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 6.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 4.0f));
        cube(consumer, poseStack.last(), CORE_SIZE, CORE_U, CORE_V);
        poseStack.popPose();

        poseStack.pushPose();
        float pulse = 1.0f + 0.08f * Mth.sin(age * 0.4f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 3.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 2.5f));
        poseStack.scale(pulse, pulse, pulse);
        cube(consumer, poseStack.last(), SHELL_SIZE, SHELL_U, SHELL_V);
        poseStack.popPose();

        poseStack.popPose();
        super.render(state, poseStack, buffers, packedLight);
    }

    /** A cube {@code size} blocks out from the middle in every direction, each face showing one texture square. */
    private static void cube(VertexConsumer consumer, PoseStack.Pose pose, float size, float u, float v) {
        float s = size;
        face(consumer, pose, u, v, 0, 0, 1, -s, -s, s, s, -s, s, s, s, s, -s, s, s);
        face(consumer, pose, u, v, 0, 0, -1, s, -s, -s, -s, -s, -s, -s, s, -s, s, s, -s);
        face(consumer, pose, u, v, 1, 0, 0, s, -s, s, s, -s, -s, s, s, -s, s, s, s);
        face(consumer, pose, u, v, -1, 0, 0, -s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s);
        face(consumer, pose, u, v, 0, 1, 0, -s, s, s, s, s, s, s, s, -s, -s, s, -s);
        face(consumer, pose, u, v, 0, -1, 0, -s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s);
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, float u, float v,
                             float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(consumer, pose, x1, y1, z1, u, v + SQUARE, nx, ny, nz);
        vertex(consumer, pose, x2, y2, z2, u + SQUARE, v + SQUARE, nx, ny, nz);
        vertex(consumer, pose, x3, y3, z3, u + SQUARE, v, nx, ny, nz);
        vertex(consumer, pose, x4, y4, z4, u, v, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
            .setColor(0xFFFFFFFF)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, nx, ny, nz);
    }
}
