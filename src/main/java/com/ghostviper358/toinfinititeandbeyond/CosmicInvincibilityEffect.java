package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Cosmic Invincibility - a custom status effect granted by the Cosmic Apple.
 *
 * There is no vanilla "true invincibility" effect (Resistance only reduces damage,
 * it does not zero it out), so this is a marker effect with no built-in behavior of
 * its own. The actual damage-blocking happens in
 * {@link ToInfinititeAndBeyond#onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent)},
 * which checks whether the hurt entity currently has this effect and, if so, cancels
 * the damage event entirely (complete immunity, not just resistance).
 */
public class CosmicInvincibilityEffect extends MobEffect {

    public CosmicInvincibilityEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}