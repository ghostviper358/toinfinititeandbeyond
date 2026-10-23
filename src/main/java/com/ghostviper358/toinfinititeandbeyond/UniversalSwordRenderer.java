package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The Universal Sword's GeckoLib renderer. Same as the stock GeoItemRenderer, except that in a
 * third-person hand it can rotate the sword around its grip, so the blade can point along the
 * arm during the combo's stab/spin and the overhead charge pose (vanilla always holds a sword
 * sticking out sideways from the fist). How far to rotate comes from
 * {@link UniversalSwordAnimations#bladeTilt}. It also makes the sword glow and pulse purple while
 * its wielder charges the shockwave.
 */
public class UniversalSwordRenderer extends GeoItemRenderer<UniversalSwordItem> {

    private static final DataTicket<Float> BLADE_TILT =
        DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_blade_tilt", Float.class);

    private static final DataTicket<Float> CHARGE_GLOW =
        DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_charge_glow", Float.class);

    /** The purple the sword pulses toward while charging (a tint can only darken, so this is the deepest point of each pulse). */
    private static final float GLOW_RED = 1.0f;
    private static final float GLOW_GREEN = 0.45f;
    private static final float GLOW_BLUE = 1.0f;

    /**
     * The middle of the grip, in the model's own space after GeckoLib centers it - see the
     * "root" bone's pivot in universal_sword.geo.json (GeckoLib mirrors geo x, hence the sign).
     */
    private static final float GRIP_X = -4.5f / 16.0f;
    private static final float GRIP_Y = -4.5f / 16.0f;

    public UniversalSwordRenderer() {
        super(new UniversalSwordModel());
    }

    @Override
    public void addRenderData(UniversalSwordItem animatable, RenderData renderData, GeoRenderState renderState) {
        ItemDisplayContext context = renderData.renderPerspective();
        float tilt = 0.0f;
        if (renderData.entity() != null) {
            float partialTick = renderState.getGeckolibData(DataTickets.PARTIAL_TICK);
            if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                tilt = UniversalSwordAnimations.bladeTilt(renderData.entity(), partialTick);
            } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                tilt = -UniversalSwordAnimations.bladeTilt(renderData.entity(), partialTick);
            }
        }
        renderState.addGeckolibData(BLADE_TILT, tilt);

        // Charge-up glow: the sword pulses purple, more strongly the longer it's charged.
        float glow = 0.0f;
        if (renderData.entity() != null) {
            float partialTick = renderState.getGeckolibData(DataTickets.PARTIAL_TICK);
            glow = UniversalSwordAnimations.chargeGlow(renderData.entity(), partialTick);
            if (glow > 0.0f) {
                float age = renderData.entity().tickCount + partialTick;
                float pulse = glow * (0.5f + 0.5f * Mth.sin(age * (0.4f + glow * 0.6f)));
                renderState.addGeckolibData(DataTickets.RENDER_COLOR, ARGB.colorFromFloat(1.0f,
                    Mth.lerp(pulse, 1.0f, GLOW_RED), Mth.lerp(pulse, 1.0f, GLOW_GREEN), Mth.lerp(pulse, 1.0f, GLOW_BLUE)));
            }
        }
        renderState.addGeckolibData(CHARGE_GLOW, glow);
    }

    /** While charging, render at full brightness so the sword glows even in the dark. */
    @Override
    public void render(GeoRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource) {
        Float glow = renderState.getGeckolibData(CHARGE_GLOW);
        if (glow != null && glow > 0.0f) {
            renderState.addGeckolibData(DataTickets.PACKED_LIGHT, LightTexture.FULL_BRIGHT);
        }
        super.render(renderState, poseStack, bufferSource);
    }

    @Override
    public void adjustPositionForRender(GeoRenderState renderState, PoseStack poseStack, BakedGeoModel model, boolean isReRender) {
        super.adjustPositionForRender(renderState, poseStack, model, isReRender);
        Float tilt = renderState.getGeckolibData(BLADE_TILT);
        if (isReRender || tilt == null || tilt == 0.0f) {
            return;
        }
        poseStack.translate(GRIP_X, GRIP_Y, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
        poseStack.translate(-GRIP_X, -GRIP_Y, 0.0f);
    }
}
