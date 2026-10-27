package com.ghostviper358.toinfinititeandbeyond;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The Universal Destroyer - the final boss, the ancient terror from before the universe that the
 * lore books warn about. Summoned by breaking a Void Seal (dropped by the Living Void - see
 * VoidSealBlock and {@link #startRitual}). See docs/requirements/universal_destroyer.md.
 *
 * A horned titan of black stone veined with glowing purple, crowned with broken black spikes,
 * about 46 blocks tall (16x scale - Minecraft's limit - drawn 1.25x bigger by its renderer), wielding a colossal black-metal sword carved with glowing
 * runes, a Universal Core set in its guard. 100,000 health - far past vanilla's limit, so it's
 * kept in GiantBoss's pool.
 *
 * It hovers 5 blocks above the ground (the model floats - the entity and its huge 36 x 56 block
 * hitbox stay on the ground, so it can still be hit with a sword), glides straight through
 * terrain toward its target, and fights in three phases, each starting with a roar (and a few
 * seconds it can't be hurt):
 *   Phase 1 - Sword Sweep (with a backhand), Overhead Smash (+ Shockwave Rings - jump them!),
 *            Rune Circles, Star Fall.
 *   Phase 2 "Unsealed" (below 2/3 health) - a thunderstorm rolls in; adds Sword Wave, Gravity
 *            Crush, Black Hole, Void Chains (lifts you up and slams you down), Shadow Swords
 *            (ghostly swords stab down from the sky).
 *   Phase 3 "Annihilation" (below 1/3) - night falls, stars rain down constantly; adds Cosmic
 *            Annihilation (a beam from the core in its sword sweeping a full circle - jump it!)
 *            and Supernova (a huge explosion - the closer you are, the more it hurts).
 * The finale (below 10,000 health): it starts to glow, snaps its sword in two and plants the
 * blade in the ground as a ramp of solid stairs, then kneels - its body turns solid, so you can run
 * up the sword onto its shoulders and walk across to its face. Climb the sword and strike its face within 3
 * minutes - or every player nearby takes 1,000,000 damage (only a god survives), and the
 * countdown starts again. Its death triggers the ending (see {@link Ending}): the universe is saved.
 *
 * Drops: 32-64 Universal Shards, the Destroyer's Crown, the final lore book, the Universal Core
 * from its sword, and 5000 XP.
 */
public class UniversalDestroyer extends GiantBoss {

    public enum Attack {
        NONE(0),
        TRANSITION(60),
        SWORD_SWEEP(50),
        OVERHEAD_SMASH(45),
        RUNE_CIRCLES(55),
        STAR_FALL(60),
        SWORD_WAVE(50),
        GRAVITY_CRUSH(55),
        BLACK_HOLE(40),
        VOID_CHAINS(75),
        SHADOW_SWORDS(70),
        ANNIHILATION(100),
        SUPERNOVA(110),
        SWORD_BREAK(60);

        public final int duration;

        Attack(int duration) {
            this.duration = duration;
        }

        /** Attacks that swing the whole sword - it can't do these once the blade's broken off. */
        public boolean needsBlade() {
            return this == SWORD_SWEEP || this == OVERHEAD_SMASH || this == SWORD_WAVE;
        }
    }

    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(UniversalDestroyer.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(UniversalDestroyer.class, EntityDataSerializers.INT);
    /** 0 = the sword is whole, 1 = kneeling while you climb the broken blade, 2 = fighting on with a broken sword. */
    private static final EntityDataAccessor<Integer> FINALE = SynchedEntityData.defineId(UniversalDestroyer.class, EntityDataSerializers.INT);

    /** Ticks between attacks, for phases 1, 2 and 3. */
    private static final int[] ATTACK_GAP_TICKS = { 0, 45, 35, 25 };

    /** It glides at its target until it's this close, at this speed. */
    private static final double STOP_DISTANCE = 24.0;
    private static final double WALK_SPEED = 0.3;

    private static final double SWEEP_RANGE = 34.0;
    private static final float SWEEP_DAMAGE = 40.0f;

    private static final double SMASH_MAX_REACH = 40.0;
    private static final double SMASH_RADIUS = 10.0;
    private static final float SMASH_DAMAGE = 45.0f;

    /** Shockwave Rings: how fast they spread, how far, and how hard they hit if you don't jump. */
    private static final double SHOCKWAVE_SPEED = 0.9;
    private static final float SHOCKWAVE_DAMAGE = 25.0f;

    private static final double RUNE_RADIUS = 4.5;
    private static final float RUNE_DAMAGE = 28.0f;

    private static final double METEOR_RADIUS = 5.0;
    private static final float METEOR_DAMAGE = 24.0f;

    private static final int WAVE_STEPS = 40;
    private static final double WAVE_STEP_BLOCKS = 1.8;
    private static final float WAVE_DAMAGE = 32.0f;

    private static final double CRUSH_PULL_RADIUS = 56.0;
    private static final double CRUSH_RADIUS = 18.0;
    private static final float CRUSH_DAMAGE = 35.0f;

    private static final int BLACK_HOLE_TICKS = 140;
    private static final double BLACK_HOLE_PULL_RADIUS = 32.0;
    private static final double BLACK_HOLE_CRUSH_RADIUS = 5.0;
    private static final float BLACK_HOLE_DAMAGE = 14.0f;

    /** Void Chains: up to this many players, lifted this high, then slammed down. */
    private static final int CHAIN_VICTIMS = 2;
    private static final double CHAIN_LIFT = 22.0;
    private static final float CHAIN_DAMAGE = 40.0f;

    /** Shadow Swords: they hover this high, fall after this long, and hit this hard. */
    private static final double SHADOW_SWORD_HEIGHT = 28.0;
    private static final int SHADOW_SWORD_HANG_TICKS = 30;
    private static final double SHADOW_SWORD_RADIUS = 3.5;
    private static final float SHADOW_SWORD_DAMAGE = 35.0f;

    private static final int ANNIHILATION_CHARGE_TICKS = 30;
    private static final double ANNIHILATION_RANGE = 64.0;
    private static final float ANNIHILATION_DAMAGE = 25.0f;

    /** Supernova: charges for 4 seconds, then hurts more the closer you are (out to the radius). */
    private static final int SUPERNOVA_CHARGE_TICKS = 80;
    private static final double SUPERNOVA_RADIUS = 72.0;
    private static final float SUPERNOVA_DAMAGE = 220.0f;

    /** Phase 3: a falling star near a random player this often. */
    private static final int AMBIENT_STAR_TICKS = 30;

    /** The finale: below this much health it glows, breaks its sword, and plants the blade as a ramp. */
    public static final float FINALE_HEALTH = 10_000.0f;
    /** How long you have to climb the sword and strike its face: 3 minutes. */
    private static final int FINALE_TICKS = 3 * 60 * 20;
    /** What everyone nearby takes if nobody strikes its face in time. Only a god survives it. */
    private static final float DOOM_DAMAGE = 1_000_000.0f;
    private static final double DOOM_RADIUS = 256.0;
    /** A hit from a player at least this high up (near the top of the ramp) strikes its face. */
    private static final double FACE_HIT_HEIGHT = 27.0;
    private static final float FACE_HIT_DAMAGE = 4000.0f;
    /** Where its face is while it kneels: this far in front of it and this high up. */
    private static final double FACE_FORWARD = 11.0;
    private static final double FACE_HEIGHT = 33.0;
    /** Swinging your weapon this close to its face strikes it, even if you miss its hitbox. */
    private static final double FACE_REACH = 6.0;
    /**
     * While it kneels its body is solid - you can walk on it - and its hitbox shrinks to its
     * kneeling shoulders, this high: a great stone platform right below its face.
     */
    private static final float KNEEL_HEIGHT = 30.0f;
    /**
     * The ramp - the blade it plants, a staircase of solid black stairs: its tip stuck in the
     * ground, its broken end just outside the Destroyer's body, level with its shoulders, so you
     * can step off it onto the Destroyer and walk to its face; 5 wide.
     */
    private static final int RAMP_TOP = 30;
    private static final int RAMP_END = 19;
    private static final int RAMP_HALF_WIDTH = 2;

    /** Summoning: how long the ritual builds, and no second Destroyer within this range. */
    private static final int RITUAL_TICKS = 120;
    private static final double SUMMON_BLOCK_RADIUS = 128.0;

    private static final List<Ritual> RITUALS = new ArrayList<>();
    private static final List<Ending> ENDINGS = new ArrayList<>();

    private int abilityTick = 0;
    private int attackTick = 0;
    private int ticksUntilNextAttack = 60;
    private final List<Attack> attackBag = new ArrayList<>();

    private Vec3 smashPoint;
    private final List<Vec3> runePoints = new ArrayList<>();
    private Vec3 waveOrigin;
    private Vec3 waveDirection;
    private float annihilationStartYaw;

    private final List<Meteor> meteors = new ArrayList<>();
    private final List<BlackHole> blackHoles = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<ShadowSword> shadowSwords = new ArrayList<>();
    private final List<Chained> chained = new ArrayList<>();

    /** The finale: the direction it kneels facing, the countdown, and the ramp blocks it placed. */
    private float kneelYaw;
    private int finaleTicksLeft;
    private final List<BlockPos> ramp = new ArrayList<>();

    /** Client side: the tickCount when the current attack started, for the animations. */
    private int clientAttackStartTick = 0;

    public UniversalDestroyer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5000;
        this.setNoGravity(true);
        // The hitbox is worked out before the SCALE attribute exists, so it starts out unscaled -
        // recalculate it now, or the boss's hitbox stays a fraction of its size.
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------------------
    // Attributes, synced data, AI, sounds, saving
    // ---------------------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1024.0)          // vanilla's cap - the real 100,000 is getBossMaxHealth()
            .add(Attributes.ATTACK_DAMAGE, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FOLLOW_RANGE, 160.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 30.0)
            .add(Attributes.ARMOR_TOUGHNESS, 20.0)
            .add(Attributes.SCALE, 16.0);                // Minecraft's limit - the renderer draws it 1.25x bigger still
    }

    /** 100,000 health (50,000 hearts). */
    @Override
    protected float getBossMaxHealth() {
        return 100_000.0f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, Attack.NONE.ordinal());
        builder.define(PHASE, 1);
        builder.define(FINALE, 0);
    }

    @Override
    protected void registerGoals() {
        // It picks its own targets (huntNearestPlayer) and moves itself (strideTowardTarget).
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_ANGRY;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 6.0f;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Its huge hitbox mustn't shove players around - especially not off the ramp. */
    @Override
    protected void pushEntities() {
    }

    /** Kneeling: its body is solid, so you can walk on it (like a boat), right up to its face. */
    @Override
    public boolean canBeCollidedWith(Entity other) {
        return getFinale() == 1 && this.isAlive();
    }

    /** Kneeling, its hitbox comes down to its shoulders - the platform you walk across to its face. */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions standing = super.getDefaultDimensions(pose);
        return getFinale() == 1 ? EntityDimensions.scalable(standing.width(), KNEEL_HEIGHT / (float) this.getScale()) : standing;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Phase", getPhase());
        output.putInt("Finale", getFinale());
        output.putInt("FinaleTicks", finaleTicksLeft);
        output.putFloat("KneelYaw", kneelYaw);
        int[] coords = new int[ramp.size() * 3];
        for (int i = 0; i < ramp.size(); i++) {
            coords[i * 3] = ramp.get(i).getX();
            coords[i * 3 + 1] = ramp.get(i).getY();
            coords[i * 3 + 2] = ramp.get(i).getZ();
        }
        output.putIntArray("Ramp", coords);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(PHASE, input.getIntOr("Phase", 1));
        this.entityData.set(FINALE, input.getIntOr("Finale", 0));
        finaleTicksLeft = input.getIntOr("FinaleTicks", FINALE_TICKS);
        kneelYaw = input.getFloatOr("KneelYaw", 0.0f);
        ramp.clear();
        input.getIntArray("Ramp").ifPresent(coords -> {
            for (int i = 0; i + 2 < coords.length; i += 3) {
                ramp.add(new BlockPos(coords[i], coords[i + 1], coords[i + 2]));
            }
        });
    }

    public Attack getCurrentAttack() {
        int id = this.entityData.get(ATTACK);
        Attack[] attacks = Attack.values();
        return id >= 0 && id < attacks.length ? attacks[id] : Attack.NONE;
    }

    /** 1, 2 or 3. */
    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    /** 0 = sword whole, 1 = kneeling (climb the blade!), 2 = broken sword. */
    public int getFinale() {
        return this.entityData.get(FINALE);
    }

    /** Below 10,000 health its whole body glows. */
    public boolean isOvercharged() {
        return getBossHealth() < FINALE_HEALTH;
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
        if (FINALE.equals(key)) {
            this.refreshDimensions(); // kneeling changes its hitbox
        }
    }

    /**
     * It can't be hurt while it's roaring into a new phase or breaking its sword. While it kneels,
     * only a hit to its face (from high up the ramp) gets through.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean unstoppable = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        Attack attack = getCurrentAttack();
        if (!unstoppable && (attack == Attack.TRANSITION || attack == Attack.SWORD_BREAK)) {
            return false;
        }
        if (!unstoppable && getFinale() == 1) {
            if (source.getEntity() instanceof Player player && player.getY() >= this.getY() + FACE_HIT_HEIGHT) {
                return strikeFace(level, player);
            }
            if (source.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.literal("§5Its stone is too hard - climb the sword and strike its §d§lFACE§5!"), true);
            }
            return false;
        }
        if (!unstoppable && getFinale() == 0) {
            // However big the hit, it can't skip the finale: before the sword breaks, it can't
            // be knocked below 10,000 health.
            amount = Math.min(amount, Math.max(0.0f, getBossHealth() - (FINALE_HEALTH - 1.0f)));
            if (amount <= 0.0f) {
                return false;
            }
        }
        return super.hurtServer(level, source, amount);
    }

    // ---------------------------------------------------------------------------
    // Every tick
    // ---------------------------------------------------------------------------

    @Override
    public void tick() {
        // A titan glides through terrain rather than around it.
        this.noPhysics = true;
        super.tick();
        abilityTick++;
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (abilityTick % 20 == 0) {
            huntNearestPlayer(level);
        }
        if (abilityTick % 4 == 0) {
            level.sendParticles(getPhase() >= 3 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH,
                this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(),
                getPhase() * 3, this.getBbWidth() * 0.3, this.getBbHeight() * 0.35, this.getBbWidth() * 0.3, 0.02);
        }
        if (isOvercharged() && abilityTick % 2 == 0) {
            // Glowing: light pouring off it.
            level.sendParticles(ParticleTypes.END_ROD, true, true, this.getX(), this.getY() + this.getBbHeight() * 0.55, this.getZ(),
                6, this.getBbWidth() * 0.25, this.getBbHeight() * 0.3, this.getBbWidth() * 0.25, 0.05);
        }
        checkPhase(level);
        strideTowardTarget(level);
        meteors.removeIf(meteor -> meteor.tick(level));
        blackHoles.removeIf(hole -> hole.tick(level));
        shockwaves.removeIf(wave -> wave.tick(level));
        shadowSwords.removeIf(sword -> sword.tick(level));
        if (getPhase() >= 3 && abilityTick % AMBIENT_STAR_TICKS == 0) {
            rainStarOnRandomPlayer(level);
        }
        if (getFinale() == 1) {
            tickFinale(level);
        } else {
            tickAttacks(level);
        }
    }

    private void huntNearestPlayer(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current != null && current.isAlive() && !(current instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            return;
        }
        this.setTarget(level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), this.getAttributeValue(Attributes.FOLLOW_RANGE),
            entity -> entity instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator()));
    }

    /** Glides at its target over (and through) the ground, always facing it. Stays still to attack. */
    private void strideTowardTarget(ServerLevel level) {
        LivingEntity target = this.getTarget();
        Vec3 move = Vec3.ZERO;
        boolean rooted = getFinale() == 1 || getCurrentAttack() == Attack.SWORD_BREAK;
        if (rooted) {
            // Kneeling (or breaking its sword): it faces the ramp and doesn't budge.
            this.setYRot(kneelYaw);
            this.yBodyRot = kneelYaw;
            this.yHeadRot = kneelYaw;
        } else if (target != null) {
            Vec3 toTarget = target.position().subtract(this.position());
            float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0f;
            if (getCurrentAttack() != Attack.ANNIHILATION) {
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.yHeadRot = yaw;
            }
            double distance = toTarget.horizontalDistance();
            if (getCurrentAttack() == Attack.NONE && distance > STOP_DISTANCE) {
                move = toTarget.multiply(1.0, 0.0, 1.0).normalize().scale(WALK_SPEED * (getPhase() >= 3 ? 1.4 : 1.0));
            }
        }
        // Keep its feet on the ground beneath it (the model floats above), whatever that ground is doing.
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(this.getX()), Mth.floor(this.getZ()));
        double dy = rooted ? 0.0 : (ground - this.getY()) * 0.3;
        this.setDeltaMovement(move.x, dy, move.z);
        // The dark power holding it up, streaming down from its feet to the ground below.
        if (abilityTick % 3 == 0 && getFinale() != 1) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 2.5, this.getZ(), 10, 4.0, 2.0, 4.0, 0.02);
            level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 3, 6.0, 0.1, 6.0, 0.01);
        }
        if (abilityTick % 40 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 5.0f, 0.3f);
        }
    }

    /** Living things its attacks hit: everything but itself, and players in creative/spectator. */
    private List<LivingEntity> targetsWithin(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive()
            && !(e instanceof Player player && (player.isCreative() || player.isSpectator())));
    }

    private List<ServerPlayer> playersNearby(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(radius),
            p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
    }

    private Vec3 facing() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private static Vec3 groundAt(ServerLevel level, double x, double z) {
        return new Vec3(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)), z);
    }

    /** Where the Universal Core in its sword's guard is, in the model's poses: about 26 blocks up. */
    private Vec3 swordCore() {
        return this.position().add(facing().scale(12.0)).add(0.0, 26.0, 0.0);
    }

    /** Where the glowing core in its chest is: about 35 blocks up. */
    private Vec3 chestCore() {
        return this.position().add(facing().scale(8.0)).add(0.0, 35.0, 0.0);
    }

    private void launch(LivingEntity victim, Vec3 from, double out, double up) {
        Vec3 away = victim.position().subtract(from).multiply(1.0, 0.0, 1.0);
        Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(out);
        victim.setDeltaMovement(push.x, up, push.z);
        victim.hurtMarked = true;
    }

    // ---------------------------------------------------------------------------
    // Phases
    // ---------------------------------------------------------------------------

    private void checkPhase(ServerLevel level) {
        float health = getBossHealthFraction();
        int phase = getPhase();
        int wanted = health < 1.0f / 3.0f ? 3 : health < 2.0f / 3.0f ? 2 : 1;
        if (wanted > phase) {
            this.entityData.set(PHASE, wanted);
            attackBag.clear();
            chained.clear();
            startAttack(level, Attack.TRANSITION);
            transitionTo(level, wanted);
            return;
        }
        Attack current = getCurrentAttack();
        if (getFinale() == 0 && isOvercharged() && current != Attack.SWORD_BREAK && current != Attack.TRANSITION) {
            attackBag.clear();
            chained.clear();
            kneelYaw = Math.round(this.getYRot() / 90.0f) * 90.0f; // square to the world, so the ramp is a clean line
            startAttack(level, Attack.SWORD_BREAK);
            broadcastTitle(level, Component.literal("§d§lIT BEGINS TO GLOW"), Component.literal("§5Something terrible is coming..."));
        }
    }

    /** A new phase: a roar, a blast that throws everyone back, and the sky changes. */
    private void transitionTo(ServerLevel level, int phase) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0f, 0.4f);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.3f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, this.getX(), this.getY() + 20.0, this.getZ(),
            1500, 18.0, 18.0, 18.0, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.getX(), this.getY() + 2.0, this.getZ(),
            10, 12.0, 2.0, 12.0, 0.0);
        for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(24.0))) {
            launch(victim, this.position(), 2.5, 1.0);
        }
        shockwaves.add(new Shockwave(this.position(), 48.0, 0));
        ServerLevel overworld = level.getServer().overworld();
        if (phase == 2) {
            overworld.setWeatherParameters(0, 24000, true, true);
            broadcastTitle(level, Component.literal("§5§lTHE SEAL IS BROKEN"), Component.literal("§dThe Destroyer is unbound"));
        } else {
            overworld.setWeatherParameters(0, 24000, true, true);
            long time = overworld.getDayTime();
            overworld.setDayTime(time - time % 24000L + 18000L);
            broadcastTitle(level, Component.literal("§4§lANNIHILATION"), Component.literal("§cThe stars are falling"));
        }
    }

    private void broadcastTitle(ServerLevel level, Component title, Component subtitle) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
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
            case SWORD_SWEEP -> tickSwordSweep(level);
            case OVERHEAD_SMASH -> tickOverheadSmash(level, target);
            case RUNE_CIRCLES -> tickRuneCircles(level);
            case STAR_FALL -> tickStarFall(level, target);
            case SWORD_WAVE -> tickSwordWave(level, target);
            case GRAVITY_CRUSH -> tickGravityCrush(level);
            case BLACK_HOLE -> tickBlackHole(level, target);
            case VOID_CHAINS -> tickVoidChains(level);
            case SHADOW_SWORDS -> tickShadowSwords(level);
            case ANNIHILATION -> tickAnnihilation(level);
            case SUPERNOVA -> tickSupernova(level);
            case SWORD_BREAK -> tickSwordBreak(level);
            default -> { }
        }
        if (attackTick >= current.duration) {
            this.entityData.set(ATTACK, Attack.NONE.ordinal());
            ticksUntilNextAttack = ATTACK_GAP_TICKS[getPhase()];
            chained.clear();
            if (current == Attack.SWORD_BREAK) {
                beginCountdown(level);
            }
        }
    }

    private Attack pickAttack(LivingEntity target) {
        boolean blade = getFinale() == 0;
        if (blade && target.distanceTo(this) < SWEEP_RANGE && this.random.nextFloat() < 0.3f) {
            return Attack.SWORD_SWEEP;
        }
        if (attackBag.isEmpty()) {
            List<Attack> all = new ArrayList<>(List.of(Attack.SWORD_SWEEP, Attack.OVERHEAD_SMASH, Attack.RUNE_CIRCLES, Attack.STAR_FALL));
            if (getPhase() >= 2) {
                all.addAll(List.of(Attack.SWORD_WAVE, Attack.GRAVITY_CRUSH, Attack.BLACK_HOLE, Attack.VOID_CHAINS, Attack.SHADOW_SWORDS));
            }
            if (getPhase() >= 3) {
                all.addAll(List.of(Attack.ANNIHILATION, Attack.ANNIHILATION, Attack.SUPERNOVA));
            }
            all.removeIf(attack -> !blade && attack.needsBlade());
            attackBag.addAll(all);
            Collections.shuffle(attackBag, new Random(this.random.nextLong()));
        }
        return attackBag.remove(attackBag.size() - 1);
    }

    private void startAttack(ServerLevel level, Attack attack) {
        this.entityData.set(ATTACK, attack.ordinal());
        attackTick = 0;
        smashPoint = null;
        runePoints.clear();
        waveOrigin = null;
        if (attack == Attack.ANNIHILATION) {
            annihilationStartYaw = this.getYRot();
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 8.0f, 0.3f);
        }
    }

    /** Sword Sweep: winds back, one huge slash across everything in front of it, then a backhand. */
    private void tickSwordSweep(ServerLevel level) {
        if (attackTick == 22 || attackTick == 36) {
            slash(level, attackTick == 22 ? 1.0 : -1.0);
        }
    }

    private void slash(ServerLevel level, double direction) {
        Vec3 facing = facing();
        for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(SWEEP_RANGE - this.getBbWidth() * 0.5, 8.0, SWEEP_RANGE - this.getBbWidth() * 0.5))) {
            Vec3 to = victim.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            if (to.length() > SWEEP_RANGE || (to.lengthSqr() > 1.0 && to.normalize().dot(facing) < -0.2)) {
                continue;
            }
            victim.hurtServer(level, this.damageSources().mobAttack(this), SWEEP_DAMAGE);
            launch(victim, this.position(), 2.2, 0.7);
        }
        // A great crescent of light, at three heights.
        for (int i = -8; i <= 8; i++) {
            double angle = Math.atan2(facing.z, facing.x) + i * 0.2 * direction;
            for (double r = 10.0; r <= SWEEP_RANGE; r += 3.0) {
                for (double h = 1.5; h <= 5.5; h += 2.0) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, true, this.getX() + Math.cos(angle) * r, this.getY() + h,
                        this.getZ() + Math.sin(angle) * r, 1, 0.0, 0.0, 0.0, 0.0);
                }
                level.sendParticles(ParticleTypes.WITCH, true, true, this.getX() + Math.cos(angle) * r, this.getY() + 3.0,
                    this.getZ() + Math.sin(angle) * r, 2, 0.3, 0.5, 0.3, 0.02);
            }
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 8.0f, 0.3f);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 8.0f, 0.5f);
    }

    /** Overhead Smash: brings the sword down where you're standing, sending Shockwave Rings out. Jump them! */
    private void tickOverheadSmash(ServerLevel level, LivingEntity target) {
        if (attackTick <= 18 && target != null) {
            Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            double reach = Math.min(to.length(), SMASH_MAX_REACH);
            Vec3 point = this.position().add(to.lengthSqr() < 1.0E-4 ? facing().scale(reach) : to.normalize().scale(reach));
            smashPoint = groundAt(level, point.x, point.z);
        }
        if (smashPoint == null) {
            return;
        }
        if (attackTick < 28 && attackTick % 2 == 0) {
            ring(level, smashPoint, SMASH_RADIUS, ParticleTypes.WITCH);
            ring(level, smashPoint, SMASH_RADIUS * 0.5, ParticleTypes.ENCHANT);
            return;
        }
        if (attackTick != 28) {
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, smashPoint.x, smashPoint.y + 0.5, smashPoint.z, 4, 3.0, 0.3, 3.0, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, smashPoint.x, smashPoint.y + 0.5, smashPoint.z, 300, 5.0, 2.0, 5.0, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, true, true, smashPoint.x, smashPoint.y + 4.0, smashPoint.z, 80, 2.0, 6.0, 2.0, 0.1);
        level.playSound(null, smashPoint.x, smashPoint.y, smashPoint.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0f, 0.4f);
        for (LivingEntity victim : targetsWithin(level, new AABB(smashPoint, smashPoint).inflate(SMASH_RADIUS, 5.0, SMASH_RADIUS))) {
            victim.hurtServer(level, this.damageSources().mobAttack(this), SMASH_DAMAGE);
            launch(victim, smashPoint, 1.5, 1.0);
        }
        // Three Shockwave Rings, one after another.
        for (int i = 0; i < 3; i++) {
            shockwaves.add(new Shockwave(smashPoint, 36.0, i * 10));
        }
    }

    /** Rune Circles: glowing runes appear under you (and all around), then erupt in pillars of light. */
    private void tickRuneCircles(ServerLevel level) {
        if (attackTick == 5) {
            for (ServerPlayer player : playersNearby(level, 64.0)) {
                runePoints.add(groundAt(level, player.getX(), player.getZ()));
                for (int i = 0; i < 4; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0;
                    double r = 4.0 + this.random.nextDouble() * 7.0;
                    runePoints.add(groundAt(level, player.getX() + Math.cos(angle) * r, player.getZ() + Math.sin(angle) * r));
                }
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 8.0f, 0.4f);
        }
        if (attackTick > 5 && attackTick < 35 && attackTick % 2 == 0) {
            for (Vec3 point : runePoints) {
                ring(level, point, RUNE_RADIUS, ParticleTypes.ENCHANT);
                ring(level, point, RUNE_RADIUS * 0.6, ParticleTypes.WITCH);
                ring(level, point, RUNE_RADIUS * 0.25, ParticleTypes.END_ROD);
            }
        }
        if (attackTick != 35) {
            return;
        }
        for (Vec3 point : runePoints) {
            for (double h = 0.5; h < 12.0; h += 1.0) {
                level.sendParticles(ParticleTypes.END_ROD, true, true, point.x, point.y + h, point.z, 3, 0.6, 0.2, 0.6, 0.02);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, point.x, point.y + 3.0, point.z, 80, 1.2, 3.0, 1.2, 0.2);
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, point.x, point.y + 0.5, point.z, 3, 1.0, 0.3, 1.0, 0.0);
            for (LivingEntity victim : targetsWithin(level, new AABB(point, point).inflate(RUNE_RADIUS, 4.0, RUNE_RADIUS))) {
                victim.hurtServer(level, level.damageSources().magic(), RUNE_DAMAGE);
                victim.setDeltaMovement(victim.getDeltaMovement().x, 1.2, victim.getDeltaMovement().z);
                victim.hurtMarked = true;
            }
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 8.0f, 0.5f);
    }

    /** Star Fall: raises its sword to the sky and calls a storm of stars down around you. */
    private void tickStarFall(ServerLevel level, LivingEntity target) {
        if (target == null || attackTick < 10 || attackTick > 54 || attackTick % 4 != 2) {
            return;
        }
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double r = this.random.nextDouble() * 10.0;
        meteors.add(new Meteor(groundAt(level, target.getX() + Math.cos(angle) * r, target.getZ() + Math.sin(angle) * r)));
    }

    private void rainStarOnRandomPlayer(ServerLevel level) {
        List<ServerPlayer> players = playersNearby(level, 96.0);
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.get(this.random.nextInt(players.size()));
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double r = this.random.nextDouble() * 12.0;
        meteors.add(new Meteor(groundAt(level, player.getX() + Math.cos(angle) * r, player.getZ() + Math.sin(angle) * r)));
    }

    /** Sword Wave (phase 2): a downward slash that sends a crescent shockwave tearing along the ground. */
    private void tickSwordWave(ServerLevel level, LivingEntity target) {
        if (attackTick == 20) {
            Vec3 toward = target != null ? target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0) : facing();
            waveDirection = toward.lengthSqr() < 1.0E-4 ? facing() : toward.normalize();
            waveOrigin = this.position().add(waveDirection.scale(this.getBbWidth() * 0.4));
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 8.0f, 0.2f);
        }
        if (waveOrigin == null || attackTick <= 20 || attackTick > 20 + WAVE_STEPS) {
            return;
        }
        int step = attackTick - 20;
        Vec3 side = new Vec3(-waveDirection.z, 0.0, waveDirection.x);
        Vec3 ahead = waveOrigin.add(waveDirection.scale(step * WAVE_STEP_BLOCKS));
        Vec3 center = groundAt(level, ahead.x, ahead.z);
        for (int i = -4; i <= 4; i++) {
            // A crescent: the ends trail behind the middle.
            Vec3 at = center.add(side.scale(i * 1.3)).subtract(waveDirection.scale(i * i * 0.12));
            level.sendParticles(ParticleTypes.WITCH, true, true, at.x, at.y + 0.8, at.z, 4, 0.2, 0.8, 0.2, 0.02);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y + 1.0, at.z, 3, 0.2, 1.0, 0.2, 0.05);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y + 2.0, at.z, 1, 0.1, 0.6, 0.1, 0.0);
            }
        }
        if (step % 3 == 0) {
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, center.x, center.y + 0.5, center.z, 2, 1.0, 0, 1.0, 0);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 1.1f);
        }
        for (LivingEntity victim : targetsWithin(level, new AABB(center, center).inflate(5.5, 3.0, 5.5))) {
            victim.hurtServer(level, this.damageSources().mobAttack(this), WAVE_DAMAGE);
            victim.setDeltaMovement(victim.getDeltaMovement().add(waveDirection.scale(1.4)).add(0.0, 0.8, 0.0));
            victim.hurtMarked = true;
        }
    }

    /** Gravity Crush (phase 2): drags everyone in toward it, then smashes the ground with both fists. */
    private void tickGravityCrush(ServerLevel level) {
        if (attackTick >= 5 && attackTick < 32) {
            for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(CRUSH_PULL_RADIUS))) {
                Vec3 to = this.position().subtract(victim.position()).multiply(1.0, 0.0, 1.0);
                if (to.length() < this.getBbWidth() * 0.4) {
                    continue;
                }
                victim.setDeltaMovement(victim.getDeltaMovement().add(to.normalize().scale(0.2)));
                victim.hurtMarked = true;
                if (attackTick % 3 == 0) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
                }
            }
            if (attackTick % 4 == 0) {
                ring(level, this.position(), CRUSH_PULL_RADIUS * (1.0 - (attackTick - 5) / 27.0), ParticleTypes.REVERSE_PORTAL);
            }
        }
        if (attackTick != 35) {
            return;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.getX(), this.getY() + 0.5, this.getZ(), 10, 10.0, 0.5, 10.0, 0.0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0f, 0.4f);
        for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(CRUSH_RADIUS - this.getBbWidth() * 0.5 + 6.0))) {
            victim.hurtServer(level, this.damageSources().mobAttack(this), CRUSH_DAMAGE);
            launch(victim, this.position(), 1.8, 1.3);
        }
        shockwaves.add(new Shockwave(this.position(), 44.0, 0));
        shockwaves.add(new Shockwave(this.position(), 44.0, 12));
    }

    /** Black Hole (phase 2): stabs the ground and tears open a black hole between you and it. */
    private void tickBlackHole(ServerLevel level, LivingEntity target) {
        if (attackTick != 20) {
            return;
        }
        Vec3 center = target != null ? this.position().lerp(target.position(), 0.55) : this.position().add(facing().scale(20.0));
        blackHoles.add(new BlackHole(center.add(0.0, 9.0, 0.0)));
        level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 8.0f, 0.4f);
    }

    /**
     * Void Chains (phase 2): chains of darkness lash out from its hand, grab the nearest players,
     * lift them high into the air... and slam them into the ground.
     */
    private void tickVoidChains(ServerLevel level) {
        if (attackTick == 10) {
            List<ServerPlayer> players = new ArrayList<>(playersNearby(level, 64.0));
            players.sort(Comparator.comparingDouble(this::distanceToSqr));
            for (int i = 0; i < Math.min(CHAIN_VICTIMS, players.size()); i++) {
                chained.add(new Chained(players.get(i), players.get(i).position().add(0.0, CHAIN_LIFT, 0.0)));
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 8.0f, 0.3f);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 8.0f, 0.6f);
        }
        if (attackTick < 10) {
            return;
        }
        Vec3 hand = this.position().add(facing().scale(14.0)).add(0.0, 24.0, 0.0);
        for (Chained chain : chained) {
            ServerPlayer player = chain.player;
            if (!player.isAlive()) {
                continue;
            }
            if (attackTick <= 50) {
                // Up into the air, then held there.
                Vec3 to = chain.liftPoint.subtract(player.position());
                player.setDeltaMovement(to.scale(attackTick < 35 ? 0.12 : 0.3));
                player.resetFallDistance();
                player.hurtMarked = true;
                if (attackTick % 2 == 0) {
                    Vec3 at = player.position().add(0.0, 1.0, 0.0);
                    for (int i = 0; i <= 20; i++) {
                        Vec3 link = hand.lerp(at, i / 20.0);
                        level.sendParticles(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.WITCH, true, true,
                            link.x, link.y, link.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            } else if (attackTick == 51) {
                player.setDeltaMovement(0.0, -4.0, 0.0);
                player.hurtMarked = true;
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 6.0f, 0.4f);
            } else if (!chain.slammed && (player.onGround() || attackTick >= 72)) {
                chain.slammed = true;
                player.hurtServer(level, this.damageSources().mobAttack(this), CHAIN_DAMAGE);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, player.getX(), player.getY() + 0.5, player.getZ(), 80, 2.0, 0.5, 2.0, 0.2);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.6f);
            }
        }
    }

    /** Shadow Swords (phase 2): giant ghostly swords appear in the sky and stab down at you. */
    private void tickShadowSwords(ServerLevel level) {
        if (attackTick != 8) {
            return;
        }
        for (ServerPlayer player : playersNearby(level, 72.0)) {
            shadowSwords.add(new ShadowSword(groundAt(level, player.getX(), player.getZ()), 0));
            for (int i = 0; i < 3; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double r = 4.0 + this.random.nextDouble() * 6.0;
                shadowSwords.add(new ShadowSword(groundAt(level, player.getX() + Math.cos(angle) * r, player.getZ() + Math.sin(angle) * r), 6 * (i + 1)));
            }
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 8.0f, 0.4f);
    }

    /**
     * Cosmic Annihilation (phase 3): holds its sword up, the core in the guard charges, then fires
     * a beam down to the ground that sweeps a full circle as the Destroyer turns. Jump over it as
     * it passes!
     */
    private void tickAnnihilation(ServerLevel level) {
        Vec3 core = swordCore();
        if (attackTick < ANNIHILATION_CHARGE_TICKS) {
            level.sendParticles(ParticleTypes.PORTAL, true, true, core.x, core.y, core.z, 50, 5.0, 5.0, 5.0, 1.5);
            level.sendParticles(ParticleTypes.END_ROD, true, true, core.x, core.y, core.z, 5, 0.6, 0.6, 0.6, 0.02);
            return;
        }
        float progress = (attackTick - ANNIHILATION_CHARGE_TICKS) / (float) (Attack.ANNIHILATION.duration - ANNIHILATION_CHARGE_TICKS);
        // It turns with the beam (turning its yaw turns the beam the same way).
        float yaw = annihilationStartYaw + progress * 360.0f;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        double angle = (annihilationStartYaw + 90.0) * Mth.DEG_TO_RAD + progress * Math.PI * 2.0;
        Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        double beamY = this.getY() + 0.6;
        Vec3 from = new Vec3(this.getX(), beamY, this.getZ()).add(direction.scale(6.0));
        Vec3 to = from.add(direction.scale(ANNIHILATION_RANGE));
        // The beam pours down from the core to the ground, then races out along it.
        for (int i = 0; i <= 12; i++) {
            Vec3 at = core.lerp(from, i / 12.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
        }
        for (int i = 0; i < (int) ANNIHILATION_RANGE; i++) {
            Vec3 at = from.add(direction.scale(i));
            level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.WITCH, true, true, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.0);
            }
        }
        for (LivingEntity victim : targetsWithin(level, new AABB(from, to).inflate(1.0))) {
            if (victim.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
                victim.hurtServer(level, level.damageSources().magic(), ANNIHILATION_DAMAGE);
                victim.igniteForTicks(80);
            }
        }
        if (attackTick % 10 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 6.0f, 0.5f);
        }
    }

    /**
     * Supernova (phase 3): the core in its chest charges for 4 seconds, then explodes. The closer
     * you are, the more it hurts - so run!
     */
    private void tickSupernova(ServerLevel level) {
        Vec3 core = chestCore();
        if (attackTick == 1) {
            for (ServerPlayer player : playersNearby(level, SUPERNOVA_RADIUS * 2.0)) {
                player.displayClientMessage(Component.literal("§c§lRUN! §6The Destroyer is going §e§lSUPERNOVA§6!"), true);
            }
        }
        if (attackTick < SUPERNOVA_CHARGE_TICKS) {
            float charge = attackTick / (float) SUPERNOVA_CHARGE_TICKS;
            level.sendParticles(ParticleTypes.PORTAL, true, true, core.x, core.y, core.z, 60, 10.0, 10.0, 10.0, 2.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, core.x, core.y, core.z, 2 + (int) (charge * 14), charge * 3.0, charge * 3.0, charge * 3.0, 0.02);
            if (attackTick % 20 == 0) {
                level.playSound(null, core.x, core.y, core.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 10.0f, 0.5f + charge);
            }
            if (attackTick % 10 == 0) {
                ring(level, groundAt(level, this.getX(), this.getZ()), SUPERNOVA_RADIUS * 0.5, ParticleTypes.FLAME);
            }
            return;
        }
        if (attackTick != SUPERNOVA_CHARGE_TICKS) {
            return;
        }
        level.sendParticles(ParticleTypes.FLASH, true, true, core.x, core.y, core.z, 3, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, core.x, core.y - 10.0, core.z, 30, 20.0, 15.0, 20.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, core.x, core.y, core.z, 2000, 4.0, 4.0, 4.0, 2.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, core.x, core.y, core.z, 1200, 25.0, 20.0, 25.0, 0.5);
        level.sendParticles(ParticleTypes.FLAME, true, true, this.getX(), this.getY() + 1.0, this.getZ(), 800, 30.0, 1.0, 30.0, 0.2);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 16.0f, 0.3f);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 16.0f, 0.4f);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 16.0f, 0.5f);
        for (LivingEntity victim : targetsWithin(level, this.getBoundingBox().inflate(SUPERNOVA_RADIUS))) {
            double distance = victim.position().subtract(this.position()).horizontalDistance();
            if (distance > SUPERNOVA_RADIUS) {
                continue;
            }
            float closeness = (float) (1.0 - distance / SUPERNOVA_RADIUS);
            victim.hurtServer(level, this.damageSources().mobAttack(this), Math.max(1.0f, SUPERNOVA_DAMAGE * closeness * closeness));
            victim.igniteForTicks(100);
            launch(victim, this.position(), 3.5 * closeness, 0.3 + closeness);
        }
        shockwaves.add(new Shockwave(this.position(), SUPERNOVA_RADIUS, 0));
    }

    /**
     * The sword breaks (below 10,000 health): it holds its sword up, snaps the blade off, and
     * hurls it into the ground in front of it - a ramp up to its face.
     */
    private void tickSwordBreak(ServerLevel level) {
        Vec3 sword = swordCore().add(0.0, 6.0, 0.0);
        if (attackTick < 24 && attackTick % 3 == 0) {
            level.sendParticles(ParticleTypes.CRIT, true, true, sword.x, sword.y, sword.z, 30, 2.0, 6.0, 2.0, 0.3);
            level.sendParticles(ParticleTypes.WITCH, true, true, sword.x, sword.y, sword.z, 20, 1.0, 6.0, 1.0, 0.05);
            level.playSound(null, sword.x, sword.y, sword.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0f, 0.3f + attackTick * 0.02f);
        }
        if (attackTick == 24) {
            level.sendParticles(ParticleTypes.FLASH, true, true, sword.x, sword.y, sword.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, sword.x, sword.y, sword.z, 3, 2.0, 2.0, 2.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, sword.x, sword.y, sword.z, 300, 1.0, 1.0, 1.0, 0.8);
            level.playSound(null, sword.x, sword.y, sword.z, SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 10.0f, 0.4f);
            level.playSound(null, sword.x, sword.y, sword.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 10.0f, 0.3f);
            broadcastTitle(level, Component.literal("§4§lTHE SWORD SHATTERS"), Component.literal("§cIts blade plunges into the ground..."));
        }
        if (attackTick > 26 && attackTick < 40) {
            // The blade flying down into the ground.
            Vec3 landing = rampBase();
            Vec3 at = sword.lerp(landing, (attackTick - 26) / 14.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 20, 1.5, 1.5, 1.5, 0.05);
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, at.x, at.y, at.z, 10, 1.0, 1.0, 1.0, 0.02);
        }
        if (attackTick == 40) {
            buildRamp(level);
        }
    }

    private void ring(ServerLevel level, Vec3 center, double radius, net.minecraft.core.particles.SimpleParticleType particle) {
        int points = Math.min(200, (int) (radius * 6) + 6);
        for (int i = 0; i < points; i++) {
            double angle = i * (Math.PI * 2.0 / points);
            level.sendParticles(particle, true, true, center.x + Math.cos(angle) * radius, center.y + 0.15,
                center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ---------------------------------------------------------------------------
    // The finale: climb the sword and strike its face
    // ---------------------------------------------------------------------------

    private Vec3 kneelForward() {
        float yaw = kneelYaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    private Vec3 rampBase() {
        return this.position().add(kneelForward().scale(RAMP_END + RAMP_TOP));
    }

    private Vec3 facePoint() {
        return this.position().add(kneelForward().scale(FACE_FORWARD)).add(0.0, FACE_HEIGHT, 0.0);
    }

    /**
     * Lays the broken blade into the world as a ramp of solid Sword Fragment stairs, climbing one
     * block per block: its tip stuck in the ground far in front of it, rising like a sword planted
     * at an angle, up to its broken end just under its face. Only fills air (and grass, water...),
     * and remembers every block so it can take them away.
     */
    private void buildRamp(ServerLevel level) {
        clearRamp(level, false);
        Vec3 forward = kneelForward();
        int dx = (int) Math.round(forward.x);
        int dz = (int) Math.round(forward.z);
        int baseY = Mth.floor(this.getY());
        BlockPos center = this.blockPosition();
        // The stairs climb toward the Destroyer, so their high side faces it.
        BlockState fragment = ToInfinititeAndBeyond.DESTROYER_SWORD_FRAGMENT.get().defaultBlockState()
            .setValue(StairBlock.FACING, Direction.getApproximateNearest(-dx, 0, -dz));
        for (int i = 0; i < RAMP_TOP; i++) {
            int along = RAMP_END + i;
            int y = baseY + RAMP_TOP - 1 - i;
            for (int s = -RAMP_HALF_WIDTH; s <= RAMP_HALF_WIDTH; s++) {
                // Sideways is (-dz, dx): square to the forward direction.
                BlockPos pos = new BlockPos(center.getX() + dx * along - dz * s, y, center.getZ() + dz * along + dx * s);
                if (!level.getBlockState(pos).canBeReplaced()) {
                    continue;
                }
                level.setBlock(pos, fragment, 3);
                ramp.add(pos);
                if (s == 0 && i % 3 == 0) {
                    level.sendParticles(ParticleTypes.EXPLOSION, true, true, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 1.0, 0.5, 1.0, 0.0);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 15, 2.0, 0.5, 2.0, 0.1);
                }
            }
        }
        Vec3 base = rampBase();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, base.x, base.y + 1.0, base.z, 4, 3.0, 1.0, 3.0, 0.0);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10.0f, 0.4f);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 10.0f, 0.2f);
    }

    /** Takes the ramp away again (only blocks that are still Sword Fragments). */
    private void clearRamp(ServerLevel level, boolean crumble) {
        for (BlockPos pos : ramp) {
            if (level.getBlockState(pos).is(ToInfinititeAndBeyond.DESTROYER_SWORD_FRAGMENT.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                if (crumble && this.random.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
                    level.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
        if (crumble && !ramp.isEmpty()) {
            Vec3 base = rampBase();
            level.playSound(null, base.x, base.y, base.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 10.0f, 0.4f);
        }
        ramp.clear();
    }

    private void beginCountdown(ServerLevel level) {
        this.entityData.set(FINALE, 1);
        finaleTicksLeft = FINALE_TICKS;
        broadcastTitle(level, Component.literal("§c§lCLIMB THE SWORD!"),
            Component.literal("§eStrike its face within 3 minutes - or everyone dies"));
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 10.0f, 0.3f);
    }

    /** Kneeling: the countdown ticks away. If it runs out, the doom - and then it starts again. */
    private void tickFinale(ServerLevel level) {
        finaleTicksLeft--;
        Vec3 face = facePoint();
        if (finaleTicksLeft % 10 == 0) {
            // Light sparkling up the blade, and a ring round its face - so you can see where to go.
            for (int i = 0; i < ramp.size(); i += 4) {
                BlockPos pos = ramp.get(i);
                level.sendParticles(ParticleTypes.END_ROD, true, true, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 1, 0.3, 0.2, 0.3, 0.01);
            }
            for (int i = 0; i < 24; i++) {
                double angle = i * (Math.PI * 2.0 / 24.0);
                level.sendParticles(ParticleTypes.WITCH, true, true, face.x + Math.cos(angle) * 3.0, face.y + Math.sin(angle) * 3.0, face.z,
                    1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        // Anyone up at its face who swings strikes it - no need to hit the hitbox exactly.
        for (ServerPlayer player : playersNearby(level, 8.0)) {
            if (player.swinging && player.swingTime < 2 && player.getEyePosition().distanceTo(face) <= FACE_REACH) {
                strikeFace(level, player);
                return;
            }
        }
        if (finaleTicksLeft % 20 == 0) {
            int seconds = finaleTicksLeft / 20;
            Component message = Component.literal(String.format("§c§lClimb the sword and strike its face! §e§l%d:%02d", seconds / 60, seconds % 60));
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(DOOM_RADIUS))) {
                player.displayClientMessage(message, true);
            }
            if (seconds <= 10) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 12.0f, 0.5f);
            }
        }
        if (finaleTicksLeft % 5 == 0) {
            // The doom gathering in its chest, faster as time runs out.
            Vec3 core = chestCore();
            float doom = 1.0f - finaleTicksLeft / (float) FINALE_TICKS;
            level.sendParticles(ParticleTypes.PORTAL, true, true, core.x, core.y - 8.0, core.z, 10 + (int) (doom * 60), 8.0, 8.0, 8.0, 1.5);
        }
        if (finaleTicksLeft <= 0) {
            unleashDoom(level);
            finaleTicksLeft = FINALE_TICKS;
        }
    }

    /** Nobody struck its face in time: 1,000,000 damage to every player nearby. Only a god survives. */
    private void unleashDoom(ServerLevel level) {
        Vec3 core = chestCore();
        level.sendParticles(ParticleTypes.FLASH, true, true, core.x, core.y, core.z, 5, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, core.x, core.y - 15.0, core.z, 40, 30.0, 15.0, 30.0, 0.0);
        level.sendParticles(ParticleTypes.SQUID_INK, true, true, core.x, core.y, core.z, 2000, 30.0, 20.0, 30.0, 0.5);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 16.0f, 0.3f);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 16.0f, 0.2f);
        for (ServerPlayer player : playersNearby(level, DOOM_RADIUS)) {
            player.invulnerableTime = 0;
            player.hurtServer(level, level.damageSources().fellOutOfWorld(), DOOM_DAMAGE);
        }
        broadcastTitle(level, Component.literal("§4§lTOO SLOW"), Component.literal("§cOnly a god could survive that. The countdown begins again..."));
    }

    /** Someone made it up the sword and hit its face: a huge blow, the ramp crumbles, and it fights on. */
    private boolean strikeFace(ServerLevel level, Player striker) {
        this.entityData.set(FINALE, 2);
        clearRamp(level, true);
        // Everyone up on the ramp floats gently down instead of falling 30 blocks.
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(128.0))) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
        }
        Vec3 core = chestCore();
        level.sendParticles(ParticleTypes.FLASH, true, true, core.x, core.y - 2.0, core.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, core.x, core.y - 2.0, core.z, 400, 3.0, 3.0, 3.0, 0.6);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 12.0f, 0.5f);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 12.0f, 0.6f);
        broadcastTitle(level, Component.literal("§a§lDIRECT HIT!"), Component.literal("§e" + striker.getName().getString() + " struck its face - finish it!"));
        this.invulnerableTime = 0;
        boolean hurt = super.hurtServer(level, this.damageSources().indirectMagic(striker, striker), FACE_HIT_DAMAGE);
        if (this.isAlive()) {
            startAttack(level, Attack.TRANSITION); // it reels, roaring
        }
        return hurt;
    }

    // ---------------------------------------------------------------------------
    // Things its attacks leave behind
    // ---------------------------------------------------------------------------

    /** A falling star: streaks down from high above over a second, then explodes. Never breaks blocks. */
    private final class Meteor {
        final Vec3 target;
        final Vec3 start;
        int ticks = 0;

        Meteor(Vec3 target) {
            this.target = target;
            this.start = target.add(-16.0, 50.0, -10.0);
        }

        boolean tick(ServerLevel level) {
            ticks++;
            Vec3 at = start.lerp(target, Math.min(1.0, ticks / 20.0));
            level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 10, 0.6, 0.6, 0.6, 0.05);
            level.sendParticles(ParticleTypes.WITCH, true, true, at.x, at.y, at.z, 8, 0.7, 0.7, 0.7, 0.02);
            level.sendParticles(ParticleTypes.FLAME, true, true, at.x, at.y, at.z, 4, 0.5, 0.5, 0.5, 0.02);
            if (ticks % 4 == 0) {
                ring(level, target, METEOR_RADIUS, ParticleTypes.WITCH);
            }
            if (ticks < 20) {
                return false;
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, target.x, target.y + 0.5, target.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, target.x, target.y + 0.5, target.z, 40, 0.5, 0.5, 0.5, 0.4);
            level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.9f);
            for (LivingEntity victim : targetsWithin(level, new AABB(target, target).inflate(METEOR_RADIUS))) {
                victim.hurtServer(level, UniversalDestroyer.this.damageSources().mobAttack(UniversalDestroyer.this), METEOR_DAMAGE);
                victim.igniteForTicks(60);
            }
            return true;
        }
    }

    /** A black hole hanging in the air: pulls everything toward it and crushes whatever gets close. */
    private final class BlackHole {
        final Vec3 center;
        int ticksLeft = BLACK_HOLE_TICKS;

        BlackHole(Vec3 center) {
            this.center = center;
        }

        boolean tick(ServerLevel level) {
            ticksLeft--;
            if (ticksLeft <= 0 || !UniversalDestroyer.this.isAlive()) {
                level.sendParticles(ParticleTypes.FLASH, true, true, center.x, center.y, center.z, 1, 0, 0, 0, 0);
                return true;
            }
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, center.x, center.y, center.z, 16, 1.0, 1.0, 1.0, 0.0);
            double spin = ticksLeft * 0.3;
            for (int i = 0; i < 20; i++) {
                double angle = spin + i * (Math.PI * 2.0 / 20.0);
                double r = 4.0 + (i % 3) * 1.2;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, center.x + Math.cos(angle) * r, center.y + (i % 2 == 0 ? 0.3 : -0.3),
                    center.z + Math.sin(angle) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
            for (LivingEntity victim : targetsWithin(level, new AABB(center, center).inflate(BLACK_HOLE_PULL_RADIUS))) {
                Vec3 toward = center.subtract(victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0));
                double distance = toward.length();
                if (distance > BLACK_HOLE_PULL_RADIUS) {
                    continue;
                }
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

    /** A Shockwave Ring spreading out along the ground. Anyone standing on the ground as it passes is hit - jump! */
    private final class Shockwave {
        final Vec3 center;
        final double maxRadius;
        int delay;
        double radius = 0.0;
        final Set<UUID> hit = new HashSet<>();

        Shockwave(Vec3 center, double maxRadius, int delay) {
            this.center = center;
            this.maxRadius = maxRadius;
            this.delay = delay;
        }

        boolean tick(ServerLevel level) {
            if (delay-- > 0) {
                return false;
            }
            radius += SHOCKWAVE_SPEED;
            int points = Math.min(160, (int) (radius * 4) + 8);
            for (int i = 0; i < points; i++) {
                double angle = i * (Math.PI * 2.0 / points);
                Vec3 at = groundAt(level, center.x + Math.cos(angle) * radius, center.z + Math.sin(angle) * radius);
                level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH, true, true, at.x, at.y + 0.3, at.z, 1, 0.05, 0.15, 0.05, 0.0);
            }
            for (LivingEntity victim : targetsWithin(level, new AABB(center, center).inflate(radius + 1.5, 8.0, radius + 1.5))) {
                double distance = victim.position().subtract(center).horizontalDistance();
                if (Math.abs(distance - radius) > 1.2 || !victim.onGround() || !hit.add(victim.getUUID())) {
                    continue;
                }
                victim.hurtServer(level, UniversalDestroyer.this.damageSources().mobAttack(UniversalDestroyer.this), SHOCKWAVE_DAMAGE);
                launch(victim, center, 0.8, 0.8);
            }
            return radius >= maxRadius;
        }
    }

    /** A giant ghostly sword: hangs in the sky over its target, then stabs straight down. */
    private final class ShadowSword {
        final Vec3 target;
        int delay;
        int ticks = 0;

        ShadowSword(Vec3 target, int delay) {
            this.target = target;
            this.delay = delay;
        }

        boolean tick(ServerLevel level) {
            if (delay-- > 0) {
                return false;
            }
            ticks++;
            int fallEnd = SHADOW_SWORD_HANG_TICKS + 6;
            if (ticks < SHADOW_SWORD_HANG_TICKS) {
                if (ticks % 2 == 0) {
                    drawSword(level, target.y + SHADOW_SWORD_HEIGHT + Mth.sin(ticks * 0.3f) * 0.5);
                }
                if (ticks % 4 == 0) {
                    ring(level, target, SHADOW_SWORD_RADIUS, ParticleTypes.WITCH);
                }
                return false;
            }
            if (ticks < fallEnd) {
                double progress = (ticks - SHADOW_SWORD_HANG_TICKS) / 6.0;
                drawSword(level, target.y + SHADOW_SWORD_HEIGHT * (1.0 - progress));
                return false;
            }
            if (ticks == fallEnd) {
                level.sendParticles(ParticleTypes.EXPLOSION, true, true, target.x, target.y + 0.5, target.z, 4, 1.5, 0.3, 1.5, 0.0);
                level.sendParticles(ParticleTypes.SQUID_INK, true, true, target.x, target.y + 0.5, target.z, 60, 2.0, 0.5, 2.0, 0.1);
                level.playSound(null, target.x, target.y, target.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 3.0f, 0.6f);
                for (LivingEntity victim : targetsWithin(level, new AABB(target, target).inflate(SHADOW_SWORD_RADIUS, 4.0, SHADOW_SWORD_RADIUS))) {
                    victim.hurtServer(level, UniversalDestroyer.this.damageSources().mobAttack(UniversalDestroyer.this), SHADOW_SWORD_DAMAGE);
                    victim.setDeltaMovement(victim.getDeltaMovement().x, 1.0, victim.getDeltaMovement().z);
                    victim.hurtMarked = true;
                }
            }
            // Stuck in the ground for a moment, fading away.
            if (ticks % 4 == 0) {
                drawSword(level, target.y - 2.0);
            }
            return ticks >= fallEnd + 20;
        }

        /** A sword of darkness pointing down, its tip at the given height. */
        private void drawSword(ServerLevel level, double tipY) {
            for (double h = 0.0; h < 12.0; h += 0.7) {
                level.sendParticles(ParticleTypes.SQUID_INK, true, true, target.x, tipY + h, target.z, 1, 0.12, 0.0, 0.12, 0.0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, target.x, tipY + h, target.z, 1, 0.2, 0.0, 0.2, 0.0);
            }
            for (double w = -2.5; w <= 2.5; w += 0.5) {
                level.sendParticles(ParticleTypes.WITCH, true, true, target.x + w, tipY + 12.0, target.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            for (double h = 12.5; h < 15.0; h += 0.6) {
                level.sendParticles(ParticleTypes.SQUID_INK, true, true, target.x, tipY + h, target.z, 1, 0.08, 0.0, 0.08, 0.0);
            }
        }
    }

    /** A player caught in the Void Chains: where they're being lifted to, and whether they've been slammed. */
    private static final class Chained {
        final ServerPlayer player;
        final Vec3 liftPoint;
        boolean slammed = false;

        Chained(ServerPlayer player, Vec3 liftPoint) {
            this.player = player;
            this.liftPoint = liftPoint;
        }
    }

    // ---------------------------------------------------------------------------
    // Death, drops, and the ending
    // ---------------------------------------------------------------------------

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.getX(), this.getY() + 8.0, this.getZ(), 12, 6.0, 8.0, 6.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, true, true, this.getX(), this.getY() + 10.0, this.getZ(), 600, 8.0, 10.0, 8.0, 0.3);
            ENDINGS.add(new Ending(level, this.position()));
            clearRamp(level, true);
        }
    }

    /** If it's killed or removed (not just unloaded with its chunk), the ramp goes with it. */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
            clearRamp(level, false);
        }
        super.remove(reason);
    }

    /** 32-64 Universal Shards, the Destroyer's Crown, the final book, and the core from its sword. (XP is xpReward.) */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.UNIVERSAL_SHARD.get(), 32 + this.random.nextInt(33)));
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.DESTROYERS_CROWN.get()));
        this.spawnAtLocation(level, createFinalBook());
        this.spawnAtLocation(level, new ItemStack(ToInfinititeAndBeyond.UNIVERSAL_CORE.get()));
    }

    /** The last page of the story. */
    private static ItemStack createFinalBook() {
        List<Filterable<Component>> pages = List.of(
            "It is over.\n\nI did not believe anyone could do it. I watched from very far away, and I still felt the"
                + " ground shake when it fell.",
            "It was here before the first star. Before the void. Before anything that could be named. It was not evil,"
                + " not the way we mean it. It simply wanted everything to be as it was before: nothing at all.",
            "The void was its prison, and the Living Void was its jailer. The Monstrosity was its guard at the door."
                + " Every lock is broken now - but there is nothing left behind them to escape.",
            "You held the shards of a universe it had already ended, and you used them to save this one."
                + "\n\nLook up tonight.",
            "The stars are coming back.\n\nOne by one, they are lighting up again - every one it put out. They remember"
                + " how to shine. So will we.",
            "Whoever you are: thank you.\n\nThe universe goes on, because of you.\n\n- the last one who looked,"
                + " the one who listened, and everyone who never knew how close it came"
        ).stream().map(text -> Filterable.passThrough((Component) Component.literal(text))).toList();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
            new WrittenBookContent(Filterable.passThrough("The Stars Return"), "???", 3, pages, true));
        return book;
    }

    /**
     * Forgets everything still in progress. Called when the server stops, so nothing keeps
     * running against a closed world after you quit to the title screen.
     */
    public static void clearAll() {
        RITUALS.clear();
        ENDINGS.clear();
    }

    /** Advances every summoning ritual and ending. Called once per server tick. */
    public static void tickRitualsAndEndings() {
        RITUALS.removeIf(Ritual::tick);
        ENDINGS.removeIf(Ending::tick);
    }

    /**
     * The universe is saved: a title for everyone, the credits in chat, the storm clearing to a
     * starry night (the stars coming back), and fireworks around every player for ten seconds.
     */
    private static final class Ending {
        private static final int DURATION = 200;
        private static final String[] CREDITS = {
            "§6§l=== TO INFINITITE AND BEYOND ===",
            "§eThe Universal Destroyer has fallen.",
            "§eThe Living Void is silent. The Monstrosity's heart is still.",
            "§bThe stars are coming back.",
            "§dCreated by §lghostviper358",
            "§7Thank you for playing.",
        };

        final ServerLevel level;
        final Vec3 where;
        int ticks = 0;

        Ending(ServerLevel level, Vec3 where) {
            this.level = level;
            this.where = where;
        }

        boolean tick() {
            ticks++;
            if (ticks == 20) {
                ServerLevel overworld = level.getServer().overworld();
                overworld.setWeatherParameters(24000, 0, false, false);
                long time = overworld.getDayTime();
                overworld.setDayTime(time - time % 24000L + 18000L); // midnight: every star in the sky
                for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 40));
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§lTHE UNIVERSE IS SAVED")));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§eThe ancient terror has been destroyed")));
                    player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
                }
            }
            int creditLine = (ticks - 40) / 25;
            if (ticks >= 40 && (ticks - 40) % 25 == 0 && creditLine < CREDITS.length) {
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(CREDITS[creditLine]), false);
            }
            if (ticks % 8 == 0) {
                for (ServerPlayer player : level.players()) {
                    launchFirework(player.position().add(level.random.nextGaussian() * 6.0, 0.0, level.random.nextGaussian() * 6.0));
                }
                launchFirework(where.add(level.random.nextGaussian() * 10.0, 0.0, level.random.nextGaussian() * 10.0));
            }
            return ticks >= DURATION;
        }

        private void launchFirework(Vec3 at) {
            int[] palette = { 0xB565F5, 0xFFD24A, 0xFFFFFF, 0xFF6BD6, 0x7FD8FF };
            IntList colors = IntList.of(palette[level.random.nextInt(palette.length)], palette[level.random.nextInt(palette.length)]);
            FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
            FireworkExplosion burst = new FireworkExplosion(shapes[level.random.nextInt(shapes.length)], colors,
                IntList.of(0xFFFFFF), true, true);
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + level.random.nextInt(2), List.of(burst)));
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
            level.addFreshEntity(new FireworkRocketEntity(level, at.x, ground + 1.0, at.z, rocket));
        }
    }

    // ---------------------------------------------------------------------------
    // Summoning (breaking a Void Seal)
    // ---------------------------------------------------------------------------

    /** Called by VoidSealBlock when a player breaks the seal. Returns false if a Destroyer is already here. */
    public static boolean startRitual(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        for (Ritual ritual : RITUALS) {
            if (ritual.level == level && ritual.center.distanceTo(center) < SUMMON_BLOCK_RADIUS) {
                return false;
            }
        }
        if (!level.getEntitiesOfClass(UniversalDestroyer.class, new AABB(center, center).inflate(SUMMON_BLOCK_RADIUS), UniversalDestroyer::isAlive).isEmpty()) {
            return false;
        }
        RITUALS.add(new Ritual(level, center));
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§4§lThe last seal shatters. Something ancient opens its eyes."), false);
        return true;
    }

    /**
     * The ground shakes, purple lightning strikes all around, the storm rolls in and the darkness
     * gathers - then the Universal Destroyer rises.
     */
    private static final class Ritual {
        final ServerLevel level;
        final Vec3 center;
        int ticks = 0;

        Ritual(ServerLevel level, Vec3 center) {
            this.level = level;
            this.center = center;
        }

        boolean tick() {
            ticks++;
            if (ticks == 1) {
                level.getServer().overworld().setWeatherParameters(0, 24000, true, true);
            }
            double progress = ticks / (double) RITUAL_TICKS;
            for (int i = 0; i < 4; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double r = level.random.nextDouble() * 12.0;
                level.sendParticles(ParticleTypes.SQUID_INK, true, true, center.x + Math.cos(angle) * r, center.y + 0.2,
                    center.z + Math.sin(angle) * r, 2, 0.2, 0.1, 0.2, 0.02);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, center.x + Math.cos(angle) * r, center.y + 0.2,
                    center.z + Math.sin(angle) * r, 2, 0.2, 0.5 + progress * 3.0, 0.2, 0.1);
            }
            if (ticks % 20 == 0) {
                level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 6.0f, 0.4f);
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(center.x + Math.cos(angle) * 10.0, center.y, center.z + Math.sin(angle) * 10.0);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
                level.sendParticles(ParticleTypes.WITCH, true, true, bolt.getX(), bolt.getY() + 4.0, bolt.getZ(), 40, 0.3, 4.0, 0.3, 0.05);
            }
            if (ticks < RITUAL_TICKS) {
                return false;
            }

            UniversalDestroyer boss = ToInfinititeAndBeyond.UNIVERSAL_DESTROYER.get().create(level, EntitySpawnReason.TRIGGERED);
            if (boss != null) {
                boss.snapTo(center.x, center.y, center.z, level.random.nextFloat() * 360.0f, 0.0f);
                level.addFreshEntity(boss);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, center.x, center.y + 2.0, center.z, 10, 6.0, 2.0, 6.0, 0.0);
            level.sendParticles(ParticleTypes.SQUID_INK, true, true, center.x, center.y + 10.0, center.z, 600, 8.0, 10.0, 8.0, 0.2);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 8.0f, 0.3f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.3f);
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§4§lTHE UNIVERSAL DESTROYER")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§cThe ancient terror is free")));
            }
            return true;
        }
    }
}
