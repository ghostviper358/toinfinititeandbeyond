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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A chunk of rock hurled by the Infinitite Monstrosity's Rock Barrage. Drawn as a big block of
 * crying obsidian (see the renderer registered in ToInfinititeAndBeyond). Bursts into purple
 * sparks on impact, hurting whatever it hits directly and anything right around it - but never
 * breaking blocks.
 */
public class MonstrosityRockEntity extends ThrowableItemProjectile {

    private static final float DIRECT_DAMAGE = 20.0f;
    private static final float SPLASH_DAMAGE = 8.0f;
    private static final double SPLASH_RADIUS = 2.5;

    public MonstrosityRockEntity(EntityType<? extends MonstrosityRockEntity> type, Level level) {
        super(type, level);
    }

    public MonstrosityRockEntity(Level level, LivingEntity thrower) {
        super(ToInfinititeAndBeyond.MONSTROSITY_ROCK.get(), thrower, level, Items.CRYING_OBSIDIAN.getDefaultInstance());
    }

    @Override
    protected Item getDefaultItem() {
        return Items.CRYING_OBSIDIAN;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel level && result.getEntity() != this.getOwner()) {
            result.getEntity().hurtServer(level, this.damageSources().thrown(this, this.getOwner()), DIRECT_DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = this.getOwner();
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position()).inflate(SPLASH_RADIUS),
                e -> e != owner && e.isAlive() && !(e instanceof InfinititeMonstrosity)
                    && !(e instanceof Player player && (player.isCreative() || player.isSpectator())))) {
            nearby.hurtServer(level, this.damageSources().thrown(this, owner), SPLASH_DAMAGE);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 40, 0.8, 0.8, 0.8, 0.15);
        level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY(), this.getZ(), 15, 0.8, 0.8, 0.8, 0.05);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.1f);
        this.discard();
    }
}
