package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Universal God - the permanent status effect of a player who has eaten a Universal Core.
 *
 * A marker with no behavior of its own (same idea as CosmicInvincibilityEffect). The real record
 * of godhood is a flag in the player's persistent data; the server keeps this effect on every god
 * (see UniversalCoreItem.tick) because status effects are sent to every client automatically, so
 * this is how clients know who's a god - for noclip flying and the god-power keys.
 */
public class UniversalGodEffect extends MobEffect {

    public UniversalGodEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
