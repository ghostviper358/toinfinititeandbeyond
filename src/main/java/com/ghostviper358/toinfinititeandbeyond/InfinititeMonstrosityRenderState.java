package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** What InfinititeMonstrosityModel needs to know about the boss each frame, beyond the usual mob state. */
@OnlyIn(Dist.CLIENT)
public class InfinititeMonstrosityRenderState extends LivingEntityRenderState {
    /** The special attack being played, and how many ticks into it. */
    public InfinititeMonstrosity.Attack attack = InfinititeMonstrosity.Attack.NONE;
    public float attackTicks;
    /** Phase 2 - its cracks blaze brighter. */
    public boolean enraged;
    /** Progress (0-1) through an ordinary melee swing, 0 when not swinging. */
    public float attackTime;
}
