package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Universal Helmet - the final and most powerful piece of the Universal armor set, crafted from
 * Universal Shards. See docs/requirements/universal_helmet.md.
 *
 * Defensive stats (Armor 999, Toughness 6, +100 max health - 50 hearts, more than any other
 * piece - 40% knockback resistance, unbreakable) are applied via attribute modifiers / the vanilla
 * {@code minecraft:unbreakable} data component registered in ToInfinititeAndBeyond.java (see
 * UNIVERSAL_HELMET), same as the rest of the set.
 *
 * Abilities:
 *   1. Cosmic Eye Beam - press the Eye Beam key (R by default) to fire a purple laser from your
 *                        eyes, blasting through every target in a straight line.
 *   2. Time Stop       - press the Time Stop key (G by default) to freeze every mob, player and
 *                        projectile around you for 5 seconds, while you keep moving.
 *   3. Mind Control    - hostile mobs near you can't target you, and turn on each other instead.
 *
 * The two key abilities come from UniversalHelmetClientHandler via UniversalHelmetNetworking.
 * Mind Control runs from {@link #tick} (onPlayerTick) and {@link #onChangeTarget}; Time Stop
 * advances once per server tick from {@link #tickTimeStops} (onServerTick).
 */
public class UniversalHelmetItem extends Item {

    /** Cosmic Eye Beam: range in blocks (stops at solid blocks), damage, and cooldown. */
    private static final double BEAM_RANGE = 64.0;
    private static final float BEAM_DAMAGE = 500.0f;
    private static final int BEAM_COOLDOWN_TICKS = 30; // 1.5 seconds

    /** Time Stop: radius, how long time stays frozen, and the cooldown after it ends. */
    private static final double TIME_STOP_RADIUS = 32.0;
    private static final int TIME_STOP_TICKS = 100; // 5 seconds
    private static final int TIME_STOP_COOLDOWN_TICKS = 300; // 15 seconds after time starts again

    /** Mind Control: how far from the wearer hostile mobs fall under control. */
    private static final double MIND_CONTROL_RADIUS = 16.0;

    /**
     * Saved on a mob while time is stopped for it, holding whether it had No AI before. If the
     * game quits mid-freeze the mob is saved still frozen; this lets it be unfrozen properly the
     * next time it loads (see {@link #onEntityJoin}).
     */
    private static final String FROZEN_KEY = ToInfinititeAndBeyond.MOD_ID + "_time_stopped_had_no_ai";

    private static final Map<UUID, Long> BEAM_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> TIME_STOP_READY_AT = new HashMap<>();
    private static final List<TimeStop> ACTIVE_TIME_STOPS = new ArrayList<>();

    public UniversalHelmetItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof UniversalHelmetItem;
    }

    /**
     * Who the helmet's attacks and Time Stop affect: other living things - mobs of every kind and
     * other players (friendly fire is intentional, same as the rest of the set) - but never the
     * wearer, or players in creative/spectator.
     */
    private static boolean isTarget(Entity entity, Player wearer) {
        if (entity == wearer || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof Player other) {
            return !other.isCreative() && !other.isSpectator();
        }
        return entity instanceof LivingEntity;
    }

    // ===================================== Cosmic Eye Beam =====================================

    public static void handleEyeBeam(ServerPlayer player) {
        if (!isWearing(player) || !(player.level() instanceof ServerLevel level) || player.isSpectator()) {
            return;
        }
        long now = level.getGameTime();
        if (now < BEAM_READY_AT.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        BEAM_READY_AT.put(player.getUUID(), now + BEAM_COOLDOWN_TICKS);

        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = level.clip(new ClipContext(start, start.add(look.scale(BEAM_RANGE)), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player)).getLocation();

        AABB path = new AABB(start, end).inflate(1.0);
        for (Entity hit : level.getEntities(player, path,
                e -> isTarget(e, player) && e.getBoundingBox().inflate(0.5).clip(start, end).isPresent())) {
            LivingEntity victim = (LivingEntity) hit;
            victim.invulnerableTime = 0;
            victim.hurtServer(level, level.damageSources().playerAttack(player), BEAM_DAMAGE);
            victim.igniteForTicks(100);
            level.sendParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }

        // The beam itself: a dense purple-white line from the eyes to wherever it stopped. Sent
        // with "always show" so it's visible along its whole length, not just near the shooter.
        Vec3 step = end.subtract(start);
        int points = (int) Math.ceil(step.length() * 3.0);
        for (int i = 1; i <= points; i++) {
            Vec3 at = start.add(step.scale(i / (double) points)).subtract(0.0, 0.2, 0.0);
            level.sendParticles(ParticleTypes.WITCH, true, true, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(ParticleTypes.FLASH, true, true, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.4F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    // ======================================== Time Stop ========================================

    public static void handleTimeStop(ServerPlayer player) {
        if (!isWearing(player) || !(player.level() instanceof ServerLevel level) || player.isSpectator()) {
            return;
        }
        long now = level.getGameTime();
        if (now < TIME_STOP_READY_AT.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        for (TimeStop stop : ACTIVE_TIME_STOPS) {
            if (stop.caster == player) {
                return;
            }
        }
        TIME_STOP_READY_AT.put(player.getUUID(), now + TIME_STOP_TICKS + TIME_STOP_COOLDOWN_TICKS);
        ACTIVE_TIME_STOPS.add(new TimeStop(level, player));
    }

    /** Advances every Time Stop by one tick. Called once per server tick. */
    public static void tickTimeStops() {
        ACTIVE_TIME_STOPS.removeIf(TimeStop::tick);
    }

    /** Unfreeze everything right away - the server is shutting down. */
    public static void endAllTimeStops() {
        for (TimeStop stop : ACTIVE_TIME_STOPS) {
            stop.release();
        }
        ACTIVE_TIME_STOPS.clear();
    }

    /** A mob that was saved to disk mid-freeze (the game quit) gets unfrozen as it loads back in. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            CompoundTag data = mob.getPersistentData();
            if (data.contains(FROZEN_KEY) && !isFrozenNow(mob)) {
                mob.setNoAi(data.getBooleanOr(FROZEN_KEY, false));
                data.remove(FROZEN_KEY);
            }
        }
    }

    private static boolean isFrozenNow(Entity entity) {
        for (TimeStop stop : ACTIVE_TIME_STOPS) {
            for (Frozen frozen : stop.frozen) {
                if (frozen.entity == entity) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One entity held still: where it's held, and what to give back when time starts again. */
    private static final class Frozen {
        final Entity entity;
        final Vec3 position;
        final Vec3 velocity;
        final boolean hadNoGravity;

        Frozen(Entity entity) {
            this.entity = entity;
            this.position = entity.position();
            this.velocity = entity.getDeltaMovement();
            this.hadNoGravity = entity.isNoGravity();
        }
    }

    /**
     * Five seconds of stopped time around one caster. Everything caught at the start - mobs,
     * other players, arrows and other projectiles - is held exactly where it was every tick, with
     * mob AI switched off. When time starts again, projectiles carry on flying as if nothing
     * happened. The caster can move, fight and use their other abilities freely the whole time.
     */
    private static final class TimeStop {
        final ServerLevel level;
        final ServerPlayer caster;
        final List<Frozen> frozen = new ArrayList<>();
        int ticksLeft = TIME_STOP_TICKS;

        TimeStop(ServerLevel level, ServerPlayer caster) {
            this.level = level;
            this.caster = caster;
            AABB area = caster.getBoundingBox().inflate(TIME_STOP_RADIUS);
            for (Entity entity : level.getEntities(caster, area,
                    e -> (isTarget(e, caster) || e instanceof Projectile) && !isFrozenNow(e))) {
                frozen.add(new Frozen(entity));
                entity.setNoGravity(true);
                if (entity instanceof Mob mob) {
                    mob.getPersistentData().putBoolean(FROZEN_KEY, mob.isNoAi());
                    mob.setNoAi(true);
                }
            }

            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, caster.getX(), caster.getY() + 1.0,
                caster.getZ(), 300, TIME_STOP_RADIUS * 0.3, 2.0, TIME_STOP_RADIUS * 0.3, 0.05);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.6F);
            caster.displayClientMessage(Component.literal("§5§lTime stops!"), true);
        }

        /** @return true once time has started again (safe to discard this instance). */
        boolean tick() {
            ticksLeft--;
            if (ticksLeft <= 0 || !caster.isAlive()) {
                release();
                return true;
            }

            for (Iterator<Frozen> it = frozen.iterator(); it.hasNext(); ) {
                Frozen held = it.next();
                Entity entity = held.entity;
                if (entity.isRemoved() || entity.level() != level) {
                    it.remove();
                    continue;
                }
                if (entity instanceof ServerPlayer player) {
                    player.teleportTo(held.position.x, held.position.y, held.position.z);
                    player.resetFallDistance();
                } else {
                    entity.setPos(held.position);
                }
                entity.setDeltaMovement(Vec3.ZERO);
                entity.hurtMarked = true;
                if (ticksLeft % 10 == 0) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                        entity.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
                }
            }
            return false;
        }

        void release() {
            for (Frozen held : frozen) {
                Entity entity = held.entity;
                if (entity.isRemoved()) {
                    continue;
                }
                entity.setNoGravity(held.hadNoGravity);
                if (entity instanceof Mob mob) {
                    CompoundTag data = mob.getPersistentData();
                    mob.setNoAi(data.getBooleanOr(FROZEN_KEY, false));
                    data.remove(FROZEN_KEY);
                }
                if (entity instanceof Projectile) {
                    entity.setDeltaMovement(held.velocity); // and off it goes again
                    entity.hurtMarked = true;
                }
            }
            frozen.clear();
            if (caster.isAlive()) {
                level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.4F);
                caster.displayClientMessage(Component.literal("§dTime resumes..."), true);
            }
        }
    }

    // ====================================== Mind Control =======================================

    /**
     * Mind Control, every half second: every hostile mob near the wearer that's after them (or has
     * no target) is turned on the nearest other hostile mob instead, with a purple swirl over its
     * head to show it's under control.
     */
    public static void tick(Player player, ServerLevel level) {
        if (!isWearing(player) || player.tickCount % 10 != 0) {
            return;
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MIND_CONTROL_RADIUS),
                m -> m instanceof Enemy && m.isAlive() && !m.isNoAi())) {
            LivingEntity current = mob.getTarget();
            if (current == null || !current.isAlive() || current == player) {
                mob.setTarget(otherEnemyNear(mob, level));
            }
            level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(),
                2, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /**
     * Mind Control's other half: whenever a hostile mob near a helmet wearer tries to target them,
     * redirect it at another hostile mob (or, if there's none around, at nothing).
     *
     * @return true to cancel the target change
     */
    public static boolean onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)
                || !(event.getNewTarget() instanceof Player target) || !isWearing(target)
                || !(mob.level() instanceof ServerLevel level) || mob.distanceTo(target) > MIND_CONTROL_RADIUS) {
            return false;
        }
        LivingEntity other = otherEnemyNear(mob, level);
        if (other == null) {
            return true;
        }
        event.setNewTarget(other);
        return false;
    }

    /** The closest other hostile mob to {@code mob}, or null if there isn't one nearby. */
    private static LivingEntity otherEnemyNear(Mob mob, ServerLevel level) {
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(MIND_CONTROL_RADIUS),
                e -> e != mob && e instanceof Enemy && e.isAlive())) {
            double distance = other.distanceToSqr(mob);
            if (distance < closestDistance) {
                closest = other;
                closestDistance = distance;
            }
        }
        return closest;
    }
}
