package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.HashMap;
import java.util.Map;

/**
 * The Black Hole Arm Cannon's renderer. Everything that moves is moved here in code (like
 * UniversalCoreRenderer), because it depends on the cannon's ammo and on how long it's been held:
 *
 * - Picking it up "brings it to life": the four fins slide out past the barrel and the black
 *   hole grows between their tips. Putting it away (or seeing it in the inventory) folds the
 *   fins back and the black hole disappears.
 * - The black hole spins - its black inside one way, its see-through purple outside the other,
 *   gently pulsing. While it's recharging an empty cannon it spins much faster and pulses harder.
 * - The ammo floats around the black hole as little void blocks, one for each quarter of the
 *   ammo left, so you can see it running out.
 * - In first person, aiming down the sight (holding left-click - see
 *   BlackHoleArmCannonClientHandler) slides the cannon to the middle of the screen.
 */
public class BlackHoleArmCannonRenderer extends GeoItemRenderer<BlackHoleArmCannonItem> {

    private static final DataTicket<Float> DEPLOY = DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_cannon_deploy", Float.class);
    private static final DataTicket<Float> SPIN = DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_cannon_spin", Float.class);
    private static final DataTicket<Boolean> RECHARGING = DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_cannon_recharging", Boolean.class);
    private static final DataTicket<Integer> AMMO_BLOCKS = DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_cannon_ammo_blocks", Integer.class);
    private static final DataTicket<Float> AIM_SHIFT = DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_cannon_aim_shift", Float.class);

    /** How far back the fins slide when put away (matches FIN_RETRACT in gen_black_hole_arm_cannon_model.py). */
    private static final float FIN_RETRACT = 9.0f;
    private static final String[] FINS = {"fin_ne", "fin_nw", "fin_se", "fin_sw"};
    private static final int AMMO_BLOCK_COUNT = 4;

    /** Seconds it takes the fins to slide out and the black hole to appear. */
    private static final float DEPLOY_SECONDS = 0.4f;
    /** Black hole turns per second, normally and while recharging. */
    private static final float SPIN_TURNS_PER_SECOND = 0.4f;
    private static final float RECHARGE_TURNS_PER_SECOND = 2.5f;

    /**
     * How far the cannon slides toward the middle of the screen when fully aimed, in blocks (the
     * first-person hand sits 0.56 to the side and 0.52 down from the middle), before the 0.6
     * first-person scale in models/item/black_hole_arm_cannon.json.
     */
    private static final float AIM_SIDEWAYS = 0.56f / 0.6f;
    private static final float AIM_UP = 0.25f / 0.6f;

    /** Fins and spin per holder (entity id), so each player's cannon comes to life on its own. */
    private static final Map<Integer, State> STATES = new HashMap<>();

    private static final class State {
        float deploy;
        float spinTurns;
        long lastMillis = Util.getMillis();
    }

    public BlackHoleArmCannonRenderer() {
        super(new BlackHoleArmCannonModel());
    }

    /** Called when a player puts the cannon away, so it comes to life again from folded next time. */
    public static void forget(int holderId) {
        STATES.remove(holderId);
    }

    private static boolean inHand(ItemDisplayContext context) {
        return context.firstPerson()
            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Override
    public void addRenderData(BlackHoleArmCannonItem animatable, RenderData renderData, GeoRenderState renderState) {
        ItemStack stack = renderData.itemStack();
        LivingEntity holder = renderData.entity();
        ItemDisplayContext context = renderData.renderPerspective();
        boolean recharging = BlackHoleArmCannonItem.isRecharging(stack);
        int ammo = BlackHoleArmCannonItem.getAmmo(stack);

        float deploy = 0.0f;
        float spin = 0.0f;
        if (holder != null && inHand(context)) {
            State state = STATES.computeIfAbsent(holder.getId(), id -> new State());
            long now = Util.getMillis();
            float seconds = Math.min((now - state.lastMillis) / 1000.0f, 0.25f);
            state.lastMillis = now;
            state.deploy = Math.min(1.0f, state.deploy + seconds / DEPLOY_SECONDS);
            state.spinTurns = (state.spinTurns + seconds * (recharging ? RECHARGE_TURNS_PER_SECOND : SPIN_TURNS_PER_SECOND)) % 1000.0f;
            deploy = state.deploy;
            spin = state.spinTurns;
        }

        float aimShift = 0.0f;
        if (context.firstPerson() && holder == Minecraft.getInstance().player) {
            float partialTick = renderState.getGeckolibData(DataTickets.PARTIAL_TICK);
            float aim = BlackHoleArmCannonClientHandler.aimProgress(partialTick);
            aimShift = context.leftHand() ? aim : -aim;
        }

        renderState.addGeckolibData(DEPLOY, deploy);
        renderState.addGeckolibData(SPIN, spin);
        renderState.addGeckolibData(RECHARGING, recharging);
        renderState.addGeckolibData(AMMO_BLOCKS, Mth.ceil(ammo * (float) AMMO_BLOCK_COUNT / BlackHoleArmCannonItem.MAX_AMMO));
        renderState.addGeckolibData(AIM_SHIFT, aimShift);
    }

    @Override
    public void adjustPositionForRender(GeoRenderState renderState, PoseStack poseStack, BakedGeoModel model, boolean isReRender) {
        super.adjustPositionForRender(renderState, poseStack, model, isReRender);
        Float aimShift = renderState.getGeckolibData(AIM_SHIFT);
        if (isReRender || aimShift == null || aimShift == 0.0f) {
            return;
        }
        poseStack.translate(aimShift * AIM_SIDEWAYS, Math.abs(aimShift) * AIM_UP, 0.0f);
    }

    @Override
    public void preRender(GeoRenderState renderState, PoseStack poseStack, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(renderState, poseStack, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, renderColor);
        float deployRaw = orZero(renderState.getGeckolibData(DEPLOY));
        float deploy = deployRaw * deployRaw * (3.0f - 2.0f * deployRaw); // ease in and out
        float angle = orZero(renderState.getGeckolibData(SPIN)) * Mth.TWO_PI;
        boolean recharging = Boolean.TRUE.equals(renderState.getGeckolibData(RECHARGING));
        Integer ammoBlocks = renderState.getGeckolibData(AMMO_BLOCKS);
        int shownAmmo = ammoBlocks == null ? AMMO_BLOCK_COUNT : ammoBlocks;

        // The fins slide out along the barrel.
        for (String fin : FINS) {
            model.getBone(fin).ifPresent(bone -> bone.setPosZ(FIN_RETRACT * (1.0f - deploy)));
        }

        // The black hole grows in as the fins come out, then spins and pulses.
        boolean shown = deploy > 0.02f;
        float pulse = 1.0f + (recharging ? 0.18f : 0.07f) * Mth.sin(angle * 3.0f);
        model.getBone("black_hole").ifPresent(bone -> {
            bone.setHidden(!shown);
            bone.setScaleX(deploy);
            bone.setScaleY(deploy);
            bone.setScaleZ(deploy);
        });
        model.getBone("black_hole_core").ifPresent(bone -> {
            bone.setRotX(angle * 0.7f);
            bone.setRotY(angle);
        });
        model.getBone("black_hole_shell").ifPresent(bone -> {
            bone.setRotZ(-angle * 1.3f);
            bone.setRotY(-angle * 0.5f);
            bone.setScaleX(pulse);
            bone.setScaleY(pulse);
            bone.setScaleZ(pulse);
        });

        // The ammo orbits the black hole - one block for each quarter of the ammo left.
        model.getBone("ammo_ring").ifPresent(bone -> {
            bone.setHidden(!shown);
            bone.setRotZ(angle * 0.8f);
            bone.setScaleX(deploy);
            bone.setScaleY(deploy);
            bone.setScaleZ(deploy);
        });
        for (int i = 0; i < AMMO_BLOCK_COUNT; i++) {
            boolean hasAmmo = i < shownAmmo;
            float tumble = angle * 2.0f + i;
            model.getBone("ammo_" + i).ifPresent(bone -> {
                bone.setHidden(!hasAmmo);
                bone.setRotX(tumble);
                bone.setRotY(tumble * 0.6f);
            });
        }
    }

    private static float orZero(@Nullable Float value) {
        return value == null ? 0.0f : value;
    }
}
