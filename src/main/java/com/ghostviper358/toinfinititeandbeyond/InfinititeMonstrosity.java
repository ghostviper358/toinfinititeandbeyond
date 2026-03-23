package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Infinitite Monstrosity - A giant, terrifying boss-tier mob armed with a void sword.
 *
 * Properties:
 * - 300 max health (150 hearts)
 * - 20 melee attack damage
 * - High follow range (64 blocks)
 * - Slow movement (it is massive)
 * - Applies Wither II + Poison II + Weakness III on hit
 * - Summons void lightning when badly hurt (below 50% HP)
 * - Periodic ground slam shockwave
 */
public class InfinititeMonstrosity extends Monster {

    /** Tick counter used to space out special ability triggers. */
    private int abilityTick = 0;

    public InfinititeMonstrosity(EntityType<? extends InfinititeMonstrosity> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------------------
    // Attributes - called once during registration to define the mob's stats
    // ---------------------------------------------------------------------------
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0)          // 150 hearts
            .add(Attributes.ATTACK_DAMAGE, 20.0)        // 10 hearts per hit
            .add(Attributes.MOVEMENT_SPEED, 0.22)       // slower than zombies (0.23)
            .add(Attributes.FOLLOW_RANGE, 64.0)         // sees players from far
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)  // immune to knockback
            .add(Attributes.ARMOR, 10.0)                // heavily armoured
            .add(Attributes.ARMOR_TOUGHNESS, 8.0)
            .add(Attributes.SCALE, 3.0);                // 3x physical size
    }

    // ---------------------------------------------------------------------------
    // AI goals
    // ---------------------------------------------------------------------------
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------------------------------------------------------------------------
    // Sounds
    // ---------------------------------------------------------------------------
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    // ---------------------------------------------------------------------------
    // Tick - special abilities
    // ---------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        abilityTick++;

        if (!this.level().isClientSide) {
            // Purple particle aura every 5 ticks
            if (abilityTick % 5 == 0) {
                spawnPurpleAura();
            }

            // Ground slam shockwave every 8 seconds (160 ticks) when on ground
            if (abilityTick % 160 == 0 && this.onGround()) {
                doGroundSlam();
            }

            // Void lightning counter-attack when below 50% HP, every 6 seconds
            if (abilityTick % 120 == 0 && this.getHealth() < this.getMaxHealth() * 0.5f) {
                doVoidLightningBurst();
            }
        }
    }

    /**
     * Called when the mob deals melee damage.
     * Applies Wither II + Poison II + Weakness III to the target.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));    // Wither II, 5 sec
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));    // Poison II, 5 sec
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));  // Weakness III, 10 sec
        }
        return hit;
    }

    /**
     * On death: big explosion + particle burst.
     */
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.explode(this,
                this.getX(), this.getY() + 1.0, this.getZ(),
                5.0f, Level.ExplosionInteraction.NONE);

            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                this.getX(), this.getY() + 1.5, this.getZ(),
                100, 2.0, 1.0, 2.0, 0.1);
        }
    }

    // ---------------------------------------------------------------------------
    // Private ability helpers
    // ---------------------------------------------------------------------------

    /** Emits purple dragon-breath particles around the mob as a void aura. */
    private void spawnPurpleAura() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                this.getX() + (this.random.nextDouble() - 0.5) * 3.0,
                this.getY() + this.random.nextDouble() * 5.0,
                this.getZ() + (this.random.nextDouble() - 0.5) * 3.0,
                1, 0, 0, 0, 0.01);
        }
    }

    /**
     * GROUND SLAM: damages and knocks back all entities within 8 blocks.
     */
    private void doGroundSlam() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
            this.getX(), this.getY() + 0.5, this.getZ(),
            40, 4.0, 0.5, 4.0, 0.08);

        AABB box = this.getBoundingBox().inflate(8.0);
        serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())
            .forEach(target -> {
                target.hurt(this.damageSources().mobAttack(this), 12.0f);
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    target.knockback(3.0, -dx / dist, -dz / dist);
                }
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
            });

        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0f, 0.5f);
    }

    /**
     * VOID LIGHTNING BURST: summons up to 3 lightning bolts on nearby players
     * when below 50% health.
     */
    private void doVoidLightningBurst() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        AABB box = this.getBoundingBox().inflate(16.0);
        serverLevel.getEntitiesOfClass(Player.class, box, Player::isAlive)
            .stream()
            .limit(3)
            .forEach(target -> {
                net.minecraft.world.entity.LightningBolt lightning =
                    new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
                lightning.setPos(target.getX(), target.getY(), target.getZ());
                serverLevel.addFreshEntity(lightning);
            });
    }
}