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
 * 3D model for the Living Void: a giant eye of darkness with a glowing iris, two rings of black
 * shards orbiting it on different tilts, and six jointed tentacles reaching down and out of the
 * dark beneath it. The tentacles writhe constantly, and curl, whip and splay for its attacks.
 *
 * Texture sheet: 64 x 64 (painted by gen_living_void_texture.py - the cube list there must match
 * this one). Actual in-world size comes from the entity's SCALE attribute (4x).
 */
@OnlyIn(Dist.CLIENT)
public class LivingVoidModel extends EntityModel<LivingVoidRenderState> {

    private static final int TENTACLES = 6;
    private static final int SEGMENTS = 4;
    /** How far each tentacle leans outward from straight down, at rest. */
    private static final float SPREAD = -0.45f;

    private final ModelPart body;
    private final ModelPart ringA;
    private final ModelPart ringB;
    private final ModelPart[][] tentacles = new ModelPart[TENTACLES][SEGMENTS];

    public LivingVoidModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.ringA = body.getChild("ring_a");
        this.ringB = body.getChild("ring_b");
        for (int t = 0; t < TENTACLES; t++) {
            ModelPart part = body.getChild("tentacle" + t);
            for (int s = 0; s < SEGMENTS; s++) {
                part = part.getChild("segment" + s);
                tentacles[t][s] = part;
            }
        }
    }

    /** Called once during Forge model layer registration (client-side). */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition body = root.addOrReplaceChild("body",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0f, -6.0f, -6.0f, 12, 12, 12, none)   // the eye
                .texOffs(0, 24).addBox(-4.0f, -4.0f, -6.5f, 8, 8, 1, none),    // the iris, bulging forward
            PartPose.offset(0.0f, -6.0f, 0.0f));

        // Ring of small shards, level with the eye's middle.
        CubeListBuilder ringA = CubeListBuilder.create();
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI * 2.0 / 8.0);
            float x = (float) (Math.cos(angle) * 10.0);
            float z = (float) (Math.sin(angle) * 10.0);
            ringA.texOffs(48, 0).addBox(x - 1.0f, -1.0f, z - 1.0f, 2, 2, 2, none);
        }
        body.addOrReplaceChild("ring_a", ringA, PartPose.ZERO);

        // A wider, tilted ring of bigger shards.
        CubeListBuilder ringB = CubeListBuilder.create();
        for (int i = 0; i < 6; i++) {
            double angle = i * (Math.PI * 2.0 / 6.0);
            float x = (float) (Math.cos(angle) * 12.0);
            float z = (float) (Math.sin(angle) * 12.0);
            ringB.texOffs(48, 4).addBox(x - 1.5f, -1.5f, z - 1.5f, 3, 3, 3, none);
        }
        body.addOrReplaceChild("ring_b", ringB, PartPose.rotation(0.0f, 0.0f, 0.7f));

        // Six tentacles around the underside, each four segments getting thinner toward the tip.
        // Each tentacle root is turned so its local +x points outward from the eye; leaning the
        // segments (zRot) then swings them outward.
        int[][] segmentUv = { { 18, 24 }, { 34, 24 }, { 46, 24 }, { 54, 24 } };
        float[] segmentWidth = { 4.0f, 3.0f, 2.0f, 1.0f };
        float[] segmentLength = { 6.0f, 6.0f, 6.0f, 5.0f };
        for (int t = 0; t < TENTACLES; t++) {
            double angle = t * (Math.PI * 2.0 / TENTACLES) + Math.PI / TENTACLES;
            PartDefinition part = body.addOrReplaceChild("tentacle" + t, CubeListBuilder.create(),
                PartPose.offsetAndRotation((float) (Math.cos(angle) * 4.0), 4.0f, (float) (Math.sin(angle) * 4.0),
                    0.0f, (float) -angle, 0.0f));
            float offset = 0.0f;
            for (int s = 0; s < SEGMENTS; s++) {
                float w = segmentWidth[s];
                part = part.addOrReplaceChild("segment" + s,
                    CubeListBuilder.create().texOffs(segmentUv[s][0], segmentUv[s][1])
                        .addBox(-w / 2.0f, 0.0f, -w / 2.0f, w, segmentLength[s], w, none),
                    PartPose.offsetAndRotation(0.0f, offset, 0.0f, 0.0f, 0.0f, s == 0 ? SPREAD : 0.0f));
                offset = segmentLength[s];
            }
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(LivingVoidRenderState state) {
        super.setupAnim(state); // back to the resting pose built above
        float age = state.ageInTicks;

        // Stares at what it's after.
        body.xRot = state.xRot * Mth.DEG_TO_RAD;

        // The shard rings orbit in opposite directions.
        ringA.yRot = age * 0.05f;
        ringB.yRot = -age * 0.035f;

        // Every tentacle writhes in a slow wave running down its length.
        for (int t = 0; t < TENTACLES; t++) {
            for (int s = 0; s < SEGMENTS; s++) {
                ModelPart segment = tentacles[t][s];
                float phase = age * 0.12f + t * 1.1f + s * 0.6f;
                segment.zRot += Mth.sin(phase) * 0.22f;
                segment.xRot += Mth.cos(phase * 0.8f) * 0.15f;
            }
        }

        poseAttack(state);
    }

    /** Tentacle poses for its attacks, driven by how many ticks into the attack it is. */
    private void poseAttack(LivingVoidRenderState state) {
        float t = state.attackTicks;
        float curl = 0.0f;   // + curls the tentacles up and outward
        switch (state.attack) {
            case TENTACLE_LASH -> curl = whip(t, 12.0f);
            case DARKNESS -> curl = whip(t, 30.0f);
            case GRAVITY_FLIP -> curl = t < 10 ? Mth.lerp(t / 10.0f, 0.0f, 1.0f)       // flung wide
                : t < 30 ? 1.0f
                : Mth.lerp(Mth.clamp((t - 30.0f) / 4.0f, 0.0f, 1.0f), 1.0f, -0.6f); // snapped in
            case VOID_RIFTS -> curl = -0.4f * Mth.clamp(t / 10.0f, 0.0f, 1.0f);      // hanging rigid
            case VOID_ORBS, GROUND_BEAM -> {
                // Recoils as it fires.
                float kick = Mth.clamp(1.0f - (t % 10.0f) / 4.0f, 0.0f, 1.0f);
                body.xRot -= kick * 0.15f;
                curl = 0.3f;
            }
            default -> { }
        }
        if (curl != 0.0f) {
            for (int tentacle = 0; tentacle < TENTACLES; tentacle++) {
                for (int s = 0; s < SEGMENTS; s++) {
                    tentacles[tentacle][s].zRot -= curl * (s == 0 ? 0.9f : 0.35f);
                }
            }
        }
    }

    /** Rears the tentacles back up, then whips them down hard at {@code hitTick}. */
    private static float whip(float t, float hitTick) {
        if (t < hitTick) {
            float rear = t / hitTick;
            return rear * rear * 1.3f;
        }
        return Mth.lerp(Mth.clamp((t - hitTick) / 3.0f, 0.0f, 1.0f), 1.3f, -0.7f)
            + Mth.clamp((t - hitTick - 3.0f) / 8.0f, 0.0f, 1.0f) * 0.7f;
    }
}
