package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Points GeckoLib at the Universal Sword's 3D model, texture, and animation files.
 * See docs/requirements/universal_sword_shockwave.md, "Part 1: The Animation".
 *
 * File locations (GeckoLib's fixed convention):
 * - Model:     assets/toinfinititeandbeyond/geckolib/models/item/universal_sword.geo.json
 * - Animation: assets/toinfinititeandbeyond/geckolib/animations/item/universal_sword.animation.json
 * - Texture:   assets/toinfinititeandbeyond/textures/item/universal_sword_3d.png (a separate
 *   file from universal_sword.png, which is the flat 2D inventory-icon frame-strip used before
 *   GeckoLib - the 3D model needs its own UV-mapped skin, not a frame-strip animation).
 */
public class UniversalSwordModel extends GeoModel<UniversalSwordItem> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "item/universal_sword");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "textures/item/universal_sword_3d.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
        ToInfinititeAndBeyond.MOD_ID, "item/universal_sword");

    @Override
    public ResourceLocation getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(UniversalSwordItem animatable) {
        return ANIMATIONS;
    }
}
