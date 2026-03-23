package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 3-D model for the Infinitite Monstrosity.
 *
 * Texture sheet: 128 x 128 pixels.
 *
 * Body layout (humanoid proportions; actual in-world size is set by
 * the SCALE attribute = 3.0 on the entity):
 *   head      - 8x8x8 cube
 *   body      - 8x12x4 torso
 *   rightArm  - 4x12x4 arm  (+ giant void sword as child parts)
 *   leftArm   - 4x12x4 arm
 *   rightLeg  - 4x12x4 leg
 *   leftLeg   - 4x12x4 leg
 *
 * Sword parts (children of rightArm, UV area starting at u=48):
 *   swordHandle    texOffs(48,32) box(-1,-2,-1, 2,6,2)
 *   swordGuard     texOffs(48,40) box(-3,-3,-1, 6,2,2)
 *   swordBladeLow  texOffs(48,44) box(-1,-10,-1, 2,8,2)
 *   swordBladeHigh texOffs(48,54) box(-1,-18,-1, 2,8,2)
 *   swordTip       texOffs(52,44) box(-0.5,-22,-0.5, 1,4,1)
 *
 * The arm pivot is at (x=±5, y=2) relative to the body origin.
 * Arm geometry starts at y=-2 and runs to y=10 (relative to arm pivot).
 * "Fist" = y=10. Sword parts are offset downward beyond y=10 so the
 * blade extends below the fist.
 */
@OnlyIn(Dist.CLIENT)
public class InfinititeMonstrosityModel extends EntityModel<LivingEntityRenderState> {

    // Top-level body parts
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public InfinititeMonstrosityModel(ModelPart root) {
        super(root);
        this.head     = root.getChild("head");
        this.body     = root.getChild("body");
        this.rightArm = root.getChild("rightArm");
        this.leftArm  = root.getChild("leftArm");
        this.rightLeg = root.getChild("rightLeg");
        this.leftLeg  = root.getChild("leftLeg");
    }

    /**
     * Called once during Forge model layer registration (client-side).
     * Defines every cube, its UV offset, and its starting pose.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---- HEAD ------------------------------------------------------------
        root.addOrReplaceChild("head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8, CubeDeformation.NONE),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        // ---- BODY ------------------------------------------------------------
        root.addOrReplaceChild("body",
            CubeListBuilder.create()
                .texOffs(16, 16)
                .addBox(-4.0f, 0.0f, -2.0f, 8, 12, 4, CubeDeformation.NONE),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        // ---- RIGHT ARM + SWORD CHILDREN -------------------------------------
        PartDefinition rightArm = root.addOrReplaceChild("rightArm",
            CubeListBuilder.create()
                .texOffs(40, 16)
                .addBox(-3.0f, -2.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE),
            PartPose.offset(-5.0f, 2.0f, 0.0f));

        // Sword parts are positioned relative to the rightArm pivot.
        // The arm occupies y=-2 to y=10. The hand is at y=10.
        // All sword parts are offset further down (y > 10) to extend below the fist.

        // Handle: occupies the fist and just below it
        rightArm.addOrReplaceChild("swordHandle",
            CubeListBuilder.create()
                .texOffs(48, 32)
                .addBox(-1.0f, -2.0f, -1.0f, 2, 6, 2, CubeDeformation.NONE),
            PartPose.offset(0.0f, 14.0f, 0.0f));

        // Guard: wider cross-piece just below the bottom of the handle
        rightArm.addOrReplaceChild("swordGuard",
            CubeListBuilder.create()
                .texOffs(48, 40)
                .addBox(-3.0f, -3.0f, -1.0f, 6, 2, 2, CubeDeformation.NONE),
            PartPose.offset(0.0f, 18.0f, 0.0f));

        // Lower blade: long segment below the guard
        rightArm.addOrReplaceChild("swordBladeLow",
            CubeListBuilder.create()
                .texOffs(48, 44)
                .addBox(-1.0f, -10.0f, -1.0f, 2, 8, 2, CubeDeformation.NONE),
            PartPose.offset(0.0f, 28.0f, 0.0f));

        // Upper blade: second segment continuing upward from the lower blade
        rightArm.addOrReplaceChild("swordBladeHigh",
            CubeListBuilder.create()
                .texOffs(48, 54)
                .addBox(-1.0f, -18.0f, -1.0f, 2, 8, 2, CubeDeformation.NONE),
            PartPose.offset(0.0f, 28.0f, 0.0f));

        // Tip: narrow tapered point at the very bottom
        rightArm.addOrReplaceChild("swordTip",
            CubeListBuilder.create()
                .texOffs(52, 44)
                .addBox(-0.5f, -22.0f, -0.5f, 1, 4, 1, CubeDeformation.NONE),
            PartPose.offset(0.0f, 28.0f, 0.0f));

        // ---- LEFT ARM --------------------------------------------------------
        root.addOrReplaceChild("leftArm",
            CubeListBuilder.create()
                .texOffs(40, 16)
                .addBox(-1.0f, -2.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE),
            PartPose.offset(5.0f, 2.0f, 0.0f));

        // ---- RIGHT LEG -------------------------------------------------------
        root.addOrReplaceChild("rightLeg",
            CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-2.0f, 0.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE),
            PartPose.offset(-1.9f, 12.0f, 0.0f));

        // ---- LEFT LEG --------------------------------------------------------
        root.addOrReplaceChild("leftLeg",
            CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-2.0f, 0.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE),
            PartPose.offset(1.9f, 12.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    // ---------------------------------------------------------------------------
    // Animation - called every frame with the current render state
    // ---------------------------------------------------------------------------

    @Override
    public void setupAnim(LivingEntityRenderState renderState) {
        super.setupAnim(renderState);

        // Walk animation uses the render state's walkAnimationPos / walkAnimationSpeed fields
        float limbSwing  = renderState.walkAnimationPos;
        float limbAmount = renderState.walkAnimationSpeed;

        // Head tracking
        this.head.yRot = renderState.yRot * ((float) Math.PI / 180.0f);
        this.head.xRot = renderState.xRot * ((float) Math.PI / 180.0f);

        // Leg swing
        this.rightLeg.xRot =  (float) Math.cos(limbSwing * 0.6662f)           * 1.4f * limbAmount;
        this.leftLeg.xRot  =  (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.4f * limbAmount;

        // Arm swing (opposite phase to legs)
        this.rightArm.xRot =  (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.4f * limbAmount;
        this.leftArm.xRot  =  (float) Math.cos(limbSwing * 0.6662f)           * 1.4f * limbAmount;

        // Sword arm slight outward tilt for a menacing look
        this.rightArm.zRot = -0.1f;
        this.leftArm.zRot  =  0.1f;
    }
}