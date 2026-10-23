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
 * 3D model for the Infinitite Monstrosity: a hunched golem of dark rock - a huge chest, a small
 * head sunk low between massive boulder shoulders, long gorilla arms ending in boulder fists, short
 * thick legs, and purple Infinitite crystals growing out of its back. Its right fist grips the
 * giant void sword, which points forward at rest.
 *
 * Texture sheet: 128 x 128 (painted by gen_infinitite_monstrosity_texture.py - the cube list
 * there must match this one). Actual in-world size comes from the entity's SCALE attribute (5x).
 *
 * Part tree (so the arms and head move with the hunched chest):
 *   waist
 *     chest (tilted forward)
 *       head (+ brow), crystal1-3, rightArm (+ sword), leftArm
 *   rightLeg, leftLeg
 */
@OnlyIn(Dist.CLIENT)
public class InfinititeMonstrosityModel extends EntityModel<InfinititeMonstrosityRenderState> {

    private static final float HUNCH = 0.35f;
    private static final float ARM_REST = -0.15f;

    private final ModelPart waist;
    private final ModelPart chest;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart sword;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public InfinititeMonstrosityModel(ModelPart root) {
        super(root);
        this.waist = root.getChild("waist");
        this.chest = waist.getChild("chest");
        this.head = chest.getChild("head");
        this.rightArm = chest.getChild("rightArm");
        this.leftArm = chest.getChild("leftArm");
        this.sword = rightArm.getChild("sword");
        this.rightLeg = root.getChild("rightLeg");
        this.leftLeg = root.getChild("leftLeg");
    }

    /** Called once during Forge model layer registration (client-side). */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition waist = root.addOrReplaceChild("waist",
            CubeListBuilder.create().texOffs(0, 26).addBox(-7.0f, -6.0f, -5.0f, 14, 8, 10, none),
            PartPose.offset(0.0f, 12.0f, 0.0f));

        PartDefinition chest = waist.addOrReplaceChild("chest",
            CubeListBuilder.create().texOffs(0, 0).addBox(-10.0f, -14.0f, -7.0f, 20, 14, 12, none),
            PartPose.offsetAndRotation(0.0f, -5.0f, 0.0f, HUNCH, 0.0f, 0.0f));

        chest.addOrReplaceChild("head",
            CubeListBuilder.create()
                .texOffs(64, 0).addBox(-4.0f, -4.0f, -8.0f, 8, 8, 8, none)
                .texOffs(22, 86).addBox(-5.0f, -5.0f, -9.0f, 10, 3, 3, none)  // heavy brow
                .texOffs(48, 86).addBox(-4.0f, 3.0f, -9.0f, 8, 3, 5, none),   // jutting jaw, glowing teeth
            PartPose.offset(0.0f, -9.0f, -6.0f));

        // Infinitite crystals jutting out of its back.
        chest.addOrReplaceChild("crystal1",
            CubeListBuilder.create().texOffs(104, 44).addBox(-1.5f, -7.0f, -1.5f, 3, 7, 3, none),
            PartPose.offsetAndRotation(-4.0f, -12.0f, 3.0f, -0.5f, 0.0f, -0.4f));
        chest.addOrReplaceChild("crystal2",
            CubeListBuilder.create().texOffs(104, 54).addBox(-1.5f, -7.0f, -1.5f, 3, 7, 3, none),
            PartPose.offsetAndRotation(3.0f, -13.0f, 2.0f, -0.7f, 0.0f, 0.3f));
        chest.addOrReplaceChild("crystal3",
            CubeListBuilder.create().texOffs(116, 44).addBox(-1.5f, -7.0f, -1.5f, 3, 7, 3, none),
            PartPose.offsetAndRotation(0.0f, -6.0f, 4.0f, -1.1f, 0.0f, 0.1f));

        PartDefinition rightArm = chest.addOrReplaceChild("rightArm",
            CubeListBuilder.create()
                .texOffs(48, 26).addBox(-7.0f, -5.0f, -5.0f, 10, 8, 10, none)  // boulder shoulder
                .texOffs(0, 44).addBox(-5.0f, 1.0f, -4.0f, 8, 14, 8, none)     // arm
                .texOffs(64, 44).addBox(-6.0f, 15.0f, -5.0f, 10, 10, 10, none), // boulder fist
            PartPose.offsetAndRotation(-12.0f, -11.0f, 0.0f, ARM_REST, 0.0f, 0.0f));

        // The void sword, gripped in the right fist. Built pointing up, then turned to point forward.
        rightArm.addOrReplaceChild("sword",
            CubeListBuilder.create()
                .texOffs(104, 66).addBox(-1.0f, -2.0f, -1.0f, 2, 8, 2, none)     // handle
                .texOffs(0, 86).addBox(-4.0f, -4.0f, -1.5f, 8, 2, 3, none)       // guard
                .texOffs(120, 64).addBox(-1.5f, -34.0f, -0.5f, 3, 30, 1, none)   // blade
                .texOffs(104, 76).addBox(-0.5f, -38.0f, -0.5f, 1, 4, 1, none),   // tip
            PartPose.offsetAndRotation(-1.0f, 20.0f, 0.0f, Mth.HALF_PI, 0.0f, 0.0f));

        chest.addOrReplaceChild("leftArm",
            CubeListBuilder.create()
                .texOffs(88, 26).addBox(-3.0f, -5.0f, -5.0f, 10, 8, 10, none)
                .texOffs(32, 44).addBox(-3.0f, 1.0f, -4.0f, 8, 14, 8, none)
                .texOffs(0, 66).addBox(-4.0f, 15.0f, -5.0f, 10, 10, 10, none),
            PartPose.offsetAndRotation(12.0f, -11.0f, 0.0f, ARM_REST, 0.0f, 0.0f));

        root.addOrReplaceChild("rightLeg",
            CubeListBuilder.create().texOffs(40, 66).addBox(-4.0f, 0.0f, -4.0f, 8, 11, 8, none),
            PartPose.offset(-4.5f, 13.0f, 0.0f));
        root.addOrReplaceChild("leftLeg",
            CubeListBuilder.create().texOffs(72, 66).addBox(-4.0f, 0.0f, -4.0f, 8, 11, 8, none),
            PartPose.offset(4.5f, 13.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(InfinititeMonstrosityRenderState state) {
        super.setupAnim(state); // back to the resting pose built above

        float walk = state.walkAnimationPos * 0.6662f;
        float stride = state.walkAnimationSpeed;

        // Heavy, stomping steps, with the arms swinging the opposite way like a gorilla's.
        rightLeg.xRot = Mth.cos(walk) * 1.0f * stride;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.0f * stride;
        rightArm.xRot += Mth.cos(walk + Mth.PI) * 0.5f * stride;
        leftArm.xRot += Mth.cos(walk) * 0.5f * stride;
        waist.yRot = Mth.cos(walk) * 0.08f * stride;

        // Slow, heaving breaths.
        chest.xRot += Mth.sin(state.ageInTicks * 0.06f) * 0.03f;

        // Look at what it's after (the head can only turn so far, sunk between its shoulders).
        head.yRot = Mth.clamp(state.yRot, -45.0f, 45.0f) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(state.xRot, -25.0f, 25.0f) * Mth.DEG_TO_RAD - HUNCH;

        // An ordinary sword swing: an overhead chop with the sword arm.
        if (state.attackTime > 0.0f) {
            float swing = Mth.sin(state.attackTime * Mth.PI);
            rightArm.xRot -= swing * 2.2f;
            chest.yRot -= swing * 0.3f;
        }

        poseSpecialAttack(state);
    }

    /** Poses for the special attacks, driven by how many ticks into the attack it is. */
    private void poseSpecialAttack(InfinititeMonstrosityRenderState state) {
        float t = state.attackTicks;
        switch (state.attack) {
            case GROUND_SLAM -> {
                // Both arms up high, then smashed down at tick 15.
                float arms = t < 15 ? Mth.lerp(ease(t / 15.0f), ARM_REST, -2.6f)
                    : t < 19 ? Mth.lerp((t - 15) / 4.0f, -2.6f, 0.3f)
                    : Mth.lerp(ease(Mth.clamp((t - 19) / 11.0f, 0.0f, 1.0f)), 0.3f, ARM_REST);
                rightArm.xRot = arms;
                leftArm.xRot = arms;
                chest.xRot += t >= 15 && t < 22 ? 0.3f : 0.0f;
            }
            case ROCK_BARRAGE -> {
                // The left arm winds back and hurls, three times (throws at ticks 15, 27, 39).
                float cycle = ((t - 3.0f) % 12.0f + 12.0f) % 12.0f / 12.0f;
                leftArm.xRot = cycle < 0.8f ? Mth.lerp(ease(cycle / 0.8f), ARM_REST, -2.8f)
                    : Mth.lerp((cycle - 0.8f) / 0.2f, -2.8f, 0.4f);
                leftArm.zRot = -0.3f;
                chest.yRot = 0.25f;
            }
            case FIST_QUAKE -> {
                // Both fists raised together over its head, then pounded into the ground and held.
                float arms = t < 15 ? Mth.lerp(ease(t / 15.0f), ARM_REST, -2.9f)
                    : t < 18 ? Mth.lerp((t - 15) / 3.0f, -2.9f, 0.5f)
                    : 0.5f;
                rightArm.xRot = arms;
                leftArm.xRot = arms;
                rightArm.zRot = t >= 15 ? 0.25f : 0.0f;
                leftArm.zRot = t >= 15 ? -0.25f : 0.0f;
                chest.xRot += t >= 15 ? 0.35f : 0.0f;
            }
            case VOID_BEAM -> {
                // Arms flung wide and chest thrown open toward the target.
                float open = ease(Mth.clamp(t / 20.0f, 0.0f, 1.0f));
                rightArm.zRot = 1.2f * open;
                leftArm.zRot = -1.2f * open;
                rightArm.xRot = Mth.lerp(open, ARM_REST, -0.6f);
                leftArm.xRot = Mth.lerp(open, ARM_REST, -0.6f);
                chest.xRot -= 0.5f * open;
                if (t >= 30) {
                    chest.xRot += Mth.sin(state.ageInTicks * 1.5f) * 0.02f; // straining with the beam
                }
            }
            case BLACK_HOLE -> {
                // Sword raised high, then stabbed down into the ground at tick 20 and held there.
                float arm = t < 20 ? Mth.lerp(ease(t / 20.0f), ARM_REST, -2.4f)
                    : t < 23 ? Mth.lerp((t - 20) / 3.0f, -2.4f, 0.9f)
                    : 0.9f;
                rightArm.xRot = arm;
                leftArm.xRot = arm * 0.8f; // both hands on the hilt
                leftArm.yRot = 0.5f;
                chest.xRot += t >= 20 ? 0.25f : 0.0f;
            }
            default -> { }
        }
    }

    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
