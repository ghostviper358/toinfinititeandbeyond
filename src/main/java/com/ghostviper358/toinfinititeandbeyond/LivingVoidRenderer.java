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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Renderer for the Living Void: the dark eye, shards and tentacles, plus a glow layer drawing the
 * iris, the veins of light running through the darkness, the shard edges and the glowing suckers
 * on its tentacles at full brightness - so in its Darkness attack, the eye is all you can see.
 * The in-world size comes from the entity's SCALE attribute (4x).
 */
@OnlyIn(Dist.CLIENT)
public class LivingVoidRenderer extends MobRenderer<LivingVoid, LivingVoidRenderState, LivingVoidModel> {

    /** Registered in {@code EntityRenderersEvent.RegisterLayerDefinitions}. */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "living_void"), "main");

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "textures/entity/living_void.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "textures/entity/living_void_glow.png");

    public LivingVoidRenderer(EntityRendererProvider.Context context) {
        super(context, new LivingVoidModel(context.bakeLayer(LAYER_LOCATION)), 1.5f /* shadow radius */);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(LivingVoidRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public LivingVoidRenderState createRenderState() {
        return new LivingVoidRenderState();
    }

    @Override
    public void extractRenderState(LivingVoid entity, LivingVoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getCurrentAttack();
        state.attackTicks = entity.getAttackTicks(partialTick);
    }

    /** The glowing parts - drawn at full brightness on top of the body, like an Enderman's eyes. */
    private static final class GlowLayer extends RenderLayer<LivingVoidRenderState, LivingVoidModel> {

        GlowLayer(RenderLayerParent<LivingVoidRenderState, LivingVoidModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                           LivingVoidRenderState state, float yRot, float xRot) {
            this.getParentModel().renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW)),
                packedLight, OverlayTexture.NO_OVERLAY);
        }
    }
}
