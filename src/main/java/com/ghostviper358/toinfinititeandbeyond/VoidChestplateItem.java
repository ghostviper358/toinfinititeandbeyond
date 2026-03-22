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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Void Chestplate - Five dark abilities:
 * 1. SOUL DRAIN:     Heals wearer while nearby enemies have Wither
 * 2. VOID SHIELD:    Absorbs 25% of all incoming damage
 * 3. WITHER BURST:   When hit, pulses Wither III on all nearby enemies
 * 4. DARK SHROUD:    20% chance projectiles pass through you
 * 5. ARMOR SHATTER:  20% chance on hit to reduce target armor durability by 25%
 * 6. VOID HEALING:   Crouch 2 seconds → drain nearby health to heal, then explode 30 dmg
 */
public class VoidChestplateItem extends Item {

    // Crouch timer: player UUID -> ticks spent crouching
    private static final Map<UUID, Integer> crouchTicks = new HashMap<>();

    public VoidChestplateItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof VoidChestplateItem;
    }

    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        // Only run when this stack is in the chest slot
        if (player.getItemBySlot(EquipmentSlot.CHEST) != stack) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // SOUL DRAIN: every second, heal 0.5 hearts per nearby withered enemy
        if (player.tickCount % 20 == 0) {
            AABB box = player.getBoundingBox().inflate(8.0);
            long witheredCount = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e.hasEffect(MobEffects.WITHER)).stream().count();
            if (witheredCount > 0) {
                player.heal((float) witheredCount * 1.0f); // 0.5 hearts per withered enemy
            }
        }

        // VOID HEALING: track crouch, activate after 2 seconds (40 ticks)
        UUID id = player.getUUID();
        if (player.isShiftKeyDown()) {
            int ticks = crouchTicks.getOrDefault(id, 0) + 1;
            crouchTicks.put(id, ticks);

            // ANIMATION: void energy spirals inward toward the player
            double angle = (ticks * 18.0) * Math.PI / 180.0; // rotate 18 deg per tick
            float progress = ticks / 40.0f; // 0.0 → 1.0 over 2 seconds
            float radius = 3.0f - (progress * 2.5f); // shrinks from 3 to 0.5 blocks

            // Spawn 3 spiral arms of particles
            for (int arm = 0; arm < 3; arm++) {
                double a = angle + (arm * 2.0 * Math.PI / 3.0);
                double px2 = player.getX() + radius * Math.cos(a);
                double pz2 = player.getZ() + radius * Math.sin(a);
                double py2 = player.getY() + 1.0 + Math.sin(ticks * 0.3) * 0.4;

                serverLevel.sendParticles(ParticleTypes.PORTAL, px2, py2, pz2, 2, 0.05, 0.05, 0.05, 0.0);
                if (ticks > 20) {
                    serverLevel.sendParticles(ParticleTypes.WITCH, px2, py2, pz2, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }

            // Rising smoke column while channeling
            if (ticks % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 0.5 + (ticks / 40.0), player.getZ(),
                    1, 0.1, 0.0, 0.1, 0.01);
            }

            // Half-way pulse: brief darkness flash
            if (ticks == 20) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 15, 0, false, false, false));
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 1.0, 0.5, 1.0, 0.08);
            }

            if (ticks >= 40) {
                crouchTicks.put(id, 0);
                activateVoidHealing(player, serverLevel);
            }
        } else {
            crouchTicks.put(id, 0);
        }
    }

    private static void activateVoidHealing(Player player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(10.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());

        // Drain health from nearby mobs/players to heal wearer
        float totalDrained = 0;
        for (LivingEntity mob : nearby) {
            float drain = Math.min(mob.getHealth(), 10.0f);
            mob.hurt(level.damageSources().magic(), drain);
            totalDrained += drain;
        }
        player.heal(totalDrained);

        // Explode excess: 30 damage to all nearby (re-fetch alive targets after drain)
        level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())
            .forEach(mob -> mob.hurt(level.damageSources().explosion(null), 30.0f));

        // Visual explosion
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            player.getX(), player.getY() + 0.5, player.getZ(), 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.WITCH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            60, 3.0, 1.5, 3.0, 0.15);

        player.displayClientMessage(
            Component.literal("\u00a75Void energy released!"), true);
    }

    /**
     * VOID SHIELD: reduce incoming damage by 25%.
     * DARK SHROUD: 20% chance to negate projectile damage entirely.
     * WITHER BURST: when hit, apply Wither III to all nearby enemies.
     * Called from LivingHurtEvent in the main mod class.
     * Returns true to cancel the event (block the hit).
     */
    public static boolean handleChestplateHurt(LivingHurtEvent event, Player player, ServerLevel level) {
        Entity direct = event.getSource().getDirectEntity();

        // DARK SHROUD: 20% chance to negate projectile damage
        if (direct instanceof Projectile) {
            if (level.random.nextFloat() < 0.20f) {
                level.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.4, 0.5, 0.4, 0.02);
                player.displayClientMessage(
                    Component.literal("\u00a78The void swallowed the projectile..."), true);
                return true; // cancel the event
            }
        }

        // VOID SHIELD: absorb 25% of incoming damage
        float reduced = event.getAmount() * 0.75f;
        event.setAmount(reduced);

        // WITHER BURST: apply Wither III to nearby enemies
        AABB box = player.getBoundingBox().inflate(8.0);
        level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())
            .forEach(mob -> mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 2, false, false)));

        level.sendParticles(ParticleTypes.WITCH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            15, 1.0, 0.5, 1.0, 0.05);

        return false;
    }

    /**
     * ARMOR SHATTER: 20% chance on hit to reduce target's armor durability by 25%.
     * Called from LivingHurtEvent when attacker is a player wearing this chestplate.
     */
    public static void handleArmorShatter(Player player, LivingEntity target, ServerLevel level) {
        if (level.random.nextFloat() >= 0.20f) return;

        boolean shattered = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = target.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem()) {
                int currentDamage = armor.getDamageValue();
                int maxDamage = armor.getMaxDamage();
                int remaining = maxDamage - currentDamage;
                int shatterAmount = (int) (remaining * 0.25f);
                if (shatterAmount > 0) {
                    armor.setDamageValue(Math.min(currentDamage + shatterAmount, maxDamage - 1));
                    shattered = true;
                }
            }
        }

        if (shattered) {
            level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + 1.0, target.getZ(),
                10, 0.3, 0.5, 0.3, 0.1);
            player.displayClientMessage(
                Component.literal("\u00a75Armor shattered!"), true);
        }
    }
}