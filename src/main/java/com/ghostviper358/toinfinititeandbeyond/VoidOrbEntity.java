package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A Void Orb, fired by the Living Void: a slow ball of darkness that homes in on its target,
 * then explodes, hurting everything nearby and leaving a patch of withering darkness behind.
 * There's no model - it's drawn entirely with particles (see {@link #tick()}), and registered
 * with an empty renderer in ToInfinititeAndBeyond.
 */
public class VoidOrbEntity extends Projectile {

    private static final double SPEED = 0.6;
    /** How sharply it can turn toward its target each tick (0-1). */
    private static final double HOMING = 0.12;
    private static final int LIFETIME_TICKS = 200;

    private static final float EXPLOSION_DAMAGE = 14.0f;
    private static final double EXPLOSION_RADIUS = 3.0;

    @Nullable
    private Entity target;

    public VoidOrbEntity(EntityType<? extends VoidOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public VoidOrbEntity(Level level, LivingEntity shooter, Entity target, Vec3 from) {
        this(ToInfinititeAndBeyond.VOID_ORB.get(), level);
        this.setOwner(shooter);
        this.target = target;
        this.setPos(from);
        Vec3 toward = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(from);
        this.setDeltaMovement(toward.normalize().scale(SPEED));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Level level = this.level();

        if (level.isClientSide()) {
            // The orb itself: a dense knot of ink-black darkness with purple sparks.
            level.addParticle(ParticleTypes.SQUID_INK, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.SQUID_INK, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.PORTAL, this.getX(), this.getY(), this.getZ(),
                (this.random.nextDouble() - 0.5) * 0.5, (this.random.nextDouble() - 0.5) * 0.5, (this.random.nextDouble() - 0.5) * 0.5);
            level.addParticle(ParticleTypes.WITCH, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }

        if (!level.isClientSide()) {
            if (this.tickCount > LIFETIME_TICKS) {
                explode();
                return;
            }
            if (target != null && target.isAlive()) {
                Vec3 wanted = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(this.position()).normalize().scale(SPEED);
                this.setDeltaMovement(this.getDeltaMovement().lerp(wanted, HOMING));
            }
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        this.setPos(this.position().add(this.getDeltaMovement()));
        if (hit.getType() != HitResult.Type.MISS && !level.isClientSide()) {
            this.hitTargetOrDeflectSelf(hit);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof LivingVoid) && !(entity instanceof VoidOrbEntity);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        explode();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // The explosion (from onHit) does the damage.
    }

    /** Bursts, hurting everything nearby, and leaves a lingering cloud of withering darkness. */
    private void explode() {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        Entity owner = this.getOwner();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(EXPLOSION_RADIUS),
                e -> e != owner && e.isAlive() && !(e instanceof LivingVoid)
                    && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
            victim.hurtServer(level, this.damageSources().indirectMagic(this, owner), EXPLOSION_DAMAGE);
        }

        AreaEffectCloud cloud = new AreaEffectCloud(level, this.getX(), this.getY() - 0.5, this.getZ());
        if (owner instanceof LivingEntity livingOwner) {
            cloud.setOwner(livingOwner);
        }
        cloud.setCustomParticle(ParticleTypes.SQUID_INK);
        cloud.setRadius(3.0f);
        cloud.setDuration(100);
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(-0.01f);
        cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
        level.addFreshEntity(cloud);

        level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY(), this.getZ(), 40, 1.2, 1.2, 1.2, 0.1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 30, 1.0, 1.0, 1.0, 0.2);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.7f);
        this.discard();
    }
}
