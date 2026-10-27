package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Infinitite Armor - Ultimate protection with permanent buffs and damage reflection
 *
 * Features:
 * - Extra armor and toughness (better than Netherite)
 * - Permanent effects: Regeneration II, Resistance II, Speed II, Strength II
 * - Damage Reflection: 25% of damage reflected back to attacker
 * - +5 Hearts (10 health points)
 */
public class InfinititeArmorItem extends Item {

    private static final float DAMAGE_REFLECTION_PERCENT = 9.25F; // 50% reflection

    public InfinititeArmorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (entity instanceof Player player) {
            // Only apply effects if wearing the chest plate in the chest slot
            ItemStack chestSlot = player.getItemBySlot(EquipmentSlot.CHEST);
            if (chestSlot == stack) {
                applyPermanentEffects(player);
            }
        }
    }

    /**
     * Apply permanent buffs while wearing the armor
     */
    private void applyPermanentEffects(Player player) {
        // Regeneration II - heal over time
        addEffectIfNeeded(player, MobEffects.REGENERATION, 6);

        // Resistance 10 - reduce damage
        addEffectIfNeeded(player, MobEffects.RESISTANCE, 9);

        // Speed II - move faster
        addEffectIfNeeded(player, MobEffects.SPEED, 10);

        // Strength II - hit harder
        addEffectIfNeeded(player, MobEffects.STRENGTH, 7);
    }

    /**
     * Helper to add effect if not already active
     */
    private void addEffectIfNeeded(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        MobEffectInstance existingEffect = player.getEffect(effect);
        if (existingEffect == null || existingEffect.getDuration() < 40) {
            player.addEffect(new MobEffectInstance(effect, 100, amplifier, false, false, true));
        }
    }

    /**
     * Damage reflection - when player is hurt, reflect damage to attacker
     */
    public static void handleDamageReflection(LivingEntity wearer, Entity attacker, float damage) {
        // Check if wearing Infinitite Chest Plate
        ItemStack chestplate = wearer.getItemBySlot(EquipmentSlot.CHEST);
        if (chestplate.getItem() instanceof InfinititeArmorItem) {
            // Reflect 25% of damage back to attacker
            if (attacker instanceof LivingEntity livingAttacker) {
                float reflectedDamage = damage * DAMAGE_REFLECTION_PERCENT;
                livingAttacker.hurt(wearer.damageSources().thorns(wearer), reflectedDamage);
            }
        }
    }
}