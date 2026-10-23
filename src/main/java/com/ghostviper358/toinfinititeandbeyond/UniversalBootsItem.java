package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Universal Boots - the third piece of the Universal armor set, crafted from Universal Shards.
 * See docs/requirements/universal_boots.md.
 *
 * Defensive stats (Armor 999, Toughness 6, +40 max health stacking with the other pieces, 40%
 * knockback resistance, unbreakable) are applied via attribute modifiers / the vanilla
 * {@code minecraft:unbreakable} data component registered in ToInfinititeAndBeyond.java (see
 * UNIVERSAL_BOOTS), same as the chestplate and leggings.
 *
 * Abilities:
 *   1. Cosmic Flight   - creative-style flight (double-tap jump), with a star trail.
 *   2. Meteor Slam     - sneak in mid-air to rocket straight down; landing blasts everything
 *                        around you. No fall damage, ever, while worn.
 *   3. Liquid Walking  - walk on top of water and lava (sneak to sink in).
 *   4. Supernova Trail - your footsteps leave burning starfire that hurts and ignites enemies.
 *
 * Everything runs from onPlayerTick in the main mod class: {@link #tick} on the server, and
 * {@link #tickLiquidWalking} on the wearer's own client (in Minecraft each player's client
 * decides where that player moves, so standing on liquid has to happen there). Meteor Slam needs
 * no packet - the server already knows when a player is sneaking.
 */
public class UniversalBootsItem extends Item {

    /** Meteor Slam: how fast you rocket downward, in blocks per tick. */
    private static final double SLAM_SPEED = 3.0;

    /** Meteor Slam: everything within this many blocks of the landing is hit. */
    private static final double SLAM_RADIUS = 8.0;
    private static final float SLAM_DAMAGE = 300.0f;

    /** Meteor Slam: how hard the landing launches things - outward and up. */
    private static final double SLAM_LAUNCH_OUT = 1.2;
    private static final double SLAM_LAUNCH_UP = 0.9;

    /** Supernova Trail: a new patch of starfire every this many ticks while walking. */
    private static final int TRAIL_SPACING_TICKS = 2;

    /** Supernova Trail: how long each patch keeps burning. */
    private static final int TRAIL_LIFETIME_TICKS = 60; // 3 seconds

    private static final float TRAIL_DAMAGE = 20.0f;
    private static final int TRAIL_FIRE_TICKS = 100; // set on fire for 5 seconds

    /** Persistent-data flag: flight was switched on by these boots (so taking them off switches it back off). */
    private static final String FLIGHT_GRANTED_KEY = ToInfinititeAndBeyond.MOD_ID + "_universal_boots_flight";

    /** Players currently rocketing down in a Meteor Slam. */
    private static final Set<UUID> SLAMMING = new HashSet<>();

    /** Whether each player was sneaking last tick, to spot the moment they start. */
    private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();

    /** Each player's burning trail, oldest patch first. */
    private static final Map<UUID, ArrayDeque<TrailPatch>> TRAILS = new HashMap<>();

    private record TrailPatch(Vec3 position, long expiresAt) {
    }

    public UniversalBootsItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof UniversalBootsItem;
    }

    /** Server side, every tick. */
    public static void tick(Player player, ServerLevel level) {
        updateFlight(player);
        tickTrail(player, level);
        if (!isWearing(player)) {
            SLAMMING.remove(player.getUUID());
            WAS_SNEAKING.remove(player.getUUID());
            return;
        }

        // No fall damage, ever.
        player.resetFallDistance();

        // Standing on lava doesn't set you alight (being *in* it still would).
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        if (level.getFluidState(below).is(FluidTags.LAVA) && !player.isInLava()) {
            player.clearFire();
        }

        tickMeteorSlam(player, level);

        if (player.getAbilities().flying) {
            // Cosmic Flight star trail.
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(),
                1, 0.15, 0.05, 0.15, 0.0);
            level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 0.2, player.getZ(),
                1, 0.2, 0.1, 0.2, 0.0);
        } else if (player.onGround() && player.tickCount % TRAIL_SPACING_TICKS == 0) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.x * motion.x + motion.z * motion.z > 0.001) {
                TRAILS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>())
                    .addLast(new TrailPatch(player.position(), level.getGameTime() + TRAIL_LIFETIME_TICKS));
            }
        }
    }

    /**
     * Cosmic Flight: lets the wearer fly while the boots are on, and takes it away again when
     * they come off - but only if the boots were what gave it (never from a creative player).
     * Remembered in the player's saved data, so it's still taken away after a restart.
     */
    private static void updateFlight(Player player) {
        CompoundTag data = player.getPersistentData();
        boolean granted = data.getBooleanOr(FLIGHT_GRANTED_KEY, false);
        boolean wearing = isWearing(player);
        var abilities = player.getAbilities();

        if (wearing && !abilities.mayfly) {
            abilities.mayfly = true;
            player.onUpdateAbilities();
            data.putBoolean(FLIGHT_GRANTED_KEY, true);
        } else if (!wearing && granted) {
            data.remove(FLIGHT_GRANTED_KEY);
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    /**
     * Meteor Slam: the moment the wearer starts sneaking in mid-air (not while flying - sneak
     * means "fly down" there), rocket them straight down until they land, then blast.
     */
    private static void tickMeteorSlam(Player player, ServerLevel level) {
        UUID id = player.getUUID();
        boolean sneaking = player.isShiftKeyDown();
        boolean startedSneaking = sneaking && !WAS_SNEAKING.getOrDefault(id, false);
        WAS_SNEAKING.put(id, sneaking);

        if (!SLAMMING.contains(id)) {
            if (startedSneaking && !player.onGround() && !player.getAbilities().flying
                    && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
                SLAMMING.add(id);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
            } else {
                return;
            }
        }

        if (player.onGround() || player.isInWater() || player.isInLava()) {
            SLAMMING.remove(id);
            meteorImpact(player, level);
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 0.2, -SLAM_SPEED, motion.z * 0.2);
        player.hurtMarked = true; // send the new speed to the player's client right away
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.5, player.getZ(),
            6, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
            6, 0.3, 0.6, 0.3, 0.02);
    }

    /** The Meteor Slam landing: damage and launch everything around, with a huge (harmless to blocks) blast. */
    private static void meteorImpact(Player player, ServerLevel level) {
        for (LivingEntity victim : targetsNear(player, level, player.getBoundingBox().inflate(SLAM_RADIUS))) {
            if (victim.distanceTo(player) > SLAM_RADIUS) {
                continue;
            }
            victim.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE);
            Vec3 away = victim.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
            Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(SLAM_LAUNCH_OUT);
            victim.setDeltaMovement(push.x, SLAM_LAUNCH_UP, push.z);
            victim.hurtMarked = true;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 48; i++) {
            double angle = i * (Math.PI * 2.0 / 48.0);
            for (double r = 2.0; r <= SLAM_RADIUS; r += 3.0) {
                level.sendParticles(ParticleTypes.FLAME, x + Math.cos(angle) * r, y + 0.2, z + Math.sin(angle) * r,
                    1, 0.1, 0.1, 0.1, 0.02);
            }
            level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(angle) * SLAM_RADIUS, y + 0.3,
                z + Math.sin(angle) * SLAM_RADIUS, 1, 0.0, 0.2, 0.0, 0.05);
        }
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.7F);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    /**
     * Supernova Trail: every patch of starfire burns and flickers until it expires, and hurts and
     * ignites anything that steps in it. Keeps burning out even after the boots come off.
     */
    private static void tickTrail(Player player, ServerLevel level) {
        ArrayDeque<TrailPatch> trail = TRAILS.get(player.getUUID());
        if (trail == null) {
            return;
        }
        long now = level.getGameTime();
        while (!trail.isEmpty() && trail.peekFirst().expiresAt() <= now) {
            trail.removeFirst();
        }
        if (trail.isEmpty() || player.level() != level) {
            TRAILS.remove(player.getUUID());
            return;
        }

        boolean sparkle = now % 2 == 0;
        for (TrailPatch patch : trail) {
            Vec3 at = patch.position();
            if (sparkle) {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z, 1, 0.15, 0.05, 0.15, 0.005);
                if (level.random.nextInt(4) == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.2, at.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
            AABB fire = new AABB(at.x - 0.5, at.y, at.z - 0.5, at.x + 0.5, at.y + 1.0, at.z + 0.5);
            for (LivingEntity victim : targetsNear(player, level, fire)) {
                victim.igniteForTicks(TRAIL_FIRE_TICKS);
                victim.hurtServer(level, level.damageSources().playerAttack(player), TRAIL_DAMAGE);
            }
        }
    }

    /**
     * Who the boots' attacks hit: hostile mobs and other players (friendly fire is intentional,
     * same as the Universal Sword), never the wearer or players in creative/spectator.
     */
    private static List<LivingEntity> targetsNear(Player wearer, Level level, AABB area) {
        return level.getEntitiesOfClass(LivingEntity.class, area,
            e -> e.isAlive() && e != wearer
                && (e instanceof Enemy || (e instanceof Player other && !other.isCreative() && !other.isSpectator())));
    }

    /**
     * Liquid Walking, on the wearer's own client every tick: if their feet are at (or just below)
     * the top of water or lava with open air above it, hold them on the surface as if it were
     * solid ground. Sneaking lets them sink in to swim; flying is unaffected.
     */
    public static void tickLiquidWalking(Player player) {
        if (!isWearing(player) || player.isShiftKeyDown() || player.getAbilities().flying || player.isSpectator()) {
            return;
        }
        Level level = player.level();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        FluidState fluid = level.getFluidState(below);
        if (fluid.isEmpty() || !level.getFluidState(below.above()).isEmpty()) {
            return; // not standing on a liquid surface (or already deep under it)
        }
        double surface = below.getY() + fluid.getHeight(level, below);
        Vec3 motion = player.getDeltaMovement();
        if (player.getY() > surface || motion.y > 0.0) {
            return; // above the surface, or jumping up off it
        }
        player.setPos(player.getX(), surface, player.getZ());
        player.setDeltaMovement(motion.x, 0.0, motion.z);
        player.setOnGround(true);
        player.resetFallDistance();
    }
}
