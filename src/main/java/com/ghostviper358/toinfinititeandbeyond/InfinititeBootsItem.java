package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class InfinititeBootsItem extends Item {

    record PhaseCannonData(Vec3 dir, int ticks) {}

    private static final Map<UUID, long[]> SHIFT_TIMES = new HashMap<>();
    private static final Map<UUID, Boolean> PREV_SNEAKING = new HashMap<>();
    private static final Map<UUID, PhaseCannonData> PHASE_CANNON = new HashMap<>();
    private static final Map<UUID, Long> LAST_STOMP = new HashMap<>();

    public InfinititeBootsItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof InfinititeBootsItem;
    }

    private static boolean isWearingFullSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof InfinititeHelmetItem
            && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof InfinititeArmorItem
            && player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof InfinititeLeggingsItem
            && player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof InfinititeBootsItem;
    }

    public static void tick(Player player, ServerLevel level) {
        if (!isWearing(player)) return;

        UUID id = player.getUUID();
        long currentTick = player.tickCount;

        // --- PHASE CANNON: tick-by-tick teleportation ---
        PhaseCannonData cannon = PHASE_CANNON.get(id);
        if (cannon != null) {
            if (cannon.ticks() > 0) {
                Vec3 step = cannon.dir().scale(100.0);
                player.teleportTo(
                    player.getX() + step.x,
                    player.getY() + step.y,
                    player.getZ() + step.z
                );
                PHASE_CANNON.put(id, new PhaseCannonData(cannon.dir(), cannon.ticks() - 1));
            } else {
                PHASE_CANNON.remove(id);
            }
            // Skip all other ability processing during Phase Cannon launch
            trackSneak(player, level, id, currentTick);
            return;
        }

        // --- Full Set Bonus: PHASE CANNON trigger detection ---
        if (isWearingFullSet(player)) {
            checkPhaseCannon(player, level, id, currentTick);
        } else {
            trackSneak(player, level, id, currentTick);
        }

        // --- VOID STEP: walk on water/lava ---
        if (player.isInFluidType() && !player.isShiftKeyDown()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, 0.05, motion.z);
        }

        // --- VOID STEP: void crack trail every 5 ticks ---
        if (currentTick % 5 == 0) {
            level.sendParticles(ParticleTypes.SQUID_INK,
                player.getX(), player.getY() + 0.1, player.getZ(),
                3, 0.2, 0.05, 0.2, 0.01);
        }

        // --- VOID STEP: launch nearby mobs every 40 ticks ---
        if (currentTick % 40 == 0) {
            AABB box = player.getBoundingBox().inflate(5.0);
            List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());
            for (LivingEntity mob : nearby) {
                double dx = mob.getX() - player.getX();
                double dz = mob.getZ() - player.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    mob.knockback(3.0, -dx / dist, -dz / dist);
                }
            }
        }

        // --- COSMIC DASH: burst forward while sprinting every 40 ticks ---
        if (player.isSprinting() && currentTick % 40 == 0) {
            Vec3 look = player.getLookAngle();
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x + look.x * 1.2, motion.y, motion.z + look.z * 1.2);
            // END_ROD particles on burst
            level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.05);
        }

        // --- COSMIC DASH: FIREWORK particle trail every 4 ticks while sprinting ---
        if (player.isSprinting() && currentTick % 4 == 0) {
            level.sendParticles(ParticleTypes.FIREWORK,
                player.getX(), player.getY() + 0.2, player.getZ(),
                2, 0.15, 0.1, 0.15, 0.01);
        }

        // --- GRAVITY DEFIANCE: hold jump in mid-air ---
        if (!player.onGround() && player instanceof ServerPlayer serverPlayer
                && serverPlayer.getLastClientInput().jump()) {
            Vec3 motion = player.getDeltaMovement();
            double newY = Math.min(motion.y + 0.15, 0.3);
            player.setDeltaMovement(motion.x, newY, motion.z);
        }

        // --- STAR STOMP: fire 8 SmallFireball at nearest mobs every 600 ticks ---
        if (player.onGround()) {
            long lastStomp = LAST_STOMP.getOrDefault(id, 0L);
            if (currentTick - lastStomp >= 600) {
                LAST_STOMP.put(id, currentTick);
                doStarStomp(player, level);
            }
        }
    }

    private static void checkPhaseCannon(Player player, ServerLevel level, UUID id, long currentTick) {
        boolean sneaking = player.isShiftKeyDown();
        boolean wasSneaking = PREV_SNEAKING.getOrDefault(id, false);

        if (sneaking && !wasSneaking) {
            // New shift press detected
            long[] times = SHIFT_TIMES.computeIfAbsent(id, k -> new long[0]);
            // Filter to presses within last 100 ticks
            long[] filtered = Arrays.stream(times)
                .filter(t -> currentTick - t <= 100)
                .toArray();
            // Add new press
            long[] updated = Arrays.copyOf(filtered, filtered.length + 1);
            updated[filtered.length] = currentTick;
            SHIFT_TIMES.put(id, updated);

            if (updated.length >= 3) {
                // Launch Phase Cannon!
                SHIFT_TIMES.put(id, new long[0]);
                Vec3 dir = player.getLookAngle().normalize();
                PHASE_CANNON.put(id, new PhaseCannonData(dir, 30));
            }
        }

        PREV_SNEAKING.put(id, sneaking);
    }

    private static void trackSneak(Player player, ServerLevel level, UUID id, long currentTick) {
        PREV_SNEAKING.put(id, player.isShiftKeyDown());
    }

    private static void doStarStomp(Player player, ServerLevel level) {
        // Big EXPLOSION particle burst
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            player.getX(), player.getY(), player.getZ(),
            1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 0.5, player.getZ(),
            30, 1.5, 0.5, 1.5, 0.1);

        // Find up to 8 nearest living entities
        AABB box = player.getBoundingBox().inflate(30.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());
        nearby.sort((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

        int count = 0;
        for (LivingEntity target : nearby) {
            if (count >= 8) break;
            Vec3 dir = target.position().subtract(player.position()).normalize();
            SmallFireball fireball = new SmallFireball(level, player, dir);
            fireball.setPos(player.getX(), player.getY() + 1.0, player.getZ());
            level.addFreshEntity(fireball);
            count++;
        }
    }
}
