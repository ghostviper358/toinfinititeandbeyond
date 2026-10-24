package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Renderer for the Universal Destroyer: the black stone body, plus a glow layer drawing its
 * purple veins, eyes, sword runes and the Universal Core at full brightness. In phase 3
 * (Annihilation) the glow layer switches to a second texture with every dormant vein blazing, and
 * below 10,000 health a third, pulsing pass makes its whole body glow. Its size: the entity's
 * SCALE attribute is 16x (Minecraft's limit), and {@link #scale} draws it 1.25x bigger on top of
 * that - 20x in all, matching its hitbox.
 */
@OnlyIn(Dist.CLIENT)
public class UniversalDestroyerRenderer
        extends MobRenderer<UniversalDestroyer, UniversalDestroyerRenderState, UniversalDestroyerModel> {

    /** Registered in {@code EntityRenderersEvent.RegisterLayerDefinitions}. */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "universal_destroyer"),
        "main"
    );

    private static final ResourceLocation TEXTURE = texture("universal_destroyer");
    private static final ResourceLocation GLOW = texture("universal_destroyer_glow");
    private static final ResourceLocation GLOW_ANNIHILATION = texture("universal_destroyer_glow_annihilation");
    private static final ResourceLocation GLOW_OVERCHARGED = texture("universal_destroyer_glow_overcharged");

    public UniversalDestroyerRenderer(EntityRendererProvider.Context context) {
        super(context, new UniversalDestroyerModel(context.bakeLayer(LAYER_LOCATION)), 1.2f /* shadow radius */);
        this.addLayer(new GlowLayer(this));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(UniversalDestroyerRenderState renderState) {
        return TEXTURE;
    }

    /** On top of its 16x SCALE: 1.25x more, 20x in all. */
    private static final float EXTRA_SCALE = 1.25f;

    @Override
    protected void scale(UniversalDestroyerRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        poseStack.scale(EXTRA_SCALE, EXTRA_SCALE, EXTRA_SCALE);
    }

    @Override
    public UniversalDestroyerRenderState createRenderState() {
        return new UniversalDestroyerRenderState();
    }

    @Override
    public void extractRenderState(UniversalDestroyer entity, UniversalDestroyerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getCurrentAttack();
        state.attackTicks = entity.getAttackTicks(partialTick);
        state.phase = entity.getPhase();
        state.finale = entity.getFinale();
        state.overcharged = entity.isOvercharged();
    }

    /** The glowing veins - drawn at full brightness on top of the body, like an Enderman's eyes. */
    private static final class GlowLayer extends RenderLayer<UniversalDestroyerRenderState, UniversalDestroyerModel> {

        GlowLayer(RenderLayerParent<UniversalDestroyerRenderState, UniversalDestroyerModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                           UniversalDestroyerRenderState state, float yRot, float xRot) {
            RenderType glow = RenderType.eyes(state.phase >= 3 ? GLOW_ANNIHILATION : GLOW);
            this.getParentModel().renderToBuffer(poseStack, bufferSource.getBuffer(glow), packedLight, OverlayTexture.NO_OVERLAY);
            if (state.overcharged) {
                // Its whole body glowing, pulsing like a heartbeat.
                float pulse = 0.55f + 0.45f * Mth.sin(state.ageInTicks * 0.25f);
                this.getParentModel().renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW_OVERCHARGED)), packedLight,
                    OverlayTexture.NO_OVERLAY, ARGB.colorFromFloat(1.0f, pulse, pulse, pulse));
            }
        }
    }
}
