package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Void Helmet - Four dark abilities:
 * 1. NIGHT VISION:  Permanent night vision while wearing
 * 2. VOID SIGHT:    Nearby mobs glow (visible through walls)
 * 3. WITHER AURA:   Wither 5 applied to all nearby enemies
 * 4. BLEEDING:      5 DPS for 10 seconds on nearby enemies
 * 5. GAZE CURSE:    Looking at an enemy gives them Slowness 250
 * 6. HEART DRAIN:   Every 5 player hits, permanently remove 2 hearts (until death)
 */
public class VoidHelmetItem extends Item {

    // Bleeding tracker: entity UUID -> [lastDamageTick, endTick]
    private static final Map<UUID, int[]> bleedingTargets = new HashMap<>();

    // Heart curse tracker: entity UUID -> hit count from player wearing the helmet
    private static final Map<UUID, Integer> helmetHitCounts = new HashMap<>();
    private static final ResourceLocation VOID_HELMET_CURSE_ID =
        ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "void_helmet_heart_drain");

    public VoidHelmetItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof VoidHelmetItem;
    }

    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (!isWearing(player)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        int currentTick = player.tickCount;

        // NIGHT VISION: permanent while wearing
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        if (nightVision == null || nightVision.getDuration() < 60) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));
        }

        // Scan nearby enemies (8 block radius)
        AABB box = player.getBoundingBox().inflate(8.0);
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());

        for (LivingEntity mob : nearby) {
            // WITHER AURA: Wither 5 (amplifier 4)
            MobEffectInstance wither = mob.getEffect(MobEffects.WITHER);
            if (wither == null || wither.getDuration() < 20) {
                mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 4, false, false));
            }

            // VOID SIGHT: make them glow
            if (mob.getEffect(MobEffects.GLOWING) == null) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
            }

            // BLEEDING: add/refresh bleeding for 10 seconds (200 ticks)
            UUID id = mob.getUUID();
            int[] bleeding = bleedingTargets.get(id);
            if (bleeding == null) {
                bleedingTargets.put(id, new int[]{currentTick, currentTick + 200});
            } else {
                // Refresh end time if they stay in range
                bleeding[1] = Math.max(bleeding[1], currentTick + 200);
            }
        }

        // Process bleeding: deal 5 damage every 20 ticks (1 second = 5 DPS)
        bleedingTargets.entrySet().removeIf(entry -> {
            int[] data = entry.getValue(); // [lastDamageTick, endTick]
            if (currentTick > data[1]) return true; // expired - remove
            if (currentTick - data[0] >= 20) {
                Entity target = serverLevel.getEntity(entry.getKey());
                if (target instanceof LivingEntity le && le.isAlive()) {
                    le.hurt(serverLevel.damageSources().magic(), 5.0f);
                }
                data[0] = currentTick; // update last damage tick
            }
            return false;
        });

        // GAZE CURSE: Slowness 250 when looking directly at an enemy (20 block range)
        Vec3 look = player.getLookAngle().normalize();
        Vec3 eyePos = player.getEyePosition();
        AABB gazeBox = player.getBoundingBox().inflate(20.0);
        serverLevel.getEntitiesOfClass(LivingEntity.class, gazeBox, e -> e != player && e.isAlive())
            .forEach(mob -> {
                Vec3 toMob = mob.getBoundingBox().getCenter().subtract(eyePos).normalize();
                if (look.dot(toMob) > 0.97) { // ~14 degree cone
                    mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 249, false, false));
                }
            });
    }

    /**
     * Called from LivingHurtEvent when a player wearing the void helmet hits an enemy.
     * Every 5 hits: permanently remove 2 hearts from the target until they die.
     */
    public static void handleHelmetHitCurse(Player player, LivingEntity target, ServerLevel level) {
        UUID targetId = target.getUUID();
        int hits = helmetHitCounts.getOrDefault(targetId, 0) + 1;
        helmetHitCounts.put(targetId, hits);

        if (hits % 5 == 0) {
            var maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && target.getMaxHealth() > 4.0f) {
                // Remove old modifier and stack a new one: -4 HP (-2 hearts) per 5 hits
                maxHealth.removeModifier(VOID_HELMET_CURSE_ID);
                double reduction = -(hits / 5) * 4.0;
                maxHealth.addPermanentModifier(new AttributeModifier(
                    VOID_HELMET_CURSE_ID,
                    reduction,
                    AttributeModifier.Operation.ADD_VALUE
                ));
                // Clamp health to new max
                if (target.getHealth() > target.getMaxHealth()) {
                    target.setHealth(target.getMaxHealth());
                }
                player.displayClientMessage(
                    Component.literal("\u00a75The void claims their soul..."), true);
            }
        }
    }
}