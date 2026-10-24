package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Infinitite Monstrosity - a towering golem of dark rock split by glowing purple Infinitite
 * cracks, gripping a giant void sword. The mod's boss. See docs/requirements/infinitite_monstrosity.md.
 *
 * Stats: 1000 health (500 hearts), 30 attack damage, 30 armor / 10 toughness, immune to
 * knockback, 5x normal size. Hunts down the nearest player in range. Its boss health bar is drawn
 * by BossBars, straight from its synced health.
 *
 * Phase 1 (above half health):
 *   - Melee hits inflict Wither II, Poison II and Weakness III.
 *   - Ground Slam - damages and knocks back everything within 8 blocks.
 *   - Rock Barrage - hurls three exploding rocks (MonstrosityRockEntity).
 *   - Fist Quake - pounds the ground, sending a line of exploding purple cracks at its target.
 * Phase 2 (below half health) - it roars and powers up: cracks blaze brighter (see the renderer),
 * faster and stronger, its boss bar turns pink, and it adds:
 *   - Void Beam - charges, then fires a sweeping purple beam from its chest.
 *   - Black Hole - stabs its sword into the ground and calls down a black hole that drags
 *     everything in and crushes whatever gets close.
 *   - Void Lightning - lightning on up to three nearby players every 6 seconds.
 *
 * Drops: Infinitite Ingots, Universal Shards, a Monstrosity Heart, a mysterious book about the
 * void, and a huge burst of XP.
 *
 * Attacks run one at a time on a timeline (see {@link Attack}): the current attack and when it
 * started are synced to clients, so InfinititeMonstrosityModel can play the matching pose.
 */
public class InfinititeMonstrosity extends Monster {

    /** The boss's special attacks. Each id is synced to clients for the animations. */
    public enum Attack {
        NONE(0),
        GROUND_SLAM(30),
        ROCK_BARRAGE(50),
        FIST_QUAKE(45),
        VOID_BEAM(80),
        BLACK_HOLE(40);

        /** How long the attack takes, in ticks. */
        public final int duration;

        Attack(int duration) {
            this.duration = duration;
        }
    }

    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(InfinititeMonstrosity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(InfinititeMonstrosity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks between special attacks. */
    private static final int ATTACK_GAP_TICKS = 70;
    private static final int ENRAGED_ATTACK_GAP_TICKS = 40;

    /** Ground Slam: when it lands within the attack, radius, damage. */
    private static final int SLAM_IMPACT_TICK = 15;
    private static final double SLAM_RADIUS = 8.0;
    private static final float SLAM_DAMAGE = 12.0f;

    /** Rock Barrage: the ticks each rock is thrown on, and the throw's physics (see aimRock). */
    private static final int[] ROCK_THROW_TICKS = { 15, 27, 39 };
    private static final double ROCK_SPEED = 1.8;
    private static final double ROCK_GRAVITY = 0.03;
    private static final double ROCK_DRAG = 0.99;

    /** Fist Quake: when the fists hit, how fast and far the cracks race, their damage. */
    private static final int QUAKE_IMPACT_TICK = 15;
    private static final double QUAKE_STEP_BLOCKS = 1.5;
    private static final int QUAKE_STEPS = 24;
    private static final float QUAKE_DAMAGE = 14.0f;

    /** Void Beam: charge-up time, then range, damage, and how fast it can turn to follow you. */
    private static final int BEAM_CHARGE_TICKS = 30;
    private static final double BEAM_RANGE = 40.0;
    private static final float BEAM_DAMAGE = 12.0f;
    private static final double BEAM_TURN_RATE = 0.06;

    /** Black Hole: when the sword stabs in, how long the hole lasts, pull range, crush range and damage. */
    private static final int BLACK_HOLE_STAB_TICK = 20;
    private static final int BLACK_HOLE_TICKS = 120;
    private static final double BLACK_HOLE_PULL_RADIUS = 24.0;
    private static final double BLACK_HOLE_CRUSH_RADIUS = 3.5;
    private static final float BLACK_HOLE_DAMAGE = 10.0f;

    private static final ResourceLocation ENRAGED_SPEED_ID = ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "monstrosity_enraged_speed");
    private static final ResourceLocation ENRAGED_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "monstrosity_enraged_damage");

    private int abilityTick = 0;
    private int attackTick = 0;
    private int ticksUntilNextAttack = ATTACK_GAP_TICKS;

    /** Where the Fist Quake's cracks have got to, and which way they're heading. */
    private Vec3 quakePoint;
    private Vec3 quakeDirection;

    /** The Void Beam's current aim - it turns toward its target a little each tick. */
    private Vec3 beamDirection;

    private final List<BlackHole> blackHoles = new ArrayList<>();

    /** Client side: the tickCount when the current attack started, for the animations. */
    private int clientAttackStartTick = 0;

    public InfinititeMonstrosity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        // The hitbox is worked out before the SCALE attribute exists, so it starts out unscaled -
        // recalculate it now, or the boss's hitbox stays a fraction of its size.
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------------------
    // Attributes, synced data, AI goals, sounds
    // ---------------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1000.0)          // 500 hearts
            .add(Attributes.ATTACK_DAMAGE, 30.0)         // 15 hearts per hit
            .add(Attributes.MOVEMENT_SPEED, 0.25)        // a lumbering giant (zombies are 0.23)
            .add(Attributes.FOLLOW_RANGE, 64.0)          // sees players from far
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)   // immune to knockback
            .add(Attributes.ARMOR, 30.0)                 // heavily armoured
            .add(Attributes.ARMOR_TOUGHNESS, 10.0)
            .add(Attributes.SCALE, 5.0);                 // 5x physical size
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, Attack.NONE.ordinal());
        builder.define(ENRAGED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** A boss never quietly despawns when players wander off. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

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
    // State shared with the client (for the model's animations)
    // ---------------------------------------------------------------------------

    public Attack getCurrentAttack() {
        int id = this.entityData.get(ATTACK);
        Attack[] attacks = Attack.values();
        return id >= 0 && id < attacks.length ? attacks[id] : Attack.NONE;
    }

    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }

    /** Client side: how many ticks into the current attack we are. */
    public float getAttackTicks(float partialTick) {
        return this.tickCount - this.clientAttackStartTick + partialTick;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ATTACK.equals(key)) {
            this.clientAttackStartTick = this.tickCount;
        }
    }

    // ---------------------------------------------------------------------------
    // Every tick
    // ---------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        abilityTick++;
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        if (!isEnraged() && this.getHealth() < this.getMaxHealth() * 0.5f) {
            enrage(level);
        }

        if (abilityTick % 5 == 0) {
            spawnAura(level);
        }
        if (isEnraged() && abilityTick % 120 == 0) {
            doVoidLightningBurst(level);
        }

        if (abilityTick % 20 == 0) {
            huntNearestPlayer(level);
        }
        blackHoles.removeIf(hole -> hole.tick(level));
        tickAttacks(level);
    }

    /**
     * Relentless: if it isn't already after someone, it goes for the nearest player within its
     * follow range - no need to see them first, or to be hit by them.
     */
    private void huntNearestPlayer(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current != null && current.isAlive() && !(current instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            return;
        }
        double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
        Player nearest = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), range,
            entity -> entity instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (nearest != null) {
            this.setTarget(nearest);
        }
    }

    /** Phase 2: a roar, a blast that throws everyone back, and it's faster and hits harder from now on. */
    private void enrage(ServerLevel level) {
        this.entityData.set(ENRAGED, true);

        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(ENRAGED_SPEED_ID)) {
            speed.addPermanentModifier(new AttributeModifier(ENRAGED_SPEED_ID, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && !damage.hasModifier(ENRAGED_DAMAGE_ID)) {
            damage.addPermanentModifier(new AttributeModifier(ENRAGED_DAMAGE_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 5.0f, 0.6f);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.5f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, this.getX(), this.getY() + 6.0, this.getZ(),
            400, 6.0, 6.0, 6.0, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.getX(), this.getY() + 1.0, this.getZ(),
            3, 3.0, 1.0, 3.0, 0.0);
        for (LivingEntity target : targetsWithin(level, this.getBoundingBox().inflate(12.0))) {
            Vec3 away = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(2.0);
            target.setDeltaMovement(push.x, 0.8, push.z);
            target.hurtMarked = true;
        }
    }

    /** Living things the boss's special attacks hit: everything but itself, and players in creative/spectator. */
    private List<LivingEntity> targetsWithin(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive()
            && !(e instanceof InfinititeMonstrosity)
            && !(e instanceof Player player && (player.isCreative() || player.isSpectator())));
    }

    // ---------------------------------------------------------------------------
    // Special attacks
    // ---------------------------------------------------------------------------

    private void tickAttacks(ServerLevel level) {
        Attack current = getCurrentAttack();
        if (current == Attack.NONE) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }
            if (--ticksUntilNextAttack > 0) {
                return;
            }
            startAttack(level, pickAttack());
            return;
        }

        attackTick++;
        // Every special attack is done standing still.
        this.getNavigation().stop();
        switch (current) {
            case GROUND_SLAM -> tickGroundSlam(level);
            case ROCK_BARRAGE -> tickRockBarrage(level);
            case FIST_QUAKE -> tickFistQuake(level);
            case VOID_BEAM -> tickVoidBeam(level);
            case BLACK_HOLE -> tickBlackHoleStab(level);
            default -> { }
        }
        if (attackTick >= current.duration) {
            this.entityData.set(ATTACK, Attack.NONE.ordinal());
            ticksUntilNextAttack = isEnraged() ? ENRAGED_ATTACK_GAP_TICKS : ATTACK_GAP_TICKS;
        }
    }

    private Attack pickAttack() {
        List<Attack> options = new ArrayList<>(List.of(Attack.GROUND_SLAM, Attack.ROCK_BARRAGE, Attack.FIST_QUAKE));
        if (isEnraged()) {
            options.add(Attack.VOID_BEAM);
            options.add(Attack.VOID_BEAM);
            if (blackHoles.isEmpty()) {
                options.add(Attack.BLACK_HOLE);
            }
        }
        return options.get(this.random.nextInt(options.size()));
    }

    private void startAttack(ServerLevel level, Attack attack) {
        this.entityData.set(ATTACK, attack.ordinal());
        attackTick = 0;
        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
        if (attack == Attack.VOID_BEAM) {
            beamDirection = null;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4.0f, 0.5f);
        }
    }

    /** Ground Slam: rears up, then smashes down, hurting and throwing back everything nearby. */
    private void tickGroundSlam(ServerLevel level) {
        if (attackTick != SLAM_IMPACT_TICK) {
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, true, true, this.getX(), this.getY() + 0.5, this.getZ(),
            60, 5.0, 0.5, 5.0, 0.08);
        for (LivingEntity target : targetsWithin(level, this.getBoundingBox().inflate(SLAM_RADIUS))) {
            target.hurtServer(level, this.damageSources().mobAttack(this), SLAM_DAMAGE);
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                target.knockback(3.0, -dx / dist, -dz / dist);
            }
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    /** Rock Barrage: three chunks of rock ripped up and hurled at its target. */
    private void tickRockBarrage(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }
        for (int throwTick : ROCK_THROW_TICKS) {
            if (attackTick == throwTick) {
                MonstrosityRockEntity rock = new MonstrosityRockEntity(level, this);
                Vec3 from = this.position().add(0.0, this.getBbHeight() * 0.8, 0.0).add(this.getLookAngle().scale(2.0));
                rock.setPos(from);
                rock.setDeltaMovement(aimRock(from, target));
                level.addFreshEntity(rock);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0f, 0.5f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 10, 0.5, 0.5, 0.5, 0.02);
            }
        }
    }

    /**
     * The launch velocity that lands a rock on its target: works out roughly how long the rock
     * will fly, leads the target by where they'll be by then, and throws high enough to make up
     * for the rock dropping (gravity) and slowing down (air drag) along the way.
     */
    private Vec3 aimRock(Vec3 from, LivingEntity target) {
        Vec3 aimAt = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        double horizontal = Math.sqrt(Math.pow(aimAt.x - from.x, 2) + Math.pow(aimAt.z - from.z, 2));
        double flightTicks = Mth.clamp(horizontal / ROCK_SPEED, 8.0, 40.0);
        aimAt = aimAt.add(target.getDeltaMovement().multiply(1.0, 0.0, 1.0).scale(flightTicks));
        Vec3 offset = aimAt.subtract(from);
        // Air drag keeps 99% of the speed each tick, so over the flight a rock only covers this
        // fraction of the distance it would without drag - throw that much harder to make up for it.
        double dragFactor = (1.0 - Math.pow(ROCK_DRAG, flightTicks)) / ((1.0 - ROCK_DRAG) * flightTicks);
        double vx = offset.x / flightTicks / dragFactor;
        double vz = offset.z / flightTicks / dragFactor;
        double vy = (offset.y + 0.5 * ROCK_GRAVITY * flightTicks * flightTicks) / flightTicks / dragFactor;
        return new Vec3(vx, vy, vz);
    }

    /** Fist Quake: pounds the ground, and a line of exploding cracks races out toward its target. */
    private void tickFistQuake(ServerLevel level) {
        if (attackTick == QUAKE_IMPACT_TICK) {
            LivingEntity target = this.getTarget();
            Vec3 toward = target != null ? target.position().subtract(this.position()) : this.getLookAngle();
            toward = toward.multiply(1.0, 0.0, 1.0);
            quakeDirection = toward.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : toward.normalize();
            quakePoint = this.position().add(quakeDirection.scale(this.getBbWidth() * 0.5));
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
        }
        if (quakePoint == null || attackTick <= QUAKE_IMPACT_TICK || attackTick > QUAKE_IMPACT_TICK + QUAKE_STEPS) {
            return;
        }
        quakePoint = quakePoint.add(quakeDirection.scale(QUAKE_STEP_BLOCKS));
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, quakePoint.x, quakePoint.y + 0.3, quakePoint.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, quakePoint.x, quakePoint.y + 0.3, quakePoint.z,
            15, 0.6, 0.3, 0.6, 0.05);
        for (LivingEntity target : targetsWithin(level, new AABB(quakePoint, quakePoint).inflate(1.8, 2.0, 1.8))) {
            target.hurtServer(level, this.damageSources().mobAttack(this), QUAKE_DAMAGE);
            target.setDeltaMovement(target.getDeltaMovement().x, 0.6, target.getDeltaMovement().z);
            target.hurtMarked = true;
        }
        if ((attackTick - QUAKE_IMPACT_TICK) % 3 == 0) {
            level.playSound(null, quakePoint.x, quakePoint.y, quakePoint.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.2f);
        }
    }

    /** Void Beam: gathers power at its chest, then fires a beam that slowly sweeps to follow its target. */
    private void tickVoidBeam(ServerLevel level) {
        Vec3 chest = this.position().add(0.0, this.getBbHeight() * 0.6, 0.0).add(this.getLookAngle().scale(this.getBbWidth() * 0.5));
        if (attackTick < BEAM_CHARGE_TICKS) {
            level.sendParticles(ParticleTypes.PORTAL, true, true, chest.x, chest.y, chest.z, 20, 2.0, 2.0, 2.0, 1.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, chest.x, chest.y, chest.z, 2, 0.3, 0.3, 0.3, 0.0);
            return;
        }

        LivingEntity target = this.getTarget();
        Vec3 wanted = target != null
            ? target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(chest).normalize()
            : this.getLookAngle();
        // Turns toward its target a little each tick, so you can outrun the sweep.
        beamDirection = beamDirection == null ? wanted : beamDirection.lerp(wanted, BEAM_TURN_RATE).normalize();
        Vec3 end = chest.add(beamDirection.scale(BEAM_RANGE));

        for (LivingEntity hit : targetsWithin(level, new AABB(chest, end).inflate(1.0))) {
            if (hit.getBoundingBox().inflate(0.6).clip(chest, end).isPresent()) {
                hit.hurtServer(level, this.damageSources().mobAttack(this), BEAM_DAMAGE);
                hit.igniteForTicks(60);
            }
        }
        int points = (int) (BEAM_RANGE * 2.0);
        for (int i = 0; i < points; i++) {
            Vec3 at = chest.add(beamDirection.scale(i * 0.5));
            level.sendParticles(ParticleTypes.WITCH, true, true, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH, true, true, at.x, at.y, at.z, 1, 0.15, 0.15, 0.15, 0.0);
            }
        }
        if ((attackTick - BEAM_CHARGE_TICKS) % 10 == 0) {
            level.playSound(null, chest.x, chest.y, chest.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.6f);
        }
    }

    /** Black Hole: raises its sword, stabs it into the ground, and a black hole opens in the sky above. */
    private void tickBlackHoleStab(ServerLevel level) {
        if (attackTick != BLACK_HOLE_STAB_TICK) {
            return;
        }
        Vec3 stab = this.position().add(this.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(this.getBbWidth() * 0.7));
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, stab.x, stab.y, stab.z, 4, 1.0, 0.2, 1.0, 0.0);
        level.playSound(null, stab.x, stab.y, stab.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.4f);
        Vec3 center = this.position().add(0.0, this.getBbHeight() + 4.0, 0.0);
        blackHoles.add(new BlackHole(center));
        level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    /** A black hole hanging in the air: pulls everything toward it and crushes whatever gets close. */
    private final class BlackHole {
        final Vec3 center;
        int ticksLeft = BLACK_HOLE_TICKS;

        BlackHole(Vec3 center) {
            this.center = center;
        }

        /** @return true once it has collapsed (safe to discard). */
        boolean tick(ServerLevel level) {
            ticksLeft--;
            if (ticksLeft <= 0 || !InfinititeMonstrosity.this.isAlive()) {
                level.sendParticles(ParticleTypes.FLASH, true, true, center.x, center.y, center.z, 1, 0, 0, 0, 0);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.3f);
                return true;
            }

            // The hole itself: an inky black core with a swirling purple disc around it.
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, center.x, center.y, center.z, 8, 0.5, 0.5, 0.5, 0.0);
            double spin = ticksLeft * 0.3;
            for (int i = 0; i < 12; i++) {
                double angle = spin + i * (Math.PI * 2.0 / 12.0);
                double r = 2.5 + (i % 3) * 0.8;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, center.x + Math.cos(angle) * r, center.y,
                    center.z + Math.sin(angle) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.PORTAL, true, true, center.x, center.y, center.z, 10, 6.0, 6.0, 6.0, -1.0);

            for (LivingEntity victim : targetsWithin(level, new AABB(center, center).inflate(BLACK_HOLE_PULL_RADIUS))) {
                Vec3 toward = center.subtract(victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0));
                double distance = toward.length();
                if (distance > BLACK_HOLE_PULL_RADIUS) {
                    continue;
                }
                // Stronger the closer you get.
                double strength = 0.06 + 0.25 * (1.0 - distance / BLACK_HOLE_PULL_RADIUS);
                victim.setDeltaMovement(victim.getDeltaMovement().add(toward.normalize().scale(strength)));
                victim.hurtMarked = true;
                victim.resetFallDistance();
                if (distance < BLACK_HOLE_CRUSH_RADIUS && ticksLeft % 10 == 0) {
                    victim.hurtServer(level, level.damageSources().magic(), BLACK_HOLE_DAMAGE);
                }
            }
            return false;
        }
    }

    /** Void Lightning (phase 2): lightning on up to three players near the boss. */
    private void doVoidLightningBurst(ServerLevel level) {
        level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0), p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
            .stream()
            .limit(3)
            .forEach(target -> {
                LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                lightning.setPos(target.getX(), target.getY(), target.getZ());
                level.addFreshEntity(lightning);
            });
    }

    /** Purple wisps drifting off the boss - more of them once it's enraged. */
    private void spawnAura(ServerLevel level) {
        int count = isEnraged() ? 4 : 1;
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
            this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(),
            count, this.getBbWidth() * 0.4, this.getBbHeight() * 0.4, this.getBbWidth() * 0.4, 0.01);
    }

    // ---------------------------------------------------------------------------
    // Melee hits, death, drops
    // ---------------------------------------------------------------------------

    /** Melee hits apply Wither II + Poison II + Weakness III. */
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
     * On death: the look and sound of a huge explosion and a burst of purple. Deliberately not a
     * real explosion - one goes off right after the loot drops, and it used to blow the loot away.
     */
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.getX(), this.getY() + 2.0, this.getZ(),
                4, 3.0, 2.0, 3.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, true, true, this.getX(), this.getY() + 1.5, this.getZ(),
                200, 4.0, 4.0, 4.0, 0.1);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 4.0f, 0.6f);
        }
    }

    /** Infinitite Ingots, Universal Shards, the Monstrosity Heart, and the book about the void. (The XP is xpReward.) */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.INFINITITE_INGOT.get(), 8 + this.random.nextInt(9)));
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.UNIVERSAL_SHARD.get(), 2 + this.random.nextInt(3)));
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.MONSTROSITY_HEART.get()));
        this.spawnAtLocation(level, createVoidBook());
    }

    /**
     * A worn journal from someone who has seen what lies beyond the void - it hints at something
     * far worse than the Monstrosity without ever naming it.
     */
    private static ItemStack createVoidBook() {
        List<Filterable<Component>> pages = List.of(
            "I do not know who will find this. I only know that the giant has fallen, or you would not be holding it."
                + "\n\nIt was never the master. It was the door.",
            "Before there were stars, there was the void. Not darkness - darkness is only the absence of light."
                + " The void is the absence of everything.\n\nAnd something lives in it.",
            "It does not hunger. It does not hate. It simply unmakes. Worlds go quiet one by one,"
                + " and no one remembers they were ever there.",
            "The purple stone - the Infinitite - is its blood, cooled and hardened. Every crack in the giant"
                + " glowed with it. Every crack was listening.",
            "The shards you carry now are pieces of a universe it already ended.\n\nThey remember. They will lead"
                + " you to it, if you are foolish enough to follow.",
            "I followed.\n\nI will not write what I saw. Only this: when the stars begin to go out, it is coming."
                + "\n\nDo not be there.\n\n- the last one who looked"
        ).stream().map(text -> Filterable.passThrough((Component) Component.literal(text))).toList();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
            new WrittenBookContent(Filterable.passThrough("Whispers of the Void"), "???", 3, pages, true));
        return book;
    }
}
