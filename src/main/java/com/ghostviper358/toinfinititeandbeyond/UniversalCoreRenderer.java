package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.HashMap;
import java.util.Map;

/**
 * The Universal Core's renderer: always drawn at full brightness (it glows in the dark), with its
 * two white rings tumbling around the core on different axes, the whole crystal gently bobbing,
 * and the golden aura pulsing - like a vanilla End Crystal.
 *
 * The rings are turned here in code rather than by a GeckoLib animation file because they speed
 * up while the Core is being eaten (up to {@link #MAX_EATING_SPEED} times faster just before it's
 * finished), and the speed-up has to be smooth. Each holder's rings keep their own running angle,
 * advanced every frame by the time passed times the current speed.
 */
public class UniversalCoreRenderer extends GeoItemRenderer<UniversalCoreItem> {

    private static final DataTicket<Float> SPIN = DataTicket.create(ToInfinititeAndBeyond.MOD_ID + "_core_spin", Float.class);

    /** Ring turns per second at normal speed. */
    private static final float BASE_TURNS_PER_SECOND = 0.25f;

    /** How much faster the rings are spinning at the very end of eating the Core. */
    private static final float MAX_EATING_SPEED = 8.0f;

    /** Spin progress (in turns) per holder - entity id, or -1 for one not held (inventory, ground, item frame). */
    private static final Map<Integer, Spin> SPINS = new HashMap<>();

    private static final class Spin {
        float turns;
        long lastMillis = Util.getMillis();
    }

    public UniversalCoreRenderer() {
        super(new UniversalCoreModel());
    }

    @Override
    public void addRenderData(UniversalCoreItem animatable, RenderData renderData, GeoRenderState renderState) {
        LivingEntity holder = renderData.entity();
        float speed = 1.0f;
        if (holder != null && holder.isUsingItem() && holder.getUseItem().getItem() instanceof UniversalCoreItem) {
            int duration = Math.max(1, holder.getUseItem().getUseDuration(holder));
            float eaten = Mth.clamp(holder.getTicksUsingItem() / (float) duration, 0.0f, 1.0f);
            speed = Mth.lerp(eaten * eaten, 1.0f, MAX_EATING_SPEED);
        }

        Spin spin = SPINS.computeIfAbsent(holder != null ? holder.getId() : -1, id -> new Spin());
        long now = Util.getMillis();
        float seconds = Math.min((now - spin.lastMillis) / 1000.0f, 0.25f);
        spin.lastMillis = now;
        spin.turns = (spin.turns + seconds * BASE_TURNS_PER_SECOND * speed) % 1000.0f;
        renderState.addGeckolibData(SPIN, spin.turns);
    }

    @Override
    public void render(GeoRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource) {
        renderState.addGeckolibData(DataTickets.PACKED_LIGHT, LightTexture.FULL_BRIGHT);
        super.render(renderState, poseStack, bufferSource);
    }

    @Override
    public void preRender(GeoRenderState renderState, PoseStack poseStack, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(renderState, poseStack, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, renderColor);
        Float spinTurns = renderState.getGeckolibData(SPIN);
        float turns = spinTurns == null ? 0.0f : spinTurns;
        float angle = turns * Mth.TWO_PI;

        // The two rings tumble on different axes at different rates, like an End Crystal's glass.
        model.getBone("ring_inner").ifPresent(bone -> {
            bone.setRotX(angle * 1.3f);
            bone.setRotY(angle * 2.0f);
        });
        model.getBone("ring_outer").ifPresent(bone -> {
            bone.setRotY(-angle * 1.5f);
            bone.setRotZ(angle * 1.1f);
        });
        model.getBone("core").ifPresent(bone -> bone.setRotY(angle * 0.6f));

        // The aura breathes in and out, and the whole crystal bobs gently.
        float pulse = 1.0f + 0.07f * Mth.sin(angle * 3.0f);
        model.getBone("aura").ifPresent(bone -> {
            bone.setScaleX(pulse);
            bone.setScaleY(pulse);
            bone.setScaleZ(pulse);
        });
        model.getBone("root").ifPresent(bone -> bone.setPosY(0.6f * Mth.sin(angle * 2.0f)));
    }
}
