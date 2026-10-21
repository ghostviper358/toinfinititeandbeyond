package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The Universal Sword's GeckoLib renderer. Same as the stock GeoItemRenderer, except that in a
 * third-person hand it can rotate the sword around its grip, so the blade can point along the
 * arm during the combo's stab/spin and the overhead charge pose (vanilla always holds a sword
 * sticking out sideways from the fist). How far to rotate comes from
 * {@link UniversalSwordAnimations#bladeTilt}.
 */
public class UniversalSwordRenderer extends GeoItemRenderer<UniversalSwordItem> {

    private static final DataTicket<Float> BLADE_TILT =
        DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_blade_tilt", Float.class);

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
            float partialTick = renderState.getGeckolibData(software.bernie.geckolib.constant.DataTickets.PARTIAL_TICK);
            if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                tilt = UniversalSwordAnimations.bladeTilt(renderData.entity(), partialTick);
            } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                tilt = -UniversalSwordAnimations.bladeTilt(renderData.entity(), partialTick);
            }
        }
        renderState.addGeckolibData(BLADE_TILT, tilt);
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
