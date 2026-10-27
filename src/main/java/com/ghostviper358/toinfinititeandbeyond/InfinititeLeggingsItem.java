package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Infinitite Leggings - All 4 epic abilities combined!
 *
 * 1. ROCKET LEGS:    No fall damage + landing shockwave blasts mobs away
 * 2. SPEED DEMON:    Auto-sprint + purple particle trail
 * 3. GHOST WALKER:   Invisible + slow fall when sneaking (mobs ignore you)
 * 4. GRAVITY MASTER: Sneak in mid-air to slam down and smash everything below!
 */
public class InfinititeLeggingsItem extends Item {

    public InfinititeLeggingsItem(Properties properties) {
        super(properties);
    }

    /**
     * Check if the player is wearing Infinitite Leggings in the legs slot
     */
    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof InfinititeLeggingsItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        if (!isWearing(player)) return;

        // SPEED DEMON: auto-sprint when moving and not sneaking
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.001 && !player.isShiftKeyDown()) {
            player.setSprinting(true);
        }

        // GHOST WALKER: invisibility + slow fall when sneaking
        if (player.isShiftKeyDown()) {
            addEffectIfNeeded(player, new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false, true));
            if (!player.onGround()) {
                addEffectIfNeeded(player, new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
            }
        }

        // SPEED DEMON: purple particle trail when sprinting
        if (player.isSprinting()) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                player.getX(), player.getY() + 0.1, player.getZ(),
                3, 0.25, 0.05, 0.25, 0.01);
        }
    }

    private void addEffectIfNeeded(Player player, MobEffectInstance effect) {
        var existing = player.getEffect(effect.getEffect());
        if (existing == null || existing.getDuration() < 40) {
            player.addEffect(effect);
        }
    }

    /**
     * GRAVITY MASTER: called from PlayerTickEvent.
     * When sneaking in mid-air, slam downward at high speed!
     */
    public static void handleGravitySlam(Player player) {
        if (!isWearing(player)) return;
        if (!player.isShiftKeyDown()) return;
        if (player.onGround()) return;

        // Kick into slam mode if not already falling fast
        if (player.getDeltaMovement().y > -2.5) {
            player.setDeltaMovement(
                player.getDeltaMovement().x,
                -3.5,
                player.getDeltaMovement().z
            );
        }
    }

    /**
     * ROCKET LEGS: called from LivingFallEvent.
     * Cancels fall damage and triggers a shockwave when landing.
     * Returns true to cancel the event (no damage taken).
     */
    public static boolean handleLanding(Player player, double fallDistance) {
        // Trigger shockwave if fell more than 3 blocks
        if (fallDistance > 3.0 && player.level() instanceof ServerLevel serverLevel) {
            triggerShockwave(player, serverLevel, (float) fallDistance);
        }
        return true; // always cancel fall damage
    }

    private static void triggerShockwave(Player player, ServerLevel level, float fallDistance) {
        float blastRadius = 5.0f;
        float knockbackStrength = Math.min(fallDistance / 4.0f, 3.5f);

        // Blast particles at impact point
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            player.getX(), player.getY(), player.getZ(),
            1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            player.getX(), player.getY() + 0.5, player.getZ(),
            20, blastRadius * 0.5, 0.3, blastRadius * 0.5, 0.05);

        // Knock back and damage nearby mobs
        AABB searchBox = player.getBoundingBox().inflate(blastRadius);
        List<net.minecraft.world.entity.LivingEntity> nearby = level.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            searchBox,
            e -> e != player && e.isAlive()
        );

        for (var target : nearby) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                target.knockback(knockbackStrength, -dx / dist, -dz / dist);
            }
            target.hurt(player.damageSources().fall(), fallDistance * 0.5f);
        }
    }
}
