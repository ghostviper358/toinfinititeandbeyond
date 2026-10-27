package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Living Void - the second boss, summoned by throwing a Monstrosity Heart on the ground with at
 * least 10 Infinitite Ingots and 1 Universal Shard (see {@link #checkForSummon}). A giant floating
 * eye of darkness with rings of black shards orbiting it and tentacles reaching out of the dark
 * beneath. See docs/requirements/living_void.md.
 *
 * It flies, circling high above its target and always staring at it. Attacks, one at a time:
 *   - Tentacle Lash - whips anyone close.
 *   - Void Rifts - rifts tear open around the arena and spit out Endermen and Vexes.
 *   - Darkness - plunges everyone into darkness, then its tentacles strike.
 *   - Gravity Flip - flings everyone into the sky, then slams them back down.
 *   - Void Orbs - slow homing orbs of darkness (VoidOrbEntity).
 *   - Ground Beam - a beam from its eye explodes on the ground and leaves purple fire burning.
 * Below half health it attacks faster and opens twice as many rifts.
 *
 * Drops a pile of Universal Shards, a second lore book, the Void Seal (break it to summon the
 * Universal Destroyer), and lots of XP. Its minions vanish with it.
 *
 * The current attack and when it started are synced to clients for the animations, the same way
 * as InfinititeMonstrosity.
 */
public class LivingVoid extends GiantBoss {

    public enum Attack {
        NONE(0),
        TENTACLE_LASH(25),
        VOID_RIFTS(40),
        DARKNESS(45),
        GRAVITY_FLIP(45),
        VOID_ORBS(40),
        GROUND_BEAM(40);

        public final int duration;

        Attack(int duration) {
            this.duration = duration;
        }
    }

    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(LivingVoid.class, EntityDataSerializers.INT);

    private static final int ATTACK_GAP_TICKS = 40;
    private static final int ANGRY_ATTACK_GAP_TICKS = 25;

    /**
     * How high above its target it hovers, and how wide a circle it flies around it - close and
     * low enough that its tentacles hang within sword reach, so it can be fought up close.
     */
    private static final double HOVER_HEIGHT = 2.5;
    private static final double CIRCLE_RADIUS = 6.0;
    private static final double FLY_SPEED = 0.45;

    /** Tentacle Lash: how far its tentacles reach from its body, when it lands, damage. */
    private static final double LASH_RANGE = 8.0;
    private static final int LASH_HIT_TICK = 12;
    private static final float LASH_DAMAGE = 22.0f;

    /** Void Rifts: how many rifts (doubled when angry), when they open, minions per rift. */
    private static final int RIFTS = 2;
    private static final int RIFT_OPEN_TICK = 20;

    /** Darkness: how far it reaches, and the tentacle strike that follows. */
    private static final double DARKNESS_RADIUS = 40.0;
    private static final int DARKNESS_STRIKE_TICK = 30;
    private static final float DARKNESS_STRIKE_DAMAGE = 25.0f;

    /** Gravity Flip: radius, when everyone is flung up, when they're slammed down, slam damage. */
    private static final double FLIP_RADIUS = 24.0;
    private static final int FLIP_UP_TICK = 10;
    private static final int FLIP_DOWN_TICK = 30;
    private static final float FLIP_DAMAGE = 15.0f;

    /** Void Orbs: the ticks each orb is fired on. */
    private static final int[] ORB_TICKS = { 10, 20, 30 };

    /**
     * Ground Beam: it tracks its target until BEAM_LOCK_TICK (the warning ring stops moving - your
     * moment to dodge), then hits at BEAM_HIT_TICK. Blast radius and damage, then the purple fire.
     */
    private static final int BEAM_LOCK_TICK = 14;
    private static final int BEAM_HIT_TICK = 20;
    private static final double BEAM_BLAST_RADIUS = 3.5;
    private static final float BEAM_BLAST_DAMAGE = 20.0f;
    private static final int FIRE_TICKS = 120;
    private static final double FIRE_RADIUS = 3.5;
    private static final float FIRE_DAMAGE = 6.0f;

    /** Summoning: what has to be thrown down together, and how long the ritual builds before it arrives. */
    private static final int SUMMON_INGOTS = 10;
    private static final int SUMMON_SHARDS = 1;
    private static final double SUMMON_GATHER_RADIUS = 2.5;
    private static final int RITUAL_TICKS = 80;
    /** No new summoning while a ritual or a living Living Void is this close. */
    private static final double SUMMON_BLOCK_RADIUS = 64.0;

    private static final List<SummonRitual> RITUALS = new ArrayList<>();

    private int abilityTick = 0;
    private int attackTick = 0;
    private int ticksUntilNextAttack = ATTACK_GAP_TICKS;

    /** Attacks still to come this round - every attack once, in a shuffled order, then a new round. */
    private final List<Attack> attackBag = new ArrayList<>();

    /** Where the Ground Beam is aimed (picked as the attack starts, so you can dodge it). */
    private Vec3 beamTarget;

    private final List<PurpleFire> fires = new ArrayList<>();
    private final List<UUID> minions = new ArrayList<>();

    /** Client side: the tickCount when the current attack started, for the animations. */
    private int clientAttackStartTick = 0;

    public LivingVoid(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1000;
        this.setNoGravity(true);
        // The hitbox is worked out before the SCALE attribute exists, so it starts out unscaled -
        // recalculate it now, or the boss's hitbox stays a fraction of its size.
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------------------
    // Attributes, synced data, AI, sounds
    // ---------------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1024.0)          // vanilla's cap - the real 2000 is getBossMaxHealth()
            .add(Attributes.ATTACK_DAMAGE, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 96.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 20.0)
            .add(Attributes.ARMOR_TOUGHNESS, 10.0)
            .add(Attributes.SCALE, 4.0);                 // 4x physical size
    }

    /** 2000 health (1000 hearts) - more than vanilla allows, so it's kept in GiantBoss's pool. */
    @Override
    protected float getBossMaxHealth() {
        return 2000.0f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, Attack.NONE.ordinal());
    }

    @Override
    protected void registerGoals() {
        // It picks its own targets (see huntNearestPlayer) and flies itself (see flyAroundTarget).
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_SHRIEKER_SHRIEK;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ELDER_GUARDIAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false; // it floats
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** A boss never quietly despawns when players wander off. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    public Attack getCurrentAttack() {
        int id = this.entityData.get(ATTACK);
        Attack[] attacks = Attack.values();
        return id >= 0 && id < attacks.length ? attacks[id] : Attack.NONE;
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

    private boolean isAngry() {
        return getBossHealthFraction() < 0.5f;
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
        if (abilityTick % 20 == 0) {
            huntNearestPlayer(level);
        }
        if (abilityTick % 4 == 0) {
            level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(),
                2, this.getBbWidth() * 0.4, this.getBbHeight() * 0.4, this.getBbWidth() * 0.4, 0.0);
        }
        flyAroundTarget();
        fires.removeIf(fire -> fire.tick(level));
        tickAttacks(level);
    }

    /** Goes for the nearest survival player in range - no need to see them, or be hit first. */
    private void huntNearestPlayer(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current != null && current.isAlive() && !(current instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            return;
        }
        Player nearest = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), this.getAttributeValue(Attributes.FOLLOW_RANGE),
            entity -> entity instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
        this.setTarget(nearest);
    }

    /**
     * Circles slowly around its target, high above it, always turned to stare at it. Hangs still
     * in the air while it attacks, and drifts where it is when it has no target.
     */
    private void flyAroundTarget() {
        LivingEntity target = this.getTarget();
        Vec3 wanted;
        if (target == null) {
            wanted = this.position();
        } else {
            double angle = abilityTick * 0.012;
            wanted = target.position().add(Math.cos(angle) * CIRCLE_RADIUS, HOVER_HEIGHT, Math.sin(angle) * CIRCLE_RADIUS);
            Vec3 toTarget = target.getEyePosition().subtract(this.getEyePosition());
            float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0f;
            float pitch = (float) -(Mth.atan2(toTarget.y, toTarget.horizontalDistance()) * Mth.RAD_TO_DEG);
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
            this.setXRot(pitch);
        }
        if (getCurrentAttack() != Attack.NONE) {
            wanted = this.position();
        }
        Vec3 move = wanted.subtract(this.position()).scale(0.05);
        if (move.length() > FLY_SPEED) {
            move = move.normalize().scale(FLY_SPEED);
        }
        // A slow bob, like it's breathing.
        this.setDeltaMovement(move.add(0.0, Mth.sin(abilityTick * 0.08f) * 0.02, 0.0));
    }

    /** Living things its attacks hit: everything but itself, and players in creative/spectator. */
    private List<LivingEntity> targetsWithin(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive()
            && !minions.contains(e.getUUID())
            && !(e instanceof Player player && (player.isCreative() || player.isSpectator())));
    }

    private Vec3 eyePosition() {
        return this.position().add(0.0, this.getBbHeight() * 0.55, 0.0);
    }

    // ---------------------------------------------------------------------------
    // Attacks
    // ---------------------------------------------------------------------------

    private void tickAttacks(ServerLevel level) {
        Attack current = getCurrentAttack();
        LivingEntity target = this.getTarget();
        if (current == Attack.NONE) {
            if (target == null || !target.isAlive() || --ticksUntilNextAttack > 0) {
                return;
            }
            startAttack(level, pickAttack(target));
            return;
        }

        attackTick++;
        switch (current) {
            case TENTACLE_LASH -> tickTentacleLash(level, target);
            case VOID_RIFTS -> tickVoidRifts(level, target);
            case DARKNESS -> tickDarkness(level, target);
            case GRAVITY_FLIP -> tickGravityFlip(level);
            case VOID_ORBS -> tickVoidOrbs(level, target);
            case GROUND_BEAM -> tickGroundBeam(level);
            default -> { }
        }
        if (attackTick >= current.duration) {
            this.entityData.set(ATTACK, Attack.NONE.ordinal());
            ticksUntilNextAttack = isAngry() ? ANGRY_ATTACK_GAP_TICKS : ATTACK_GAP_TICKS;
        }
    }

    private Attack pickAttack(LivingEntity target) {
        // Within reach of its tentacles? Then it often whips them first.
        if (withinLashRange(target) && this.random.nextFloat() < 0.5f) {
            return Attack.TENTACLE_LASH;
        }
        if (attackBag.isEmpty()) {
            attackBag.addAll(List.of(Attack.TENTACLE_LASH, Attack.VOID_RIFTS, Attack.DARKNESS, Attack.GRAVITY_FLIP,
                Attack.VOID_ORBS, Attack.GROUND_BEAM));
            java.util.Collections.shuffle(attackBag, new java.util.Random(this.random.nextLong()));
        }
        return attackBag.remove(attackBag.size() - 1);
    }

    /** Whether something is within reach of its tentacles - measured from its body, not its feet. */
    private boolean withinLashRange(LivingEntity entity) {
        return this.getBoundingBox().inflate(LASH_RANGE).intersects(entity.getBoundingBox());
    }

    private void startAttack(ServerLevel level, Attack attack) {
        this.entityData.set(ATTACK, attack.ordinal());
        attackTick = 0;
        if (attack == Attack.GROUND_BEAM && this.getTarget() != null) {
            beamTarget = this.getTarget().position();
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0f, 0.5f);
        }
    }

    /** Tentacle Lash: the tentacles rear back and whip anyone close. */
    private void tickTentacleLash(ServerLevel level, LivingEntity target) {
        if (attackTick != LASH_HIT_TICK) {
            return;
        }
        for (LivingEntity hit : targetsWithin(level, this.getBoundingBox().inflate(LASH_RANGE))) {
            hit.hurtServer(level, this.damageSources().mobAttack(this), LASH_DAMAGE);
            Vec3 away = hit.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0E-4) {
                Vec3 push = away.normalize().scale(1.5);
                hit.setDeltaMovement(push.x, 0.5, push.z);
                hit.hurtMarked = true;
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.getX(), hit.getY() + 1.0, hit.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.4f);
    }

    /** Void Rifts: rifts tear open on the ground around its target and spit out void minions. */
    private void tickVoidRifts(ServerLevel level, LivingEntity target) {
        if (target == null) {
            return;
        }
        int rifts = isAngry() ? RIFTS * 2 : RIFTS;
        if (attackTick < RIFT_OPEN_TICK) {
            for (int i = 0; i < rifts; i++) {
                Vec3 rift = riftPosition(level, target, i, rifts);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, rift.x, rift.y + 1.0, rift.z, 12, 0.3, 1.0, 0.3, 0.05);
            }
            return;
        }
        if (attackTick != RIFT_OPEN_TICK) {
            return;
        }
        for (int i = 0; i < rifts; i++) {
            Vec3 rift = riftPosition(level, target, i, rifts);
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, rift.x, rift.y + 1.0, rift.z, 40, 0.5, 1.2, 0.5, 0.05);
            level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.HOSTILE, 2.0f, 0.5f);
            Mob minion = (i % 2 == 0 ? EntityType.ENDERMAN : EntityType.VEX).create(level, EntitySpawnReason.MOB_SUMMONED);
            if (minion != null) {
                minion.snapTo(rift.x, rift.y, rift.z, this.random.nextFloat() * 360.0f, 0.0f);
                minion.setTarget(target);
                minion.setPersistenceRequired();
                level.addFreshEntity(minion);
                minions.add(minion.getUUID());
            }
        }
    }

    private Vec3 riftPosition(ServerLevel level, LivingEntity target, int index, int count) {
        double angle = index * (Math.PI * 2.0 / count);
        double x = target.getX() + Math.cos(angle) * 6.0;
        double z = target.getZ() + Math.sin(angle) * 6.0;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
        return new Vec3(x, ground.getY(), z);
    }

    /** Darkness: everything goes black but its glowing eye - then the tentacles strike. */
    private void tickDarkness(ServerLevel level, LivingEntity target) {
        if (attackTick == 1) {
            for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(DARKNESS_RADIUS))) {
                if (victim instanceof Player) {
                    victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0));
                    victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                }
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 4.0f, 0.5f);
        }
        if (attackTick == DARKNESS_STRIKE_TICK && target != null && target.isAlive() && target.distanceTo(this) < DARKNESS_RADIUS) {
            target.hurtServer(level, this.damageSources().mobAttack(this), DARKNESS_STRIKE_DAMAGE);
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.5, 1.0, 0.5, 0.1);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 3.0f, 0.6f);
        }
    }

    /** Gravity Flip: everyone nearby is flung into the sky... then slammed back down. */
    private void tickGravityFlip(ServerLevel level) {
        if (attackTick == FLIP_UP_TICK) {
            for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(FLIP_RADIUS))) {
                victim.setDeltaMovement(victim.getDeltaMovement().x, 1.8, victim.getDeltaMovement().z);
                victim.hurtMarked = true;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(), victim.getY(), victim.getZ(), 20, 0.4, 0.2, 0.4, 0.2);
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4.0f, 0.5f);
        }
        if (attackTick == FLIP_DOWN_TICK) {
            for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(FLIP_RADIUS + 10.0))) {
                if (victim.onGround()) {
                    continue;
                }
                victim.setDeltaMovement(victim.getDeltaMovement().x, -3.0, victim.getDeltaMovement().z);
                victim.hurtMarked = true;
                victim.hurtServer(level, this.damageSources().mobAttack(this), FLIP_DAMAGE);
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
        }
    }

    /** Void Orbs: three slow homing orbs of darkness. */
    private void tickVoidOrbs(ServerLevel level, LivingEntity target) {
        if (target == null) {
            return;
        }
        for (int orbTick : ORB_TICKS) {
            if (attackTick == orbTick) {
                Vec3 from = eyePosition().add(this.getLookAngle().scale(this.getBbWidth() * 0.5));
                level.addFreshEntity(new VoidOrbEntity(level, this, target, from));
                level.playSound(null, from.x, from.y, from.z, SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 3.0f, 0.5f);
            }
        }
    }

    /** Ground Beam: a beam from its eye to the ground where you were standing, then a blast and purple fire. */
    private void tickGroundBeam(ServerLevel level) {
        if (beamTarget == null || attackTick > BEAM_HIT_TICK) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (attackTick <= BEAM_LOCK_TICK && target != null && target.isAlive()) {
            beamTarget = target.position();
        }
        Vec3 from = eyePosition();
        Vec3 step = beamTarget.subtract(from);
        int points = (int) (step.length() * 2.0);
        for (int i = 0; i < points; i++) {
            Vec3 at = from.add(step.scale(i / (double) points));
            level.sendParticles(ParticleTypes.WITCH, true, true, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (attackTick >= BEAM_HIT_TICK - 5 && i % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        // A warning ring on the ground where it's going to hit.
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0 / 16.0);
            level.sendParticles(ParticleTypes.WITCH, true, true, beamTarget.x + Math.cos(angle) * BEAM_BLAST_RADIUS, beamTarget.y + 0.1,
                beamTarget.z + Math.sin(angle) * BEAM_BLAST_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (attackTick != BEAM_HIT_TICK) {
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, beamTarget.x, beamTarget.y + 0.5, beamTarget.z, 1, 0, 0, 0, 0);
        level.playSound(null, beamTarget.x, beamTarget.y, beamTarget.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.8f);
        for (LivingEntity victim : targetsWithin(level, new AABB(beamTarget, beamTarget).inflate(BEAM_BLAST_RADIUS))) {
            victim.hurtServer(level, this.damageSources().mobAttack(this), BEAM_BLAST_DAMAGE);
            victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.7, 0.0));
            victim.hurtMarked = true;
        }
        fires.add(new PurpleFire(beamTarget));
    }

    /** A patch of purple fire on the ground that burns anything standing in it until it dies out. */
    private final class PurpleFire {
        final Vec3 center;
        int ticksLeft = FIRE_TICKS;

        PurpleFire(Vec3 center) {
            this.center = center;
        }

        /** @return true once it has burned out. */
        boolean tick(ServerLevel level) {
            ticksLeft--;
            if (ticksLeft % 2 == 0) {
                for (int i = 0; i < 6; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2.0;
                    double r = Math.sqrt(level.random.nextDouble()) * FIRE_RADIUS;
                    double x = center.x + Math.cos(angle) * r;
                    double z = center.z + Math.sin(angle) * r;
                    level.sendParticles(ParticleTypes.WITCH, true, true, x, center.y + 0.1, z, 1, 0.05, 0.2, 0.05, 0.02);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, x, center.y + 0.3, z, 1, 0.05, 0.3, 0.05, 0.05);
                }
            }
            if (ticksLeft % 10 == 0) {
                for (LivingEntity victim : targetsWithin(level, new AABB(center, center).inflate(FIRE_RADIUS, 1.5, FIRE_RADIUS))) {
                    victim.hurtServer(level, level.damageSources().magic(), FIRE_DAMAGE);
                    victim.igniteForTicks(60);
                }
            }
            return ticksLeft <= 0 || !LivingVoid.this.isAlive();
        }
    }

    // ---------------------------------------------------------------------------
    // Death and drops
    // ---------------------------------------------------------------------------

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level) {
            // Its minions are pulled back into the void with it.
            for (UUID id : minions) {
                Entity minion = level.getEntity(id);
                if (minion != null && minion.isAlive()) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, minion.getX(), minion.getY() + 1.0, minion.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
                    minion.discard();
                }
            }
            minions.clear();
            // Everyone who fought it unlocks the Black Hole Arm Cannon's Black Hole mode.
            for (Player fighter : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(64.0))) {
                BlackHoleArmCannonItem.unlockBlackHole(fighter);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, this.getX(), this.getY() + 2.0, this.getZ(), 300, 4.0, 4.0, 4.0, 0.2);
            level.sendParticles(ParticleTypes.FLASH, true, true, this.getX(), this.getY() + 2.0, this.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** A pile of Universal Shards, the second lore book, and the Void Seal. (The XP is xpReward.) */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.UNIVERSAL_SHARD.get(), 8 + this.random.nextInt(9)));
        this.spawnAtLocation(level, createLoreBook());
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.VOID_SEAL_ITEM.get()));
    }

    /**
     * The second journal: even the void is afraid. Something older than the universe, locked away
     * before the first star, is breaking free. It never says what.
     */
    private static ItemStack createLoreBook() {
        List<Filterable<Component>> pages = List.of(
            "If you are reading this, the void bled. I did not think it could.\n\nI was wrong about many things."
                + " I thought the void was the end of all things. It is not. It is a wall.",
            "Before the first light, before the first stone, before the void itself, there was something else.\n\n"
                + "The void was not made to unmake worlds. It was made to keep that thing in.",
            "It was locked away forever - folded into the space between spaces, sealed with every star"
                + " that would ever burn.\n\nForever is a long time. It is not long enough.",
            "The eye you fought was not hunting you. It was watching the seal.\n\nAnd it was afraid."
                + " I saw it in that eye. The void itself, afraid.",
            "The stars are going out because the locks are breaking. One by one.\n\n"
                + "Each Universal Shard you hold is a piece of a broken lock.",
            "I can hear it now, at night, beneath the silence. Not a voice. A heartbeat, older than time,"
                + " getting closer.\n\nIt is waking up.\n\nIt is breaking free.\n\n- the one who listened"
        ).stream().map(text -> Filterable.passThrough((Component) Component.literal(text))).toList();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
            new WrittenBookContent(Filterable.passThrough("Before the First Light"), "???", 3, pages, true));
        return book;
    }

    // ---------------------------------------------------------------------------
    // Summoning
    // ---------------------------------------------------------------------------

    /**
     * Called every tick for each Monstrosity Heart lying on the ground (MonstrosityHeartItem). If
     * at least 10 Infinitite Ingots and 1 Universal Shard are lying close by, the offering is taken
     * - exactly one heart, 10 ingots and 1 shard; anything extra is left - and the ritual begins.
     */
    public static void checkForSummon(ItemEntity heart) {
        if (!(heart.level() instanceof ServerLevel level) || !heart.onGround() || heart.tickCount % 10 != 0) {
            return;
        }
        // One at a time: not while a summoning is already under way nearby, or a Living Void is
        // already here. (Otherwise a stack of hearts beside a big pile of ingots summons several.)
        for (SummonRitual ritual : RITUALS) {
            if (ritual.level == level && ritual.center.distanceTo(heart.position()) < SUMMON_BLOCK_RADIUS) {
                return;
            }
        }
        if (!level.getEntitiesOfClass(LivingVoid.class, heart.getBoundingBox().inflate(SUMMON_BLOCK_RADIUS), LivingVoid::isAlive).isEmpty()) {
            return;
        }
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, heart.getBoundingBox().inflate(SUMMON_GATHER_RADIUS),
            ItemEntity::isAlive);
        int ingots = 0;
        int shards = 0;
        for (ItemEntity item : nearby) {
            if (item.getItem().is(ToInfinititeAndBeyond.INFINITITE_INGOT.get())) {
                ingots += item.getItem().getCount();
            } else if (item.getItem().is(ToInfinititeAndBeyond.UNIVERSAL_SHARD.get())) {
                shards += item.getItem().getCount();
            }
        }
        if (ingots < SUMMON_INGOTS || shards < SUMMON_SHARDS) {
            return;
        }

        take(nearby, ToInfinititeAndBeyond.INFINITITE_INGOT.get().getDefaultInstance(), SUMMON_INGOTS);
        take(nearby, ToInfinititeAndBeyond.UNIVERSAL_SHARD.get().getDefaultInstance(), SUMMON_SHARDS);
        heart.getItem().shrink(1);
        if (heart.getItem().isEmpty()) {
            heart.discard();
        }
        RITUALS.add(new SummonRitual(level, heart.position()));
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§5§lThe heart beats one last time... something stirs in the void."), false);
    }

    /** Removes {@code amount} of an item from the stacks lying on the ground. */
    private static void take(List<ItemEntity> items, ItemStack kind, int amount) {
        for (ItemEntity item : items) {
            if (amount <= 0) {
                return;
            }
            ItemStack stack = item.getItem();
            if (!ItemStack.isSameItem(stack, kind)) {
                continue;
            }
            int used = Math.min(amount, stack.getCount());
            stack.shrink(used);
            amount -= used;
            if (stack.isEmpty()) {
                item.discard();
            }
        }
    }

    /**
     * Forgets everything still in progress. Called when the server stops, so nothing keeps
     * running against a closed world after you quit to the title screen.
     */
    public static void clearAll() {
        RITUALS.clear();
    }

    /** Advances every summoning ritual. Called once per server tick. */
    public static void tickRituals() {
        RITUALS.removeIf(SummonRitual::tick);
    }

    /**
     * The summoning: darkness spirals in on the offering for a few seconds, the sky cracks with
     * lightning, and the Living Void tears its way into the world above it.
     */
    private static final class SummonRitual {
        final ServerLevel level;
        final Vec3 center;
        int ticks = 0;

        SummonRitual(ServerLevel level, Vec3 center) {
            this.level = level;
            this.center = center;
        }

        /** @return true once the Living Void has arrived. */
        boolean tick() {
            ticks++;
            double progress = ticks / (double) RITUAL_TICKS;
            double radius = 6.0 * (1.0 - progress) + 0.5;
            for (int i = 0; i < 3; i++) {
                double angle = ticks * 0.35 + i * (Math.PI * 2.0 / 3.0);
                level.sendParticles(ParticleTypes.SQUID_INK, true, true, center.x + Math.cos(angle) * radius, center.y + 0.5 + progress * 4.0,
                    center.z + Math.sin(angle) * radius, 2, 0.1, 0.1, 0.1, 0.0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, center.x + Math.cos(angle) * radius, center.y + 0.5,
                    center.z + Math.sin(angle) * radius, 2, 0.1, 0.3, 0.1, 0.02);
            }
            if (ticks % 20 == 0) {
                level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4.0f, 0.5f);
            }
            if (ticks < RITUAL_TICKS) {
                return false;
            }

            for (int i = 0; i < 3; i++) {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(center.x + (i - 1) * 3.0, center.y, center.z + (i == 1 ? 3.0 : -2.0));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            LivingVoid boss = ToInfinititeAndBeyond.LIVING_VOID.get().create(level, EntitySpawnReason.TRIGGERED);
            if (boss != null) {
                boss.snapTo(center.x, center.y + 8.0, center.z, 0.0f, 0.0f);
                level.addFreshEntity(boss);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, center.x, center.y + 8.0, center.z, 400, 3.0, 3.0, 3.0, 0.3);
            level.sendParticles(ParticleTypes.FLASH, true, true, center.x, center.y + 8.0, center.z, 1, 0, 0, 0, 0);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 5.0f, 0.4f);
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§0§l§kxx§r §5§lThe Living Void has awoken. §0§l§kxx"), false);
            return true;
        }
    }
}
