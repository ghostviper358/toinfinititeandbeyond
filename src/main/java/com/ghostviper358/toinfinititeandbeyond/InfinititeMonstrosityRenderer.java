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
 * Renderer for the Infinitite Monstrosity: the dark rock body, plus a glow layer drawing its
 * purple Infinitite cracks, eyes, chest core, crystals and sword edge at full brightness, so
 * they shine even in pitch darkness. Once it's enraged (phase 2) the glow layer switches to a
 * second texture with far more cracks blazing. The in-world size comes from the entity's SCALE
 * attribute (5x), so no extra scaling is needed here.
 */
@OnlyIn(Dist.CLIENT)
public class InfinititeMonstrosityRenderer
        extends MobRenderer<InfinititeMonstrosity, InfinititeMonstrosityRenderState, InfinititeMonstrosityModel> {

    /** Registered in {@code EntityRenderersEvent.RegisterLayerDefinitions}. */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "infinitite_monstrosity"),
        "main"
    );

    private static final ResourceLocation TEXTURE = texture("infinitite_monstrosity");
    private static final ResourceLocation GLOW = texture("infinitite_monstrosity_glow");
    private static final ResourceLocation GLOW_ENRAGED = texture("infinitite_monstrosity_glow_enraged");

    public InfinititeMonstrosityRenderer(EntityRendererProvider.Context context) {
        super(context, new InfinititeMonstrosityModel(context.bakeLayer(LAYER_LOCATION)), 1.2f /* shadow radius */);
        this.addLayer(new GlowLayer(this));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(InfinititeMonstrosityRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public InfinititeMonstrosityRenderState createRenderState() {
        return new InfinititeMonstrosityRenderState();
    }

    @Override
    public void extractRenderState(InfinititeMonstrosity entity, InfinititeMonstrosityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.attack = entity.getCurrentAttack();
        state.attackTicks = entity.getAttackTicks(partialTick);
        state.enraged = entity.isEnraged();
        state.attackTime = entity.getAttackAnim(partialTick);
    }

    /** The glowing cracks - drawn at full brightness on top of the body, like an Enderman's eyes. */
    private static final class GlowLayer extends RenderLayer<InfinititeMonstrosityRenderState, InfinititeMonstrosityModel> {

        GlowLayer(RenderLayerParent<InfinititeMonstrosityRenderState, InfinititeMonstrosityModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                           InfinititeMonstrosityRenderState state, float yRot, float xRot) {
            RenderType glow = RenderType.eyes(state.enraged ? GLOW_ENRAGED : GLOW);
            this.getParentModel().renderToBuffer(poseStack, bufferSource.getBuffer(glow), packedLight, OverlayTexture.NO_OVERLAY);
        }
    }
}
