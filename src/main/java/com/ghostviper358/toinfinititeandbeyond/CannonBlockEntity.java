package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A homing block fired by the Black Hole Arm Cannon's Attack Mode 1. Drawn as a Void Block
 * (see the renderer registered in ToInfinititeAndBeyond). Flies straight with no gravity,
 * curving toward the target the cannon locked on to, and explodes on whatever it hits -
 * hurting things nearby, but never breaking blocks (the Black Hole mode is the one that eats
 * the terrain).
 *
 * Strength: weaker per hit than a Void Sword swing, but it fires about 7 a second.
 */
public class CannonBlockEntity extends ThrowableItemProjectile {

    /** Leaves the barrel slowly (so you can see it come out of the cannon)... */
    public static final float LAUNCH_SPEED = 0.6f;
    /** ...then speeds up by this much every tick until it reaches full speed. */
    private static final double ACCELERATION = 1.6;
    private static final double SPEED = 2.5;
    /** How sharply it can turn toward its target each tick (0-1). */
    private static final double HOMING = 0.25;
    /** 400 ticks at (nearly all) 2.5 blocks a tick = about 1000 blocks of range. */
    private static final int LIFETIME_TICKS = 400;

    private static final float DIRECT_DAMAGE = 8.0f;
    private static final float SPLASH_DAMAGE = 4.0f;
    private static final double SPLASH_RADIUS = 2.0;

    @Nullable
    private LivingEntity target;

    public CannonBlockEntity(EntityType<? extends CannonBlockEntity> type, Level level) {
        super(type, level);
    }

    public CannonBlockEntity(Level level, LivingEntity shooter) {
        super(ToInfinititeAndBeyond.CANNON_BLOCK.get(), shooter, level, ToInfinititeAndBeyond.VOID_BLOCK_ITEM.get().getDefaultInstance());
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }

    @Override
    protected Item getDefaultItem() {
        return ToInfinititeAndBeyond.VOID_BLOCK_ITEM.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = this.level();

        if (level.isClientSide()) {
            level.addParticle(ParticleTypes.WITCH, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            return;
        }

        if (this.tickCount > LIFETIME_TICKS) {
            this.discard();
            return;
        }
        double speed = Math.min(SPEED, this.getDeltaMovement().length() * ACCELERATION);
        Vec3 motion = this.getDeltaMovement().normalize().scale(speed);
        if (target != null && target.isAlive()) {
            Vec3 wanted = target.getBoundingBox().getCenter().subtract(this.position()).normalize().scale(speed);
            motion = motion.lerp(wanted, HOMING);
        }
        this.setDeltaMovement(motion);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof CannonBlockEntity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel level && result.getEntity() != this.getOwner()) {
            Entity hit = result.getEntity();
            // Machine-gun fire: skip the usual short "just got hurt" immunity, or most of the
            // blocks would do nothing.
            hit.invulnerableTime = 0;
            hit.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), DIRECT_DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = this.getOwner();
        Entity directHit = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(SPLASH_RADIUS),
                e -> e != owner && e != directHit && e.isAlive()
                    && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
            nearby.hurtServer(level, this.damageSources().explosion(this, owner), SPLASH_DAMAGE);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 12, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
        this.discard();
    }
}
