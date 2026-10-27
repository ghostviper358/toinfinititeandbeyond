package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Void Boots - Six dark abilities:
 * 1. FEATHER FALL:   Cancel all fall damage
 * 2. DARK LANDING:   Fall >5 blocks → void shockwave (AoE sonic boom damage + knockback in 8 blocks)
 * 3. VOID CREEP:     Particle trail; enemies within 4 blocks get Slowness 5 + Weakness 3
 * 4. SOUL DRAIN STEP: Every 40 ticks, drain 3 HP from nearest enemy within 6 blocks and heal player
 * 5. VOID SPRINT:    While sprinting, apply Absorption 10
 * 6. VOID BUFFS:     Permanent Jump Boost 5, Slow Falling
 */
public class VoidBootsItem extends Item {

    public VoidBootsItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof VoidBootsItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        if (player.getItemBySlot(EquipmentSlot.FEET) != stack) return;

        int tick = player.tickCount;

        // VOID BUFFS: Jump Boost 5, Slow Falling (permanent)
        applyBuff(player, MobEffects.JUMP_BOOST, 4);     // Jump Boost 5
        applyBuff(player, MobEffects.SLOW_FALLING, 0);  // Slow Falling

        // VOID CREEP: particle trail + Slowness/Weakness aura
        if (tick % 3 == 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                player.getX(), player.getY() + 0.1, player.getZ(),
                2, 0.2, 0.05, 0.2, 0.0);
        }

        AABB creepBox = player.getBoundingBox().inflate(4.0);
        level.getEntitiesOfClass(LivingEntity.class, creepBox, e -> e != player && e.isAlive())
            .forEach(mob -> {
                MobEffectInstance slow = mob.getEffect(MobEffects.SLOWNESS);
                if (slow == null || slow.getDuration() < 20) {
                    mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4, false, false));
                }
                MobEffectInstance weak = mob.getEffect(MobEffects.WEAKNESS);
                if (weak == null || weak.getDuration() < 20) {
                    mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 2, false, false));
                }
            });

        // SOUL DRAIN STEP: steal 3 HP from nearest enemy every 40 ticks
        if (tick % 40 == 0) {
            AABB soulBox = player.getBoundingBox().inflate(6.0);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, soulBox,
                e -> e != player && e.isAlive());
            targets.stream()
                .min((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)))
                .ifPresent(target -> {
                    target.hurt(level.damageSources().magic(), 3.0f);
                    player.heal(3.0f);
                    level.sendParticles(ParticleTypes.SOUL,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        8, 0.3, 0.5, 0.3, 0.05);
                    level.sendParticles(ParticleTypes.SOUL,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        5, 0.2, 0.3, 0.2, 0.03);
                });
        }

        // VOID SPRINT: Absorption 10 while sprinting
        if (player.isSprinting()) {
            MobEffectInstance absorption = player.getEffect(MobEffects.ABSORPTION);
            if (absorption == null || absorption.getDuration() < 20) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 9, false, false, false));
            }
        }
    }

    private void applyBuff(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing == null || existing.getDuration() < 40) {
            player.addEffect(new MobEffectInstance(effect, 100, amplifier, false, false, true));
        }
    }

    /**
     * FEATHER FALL + DARK LANDING: called from LivingFallEvent.
     * Cancels all fall damage; if fall > 5 blocks, triggers a void shockwave.
     * Returns true to cancel the event (no damage taken).
     */
    public static boolean handleBootsFall(Player player, double fallDistance) {
        if (player.level() instanceof ServerLevel serverLevel && fallDistance > 5.0) {
            triggerDarkLanding(player, serverLevel, fallDistance);
        }
        return true; // always cancel fall damage
    }

    private static void triggerDarkLanding(Player player, ServerLevel level, double fallDistance) {
        // Visual: void eruption at landing point
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL,
            player.getX(), player.getY() + 0.5, player.getZ(),
            50, 2.0, 0.3, 2.0, 0.1);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
            player.getX(), player.getY() + 1.0, player.getZ(),
            10, 1.5, 0.5, 1.5, 0.05);

        player.displayClientMessage(
            Component.literal("\u00a75Dark Landing!"), true);

        // Damage and knockback all enemies within 8 blocks
        double damage = Math.min(fallDistance * 1.5, 30.0); // scale with fall, cap at 30
        AABB box = player.getBoundingBox().inflate(8.0);
        level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())
            .forEach(mob -> {
                mob.hurt(level.damageSources().sonicBoom(player), (float) damage);
                // Knock up
                Vec3 dir = mob.position().subtract(player.position()).normalize();
                mob.setDeltaMovement(dir.x * 1.5, 0.8, dir.z * 1.5);
                mob.hurtMarked = true;

                level.sendParticles(ParticleTypes.SONIC_BOOM,
                    mob.getX(), mob.getY() + 1.0, mob.getZ(),
                    1, 0, 0, 0, 0);
            });
    }
}
