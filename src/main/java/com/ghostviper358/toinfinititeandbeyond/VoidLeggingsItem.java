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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Void Leggings - Six dark abilities:
 * 1. VOID STEP:      Teleport forward when sprinting (10s cooldown)
 * 2. SHADOW TRAIL:   Dark particles trail behind when moving
 * 3. GRAVITY PULL:   Nearby enemies slowly dragged toward player
 * 4. PHASE:          20% chance to dodge melee hits
 * 5. SHADOW STRIKE:  3 hits taken → 5 warden blasts (20 dmg each, 50% armor break)
 * 6. VOID BUFFS:     Permanent Regen 10, Speed 15, Strength 17
 */
public class VoidLeggingsItem extends Item {

    // Void Step cooldown: player UUID → last step tick
    private static final Map<UUID, Integer> lastVoidStep = new HashMap<>();

    // Shadow Strike: player UUID → hits taken since last strike
    private static final Map<UUID, Integer> shadowStrikeHits = new HashMap<>();

    public VoidLeggingsItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof VoidLeggingsItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        if (player.getItemBySlot(EquipmentSlot.LEGS) != stack) return;

        int tick = player.tickCount;

        // VOID BUFFS: Regen 10, Speed 15, Strength 17, Resistance 20 (permanent)
        applyBuff(player, MobEffects.REGENERATION, 9);   // Regen 10
        applyBuff(player, MobEffects.SPEED, 14);         // Speed 15
        applyBuff(player, MobEffects.STRENGTH, 16);      // Strength 17
        applyBuff(player, MobEffects.RESISTANCE, 19);    // Resistance 20

        // SHADOW TRAIL: dark particles when moving
        if (tick % 2 == 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 0.1, player.getZ(),
                2, 0.2, 0.1, 0.2, 0.0);
            if (player.isSprinting()) {
                level.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 0.2, player.getZ(),
                    1, 0.15, 0.05, 0.15, 0.01);
            }
        }

        // GRAVITY PULL: drag enemies toward player every 10 ticks
        if (tick % 10 == 0) {
            AABB box = player.getBoundingBox().inflate(10.0);
            level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())
                .forEach(mob -> {
                    Vec3 toPlayer = player.position().subtract(mob.position());
                    double dist = toPlayer.length();
                    if (dist > 1.0) {
                        Vec3 pull = toPlayer.normalize().scale(0.15);
                        mob.setDeltaMovement(mob.getDeltaMovement().add(pull));
                        mob.hurtMarked = true;
                    }
                });
        }

        // VOID STEP: teleport forward on sprint (200 tick = 10s cooldown)
        UUID id = player.getUUID();
        int lastStep = lastVoidStep.getOrDefault(id, -200);
        if (player.isSprinting() && tick % 60 == 0 && (tick - lastStep) >= 200) {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 dest = player.position().add(look.scale(8.0));

            // Step along the path to find safe landing spot
            for (int i = 8; i >= 1; i--) {
                Vec3 candidate = player.position().add(look.scale(i));
                if (level.noCollision(player, player.getBoundingBox().move(
                        candidate.subtract(player.position())))) {
                    player.teleportTo(candidate.x, candidate.y, candidate.z);
                    lastVoidStep.put(id, tick);
                    // Void step particles at origin and destination
                    level.sendParticles(ParticleTypes.PORTAL,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        20, 0.5, 0.5, 0.5, 0.1);
                    level.sendParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.3, 0.5, 0.3, 0.05);
                    break;
                }
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
     * PHASE: 20% chance to dodge melee damage.
     * SHADOW STRIKE: every 3 hits taken, launch 5 warden blasts.
     * Called from LivingHurtEvent.
     * Returns true to cancel the event.
     */
    public static boolean handleLeggingsHurt(Player player, ServerLevel level, boolean isMelee) {
        UUID id = player.getUUID();

        // PHASE: 20% melee dodge
        if (isMelee && level.random.nextFloat() < 0.20f) {
            level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                15, 0.4, 0.5, 0.4, 0.05);
            player.displayClientMessage(
                Component.literal("\u00a75Phase! The void absorbed the hit."), true);
            return true; // cancel damage
        }

        // SHADOW STRIKE: count hits, trigger at 3
        int hits = shadowStrikeHits.getOrDefault(id, 0) + 1;
        if (hits >= 3) {
            shadowStrikeHits.put(id, 0);
            triggerShadowStrike(player, level);
        } else {
            shadowStrikeHits.put(id, hits);
        }

        return false;
    }

    private static void triggerShadowStrike(Player player, ServerLevel level) {
        // Find 5 nearest enemies within 20 blocks
        AABB box = player.getBoundingBox().inflate(20.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());
        nearby.sort((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

        // Visual: dark explosion burst
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.PORTAL,
            player.getX(), player.getY() + 1.0, player.getZ(),
            40, 1.5, 1.0, 1.5, 0.15);

        player.displayClientMessage(
            Component.literal("\u00a75Shadow Strike!"), true);

        int blasts = 0;
        for (LivingEntity target : nearby) {
            if (blasts >= 5) break;

            // Deal 20 sonic boom style damage (bypasses armor)
            target.hurt(level.damageSources().sonicBoom(player), 20.0f);

            // Visual: warden blast beam effect at target
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                target.getX(), target.getY() + 1.0, target.getZ(),
                15, 0.3, 0.5, 0.3, 0.1);
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                target.getX(), target.getY() + 1.0, target.getZ(),
                1, 0, 0, 0, 0);

            // 50% chance to destroy one armor piece
            if (level.random.nextFloat() < 0.50f) {
                breakRandomArmor(target);
            }

            blasts++;
        }
    }

    private static void breakRandomArmor(LivingEntity target) {
        EquipmentSlot[] armorSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        // Collect slots that have armor
        java.util.List<EquipmentSlot> wearing = new java.util.ArrayList<>();
        for (EquipmentSlot slot : armorSlots) {
            if (!target.getItemBySlot(slot).isEmpty()) {
                wearing.add(slot);
            }
        }
        if (!wearing.isEmpty()) {
            EquipmentSlot slot = wearing.get(target.level().random.nextInt(wearing.size()));
            target.setItemSlot(slot, ItemStack.EMPTY);
        }
    }
}