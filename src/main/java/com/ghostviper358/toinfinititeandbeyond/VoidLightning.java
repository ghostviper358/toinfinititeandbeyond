package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The Black Hole Arm Cannon's Attack Mode 4: Void Lightning - its strongest attack.
 *
 * Black lightning strikes instantly wherever you're aiming (no travel time, so it can't be
 * dodged), hitting everything within {@link #STRIKE_RADIUS} blocks of where it lands for
 * {@link #DAMAGE}. Then it jumps, one strike after another, to up to {@link #MAX_JUMPS} more
 * targets outside that area - each time to the closest monster or player it hasn't hit yet -
 * hitting each one for {@link #DAMAGE} too.
 *
 * It never hurts the player who fired it or their pets, and never sets anything on fire. The
 * bolts themselves are VoidLightningBolt.
 */
public final class VoidLightning {

    /** How far away you can aim it. */
    private static final double RANGE = 128.0;
    private static final double STRIKE_RADIUS = 12.0;
    private static final float DAMAGE = 200.0f;

    private static final int MAX_JUMPS = 8;
    /** How far it can jump from one target to the next. */
    private static final double JUMP_RANGE = 24.0;
    /** Ticks between each jump, so you can see it hop from target to target. */
    private static final int JUMP_DELAY_TICKS = 3;

    private static final List<Chain> CHAINS = new ArrayList<>();

    /** The jumps still to come from one strike. */
    private static final class Chain {
        final ServerLevel level;
        final Player shooter;
        final Vec3 strikePoint;
        final Set<LivingEntity> alreadyHit;
        Vec3 from;
        int jumpsLeft = MAX_JUMPS;
        int wait = JUMP_DELAY_TICKS;

        Chain(ServerLevel level, Player shooter, Vec3 strikePoint, Set<LivingEntity> alreadyHit) {
            this.level = level;
            this.shooter = shooter;
            this.strikePoint = strikePoint;
            this.alreadyHit = alreadyHit;
            this.from = strikePoint;
        }
    }

    private VoidLightning() {
    }

    /** Strikes wherever the player is aiming. Returns false (and does nothing) if they're aiming at nothing. */
    public static boolean fire(ServerLevel level, Player shooter) {
        Vec3 target = findTarget(level, shooter);
        if (target == null) {
            return false;
        }
        bolt(level, target);

        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(STRIKE_RADIUS),
                e -> canHit(e, shooter) && e.position().distanceTo(target) <= STRIKE_RADIUS)) {
            strike(level, shooter, victim);
            hit.add(victim);
        }
        level.sendParticles(ParticleTypes.SQUID_INK, target.x, target.y + 0.5, target.z, 60, 2.0, 0.5, 2.0, 0.1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.x, target.y + 0.5, target.z, 80, 3.0, 0.5, 3.0, 0.3);

        CHAINS.add(new Chain(level, shooter, target, hit));
        return true;
    }

    /**
     * Forgets everything still in progress. Called when the server stops, so nothing keeps
     * running against a closed world after you quit to the title screen.
     */
    public static void clearAll() {
        CHAINS.clear();
    }

    /** Advances every chain of jumps by one tick. Called once per server tick. */
    public static void tick() {
        Iterator<Chain> iterator = CHAINS.iterator();
        while (iterator.hasNext()) {
            Chain chain = iterator.next();
            if (--chain.wait > 0) {
                continue;
            }
            chain.wait = JUMP_DELAY_TICKS;
            LivingEntity next = nextTarget(chain);
            if (next == null) {
                iterator.remove();
                continue;
            }
            bolt(chain.level, next.position());
            strike(chain.level, chain.shooter, next);
            chain.alreadyHit.add(next);
            chain.from = next.position();
            if (--chain.jumpsLeft <= 0) {
                iterator.remove();
            }
        }
    }

    /** The closest monster or player (not hit yet, and outside the first strike's area) to jump to next. */
    @Nullable
    private static LivingEntity nextTarget(Chain chain) {
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : chain.level.getEntitiesOfClass(LivingEntity.class, new AABB(chain.from, chain.from).inflate(JUMP_RANGE),
                e -> canHit(e, chain.shooter) && (e instanceof Enemy || e instanceof Player) && !chain.alreadyHit.contains(e))) {
            if (candidate.position().distanceTo(chain.strikePoint) <= STRIKE_RADIUS) {
                continue;
            }
            double distance = candidate.position().distanceTo(chain.from);
            if (distance <= JUMP_RANGE && distance < closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }
        return closest;
    }

    /** Never the shooter, their pets, or players in creative/spectator mode. */
    private static boolean canHit(LivingEntity entity, Player shooter) {
        return entity != shooter && entity.isAlive()
            && !(entity instanceof OwnableEntity pet && pet.getOwner() == shooter)
            && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private static void strike(ServerLevel level, Player shooter, LivingEntity victim) {
        victim.invulnerableTime = 0;
        victim.hurtServer(level, level.damageSources().playerAttack(shooter), DAMAGE);
    }

    /** A Void Lightning bolt (just the look and the sound) coming down at a spot. */
    private static void bolt(ServerLevel level, Vec3 at) {
        VoidLightningBolt bolt = new VoidLightningBolt(ToInfinititeAndBeyond.VOID_LIGHTNING_BOLT.get(), level);
        bolt.setPos(at);
        level.addFreshEntity(bolt);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0f, 0.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0f, 0.5f);
    }

    /** Where the player is aiming: the first living thing their crosshair is on, or else the first block. */
    @Nullable
    private static Vec3 findTarget(ServerLevel level, Player shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 end = eye.add(shooter.getLookAngle().scale(RANGE));
        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(shooter, eye, reach,
            new AABB(eye, reach).inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(),
            eye.distanceToSqr(reach));
        if (entityHit != null) {
            return entityHit.getEntity().position();
        }
        return blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation();
    }
}
