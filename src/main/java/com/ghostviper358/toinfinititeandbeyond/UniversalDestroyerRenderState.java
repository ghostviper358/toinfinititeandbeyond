package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** What UniversalDestroyerModel needs to know about the boss each frame, beyond the usual mob state. */
@OnlyIn(Dist.CLIENT)
public class UniversalDestroyerRenderState extends LivingEntityRenderState {
    /** The special attack being played, and how many ticks into it. */
    public UniversalDestroyer.Attack attack = UniversalDestroyer.Attack.NONE;
    public float attackTicks;
    /** 1, 2 or 3 - in phase 3 (Annihilation) every vein blazes. */
    public int phase = 1;
    /** 0 = sword whole, 1 = kneeling (the blade's planted as a ramp), 2 = broken sword. */
    public int finale = 0;
    /** Below 10,000 health its whole body glows. */
    public boolean overcharged;
}
