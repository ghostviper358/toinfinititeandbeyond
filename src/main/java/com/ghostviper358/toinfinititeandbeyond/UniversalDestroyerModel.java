package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 3D model for the Universal Destroyer: a horned titan of black stone veined with glowing purple,
 * a crown of broken black spikes, spiked shoulder plates, spikes down its back, a belt with a
 * glowing buckle and a chest plate with a glowing core. Knees and elbows bend. It wields a
 * colossal black-metal sword carved with runes, winged guard, a Universal Core set in it - and
 * the top half of the blade can snap off (the finale, see UniversalDestroyer).
 *
 * The whole body is built {@link #HOVER} pixels up off the ground - with the entity's 20x SCALE
 * that's 5 blocks - so it floats above the ground while the entity itself (and its hitbox, which
 * reaches all the way down) stays on the ground, where players can still hit it.
 *
 * Texture sheet: 128 x 128 units (painted at 4x detail, 512 x 512 pixels, by
 * gen_universal_destroyer_texture.py - the cube list there must match this one).
 *
 * Part tree:
 *   waist (+ belt, buckle, tassets)
 *     chest (+ plate, core, backSpike1-3)
 *       head (+ brow, jaw, spike1-6, rightHorn/leftHorn (+ tips))
 *       rightArm (+ pauldron spikes) > rightForearm > sword > bladeTop
 *       leftArm (+ pauldron spikes) > leftForearm
 *   rightThigh > rightShin, leftThigh > leftShin
 */
@OnlyIn(Dist.CLIENT)
public class UniversalDestroyerModel extends EntityModel<UniversalDestroyerRenderState> {

    /** How far (in model pixels) the body floats off the ground. 4 px x 20 SCALE = 5 blocks. */
    private static final float HOVER = 4.0f;
    /** Kneeling lowers the body this far, so its knees rest on the ground. */
    private static final float KNEEL_DROP = 7.5f + HOVER;
    private static final float ARM_REST = -0.15f;

    private final ModelPart waist;
    private final ModelPart chest;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart sword;
    private final ModelPart bladeTop;
    private final ModelPart rightThigh;
    private final ModelPart rightShin;
    private final ModelPart leftThigh;
    private final ModelPart leftShin;

    public UniversalDestroyerModel(ModelPart root) {
        super(root);
        this.waist = root.getChild("waist");
        this.chest = waist.getChild("chest");
        this.head = chest.getChild("head");
        this.rightArm = chest.getChild("rightArm");
        this.rightForearm = rightArm.getChild("rightForearm");
        this.leftArm = chest.getChild("leftArm");
        this.leftForearm = leftArm.getChild("leftForearm");
        this.sword = rightForearm.getChild("sword");
        this.bladeTop = sword.getChild("bladeTop");
        this.rightThigh = root.getChild("rightThigh");
        this.rightShin = rightThigh.getChild("rightShin");
        this.leftThigh = root.getChild("leftThigh");
        this.leftShin = leftThigh.getChild("leftShin");
    }

    /** Called once during Forge model layer registration (client-side). */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition waist = root.addOrReplaceChild("waist",
            CubeListBuilder.create()
                .texOffs(78, 0).addBox(-6.5f, -4.0f, -4.0f, 13, 4, 8, none)      // belt
                .texOffs(70, 14).addBox(-2.0f, -3.5f, -4.5f, 4, 3, 1, none)      // glowing buckle
                .texOffs(52, 20).addBox(-8.0f, -1.0f, -3.5f, 3, 5, 7, none)      // hip plates
                .texOffs(52, 20).addBox(5.0f, -1.0f, -3.5f, 3, 5, 7, none),
            PartPose.offset(0.0f, 11.0f - HOVER, 0.0f));

        PartDefinition chest = waist.addOrReplaceChild("chest",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0f, -11.0f, -4.5f, 16, 11, 9, none)     // chest
                .texOffs(0, 20).addBox(-7.0f, -10.0f, -5.5f, 14, 6, 1, none)     // chest plate
                .texOffs(120, 0).addBox(-1.5f, -8.5f, -6.5f, 3, 3, 1, none),     // glowing core
            PartPose.offset(0.0f, -4.0f, 0.0f));

        // Spikes down its back, leaning backward.
        CubeListBuilder bigSpike = CubeListBuilder.create().texOffs(44, 27).addBox(-1.0f, -5.0f, -1.0f, 2, 5, 2, none);
        CubeListBuilder smallSpike = CubeListBuilder.create().texOffs(120, 8).addBox(-0.5f, -3.0f, -0.5f, 1, 3, 1, none);
        chest.addOrReplaceChild("backSpike1", bigSpike, PartPose.offsetAndRotation(-4.0f, -9.0f, 4.5f, -0.9f, 0.0f, -0.2f));
        chest.addOrReplaceChild("backSpike2", bigSpike, PartPose.offsetAndRotation(4.0f, -9.0f, 4.5f, -0.9f, 0.0f, 0.2f));
        chest.addOrReplaceChild("backSpike3", bigSpike, PartPose.offsetAndRotation(0.0f, -5.0f, 4.5f, -1.1f, 0.0f, 0.0f));

        PartDefinition head = chest.addOrReplaceChild("head",
            CubeListBuilder.create()
                .texOffs(50, 0).addBox(-3.5f, -7.0f, -3.5f, 7, 7, 7, none)      // head
                .texOffs(50, 14).addBox(-4.0f, -6.0f, -4.5f, 8, 2, 2, none)     // heavy brow
                .texOffs(30, 20).addBox(-3.0f, -1.0f, -4.0f, 6, 2, 5, none),    // jutting jaw
            PartPose.offset(0.0f, -11.0f, -0.5f));

        // The crown: broken black spikes, leaning every which way.
        head.addOrReplaceChild("spike1", bigSpike, PartPose.offsetAndRotation(0.0f, -7.0f, -2.5f, 0.25f, 0.0f, 0.0f));
        head.addOrReplaceChild("spike2", bigSpike, PartPose.offsetAndRotation(-2.5f, -7.0f, -1.0f, 0.0f, 0.0f, -0.35f));
        head.addOrReplaceChild("spike3", bigSpike, PartPose.offsetAndRotation(2.5f, -7.0f, -1.0f, 0.0f, 0.0f, 0.35f));
        head.addOrReplaceChild("spike4", smallSpike, PartPose.offsetAndRotation(-3.0f, -7.0f, 2.0f, -0.3f, 0.0f, -0.6f));
        head.addOrReplaceChild("spike5", smallSpike, PartPose.offsetAndRotation(3.0f, -7.0f, 2.0f, -0.3f, 0.0f, 0.6f));
        head.addOrReplaceChild("spike6", smallSpike, PartPose.offsetAndRotation(0.0f, -7.0f, 2.5f, -0.4f, 0.0f, 0.0f));

        // Great horns sweeping out from the sides of its head, then curling up.
        CubeListBuilder horn = CubeListBuilder.create().texOffs(116, 20).addBox(-1.0f, -6.0f, -1.0f, 2, 6, 2, none);
        CubeListBuilder hornTip = CubeListBuilder.create().texOffs(124, 8).addBox(-0.5f, -4.0f, -0.5f, 1, 4, 1, none);
        head.addOrReplaceChild("rightHorn", horn, PartPose.offsetAndRotation(-3.5f, -5.0f, 0.0f, -0.2f, 0.0f, -1.0f))
            .addOrReplaceChild("rightHornTip", hornTip, PartPose.offsetAndRotation(0.0f, -5.5f, 0.0f, 0.0f, 0.0f, 0.7f));
        head.addOrReplaceChild("leftHorn", horn, PartPose.offsetAndRotation(3.5f, -5.0f, 0.0f, -0.2f, 0.0f, 1.0f))
            .addOrReplaceChild("leftHornTip", hornTip, PartPose.offsetAndRotation(0.0f, -5.5f, 0.0f, 0.0f, 0.0f, -0.7f));

        PartDefinition rightArm = chest.addOrReplaceChild("rightArm",
            CubeListBuilder.create()
                .texOffs(78, 34).addBox(-5.0f, -3.0f, -4.0f, 7, 5, 8, none)     // shoulder plate
                .texOffs(0, 40).addBox(-3.5f, 2.0f, -2.5f, 5, 7, 5, none),      // upper arm
            PartPose.offsetAndRotation(-10.0f, -9.0f, 0.0f, ARM_REST, 0.0f, 0.0f));
        rightArm.addOrReplaceChild("rightPauldronSpike1", bigSpike, PartPose.offsetAndRotation(-3.0f, -3.0f, 1.0f, 0.0f, 0.0f, -0.5f));
        rightArm.addOrReplaceChild("rightPauldronSpike2", smallSpike, PartPose.offsetAndRotation(-1.0f, -3.0f, -2.0f, 0.3f, 0.0f, -0.2f));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("rightForearm",
            CubeListBuilder.create().texOffs(20, 40).addBox(-3.0f, 0.0f, -3.0f, 6, 9, 6, none), // gauntlet
            PartPose.offset(-1.0f, 9.0f, 0.0f));

        // The colossal sword, gripped in the right fist. Built pointing up, then turned to point forward.
        PartDefinition sword = rightForearm.addOrReplaceChild("sword",
            CubeListBuilder.create()
                .texOffs(22, 27).addBox(-1.0f, -2.0f, -1.0f, 2, 9, 2, none)     // handle
                .texOffs(110, 12).addBox(-1.5f, 7.0f, -1.5f, 3, 2, 3, none)     // pommel
                .texOffs(80, 12).addBox(-6.0f, -4.0f, -1.5f, 12, 2, 3, none)    // guard
                .texOffs(14, 27).addBox(-7.0f, -7.0f, -1.0f, 2, 4, 2, none)     // guard wings
                .texOffs(14, 27).addBox(5.0f, -7.0f, -1.0f, 2, 4, 2, none)
                .texOffs(30, 27).addBox(-1.5f, -5.0f, -2.0f, 3, 3, 4, none)     // the Universal Core
                .texOffs(108, 34).addBox(-2.5f, -18.0f, -0.5f, 5, 14, 1, none), // lower blade
            PartPose.offsetAndRotation(0.0f, 7.5f, 0.0f, Mth.HALF_PI, 0.0f, 0.0f));
        sword.addOrReplaceChild("bladeTop",
            CubeListBuilder.create()
                .texOffs(52, 44).addBox(-2.5f, -32.0f, -0.5f, 5, 14, 1, none)   // upper blade (snaps off)
                .texOffs(120, 4).addBox(-1.5f, -35.0f, -0.5f, 3, 3, 1, none),   // tip
            PartPose.ZERO);

        PartDefinition leftArm = chest.addOrReplaceChild("leftArm",
            CubeListBuilder.create()
                .texOffs(78, 34).addBox(-2.0f, -3.0f, -4.0f, 7, 5, 8, none)
                .texOffs(0, 40).addBox(-1.5f, 2.0f, -2.5f, 5, 7, 5, none),
            PartPose.offsetAndRotation(10.0f, -9.0f, 0.0f, ARM_REST, 0.0f, 0.0f));
        leftArm.addOrReplaceChild("leftPauldronSpike1", bigSpike, PartPose.offsetAndRotation(3.0f, -3.0f, 1.0f, 0.0f, 0.0f, 0.5f));
        leftArm.addOrReplaceChild("leftPauldronSpike2", smallSpike, PartPose.offsetAndRotation(1.0f, -3.0f, -2.0f, 0.3f, 0.0f, 0.2f));
        leftArm.addOrReplaceChild("leftForearm",
            CubeListBuilder.create().texOffs(20, 40).addBox(-3.0f, 0.0f, -3.0f, 6, 9, 6, none),
            PartPose.offset(1.0f, 9.0f, 0.0f));

        CubeListBuilder thigh = CubeListBuilder.create().texOffs(72, 20).addBox(-3.0f, 0.0f, -3.0f, 6, 7, 6, none);
        CubeListBuilder shin = CubeListBuilder.create()
            .texOffs(96, 20).addBox(-2.5f, 0.0f, -2.5f, 5, 6, 5, none)      // shin
            .texOffs(0, 27).addBox(-3.0f, -1.0f, -3.5f, 6, 3, 1, none)      // knee plate
            .texOffs(52, 34).addBox(-3.0f, 4.0f, -4.0f, 6, 2, 7, none);     // armored foot
        root.addOrReplaceChild("rightThigh", thigh, PartPose.offset(-4.0f, 11.0f - HOVER, 0.0f))
            .addOrReplaceChild("rightShin", shin, PartPose.offset(0.0f, 7.0f, 0.0f));
        root.addOrReplaceChild("leftThigh", thigh, PartPose.offset(4.0f, 11.0f - HOVER, 0.0f))
            .addOrReplaceChild("leftShin", shin, PartPose.offset(0.0f, 7.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(UniversalDestroyerRenderState state) {
        super.setupAnim(state); // back to the resting pose built above

        bladeTop.visible = state.finale == 0
            && !(state.attack == UniversalDestroyer.Attack.SWORD_BREAK && state.attackTicks >= 24);

        // Floating: the whole body bobs slowly up and down, legs dangling and swaying.
        float bob = Mth.sin(state.ageInTicks * 0.05f) * 0.6f;
        waist.y += bob;
        rightThigh.y += bob;
        leftThigh.y += bob;
        float drift = state.walkAnimationSpeed;
        rightThigh.xRot = -0.1f + Mth.sin(state.ageInTicks * 0.05f) * 0.06f + drift * 0.3f;
        leftThigh.xRot = -0.15f + Mth.sin(state.ageInTicks * 0.05f + 1.0f) * 0.06f + drift * 0.3f;
        rightShin.xRot = 0.25f + Mth.sin(state.ageInTicks * 0.05f + 0.5f) * 0.05f;
        leftShin.xRot = 0.3f + Mth.sin(state.ageInTicks * 0.05f + 1.5f) * 0.05f;
        rightArm.xRot += Mth.sin(state.ageInTicks * 0.05f) * 0.04f;
        leftArm.xRot += Mth.sin(state.ageInTicks * 0.05f + 1.5f) * 0.04f;
        rightForearm.xRot = -0.3f;
        leftForearm.xRot = -0.2f;
        chest.xRot = drift * 0.15f; // leaning into its glide

        head.yRot = Mth.clamp(state.yRot, -45.0f, 45.0f) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(state.xRot, -30.0f, 30.0f) * Mth.DEG_TO_RAD;

        poseSpecialAttack(state);
        poseKneel(state);
    }

    /** Poses for the special attacks, driven by how many ticks into the attack it is. */
    private void poseSpecialAttack(UniversalDestroyerRenderState state) {
        float t = state.attackTicks;
        switch (state.attack) {
            case TRANSITION -> {
                // A roar: arms flung wide and up, head thrown back, shaking with fury.
                float open = ease(Mth.clamp(t / 12.0f, 0.0f, 1.0f)) * (t > 48 ? ease(Mth.clamp((60 - t) / 12.0f, 0.0f, 1.0f)) : 1.0f);
                rightArm.zRot = 1.3f * open;
                leftArm.zRot = -1.3f * open;
                rightArm.xRot = Mth.lerp(open, ARM_REST, -1.0f);
                leftArm.xRot = Mth.lerp(open, ARM_REST, -1.0f);
                rightForearm.xRot = -0.9f * open;
                leftForearm.xRot = -0.9f * open;
                chest.xRot -= 0.35f * open;
                head.xRot = -0.7f * open;
                chest.yRot = Mth.sin(state.ageInTicks * 2.0f) * 0.03f * open;
            }
            case SWORD_SWEEP -> {
                // Winds back, slashes across its front at tick 22, then backhands at tick 36.
                float twist = t < 18 ? Mth.lerp(ease(t / 18.0f), 0.0f, 0.9f)
                    : t < 24 ? Mth.lerp((t - 18) / 6.0f, 0.9f, -1.0f)
                    : t < 32 ? -1.0f
                    : t < 38 ? Mth.lerp((t - 32) / 6.0f, -1.0f, 0.8f)
                    : Mth.lerp(ease(Mth.clamp((t - 38) / 12.0f, 0.0f, 1.0f)), 0.8f, 0.0f);
                chest.yRot = twist;
                rightArm.xRot = -1.4f;
                rightArm.zRot = 0.4f;
                rightForearm.xRot = -0.1f;
            }
            case OVERHEAD_SMASH -> {
                // Both hands raise the sword high, then bring it down at tick 28.
                float arms = t < 18 ? Mth.lerp(ease(t / 18.0f), ARM_REST, -2.9f)
                    : t < 24 ? -2.9f
                    : t < 28 ? Mth.lerp((t - 24) / 4.0f, -2.9f, -0.6f)
                    : t < 36 ? -0.6f
                    : Mth.lerp(ease(Mth.clamp((t - 36) / 9.0f, 0.0f, 1.0f)), -0.6f, ARM_REST);
                rightArm.xRot = arms;
                leftArm.xRot = arms;
                leftArm.yRot = 0.5f;
                rightForearm.xRot = t < 24 ? -0.5f : -0.1f;
                leftForearm.xRot = rightForearm.xRot;
                chest.xRot += t >= 28 && t < 36 ? 0.3f : 0.0f;
            }
            case RUNE_CIRCLES -> {
                // Its left hand reaches out, casting; the runes erupt at tick 35.
                float reach = ease(Mth.clamp(t / 10.0f, 0.0f, 1.0f));
                leftArm.xRot = Mth.lerp(reach, ARM_REST, -1.5f) + (t >= 35 && t < 40 ? -0.6f : 0.0f);
                leftArm.zRot = -0.3f * reach;
                leftForearm.xRot = -0.5f * reach;
                head.xRot = 0.4f * reach;
            }
            case STAR_FALL -> {
                // Raises its sword to the sky, calling the stars down.
                float raise = ease(Mth.clamp(t / 10.0f, 0.0f, 1.0f)) * (t > 52 ? ease(Mth.clamp((60 - t) / 8.0f, 0.0f, 1.0f)) : 1.0f);
                rightArm.xRot = Mth.lerp(raise, ARM_REST, -3.0f);
                rightForearm.xRot = Mth.lerp(raise, -0.3f, 0.0f);
                sword.xRot = Mth.lerp(raise, Mth.HALF_PI, 0.0f);
                head.xRot = -0.6f * raise;
                chest.xRot -= 0.2f * raise;
            }
            case SWORD_WAVE -> {
                // Raised high, then a downward slash at tick 20 that sends the wave out.
                float arm = t < 16 ? Mth.lerp(ease(t / 16.0f), ARM_REST, -2.7f)
                    : t < 21 ? Mth.lerp((t - 16) / 5.0f, -2.7f, 0.4f)
                    : Mth.lerp(ease(Mth.clamp((t - 21) / 24.0f, 0.0f, 1.0f)), 0.4f, ARM_REST);
                rightArm.xRot = arm;
                chest.xRot += t >= 18 && t < 26 ? 0.3f : 0.0f;
            }
            case GRAVITY_CRUSH -> {
                // Arms reach out, dragging everyone in, then both fists raised and slammed down at tick 35.
                float arms = t < 30 ? Mth.lerp(ease(Mth.clamp(t / 8.0f, 0.0f, 1.0f)), ARM_REST, -1.5f)
                    : t < 33 ? Mth.lerp((t - 30) / 3.0f, -1.5f, -2.9f)
                    : t < 36 ? Mth.lerp((t - 33) / 3.0f, -2.9f, 0.3f)
                    : Mth.lerp(ease(Mth.clamp((t - 36) / 19.0f, 0.0f, 1.0f)), 0.3f, ARM_REST);
                rightArm.xRot = arms;
                leftArm.xRot = arms;
                if (t < 30) {
                    // Clawing at the air, pulling.
                    rightArm.zRot = Mth.sin(t * 0.6f) * 0.15f;
                    leftArm.zRot = -Mth.sin(t * 0.6f) * 0.15f;
                    rightForearm.xRot = -0.6f + Mth.sin(t * 0.6f) * 0.3f;
                    leftForearm.xRot = -0.6f - Mth.sin(t * 0.6f) * 0.3f;
                }
            }
            case BLACK_HOLE -> {
                // Sword raised, then stabbed down at tick 20 and held, tearing the black hole open.
                float arm = t < 20 ? Mth.lerp(ease(t / 20.0f), ARM_REST, -2.4f)
                    : t < 23 ? Mth.lerp((t - 20) / 3.0f, -2.4f, -0.2f)
                    : -0.2f;
                rightArm.xRot = arm;
                leftArm.xRot = arm * 0.8f;
                leftArm.yRot = 0.5f;
                sword.xRot = t >= 20 ? Mth.PI * 0.9f : Mth.HALF_PI;
                chest.xRot += t >= 20 ? 0.25f : 0.0f;
            }
            case VOID_CHAINS -> {
                // Its left hand thrusts out, closes into a fist, lifts... and yanks down at tick 51.
                float reach = ease(Mth.clamp((t - 4) / 6.0f, 0.0f, 1.0f));
                float lift = ease(Mth.clamp((t - 10) / 25.0f, 0.0f, 1.0f));
                leftArm.xRot = t < 51 ? Mth.lerp(reach, ARM_REST, -1.4f) - lift * 1.2f
                    : Mth.lerp(Mth.clamp((t - 51) / 3.0f, 0.0f, 1.0f), -2.6f, 0.2f);
                leftForearm.xRot = -0.4f * reach;
                chest.xRot += t >= 51 && t < 60 ? 0.3f : 0.0f;
            }
            case SHADOW_SWORDS -> {
                // Both arms raised to the sky, summoning, then swept down as the swords fall.
                float raise = ease(Mth.clamp(t / 10.0f, 0.0f, 1.0f));
                float down = t > 36 ? ease(Mth.clamp((t - 36) / 6.0f, 0.0f, 1.0f)) : 0.0f;
                rightArm.xRot = Mth.lerp(raise, ARM_REST, -2.8f) + down * 2.2f;
                leftArm.xRot = rightArm.xRot;
                rightArm.zRot = 0.4f * raise;
                leftArm.zRot = -0.4f * raise;
                head.xRot = -0.5f * raise;
            }
            case ANNIHILATION -> {
                // Both hands hold the sword up in front, the core blazing, while it turns a full circle.
                float hold = ease(Mth.clamp(t / 15.0f, 0.0f, 1.0f));
                rightArm.xRot = Mth.lerp(hold, ARM_REST, -0.9f);
                leftArm.xRot = Mth.lerp(hold, ARM_REST, -0.9f);
                leftArm.yRot = -0.5f * hold;
                rightForearm.xRot = -0.4f * hold;
                leftForearm.xRot = -0.4f * hold;
                sword.xRot = Mth.lerp(hold, Mth.HALF_PI, 1.3f);
                if (t >= 30) {
                    chest.xRot += Mth.sin(state.ageInTicks * 1.5f) * 0.02f; // straining with the beam
                }
            }
            case SUPERNOVA -> {
                // Arms pulled in tight as the core charges, then flung wide as it explodes at tick 80.
                float gather = ease(Mth.clamp(t / 20.0f, 0.0f, 1.0f));
                float burst = t >= 80 ? ease(Mth.clamp((t - 80) / 4.0f, 0.0f, 1.0f)) * ease(Mth.clamp((110 - t) / 20.0f, 0.0f, 1.0f)) : 0.0f;
                rightArm.xRot = Mth.lerp(gather, ARM_REST, -1.2f) * (1.0f - burst);
                leftArm.xRot = rightArm.xRot;
                rightArm.yRot = 0.7f * gather * (1.0f - burst);
                leftArm.yRot = -rightArm.yRot;
                rightArm.zRot = 1.4f * burst;
                leftArm.zRot = -1.4f * burst;
                rightForearm.xRot = -1.2f * gather * (1.0f - burst);
                leftForearm.xRot = rightForearm.xRot;
                chest.xRot += 0.3f * gather * (1.0f - burst) - 0.4f * burst;
                if (t < 80) {
                    chest.yRot = Mth.sin(state.ageInTicks * 3.0f) * 0.02f * gather; // trembling with power
                }
            }
            case SWORD_BREAK -> {
                // Holds the sword up level in both hands, snaps it at tick 24, then hurls the blade down.
                float hold = ease(Mth.clamp(t / 12.0f, 0.0f, 1.0f));
                rightArm.xRot = Mth.lerp(hold, ARM_REST, -1.5f);
                leftArm.xRot = Mth.lerp(hold, ARM_REST, -1.5f);
                leftArm.yRot = -0.4f * hold;
                sword.xRot = Mth.lerp(hold, Mth.HALF_PI, 0.4f);
                if (t >= 20 && t < 24) {
                    // Straining - the blade bends...
                    chest.yRot = Mth.sin(state.ageInTicks * 4.0f) * 0.04f;
                    rightForearm.xRot = -0.5f;
                }
                if (t >= 24 && t < 32) {
                    leftArm.xRot = Mth.lerp((t - 24) / 8.0f, -1.5f, -2.6f); // the broken blade raised to throw
                }
                if (t >= 32 && t < 40) {
                    leftArm.xRot = Mth.lerp((t - 32) / 8.0f, -2.6f, 0.2f);
                }
            }
            default -> { }
        }
    }

    /** Kneeling (the finale): knees on the ground, leaning over the ramp, glaring down at the climbers. */
    private void poseKneel(UniversalDestroyerRenderState state) {
        float kneel = state.finale == 1 ? 1.0f
            : state.attack == UniversalDestroyer.Attack.SWORD_BREAK ? ease(Mth.clamp((state.attackTicks - 40) / 20.0f, 0.0f, 1.0f))
            : 0.0f;
        if (kneel <= 0.0f) {
            return;
        }
        float drop = KNEEL_DROP * kneel;
        waist.y += drop;
        rightThigh.y += drop;
        leftThigh.y += drop;
        rightThigh.xRot = Mth.lerp(kneel, rightThigh.xRot, -0.25f);
        leftThigh.xRot = Mth.lerp(kneel, leftThigh.xRot, -0.25f);
        rightShin.xRot = Mth.lerp(kneel, rightShin.xRot, Mth.HALF_PI + 0.25f);
        leftShin.xRot = Mth.lerp(kneel, leftShin.xRot, Mth.HALF_PI + 0.25f);
        if (state.finale == 1) {
            chest.xRot = 0.35f + Mth.sin(state.ageInTicks * 0.08f) * 0.02f; // heaving
            head.xRot = 0.3f;
            head.yRot = 0.0f;
            rightArm.xRot = -0.35f;  // the broken stub held low
            rightForearm.xRot = -0.4f;
            leftArm.xRot = -0.55f;   // bracing on the ground
            leftForearm.xRot = -0.1f;
            sword.xRot = Mth.HALF_PI;
        }
    }

    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
