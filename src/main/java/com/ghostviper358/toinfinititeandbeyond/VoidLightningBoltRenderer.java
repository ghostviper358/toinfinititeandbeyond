package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Draws a Void Lightning bolt (see VoidLightningBolt): the same zig-zag shape as vanilla lightning
 * (it follows vanilla's LightningBoltRenderer step for step, with the same branches), but black on
 * the inside with glowing purple edges.
 *
 * Vanilla lightning is four see-through layers, thin to thick, all drawn with "glowing" colours
 * that add light. Added light can't make black, so here the two thin inner layers are drawn solid
 * black instead (using the black core square of the cannon's texture), and only the two thick
 * outer layers glow - purple.
 */
public class VoidLightningBoltRenderer extends EntityRenderer<VoidLightningBolt, VoidLightningBoltRenderer.State> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "textures/item/black_hole_arm_cannon.png");
    /** The middle of the black CORE square in the cannon's 32x32 texture. */
    private static final float CORE_U = 20 / 32f;
    private static final float CORE_V = 4 / 32f;

    private static final float GLOW_RED = 0.6f;
    private static final float GLOW_GREEN = 0.15f;
    private static final float GLOW_BLUE = 0.9f;
    private static final float GLOW_ALPHA = 0.3f;

    public static class State extends EntityRenderState {
        long seed;
    }

    public VoidLightningBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VoidLightningBolt bolt, State state, float partialTick) {
        super.extractRenderState(bolt, state, partialTick);
        state.seed = bolt.seed;
    }

    @Override
    protected boolean affectedByCulling(VoidLightningBolt bolt) {
        return false;
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float[] xs = new float[8];
        float[] zs = new float[8];
        float x = 0.0f;
        float z = 0.0f;
        RandomSource shape = RandomSource.create(state.seed);
        for (int i = 7; i >= 0; i--) {
            xs[i] = x;
            zs[i] = z;
            x += shape.nextInt(11) - 5;
            z += shape.nextInt(11) - 5;
        }

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = null;
        for (int layer = 0; layer < 4; layer++) {
            boolean inside = layer < 2;
            // Getting a buffer finishes the one before it, so get each only when its layers start:
            // the black inside first, then the purple glow.
            if (layer == 0) {
                consumer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
            } else if (layer == 2) {
                consumer = buffers.getBuffer(RenderType.lightning());
            }
            RandomSource branches = RandomSource.create(state.seed);
            for (int branch = 0; branch < 3; branch++) {
                int top = branch > 0 ? 7 - branch : 7;
                int bottom = branch > 0 ? top - 2 : 0;
                float bx = xs[top] - x;
                float bz = zs[top] - z;

                for (int segment = top; segment >= bottom; segment--) {
                    float upperX = bx;
                    float upperZ = bz;
                    if (branch == 0) {
                        bx += branches.nextInt(11) - 5;
                        bz += branches.nextInt(11) - 5;
                    } else {
                        bx += branches.nextInt(31) - 15;
                        bz += branches.nextInt(31) - 15;
                    }
                    float topWidth = 0.1f + layer * 0.2f;
                    float bottomWidth = 0.1f + layer * 0.2f;
                    if (branch == 0) {
                        topWidth *= segment * 0.1f + 1.0f;
                        bottomWidth *= (segment - 1.0f) * 0.1f + 1.0f;
                    }
                    // The four sides of this piece of the bolt.
                    side(pose, consumer, inside, bx, bz, segment, upperX, upperZ, topWidth, bottomWidth, false, false, true, false);
                    side(pose, consumer, inside, bx, bz, segment, upperX, upperZ, topWidth, bottomWidth, true, false, true, true);
                    side(pose, consumer, inside, bx, bz, segment, upperX, upperZ, topWidth, bottomWidth, true, true, false, true);
                    side(pose, consumer, inside, bx, bz, segment, upperX, upperZ, topWidth, bottomWidth, false, true, false, false);
                }
            }
        }
        super.render(state, poseStack, buffers, packedLight);
    }

    /** One side of one piece of the bolt - the same corners as vanilla's LightningBoltRenderer.quad. */
    private static void side(PoseStack.Pose pose, VertexConsumer consumer, boolean black,
                             float x1, float z1, int segment, float x2, float z2, float width1, float width2,
                             boolean a, boolean b, boolean c, boolean d) {
        corner(pose, consumer, black, x1 + (a ? width2 : -width2), segment * 16, z1 + (b ? width2 : -width2));
        corner(pose, consumer, black, x2 + (a ? width1 : -width1), (segment + 1) * 16, z2 + (b ? width1 : -width1));
        corner(pose, consumer, black, x2 + (c ? width1 : -width1), (segment + 1) * 16, z2 + (d ? width1 : -width1));
        corner(pose, consumer, black, x1 + (c ? width2 : -width2), segment * 16, z1 + (d ? width2 : -width2));
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer consumer, boolean black, float x, float y, float z) {
        if (black) {
            consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(CORE_U, CORE_V)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
        } else {
            consumer.addVertex(pose, x, y, z).setColor(GLOW_RED, GLOW_GREEN, GLOW_BLUE, GLOW_ALPHA);
        }
    }
}
