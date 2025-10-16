package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Custom Infinitite Sword - A powerful weapon with special effects.
 *
 * Features:
 * - 11 attack damage (configured via Item.Properties attributes)
 * - 3124 durability (double diamond sword)
 * - Applies BLINDNESS for 3 seconds on hit
 * - Applies POISON for 3 seconds on hit
 */
public class InfiniteSwordItem extends Item {

    public InfiniteSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Apply BLINDNESS effect for 60 ticks (3 seconds) at level 0 (Blindness I)
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));

        // Apply POISON effect for 60 ticks (3 seconds) at level 0 (Poison I)
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));

        // Call parent to handle normal behavior (durability damage, stats, etc.)
        super.hurtEnemy(stack, target, attacker);
    }
}
