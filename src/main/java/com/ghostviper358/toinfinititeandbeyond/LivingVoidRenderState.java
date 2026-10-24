package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** What LivingVoidModel needs to know about the boss each frame, beyond the usual mob state. */
@OnlyIn(Dist.CLIENT)
public class LivingVoidRenderState extends LivingEntityRenderState {
    /** The attack being played, and how many ticks into it. */
    public LivingVoid.Attack attack = LivingVoid.Attack.NONE;
    public float attackTicks;
}
