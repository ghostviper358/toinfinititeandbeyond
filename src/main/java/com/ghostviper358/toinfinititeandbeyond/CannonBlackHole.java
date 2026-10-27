package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A black hole fired by the Black Hole Arm Cannon's Attack Mode 3. It appears where you're aiming
 * and lasts {@link #LIFETIME_TICKS} (5 seconds). The whole time it:
 *
 * - pulls in every mob, player and dropped item within {@link #PULL_RANGE} blocks, spiralling
 *   them toward the middle. It only pulls the player who fired it when they're within
 *   {@link #SHOOTER_PULL_RANGE} blocks, so firing it far away is safe;
 * - hurts everything that reaches the middle by {@link #DAMAGE} every half second;
 * - eats the blocks around it (anything but unbreakable blocks like bedrock, liquids, and blocks
 *   that hold items like chests), sucking them in as it grows - and every block it eats is a shot
 *   of ammo back into the shooter's cannon.
 *
 * Drawn by CannonBlackHoleRenderer as a spinning black core inside a see-through purple shell,
 * with energy streaming into it (the particles here). It's never saved: if the world is closed
 * while one is out, it's simply gone.
 */
public class CannonBlackHole extends Entity {

    public static final int LIFETIME_TICKS = 100;
    /** How far away you can aim it. */
    private static final double RANGE = 48.0;

    private static final double PULL_RANGE = 16.0;
    private static final double SHOOTER_PULL_RANGE = 8.0;
    /** How hard it pulls, right next to it (it gets weaker further away). */
    private static final double PULL_STRENGTH = 0.18;
    private static final double MAX_PULL_SPEED = 1.2;

    /** Everything within this far of the middle (the 5x5x5 centre) gets hurt. */
    private static final double CORE_RADIUS = 2.5;
    private static final float DAMAGE = 10.0f;
    private static final int DAMAGE_EVERY_TICKS = 10;

    /** It starts eating blocks right around it, then grows out to this radius. */
    private static final double MIN_EAT_RADIUS = 1.5;
    private static final double MAX_EAT_RADIUS = 3.5;
    private static final int EAT_GROW_TICKS = 40;
    /** Each tick it looks at this many random spots and eats at most this many blocks. */
    private static final int EAT_TRIES_PER_TICK = 10;
    private static final int MAX_EATEN_PER_TICK = 3;

    @Nullable
    private Player shooter;

    public CannonBlackHole(EntityType<? extends CannonBlackHole> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Fires a black hole where the player is aiming (or out in the air, if they're aiming at nothing). */
    public static void fire(ServerLevel level, Player shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        // Stop a little short of whatever was hit, so the black hole floats in front of it.
        Vec3 at = hit.getType() == HitResult.Type.MISS
            ? eye.add(look.scale(RANGE))
            : hit.getLocation().subtract(look.scale(1.5));

        CannonBlackHole blackHole = new CannonBlackHole(ToInfinititeAndBeyond.CANNON_BLACK_HOLE.get(), level);
        blackHole.shooter = shooter;
        blackHole.setPos(at);
        level.addFreshEntity(blackHole);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7f, 1.6f);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            spawnParticles();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.tickCount >= LIFETIME_TICKS) {
            collapse(level);
            return;
        }
        pull();
        if (this.tickCount % DAMAGE_EVERY_TICKS == 0) {
            hurtTheMiddle(level);
        }
        eatBlocks(level);
        if (this.tickCount % 20 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.5f);
        }
    }

    /** Spirals everything nearby in toward the middle. */
    private void pull() {
        Vec3 centre = this.position();
        for (Entity entity : this.level().getEntities(this, new AABB(centre, centre).inflate(PULL_RANGE),
                e -> !(e instanceof CannonBlackHole) && !e.isSpectator() && !(e instanceof Player player && player.getAbilities().flying))) {
            Vec3 toCentre = centre.subtract(entity.getBoundingBox().getCenter());
            double distance = toCentre.length();
            if (distance > PULL_RANGE || (entity == shooter && distance > SHOOTER_PULL_RANGE)) {
                continue;
            }
            Vec3 motion;
            if (distance < 0.8) {
                // Caught in the middle: held there, slowly turning.
                motion = entity.getDeltaMovement().scale(0.3).add(toCentre.scale(0.2));
            } else {
                Vec3 inward = toCentre.normalize();
                Vec3 around = inward.cross(new Vec3(0.0, 1.0, 0.0)).scale(0.35);
                double strength = PULL_STRENGTH * (1.0 - distance / PULL_RANGE) + 0.03;
                motion = entity.getDeltaMovement().add(inward.add(around).scale(strength));
                if (motion.length() > MAX_PULL_SPEED) {
                    motion = motion.normalize().scale(MAX_PULL_SPEED);
                }
            }
            entity.setDeltaMovement(motion);
            entity.resetFallDistance();
            entity.hurtMarked = true; // send the new speed to players' clients
        }
    }

    private void hurtTheMiddle(ServerLevel level) {
        Vec3 centre = this.position();
        DamageSource source = this.damageSources().indirectMagic(this, shooter);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(CORE_RADIUS),
                e -> e.isAlive() && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
            victim.hurtServer(level, source, DAMAGE);
        }
    }

    /** Eats a few random blocks within its (growing) reach, flinging their pieces into the middle. */
    private void eatBlocks(ServerLevel level) {
        double radius = Mth.lerp(Math.min(1.0, this.tickCount / (double) EAT_GROW_TICKS), MIN_EAT_RADIUS, MAX_EAT_RADIUS);
        Vec3 centre = this.position();
        int eaten = 0;
        for (int i = 0; i < EAT_TRIES_PER_TICK && eaten < MAX_EATEN_PER_TICK; i++) {
            Vec3 spot = centre.add(
                (this.random.nextDouble() * 2.0 - 1.0) * radius,
                (this.random.nextDouble() * 2.0 - 1.0) * radius,
                (this.random.nextDouble() * 2.0 - 1.0) * radius);
            if (spot.distanceTo(centre) > radius) {
                continue;
            }
            BlockPos pos = BlockPos.containing(spot);
            BlockState state = level.getBlockState(pos);
            if (!canEat(level, pos, state)) {
                continue;
            }
            level.removeBlock(pos, false);
            eaten++;

            Vec3 from = Vec3.atCenterOf(pos);
            Vec3 inward = centre.subtract(from).normalize();
            for (int p = 0; p < 4; p++) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    from.x, from.y, from.z, 0, inward.x, inward.y, inward.z, 0.4);
            }
        }
        if (eaten > 0) {
            if (shooter != null && shooter.isAlive()) {
                BlackHoleArmCannonItem.feedAmmo(shooter, eaten);
            }
            if (this.random.nextInt(3) == 0) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 0.5f, 0.6f);
            }
        }
    }

    /** Everything but air, liquids, unbreakable blocks (like bedrock) and blocks that hold things (like chests). */
    private static boolean canEat(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
            && state.getFluidState().isEmpty()
            && state.getDestroySpeed(level, pos) >= 0.0f
            && !state.hasBlockEntity();
    }

    /** At the end it collapses in a burst of purple. */
    private void collapse(ServerLevel level) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 80, 0.5, 0.5, 0.5, 0.6);
        level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.5f, 0.5f);
        this.discard();
    }

    /** Purple energy streaming in from all around, and a swirling ring around its middle. */
    private void spawnParticles() {
        Level level = this.level();
        for (int i = 0; i < 4; i++) {
            Vec3 from = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian())
                .normalize().scale(3.0 + this.random.nextDouble() * 3.0);
            // Portal particles fly from (position + speed) to position, so these stream inward.
            level.addParticle(ParticleTypes.PORTAL, this.getX(), this.getY(), this.getZ(), from.x, from.y, from.z);
        }
        float angle = this.tickCount * 0.5f;
        for (int i = 0; i < 3; i++) {
            float a = angle + i * Mth.TWO_PI / 3;
            double r = 2.2;
            level.addParticle(ParticleTypes.WITCH, this.getX() + Mth.cos(a) * r, this.getY(), this.getZ() + Mth.sin(a) * r, 0.0, 0.0, 0.0);
        }
    }

    // --- Plain entity bits: it can't be hurt, has no synced data, and is never saved ---

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }
}
