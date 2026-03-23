package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Renderer for the Infinitite Monstrosity.
 *
 * Wires together the entity, its render state, and the custom model.
 * The in-world size is handled by the SCALE attribute (3.0) on the entity,
 * so no extra PoseStack scaling is needed here.
 */
@OnlyIn(Dist.CLIENT)
public class InfinititeMonstrosityRenderer
        extends MobRenderer<InfinititeMonstrosity, LivingEntityRenderState, InfinititeMonstrosityModel> {

    /** Registered in {@code EntityRenderersEvent.RegisterLayerDefinitions}. */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "infinitite_monstrosity"),
        "main"
    );

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID,
        "textures/entity/infinitite_monstrosity.png"
    );

    public InfinititeMonstrosityRenderer(EntityRendererProvider.Context context) {
        super(context,
            new InfinititeMonstrosityModel(context.bakeLayer(LAYER_LOCATION)),
            0.8f /* shadow radius */);
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntityRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}