package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Black Hole Arm Cannon's Attack Mode 2: Infinitite Spikes.
 *
 * Aim at something and fire: for half a second, purple sparks swirl on the ground under it (the
 * warning - just enough time to jump out of the way). Then a ring of purple spikes bursts out of
 * the ground: one big spike in the middle with eight smaller ones around it, about 5 blocks wide.
 * Everything caught in the eruption takes a big hit and gets thrown into the air. The spikes stay
 * for a few seconds, hurting anything that walks into them, then crumble (see InfinititeSpikeBlock).
 *
 * Strength: the slow, heavy attack - {@link #ERUPTION_DAMAGE} per hit, but it costs a quarter of
 * the cannon's ammo and can only be fired once every 2 seconds.
 */
public final class CannonSpikes {

    /** How far away you can aim the spikes. */
    private static final double RANGE = 64.0;
    /** The warning before the spikes come up (half a second). */
    private static final int WARNING_TICKS = 10;
    private static final float ERUPTION_DAMAGE = 40.0f;
    private static final double LAUNCH_SPEED = 1.1;

    private static final int CENTRE_HEIGHT = 4;
    private static final int RING_HEIGHT = 2;
    /** Where the ring spikes go, around the big one in the middle. */
    private static final int[][] RING = {{2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -2}, {0, -2}, {2, -2}};
    /** How far up or down from the middle a ring spike looks for ground (for bumpy ground and slopes). */
    private static final int GROUND_SEARCH = 3;

    private static final DustParticleOptions WARNING_DUST = new DustParticleOptions(0xB040FF, 1.2f);

    private static final List<Eruption> PENDING = new ArrayList<>();

    /** One spike: where it starts and how tall it grows. */
    private record Spike(BlockPos ground, int height) {
    }

    private static final class Eruption {
        final ServerLevel level;
        final Player shooter;
        final BlockPos centre;
        final List<Spike> spikes;
        int age;

        Eruption(ServerLevel level, Player shooter, BlockPos centre, List<Spike> spikes) {
            this.level = level;
            this.shooter = shooter;
            this.centre = centre;
            this.spikes = spikes;
        }
    }

    private CannonSpikes() {
    }

    /**
     * Starts a spike attack wherever the player is aiming. Returns false (and does nothing) if
     * there's no ground there for spikes to grow from.
     */
    public static boolean fire(ServerLevel level, Player shooter) {
        Vec3 target = findTarget(level, shooter);
        if (target == null) {
            return false;
        }
        BlockPos centre = findGround(level, BlockPos.containing(target).above(), 16);
        if (centre == null) {
            return false;
        }

        List<Spike> spikes = new ArrayList<>();
        spikes.add(new Spike(centre, CENTRE_HEIGHT));
        for (int[] offset : RING) {
            BlockPos ground = findGround(level, centre.offset(offset[0], GROUND_SEARCH, offset[1]), GROUND_SEARCH * 2);
            if (ground != null) {
                spikes.add(new Spike(ground, RING_HEIGHT));
            }
        }

        PENDING.add(new Eruption(level, shooter, centre, spikes));
        level.playSound(null, centre, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    /** Where the player is aiming: the first thing (or block) their crosshair hits. */
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
        return blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation().add(0.0, 0.1, 0.0);
    }

    /**
     * Starting at {@code from} and going down at most {@code depth} blocks, the first empty space
     * sitting on solid ground - where a spike can grow from. Null if there isn't one.
     */
    @Nullable
    private static BlockPos findGround(ServerLevel level, BlockPos from, int depth) {
        BlockPos.MutableBlockPos pos = from.mutable();
        for (int i = 0; i <= depth; i++) {
            BlockPos below = pos.below();
            if (level.getBlockState(pos).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return pos.immutable();
            }
            pos.move(Direction.DOWN);
        }
        return null;
    }

    /**
     * Forgets everything still in progress. Called when the server stops, so nothing keeps
     * running against a closed world after you quit to the title screen.
     */
    public static void clearAll() {
        PENDING.clear();
    }

    /** Advances every spike attack by one tick. Called once per server tick. */
    public static void tick() {
        Iterator<Eruption> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Eruption eruption = iterator.next();
            int age = eruption.age++;
            if (age < WARNING_TICKS) {
                warn(eruption, age);
            } else if (age == WARNING_TICKS) {
                erupt(eruption);
                grow(eruption, 1);
            } else {
                int height = age - WARNING_TICKS + 1;
                grow(eruption, height);
                if (height >= CENTRE_HEIGHT) {
                    iterator.remove();
                }
            }
        }
    }

    /** The warning: purple sparks swirling on the ground where each spike will come up. */
    private static void warn(Eruption eruption, int age) {
        for (Spike spike : eruption.spikes) {
            double x = spike.ground().getX() + 0.5;
            double y = spike.ground().getY() + 0.1;
            double z = spike.ground().getZ() + 0.5;
            eruption.level.sendParticles(WARNING_DUST, x, y, z, 3, 0.3, 0.02, 0.3, 0.0);
            if (age % 3 == 0) {
                eruption.level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 2, 0.2, 0.0, 0.2, 0.05);
            }
        }
    }

    /** The spikes burst up: everything caught is hit hard and thrown into the air. */
    private static void erupt(Eruption eruption) {
        ServerLevel level = eruption.level;
        BlockPos centre = eruption.centre;
        AABB area = new AABB(centre).inflate(2.5, 0.0, 2.5).expandTowards(0.0, CENTRE_HEIGHT, 0.0);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != eruption.shooter && e.isAlive()
                    && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
            victim.hurtServer(level, level.damageSources().playerAttack(eruption.shooter), ERUPTION_DAMAGE);
            Vec3 motion = victim.getDeltaMovement();
            victim.setDeltaMovement(motion.x, LAUNCH_SPEED, motion.z);
            victim.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 2, 1.0, 0.2, 1.0, 0.0);
        level.playSound(null, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    /**
     * Each spike grows one block taller per tick until it reaches its full height. The top block
     * is always the sharp tip, so the spikes look pointed the whole time they're growing. A spike
     * stops early if something's in the way.
     */
    private static void grow(Eruption eruption, int height) {
        for (Spike spike : eruption.spikes) {
            int blocks = Math.min(height, spike.height());
            for (int i = 0; i < blocks; i++) {
                BlockPos pos = spike.ground().above(i);
                BlockState existing = eruption.level.getBlockState(pos);
                if (!existing.isAir() && !existing.is(ToInfinititeAndBeyond.INFINITITE_SPIKE.get())) {
                    break;
                }
                eruption.level.setBlock(pos, spikePart(i, blocks), 3);
            }
        }
        if (height <= CENTRE_HEIGHT) {
            eruption.level.playSound(null, eruption.centre, SoundEvents.POINTED_DRIPSTONE_PLACE, SoundSource.PLAYERS, 1.0f,
                Mth.lerp(height / (float) CENTRE_HEIGHT, 0.8f, 1.4f));
        }
    }

    /** The right part of a spike for block {@code index} (0 = bottom) of a spike {@code height} tall. */
    private static BlockState spikePart(int index, int height) {
        DripstoneThickness thickness;
        if (index == height - 1) {
            thickness = DripstoneThickness.TIP;
        } else if (index == 0 && height >= 3) {
            thickness = DripstoneThickness.BASE;
        } else {
            thickness = DripstoneThickness.MIDDLE;
        }
        return ToInfinititeAndBeyond.INFINITITE_SPIKE.get().defaultBlockState().setValue(InfinititeSpikeBlock.THICKNESS, thickness);
    }
}
