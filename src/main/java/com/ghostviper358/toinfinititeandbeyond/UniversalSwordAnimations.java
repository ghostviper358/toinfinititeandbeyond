package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-side attack combo and charge-up pose for the Universal Sword.
 * See docs/requirements/universal_sword_shockwave.md, "Part 1: The Animation".
 *
 * Every click (swing) plays the next move of a repeating four-hit combo:
 * stab -> slash from over the right shoulder -> slash from over the left shoulder -> full-body
 * spin. Pausing for
 * {@link #COMBO_RESET_TICKS} restarts the combo at the stab. Holding attack long enough to start
 * charging the shockwave raises the sword straight up overhead instead (see
 * UniversalSwordClientHandler for when charging starts).
 *
 * How the pieces fit together (all procedural - no GeckoLib animation files):
 * - Third person: {@link #getArmPose} hands the player renderer one of our custom arm poses, whose
 *   transformers move the arm/body. {@link #onRenderPlayerPre} switches off vanilla's own swing
 *   (it would otherwise be layered on top of ours) and spins the whole player for the spin move.
 *   {@link #bladeTilt} rotates the sword in the hand (see UniversalSwordRenderer) so the blade can
 *   point along the arm for the stab, spin and charge instead of sticking out sideways.
 * - First person: {@link #applyFirstPersonTransform} replaces vanilla's in-hand swing.
 *
 * The animation clock is each player's own tickCount, so moves play correctly for other players
 * too: swings already reach every client (vanilla's swing packet), and charging is mirrored to
 * nearby players by UniversalSwordNetworking.ChargeStatePacket.
 */
public final class UniversalSwordAnimations {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Move { NONE, STAB, SLASH_RIGHT, SLASH_LEFT, SPIN, CHARGE }

    /** The combo, in the order the moves play. */
    private static final Move[] COMBO = { Move.STAB, Move.SLASH_RIGHT, Move.SLASH_LEFT, Move.SPIN };

    /** How long each attack move lasts, in ticks (20 ticks = 1 second). */
    private static final float STAB_TICKS = 12.0f;
    private static final float SLASH_TICKS = 12.0f;
    private static final float SPIN_TICKS = 20.0f;

    /** How long the arm takes to rise into the overhead charge pose. */
    private static final float CHARGE_RAISE_TICKS = 8.0f;

    /** Ticks of not attacking (after a move ends) before the combo goes back to the stab. */
    private static final float COMBO_RESET_TICKS = 30.0f;

    /**
     * A click during the first part of a move is remembered and plays as soon as the current
     * move gets this far through, so clicking quickly never skips a move or cuts one off early.
     */
    private static final float NEXT_MOVE_AFTER = 0.6f;

    /** Blade tilt (degrees) that lines the blade up with the arm, pointing away from the shoulder. */
    private static final float BLADE_ALONG_ARM = 80.0f;

    /**
     * Third-person keyframes, one row per key: time (0-1 through the move), arm pitch, arm yaw
     * (negative = across the body), arm roll (radians), body twist (radians, negative = sword
     * shoulder forward), arm pushed forward (model units, negative = forward), blade tilt
     * (degrees). Written for the right arm; mirrored for left-handed players. Rest pose matches
     * vanilla's normal "holding an item" arm.
     */
    private static final float[] REST_3P = { -0.31f, 0, 0, 0, 0, 0 };

    private static final float[][] STAB_3P = {
        { 0.00f, -0.31f, 0.0f, 0.0f, 0.00f, 0.0f, 0 },
        { 0.30f, -0.90f, 0.2f, 0.1f, 0.35f, 3.0f, BLADE_ALONG_ARM }, // pull back
        { 0.45f, -1.57f, 0.0f, 0.0f, -0.50f, -5.0f, BLADE_ALONG_ARM }, // thrust
        { 0.75f, -1.57f, 0.0f, 0.0f, -0.50f, -5.0f, BLADE_ALONG_ARM }, // hold
        { 1.00f, -0.31f, 0.0f, 0.0f, 0.00f, 0.0f, 0 },
    };

    /** Over the right shoulder, cutting sideways and down across the body to the left hip. */
    private static final float[][] SLASH_RIGHT_3P = {
        { 0.00f, -0.31f, 0.0f, 0.0f, 0.00f, 0, 0 },
        { 0.30f, -2.90f, 0.5f, 0.3f, 0.40f, 0, 20 }, // raised up over the right shoulder
        { 0.55f, -0.80f, -1.1f, -0.2f, -0.50f, 0, 40 }, // cut across to the left hip
        { 0.70f, -0.80f, -1.1f, -0.2f, -0.50f, 0, 40 },
        { 1.00f, -0.31f, 0.0f, 0.0f, 0.00f, 0, 0 },
    };

    /** Over the left shoulder, cutting sideways and down across the body to the right hip. */
    private static final float[][] SLASH_LEFT_3P = {
        { 0.00f, -0.31f, 0.0f, 0.0f, 0.00f, 0, 0 },
        { 0.30f, -2.70f, -0.9f, -0.2f, -0.40f, 0, 20 }, // reached up over the left shoulder
        { 0.55f, -0.90f, 0.7f, 0.3f, 0.35f, 0, 40 }, // cut across to the right hip
        { 0.70f, -0.90f, 0.7f, 0.3f, 0.35f, 0, 40 },
        { 1.00f, -0.31f, 0.0f, 0.0f, 0.00f, 0, 0 },
    };

    private static final float[][] SPIN_3P = {
        { 0.00f, -0.31f, 0.0f, 0.00f, 0, 0, 0 },
        { 0.15f, -0.20f, 0.0f, 1.45f, 0, 0, BLADE_ALONG_ARM }, // arm straight out to the side
        { 0.85f, -0.20f, 0.0f, 1.45f, 0, 0, BLADE_ALONG_ARM },
        { 1.00f, -0.31f, 0.0f, 0.00f, 0, 0, 0 },
    };

    /** Arm pitch for the charge pose - just short of straight up, so it reads slightly forward. */
    private static final float CHARGE_ARM_PITCH = -3.0f;

    /** The spin's full-body turn happens between these points of the move. */
    private static final float SPIN_TURN_START = 0.10f;
    private static final float SPIN_TURN_END = 0.85f;

    /**
     * First-person keyframes, one row per key: time, offset x/y/z (screen space: +x right,
     * +y up, -z away from the camera), then pitch/yaw/roll in degrees (pitch -110 points the
     * blade straight ahead). Applied on top of vanilla's normal resting hand position. Written
     * for the right hand; mirrored for the left.
     */
    private static final float[][] STAB_1P = {
        { 0.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
        { 0.30f, 0.05f, -0.05f, 0.25f, -110, -5, 0 }, // pull back, blade pointing ahead
        { 0.45f, -0.20f, 0.05f, -0.70f, -110, -10, 0 }, // thrust
        { 0.75f, -0.20f, 0.05f, -0.70f, -110, -10, 0 }, // hold
        { 1.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
    };

    private static final float[][] SLASH_RIGHT_1P = {
        { 0.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
        { 0.30f, 0.15f, 0.45f, 0.10f, 30, 0, -20 }, // up over the right shoulder
        { 0.55f, -0.75f, -0.45f, -0.15f, -100, -40, 60 }, // cut down to the lower left
        { 0.70f, -0.75f, -0.45f, -0.15f, -100, -40, 60 },
        { 1.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
    };

    private static final float[][] SLASH_LEFT_1P = {
        { 0.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
        { 0.30f, -0.70f, 0.45f, 0.00f, 20, 0, 40 }, // up over the left shoulder
        { 0.55f, 0.20f, -0.40f, -0.15f, -100, 30, -50 }, // cut down to the lower right
        { 0.70f, 0.20f, -0.40f, -0.15f, -100, 30, -50 },
        { 1.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
    };

    /** The first-person spin points the blade ahead; the sweep itself is an orbit around the camera. */
    private static final float[][] SPIN_1P = {
        { 0.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
        { 0.15f, 0.00f, 0.05f, 0.00f, -110, 0, 0 },
        { 0.85f, 0.00f, 0.05f, 0.00f, -110, 0, 0 },
        { 1.00f, 0.00f, 0.00f, 0.00f, 0, 0, 0 },
    };

    /** First-person charge pose: hand lifted to the top of the screen, blade straight up. */
    private static final float[] CHARGE_1P = { -0.10f, 0.45f, 0.05f, 20, 0, 0 };

    private static final HumanoidModel.ArmPose STAB_POSE = HumanoidModel.ArmPose.create(
        "TOINFINITITEANDBEYOND_UNIVERSAL_STAB", false, (model, state, arm) -> poseThirdPerson(model, state, arm));
    private static final HumanoidModel.ArmPose SLASH_RIGHT_POSE = HumanoidModel.ArmPose.create(
        "TOINFINITITEANDBEYOND_UNIVERSAL_SLASH_RIGHT", false, (model, state, arm) -> poseThirdPerson(model, state, arm));
    private static final HumanoidModel.ArmPose SLASH_LEFT_POSE = HumanoidModel.ArmPose.create(
        "TOINFINITITEANDBEYOND_UNIVERSAL_SLASH_LEFT", false, (model, state, arm) -> poseThirdPerson(model, state, arm));
    private static final HumanoidModel.ArmPose SPIN_POSE = HumanoidModel.ArmPose.create(
        "TOINFINITITEANDBEYOND_UNIVERSAL_SPIN", false, (model, state, arm) -> poseThirdPerson(model, state, arm));
    private static final HumanoidModel.ArmPose CHARGE_POSE = HumanoidModel.ArmPose.create(
        "TOINFINITITEANDBEYOND_UNIVERSAL_CHARGE", false, (model, state, arm) -> poseThirdPerson(model, state, arm));

    /** Per-player combo progress. Weak keys so players who leave are forgotten automatically. */
    private static final Map<LivingEntity, ComboState> COMBOS = new WeakHashMap<>();

    /** Entity id -> that player's tickCount when they started charging. */
    private static final Map<Integer, Integer> CHARGE_STARTS = new HashMap<>();

    /**
     * The pose of the player currently being rendered. Minecraft extracts a player's render state
     * (which calls {@link #getArmPose}) and then immediately renders that same player, so this
     * carries the move's progress from one to the other - the arm pose transformer and the
     * RenderPlayerEvent hooks only see the render state, which has no reference to the player.
     */
    private static Sample current = Sample.NONE;

    /** Whether {@link #onRenderPlayerPre} pushed a spin rotation that the Post hook must pop. */
    private static boolean spinPushed = false;

    private static final class ComboState {
        int nextIndex = 0;
        Move move = Move.NONE;
        boolean queued = false;
        float startTime;
        float duration;
    }

    public record Sample(Move move, float progress, float ageInTicks) {
        static final Sample NONE = new Sample(Move.NONE, 0, 0);
    }

    public static final IClientItemExtensions CLIENT_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return UniversalSwordAnimations.getArmPose(entity, hand);
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                               float partialTick, float equipProcess, float swingProcess) {
            return applyFirstPersonTransform(poseStack, player, arm, partialTick, equipProcess);
        }
    };

    private UniversalSwordAnimations() {
    }

    /** Called from UniversalSwordItem#onEntitySwing on the client, for every player's swing. */
    public static void onSwing(LivingEntity entity) {
        ComboState combo = COMBOS.computeIfAbsent(entity, e -> new ComboState());
        float now = entity.tickCount;
        startQueuedMove(entity, combo, now);
        if (combo.move != Move.NONE) {
            float progress = (now - combo.startTime) / combo.duration;
            if (progress < NEXT_MOVE_AFTER) {
                combo.queued = true;
                return;
            }
            if (progress > 1.0f + COMBO_RESET_TICKS / combo.duration) {
                combo.nextIndex = 0;
            }
        }
        startMove(entity, combo, now);
    }

    /** Plays a remembered early click once the current move is far enough through. */
    private static void startQueuedMove(LivingEntity entity, ComboState combo, float now) {
        if (!combo.queued) {
            return;
        }
        float queuedStart = combo.startTime + combo.duration * NEXT_MOVE_AFTER;
        if (now >= queuedStart) {
            combo.queued = false;
            startMove(entity, combo, queuedStart);
        }
    }

    private static void startMove(LivingEntity entity, ComboState combo, float startTime) {
        combo.move = COMBO[combo.nextIndex];
        combo.nextIndex = (combo.nextIndex + 1) % COMBO.length;
        combo.startTime = startTime;
        combo.duration = switch (combo.move) {
            case STAB -> STAB_TICKS;
            case SPIN -> SPIN_TICKS;
            default -> SLASH_TICKS;
        };
        LOGGER.debug("Universal Sword combo: {} plays {}", entity.getName().getString(), combo.move);
    }

    /** Called for the local player by UniversalSwordClientHandler, and for others by the charge-state packet. */
    public static void setCharging(int entityId, boolean charging) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!charging || minecraft.level == null) {
            CHARGE_STARTS.remove(entityId);
            return;
        }
        var entity = minecraft.level.getEntity(entityId);
        CHARGE_STARTS.put(entityId, entity != null ? entity.tickCount : 0);
    }

    /** Forget everything when leaving a world - entity ids get reused between worlds. */
    public static void clearAll() {
        COMBOS.clear();
        CHARGE_STARTS.clear();
    }

    public static Sample sample(LivingEntity entity, float partialTick) {
        if (!(entity.getMainHandItem().getItem() instanceof UniversalSwordItem)) {
            return Sample.NONE;
        }
        float now = entity.tickCount + partialTick;

        Integer chargeStart = CHARGE_STARTS.get(entity.getId());
        if (chargeStart != null) {
            float raise = Mth.clamp((now - chargeStart) / CHARGE_RAISE_TICKS, 0.0f, 1.0f);
            return new Sample(Move.CHARGE, raise, now);
        }

        ComboState combo = COMBOS.get(entity);
        if (combo == null) {
            return Sample.NONE;
        }
        startQueuedMove(entity, combo, now);
        if (combo.move == Move.NONE) {
            return Sample.NONE;
        }
        float progress = (now - combo.startTime) / combo.duration;
        if (progress >= 1.0f || progress < 0.0f) {
            return Sample.NONE;
        }
        return new Sample(combo.move, progress, now);
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return null;
        }
        current = sample(entity, partialTick());
        return switch (current.move()) {
            case STAB -> STAB_POSE;
            case SLASH_RIGHT -> SLASH_RIGHT_POSE;
            case SLASH_LEFT -> SLASH_LEFT_POSE;
            case SPIN -> SPIN_POSE;
            case CHARGE -> CHARGE_POSE;
            case NONE -> null;
        };
    }

    private static boolean isOurPose(HumanoidModel.ArmPose pose) {
        return pose == STAB_POSE || pose == SLASH_RIGHT_POSE || pose == SLASH_LEFT_POSE
            || pose == SPIN_POSE || pose == CHARGE_POSE;
    }

    /**
     * Runs right before a player's model is posed. Turns off vanilla's swing while one of our moves
     * is playing (it would twist the arm and body on top of our pose), and turns the whole player
     * around for the spin move.
     */
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        PlayerRenderState state = event.getState();
        if (!isOurPose(state.rightArmPose) && !isOurPose(state.leftArmPose)) {
            return;
        }
        state.attackTime = 0.0f;

        if (current.move() == Move.SPIN) {
            float turn = smooth(Mth.clamp((current.progress() - SPIN_TURN_START) / (SPIN_TURN_END - SPIN_TURN_START), 0.0f, 1.0f));
            float side = state.mainArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * 360.0f * turn));
            spinPushed = true;
        }
    }

    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (spinPushed) {
            event.getPoseStack().popPose();
            spinPushed = false;
        }
    }

    private static void poseThirdPerson(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        float[] pose = thirdPersonPose(current);
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        ModelPart swordArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;

        float bodyTwist = side * pose[3];
        model.body.yRot = bodyTwist;
        // Keep both shoulders attached to the twisted body, the same way vanilla's swing does.
        model.rightArm.z = Mth.sin(bodyTwist) * 5.0f;
        model.rightArm.x = -Mth.cos(bodyTwist) * 5.0f;
        model.leftArm.z = -Mth.sin(bodyTwist) * 5.0f;
        model.leftArm.x = Mth.cos(bodyTwist) * 5.0f;
        model.leftArm.yRot += bodyTwist;

        swordArm.xRot = pose[0];
        swordArm.yRot = side * pose[1] + bodyTwist;
        swordArm.zRot = side * pose[2];
        swordArm.z += pose[4];

        if (current.move() == Move.CHARGE && current.progress() >= 1.0f) {
            // Fully charged: a faint tremble, like the sword is straining with power.
            swordArm.xRot += Mth.sin(current.ageInTicks() * 1.7f) * 0.03f;
            swordArm.zRot += side * Mth.cos(current.ageInTicks() * 2.3f) * 0.02f;
        }
    }

    /** Arm pitch, yaw, roll, body twist, forward push, blade tilt for this moment of the move. */
    private static float[] thirdPersonPose(Sample sample) {
        return switch (sample.move()) {
            case STAB -> interpolate(STAB_3P, sample.progress());
            case SLASH_RIGHT -> interpolate(SLASH_RIGHT_3P, sample.progress());
            case SLASH_LEFT -> interpolate(SLASH_LEFT_3P, sample.progress());
            case SPIN -> interpolate(SPIN_3P, sample.progress());
            case CHARGE -> {
                float raise = smooth(sample.progress());
                yield new float[] {
                    Mth.lerp(raise, REST_3P[0], CHARGE_ARM_PITCH), 0, 0.1f * raise, 0, 0, BLADE_ALONG_ARM * raise };
            }
            case NONE -> REST_3P;
        };
    }

    /**
     * How far (degrees) to rotate the sword around its grip in a third-person hand right now - see
     * UniversalSwordRenderer. Positive tilts the blade from pointing forward toward pointing along
     * the arm.
     */
    public static float bladeTilt(LivingEntity entity, float partialTick) {
        return thirdPersonPose(sample(entity, partialTick))[5];
    }

    private static boolean applyFirstPersonTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                                     float partialTick, float equipProcess) {
        Sample sample = sample(player, partialTick);
        if (sample.move() == Move.NONE) {
            return false;
        }
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;

        if (sample.move() == Move.SPIN) {
            // Orbit the sword all the way around the camera - it sweeps across the screen, behind
            // you, and back in from the other side.
            float turn = smooth(Mth.clamp((sample.progress() - SPIN_TURN_START) / (SPIN_TURN_END - SPIN_TURN_START), 0.0f, 1.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 360.0f * turn));
        }

        // Vanilla's resting hand position (ItemInHandRenderer#applyItemArmTransform).
        poseStack.translate(side * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);

        float[] key;
        if (sample.move() == Move.CHARGE) {
            float raise = smooth(sample.progress());
            key = new float[CHARGE_1P.length];
            for (int i = 0; i < key.length; i++) {
                key[i] = CHARGE_1P[i] * raise;
            }
            if (sample.progress() >= 1.0f) {
                key[0] += Mth.sin(sample.ageInTicks() * 1.7f) * 0.004f;
                key[1] += Mth.cos(sample.ageInTicks() * 2.3f) * 0.004f;
            }
        } else {
            float[][] keys = switch (sample.move()) {
                case STAB -> STAB_1P;
                case SLASH_RIGHT -> SLASH_RIGHT_1P;
                case SLASH_LEFT -> SLASH_LEFT_1P;
                default -> SPIN_1P;
            };
            key = interpolate(keys, sample.progress());
        }

        poseStack.translate(side * key[0], key[1], key[2]);
        poseStack.mulPose(Axis.XP.rotationDegrees(key[3]));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * key[4]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * key[5]));
        return true;
    }

    /** Smoothly blends between the two keyframes either side of {@code t} (column 0 is the time). */
    private static float[] interpolate(float[][] keys, float t) {
        int width = keys[0].length - 1;
        for (int i = 0; i < keys.length - 1; i++) {
            float[] a = keys[i];
            float[] b = keys[i + 1];
            if (t <= b[0]) {
                float local = smooth(Mth.clamp((t - a[0]) / (b[0] - a[0]), 0.0f, 1.0f));
                float[] out = new float[width];
                for (int j = 0; j < width; j++) {
                    out[j] = Mth.lerp(local, a[j + 1], b[j + 1]);
                }
                return out;
            }
        }
        return java.util.Arrays.copyOfRange(keys[keys.length - 1], 1, width + 1);
    }

    /** Ease in and out, so moves accelerate and settle instead of moving at a constant speed. */
    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
