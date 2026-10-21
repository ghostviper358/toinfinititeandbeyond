package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Universal Sword - the ultimate weapon of the mod, crafted from Universal Shards.
 * See docs/requirements/universal_shard_and_cosmic_apple.md ("Universal Sword (Item)").
 *
 * Combat stats:
 * - 999 attack damage (via an ATTACK_DAMAGE AttributeModifier registered in
 *   ToInfinititeAndBeyond.java - see UNIVERSAL_SWORD).
 * - Unbreakable: never loses durability. Implemented with vanilla's own
 *   {@code minecraft:unbreakable} data component (also applied in the
 *   registration in ToInfinititeAndBeyond.java) rather than overriding
 *   damageItem()/hurtEnemy()'s durability handling - it's the idiomatic
 *   1.21.x mechanism (the same one /give ... {Unbreakable:1b} used pre-1.20.5)
 *   and ItemStack.isDamageableItem()/hurtAndBreak() both respect it directly,
 *   so there's no custom durability logic to keep in sync here.
 *
 * On every successful hit (all apply together, no hit-count gating needed since
 * the spec calls for these on every hit rather than periodically):
 *   1. Cosmic Blast - a modest chance to instantly destroy the target outright
 *      in a burst of purple energy.
 *   2. Cosmic Drain - heals the wielder (life steal).
 *   3. Cosmic Fling - teleports the target away in a flash of cosmic energy.
 *   4. Poison, Blindness, Wither, and Weakness, all applied simultaneously.
 *   5. Purple particle burst on the target.
 *
 * Charge-up ultimate (see docs/requirements/universal_sword_shockwave.md, "Part 2: The
 * Shockwave Ability" - triggered by holding the attack key, not right-click):
 * Holding the attack button while wielding this sword charges up for
 * {@link #CHARGE_DURATION_TICKS}. Once fully charged, five expanding rings of purple/fire
 * energy sweep outward from the wielder (see {@link ShockwaveState}). Everything the rings
 * touch other than the wielder (who is always 100% immune) is affected: non-player entities
 * are destroyed outright, and players (including friendly players/teammates - friendly fire is
 * intentional, see the requirements doc) are lifted 1 block up and lose all control of their
 * character for {@link #FLOAT_LOCK_DURATION_TICKS}. The blast is also accompanied by a real,
 * temporary thunderstorm. There is currently no cooldown on this ability.
 */
public class UniversalSwordItem extends Item implements GeoItem {

    /**
     * GeckoLib's per-stack animation state cache. See {@link UniversalSwordModel} for where the
     * actual model/texture/animation files live, and {@link #createGeoRenderer} below for how
     * the renderer itself gets hooked up (GeckoLib's equivalent of Forge's
     * IClientItemExtensions.getCustomRenderer()).
     */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Chance per hit that the target is instantly destroyed outright. */
    private static final float INSTANT_KILL_CHANCE = 0.12f; // 12%

    /** Flat health restored to the wielder per hit ("Cosmic Drain" life steal). */
    private static final float LIFE_STEAL_AMOUNT = 10.0f; // 5 hearts

    /** How far the target is flung away from the wielder, in blocks. */
    private static final double FLING_DISTANCE = 5.0;

    /**
     * How long (in ticks) the attack button must be held before the shockwave ultimate fires.
     * ~2.5 seconds - meant to feel like a deliberate, weighty "ultimate move," not instant.
     * Named constant so it's easy to retune for feel later (see requirements doc, "Charge time
     * before firing").
     */
    public static final int CHARGE_DURATION_TICKS = 50; // 2.5 seconds

    /** How many expanding rings the shockwave releases. */
    private static final int RING_COUNT = 5;

    /** Gap, in ticks, between when each successive ring starts expanding. */
    private static final int RING_STAGGER_TICKS = 5;

    /**
     * How fast each ring's radius grows, in blocks per tick. Deliberately slow (2.5 seconds to
     * cross the full MAX_RING_RADIUS) so the orb is actually visible as a shape expanding
     * outward rather than flashing past before it can be seen.
     */
    private static final double RING_EXPAND_SPEED = 2.0;

    /**
     * The actual radius (in blocks) a ring stops expanding at. This is intentionally much
     * smaller than {@link #STATED_RANGE_BLOCKS} - see that constant's javadoc for why. Also kept
     * small (rather than, say, 400) so the fixed particle budget in
     * {@link #spawnShockwaveOrb} stays dense enough to read as a solid translucent orb instead of
     * thin scattered points.
     */
    private static final double MAX_RING_RADIUS = 48.0;

    /**
     * The ability's documented/flavor "range" per the requirements doc - 10,000 blocks.
     * Design note carried over from the doc: Minecraft only ever actively simulates roughly a
     * few hundred blocks around a player, so in practice a 10,000 block range behaves
     * identically to "everything currently loaded around the player" - nothing that far away is
     * ever actually loaded/ticking to be hit. This constant is used for the entity-gathering
     * query (harmless to make huge, since Minecraft's entity lookup is already bounded by
     * loaded chunks either way), while {@link #MAX_RING_RADIUS} caps how far the *visual sweep*
     * itself travels so the ring animation finishes in a reasonable ~2-3 seconds instead of
     * trying to visually crawl out to 10,000 blocks.
     */
    private static final double STATED_RANGE_BLOCKS = 10_000.0;

    /** Real damage dealt to each non-player entity the orb's leading edge sweeps over. */
    private static final float SHOCKWAVE_DAMAGE = 999.0f;

    /** How long a player caught in the blast floats, unable to control their character. */
    private static final int FLOAT_LOCK_DURATION_TICKS = 20 * 10; // 10 seconds

    /**
     * Safety-net duration for the storm while charging - set generously long since the real
     * end of the storm is driven by {@link #handleStopCharge}, not this timer expiring. Only
     * matters if a stop signal is somehow lost (e.g. a disconnect mid-charge).
     */
    private static final int CHARGE_STORM_SAFETY_DURATION_TICKS = 20 * 60 * 10; // 10 minutes

    /** How long it takes the sky to clear back to normal once charging stops. */
    private static final int CLEAR_WEATHER_DURATION_TICKS = 20 * 20; // 20 seconds

    /** Minimum/maximum ticks between random purple lightning strikes while charging. */
    private static final int LIGHTNING_MIN_COOLDOWN_TICKS = 8;
    private static final int LIGHTNING_COOLDOWN_JITTER_TICKS = 14;

    /** How close to/far from the charging player a random lightning strike can land. */
    private static final double LIGHTNING_MIN_DISTANCE = 2.0;
    private static final double LIGHTNING_MAX_DISTANCE = 14.0;

    /** Radius around a lightning strike's impact point that gets instantly destroyed. */
    private static final double LIGHTNING_KILL_RADIUS = 3.0;

    /** Root key under a player's persistent (survives-relog) NBT data for this ability's state. */
    private static final String PERSISTENT_ROOT_KEY = ToInfinititeAndBeyond.MOD_ID + "_universal_sword";
    private static final String FLOAT_TICKS_KEY = "float_ticks_remaining";
    private static final String FLOAT_X_KEY = "float_x";
    private static final String FLOAT_Y_KEY = "float_y";
    private static final String FLOAT_Z_KEY = "float_z";

    /** Shockwaves currently mid-flight, ticked once per server tick - see {@link #tickActiveShockwaves()}. */
    private static final List<ShockwaveState> ACTIVE_SHOCKWAVES = new ArrayList<>();

    /**
     * Players currently charging the ultimate (holding attack while wielding this sword),
     * mapped to the number of ticks left before their next random purple lightning strike - see
     * {@link #handleStartCharge}/{@link #handleStopCharge}/{@link #tickActiveChargers()}. The
     * storm persists for as long as this map is non-empty in a given level, and clears the
     * instant it empties out.
     */
    private static final Map<ServerPlayer, Integer> ACTIVE_CHARGERS = new HashMap<>();

    private static final Random RANDOM = new Random();

    public UniversalSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animations registered yet - this is step 1 of the GeckoLib build-out, just proving
        // a real 3D model renders in-hand. The overhead charge pose and attack swings (see
        // docs/requirements/universal_sword_shockwave.md, "Part 1: The Animation") get wired up
        // here in later steps.
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final GeoItemRenderer<UniversalSwordItem> renderer = new GeoItemRenderer<>(new UniversalSwordModel());

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return renderer;
            }
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Same shimmering enchant-glint used by the Universal Shard/Cosmic Apple.
        return true;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ServerLevel serverLevel = target.level() instanceof ServerLevel level ? level : null;

        // 1. Poison II for 8 seconds (160 ticks)
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 1));

        // 2. Blindness for 5 seconds (100 ticks)
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));

        // 3. Wither II for 6 seconds (120 ticks)
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));

        // 4. Weakness II for 10 seconds (200 ticks)
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));

        // 5. Cosmic Drain - heal the wielder
        attacker.heal(LIFE_STEAL_AMOUNT);

        // 6. Cosmic Fling - teleport the target away from the wielder (inverted Void Pull)
        if (serverLevel != null) {
            flingTarget(serverLevel, attacker, target);
        }

        // 7. Purple particle burst on the target, on every hit
        if (serverLevel != null) {
            spawnHitParticles(serverLevel, target);
        }

        // 8. Cosmic Blast - modest chance to instantly destroy the target
        if (serverLevel != null && target.isAlive() && RANDOM.nextFloat() < INSTANT_KILL_CHANCE) {
            spawnCosmicBlastParticles(serverLevel, target);
            target.kill(serverLevel);
            if (attacker instanceof Player player) {
                player.displayClientMessage(Component.literal("§dThe cosmos unravels your foe..."), true);
            }
        }

        super.hurtEnemy(stack, target, attacker);
    }

    /**
     * Teleports the target directly away from the attacker by FLING_DISTANCE blocks -
     * the mirror image of VoidSwordItem's Void Pull (which pulls the target toward the
     * attacker). Reusing teleportTo (rather than LivingEntity#knockback) keeps the
     * direction unambiguous and sidesteps knockback-resistance entirely, matching the
     * "flashy cosmic displacement" flavor of the effect.
     */
    private void flingTarget(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double dirX = dist > 0.01 ? dx / dist : 1.0;
        double dirZ = dist > 0.01 ? dz / dist : 0.0;

        target.teleportTo(
            target.getX() + dirX * FLING_DISTANCE,
            target.getY() + 0.5,
            target.getZ() + dirZ * FLING_DISTANCE
        );
    }

    /** Purple particle burst applied on every hit. */
    private static void spawnHitParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.PORTAL,
            target.getX(), target.getY() + 1.0, target.getZ(),
            20, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.WITCH,
            target.getX(), target.getY() + 1.0, target.getZ(),
            10, 0.3, 0.5, 0.3, 0.02);
    }

    /** Bigger, more dramatic purple burst for the instant-destroy ("Cosmic Blast") effect. */
    private static void spawnCosmicBlastParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.PORTAL,
            target.getX(), target.getY() + 1.0, target.getZ(),
            80, 0.6, 0.9, 0.6, 0.15);
        level.sendParticles(ParticleTypes.WITCH,
            target.getX(), target.getY() + 1.0, target.getZ(),
            50, 0.5, 0.8, 0.5, 0.1);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            target.getX(), target.getY() + 1.0, target.getZ(),
            30, 0.4, 0.7, 0.4, 0.02);
    }

    // =========================================================================================
    // Charge-up shockwave ultimate (Part 2 of the requirements doc).
    // Trigger flow: UniversalSwordClientHandler (client tick) tracks the held attack key and,
    // once charged, sends UniversalSwordNetworking.TriggerShockwavePacket to the server, which
    // is handled by handleShockwaveTrigger() below.
    // =========================================================================================

    /**
     * Server-side entry point for the charge-up ultimate, called from the network packet
     * handler once a client reports its charge as complete.
     */
    public static void handleShockwaveTrigger(ServerPlayer player) {
        // Defensive re-check: ignore stale/spoofed triggers from a client that no longer
        // actually has the Universal Sword equipped (e.g. switched items mid-charge).
        if (!(player.getMainHandItem().getItem() instanceof UniversalSwordItem)) {
            return;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            startShockwave(serverLevel, player);
        }
    }

    /**
     * Server-side entry point for when a client starts charging (holding attack while wielding
     * this sword) - kicks off the storm immediately and registers the player so
     * {@link #tickActiveChargers()} starts raining down purple lightning near them.
     */
    public static void handleStartCharge(ServerPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof UniversalSwordItem)) {
            return;
        }
        ACTIVE_CHARGERS.put(player, LIGHTNING_MIN_COOLDOWN_TICKS + RANDOM.nextInt(LIGHTNING_COOLDOWN_JITTER_TICKS));
        if (player.level() instanceof ServerLevel level) {
            level.setWeatherParameters(0, CHARGE_STORM_SAFETY_DURATION_TICKS, true, true);
        }
    }

    /**
     * Server-side entry point for when a client stops charging (released attack, switched away
     * from the sword, or the charge just fired and they let go) - clears the storm immediately,
     * unless someone else in the same level is still charging.
     */
    public static void handleStopCharge(ServerPlayer player) {
        ACTIVE_CHARGERS.remove(player);
        if (player.level() instanceof ServerLevel level && !anyoneStillChargingIn(level)) {
            level.setWeatherParameters(CLEAR_WEATHER_DURATION_TICKS, 0, false, false);
        }
    }

    private static boolean anyoneStillChargingIn(ServerLevel level) {
        for (ServerPlayer charger : ACTIVE_CHARGERS.keySet()) {
            if (charger.level() == level) {
                return true;
            }
        }
        return false;
    }

    /**
     * Advances every currently-charging player by one tick, called once per server tick (see
     * ToInfinititeAndBeyond.onServerTick). Each charger independently counts down to their next
     * random purple lightning strike (see {@link #strikePurpleLightning}); a charger who's
     * stopped holding attack, died, or put the sword away gets cleaned up here as a safety net in
     * case their stop packet never arrived (e.g. a disconnect mid-charge).
     */
    public static void tickActiveChargers() {
        if (ACTIVE_CHARGERS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<ServerPlayer, Integer>> iterator = ACTIVE_CHARGERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ServerPlayer, Integer> entry = iterator.next();
            ServerPlayer player = entry.getKey();
            if (!player.isAlive() || !(player.getMainHandItem().getItem() instanceof UniversalSwordItem)
                    || !(player.level() instanceof ServerLevel level)) {
                iterator.remove();
                continue;
            }

            int cooldown = entry.getValue() - 1;
            if (cooldown <= 0) {
                strikePurpleLightning(level, player);
                cooldown = LIGHTNING_MIN_COOLDOWN_TICKS + RANDOM.nextInt(LIGHTNING_COOLDOWN_JITTER_TICKS);
            }
            entry.setValue(cooldown);
        }
    }

    /**
     * A single purple-tinted lightning strike landing at a random spot near the charging player
     * - a real (visual-only) LightningBolt entity for the authentic flash/thunderclap, a column
     * of cosmic particles to tint it purple instead of vanilla's white/yellow, and an
     * instant-kill explosion burst at the impact point. The wielder themselves is never a valid
     * strike target (excluded below), matching the shockwave's own "never hurts its caster" rule.
     */
    private static void strikePurpleLightning(ServerLevel level, ServerPlayer caster) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double distance = LIGHTNING_MIN_DISTANCE + RANDOM.nextDouble() * (LIGHTNING_MAX_DISTANCE - LIGHTNING_MIN_DISTANCE);
        double x = caster.getX() + Math.cos(angle) * distance;
        double z = caster.getZ() + Math.sin(angle) * distance;
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            BlockPos.containing(x, caster.getY(), z));
        double y = surface.getY();

        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(x, y, z);
        bolt.setVisualOnly(true); // we apply our own kill/explosion below, purely for the flash + thunderclap
        level.addFreshEntity(bolt);

        // Purple tint: a column of cosmic particles from overhead down to the ground, since the
        // real LightningBolt's flash itself always renders vanilla white/yellow.
        for (double dy = 0; dy < 12.0; dy += 1.0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + dy, z, 3, 0.15, 0.3, 0.15, 0.02);
        }
        level.sendParticles(ParticleTypes.PORTAL, x, y + 0.2, z, 40, 0.5, 0.3, 0.5, 0.1);

        // Instant-kill explosion at the impact point - visual + sound only, no block damage.
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 1.2F);

        AABB killBox = new AABB(x, y, z, x, y, z).inflate(LIGHTNING_KILL_RADIUS);
        UUID casterId = caster.getUUID();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, killBox,
                e -> e.isAlive() && !e.getUUID().equals(casterId))) {
            spawnCosmicBlastParticles(level, victim);
            victim.kill(level);
        }
    }

    /** Kicks off a new shockwave: announces it, changes the weather, and queues the rings. */
    private static void startShockwave(ServerLevel level, ServerPlayer caster) {
        caster.displayClientMessage(
            Component.literal("§d§lThe cosmos answers your call..."), true);

        triggerStorm(level, caster);
        ACTIVE_SHOCKWAVES.add(new ShockwaveState(level, caster));
    }

    /**
     * A dramatic overhead cloud/lightning burst marking the exact moment the shockwave fires.
     * The actual storm itself is already running by this point - it started the moment the
     * player began charging (see {@link #handleStartCharge}) and will clear the moment they stop
     * holding attack (see {@link #handleStopCharge}) - this is just an extra punctuation flourish
     * timed to the blast itself.
     */
    private static void triggerStorm(ServerLevel level, ServerPlayer caster) {
        double x = caster.getX();
        double y = caster.getY() + 3.0;
        double z = caster.getZ();
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 60, 1.5, 0.6, 1.5, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 30, 1.2, 0.4, 1.2, 0.01);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 1.5, 1.0, 1.5, 0.15);
        level.playSound(null, x, caster.getY(), z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0F, 1.0F);
    }

    /** Advances every in-flight shockwave by one tick. Called once per server tick. */
    public static void tickActiveShockwaves() {
        if (ACTIVE_SHOCKWAVES.isEmpty()) {
            return;
        }
        Iterator<ShockwaveState> iterator = ACTIVE_SHOCKWAVES.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    /** How much smaller the companion accent ring is than the main orb, as a fraction of its radius. */
    private static final double COMPANION_RING_RATIO = 0.6;

    /**
     * Renders the shockwave as a single translucent orb (two concentric Fibonacci-sphere point
     * clouds - an outer shell and a slightly inset inner shell, for a "glassy ball" sense of
     * thickness instead of a razor-thin surface) plus a smaller, tilted companion ring orbiting
     * it for visual flair. The orb's radius is always exactly {@code radius} - the same value
     * {@link ShockwaveState#applyRing} uses to decide what just got hit - so the visible orb
     * genuinely *is* the danger zone: nothing dies until the orb you can see has actually swept
     * over it. Point density scales with the sphere's actual surface area (radius^2) so it reads
     * as solid at any size, and only thins out in the final stretch before maxRadius so the wave
     * visibly fades out right as it dies off rather than looking sparse the whole time.
     */
    private static void spawnShockwaveOrb(ServerLevel level, double cx, double cy, double cz, double radius, double maxRadius) {
        if (radius <= 1.0) {
            return;
        }
        double progress = radius / maxRadius;
        // Stays fully solid (1.0) until 70% of the way out, then eases down to 0 right at the
        // edge - the "fade out" only happens at the very end, not across the whole flight.
        double fade = progress < 0.7 ? 1.0 : Math.max(0.0, 1.0 - (progress - 0.7) / 0.3);
        if (fade <= 0.0) {
            return;
        }

        int basePoints = (int) Math.max(30, Math.min(700, radius * radius * 0.28 * fade));
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

        // Outer shell at the orb's true radius, inner shell pulled slightly inward - two layers
        // read as a ball with depth rather than a single hollow bubble.
        double[] shellRadii = { radius, radius * 0.9 };
        for (double shellRadius : shellRadii) {
            for (int i = 0; i < basePoints; i++) {
                double t = (i + 0.5) / basePoints;
                double polar = Math.acos(1.0 - 2.0 * t);
                double azimuth = goldenAngle * i;

                double px = cx + shellRadius * Math.sin(polar) * Math.cos(azimuth);
                double py = cy + shellRadius * Math.cos(polar);
                double pz = cz + shellRadius * Math.sin(polar) * Math.sin(azimuth);

                level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, py, pz, 1, 0.1, 0.1, 0.1, 0.0);
                if (i % 3 == 0) {
                    level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 2, 0.12, 0.12, 0.12, 0.01);
                }
                if (i % 9 == 0) {
                    level.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        }

        // Smaller companion ring, flat and level (not tilted - a tilted ring dips half its
        // points below the ground/the player's own eye line, hiding them), circling the orb's
        // equator as a distinct accent ring rather than part of the sphere surface itself.
        double smallRadius = radius * COMPANION_RING_RATIO;
        int ringPoints = (int) Math.max(12, Math.min(96, smallRadius * 1.5 * fade));
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2.0 * Math.PI * i) / ringPoints;
            double px = cx + smallRadius * Math.cos(angle);
            double pz = cz + smallRadius * Math.sin(angle);
            level.sendParticles(ParticleTypes.END_ROD, px, cy, pz, 1, 0.05, 0.05, 0.05, 0.0);
        }

        // Big expanding/fading shockwave disc (the same particle the Warden's sonic attack
        // uses) right as the orb starts out, for an extra punch of "a wave just fired".
        if (radius <= RING_EXPAND_SPEED * 1.5) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, cx, cy + 1.0, cz, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Applies the blast's effect to a single entity caught in the orb's leading edge (never
     * called for the wielder - see ShockwaveState, which excludes them at the snapshot stage).
     * Non-player entities (hostile mobs, neutral/passive mobs, tamed pets, villagers) take
     * {@link #SHOCKWAVE_DAMAGE} real damage - same "sonic boom" attributed damage source Void
     * Boots' own AoE already uses - rather than a hard kill(), so armor/resistance/Totems of
     * Undying still apply like any other hit; it's only a guaranteed kill in practice because the
     * number is so enormous. Players (any player other than the wielder, friend or foe alike) are
     * floated and lose control instead, since an instantly-destroyed player couldn't also float
     * afterward - see the requirements doc's "Reconciliation" resolution.
     */
    private static void applyBlastEffect(ServerLevel level, LivingEntity entity, ServerPlayer caster) {
        if (entity instanceof Player player) {
            applyFloatLock(player);
        } else {
            spawnCosmicBlastParticles(level, entity);
            entity.hurt(level.damageSources().sonicBoom(caster), SHOCKWAVE_DAMAGE);
        }
    }

    /**
     * Lifts a player 1 block up and locks them in place (no control) for
     * FLOAT_LOCK_DURATION_TICKS. State is stored in the player's persistent NBT data (same
     * pattern as CosmicAppleItem's max-health stacking) rather than a static map, so it
     * naturally survives relogs and doesn't leak per-player entries.
     */
    private static void applyFloatLock(Player player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag modData = persistent.getCompoundOrEmpty(PERSISTENT_ROOT_KEY);

        // If a player is caught by more than one ring in the same blast, keep the spot they
        // were already frozen at rather than re-measuring from it (which would be a no-op
        // anyway, since tickFloatLock() holds them there every tick) - just refresh the timer.
        boolean alreadyFloating = modData.getIntOr(FLOAT_TICKS_KEY, 0) > 0;
        double floatX = alreadyFloating ? modData.getDoubleOr(FLOAT_X_KEY, player.getX()) : player.getX();
        double floatY = alreadyFloating ? modData.getDoubleOr(FLOAT_Y_KEY, player.getY() + 1.0) : player.getY() + 1.0;
        double floatZ = alreadyFloating ? modData.getDoubleOr(FLOAT_Z_KEY, player.getZ()) : player.getZ();

        modData.putInt(FLOAT_TICKS_KEY, FLOAT_LOCK_DURATION_TICKS);
        modData.putDouble(FLOAT_X_KEY, floatX);
        modData.putDouble(FLOAT_Y_KEY, floatY);
        modData.putDouble(FLOAT_Z_KEY, floatZ);
        persistent.put(PERSISTENT_ROOT_KEY, modData);

        player.teleportTo(floatX, floatY, floatZ);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0;

        if (!alreadyFloating && player.level() instanceof ServerLevel serverLevel) {
            spawnHitParticles(serverLevel, player);
        }
    }

    /**
     * Ticks a player's float-lock, if they have one active. Called every server tick for every
     * player (see ToInfinititeAndBeyond.onPlayerTick) - this is what actually enforces "loses
     * all control": every single tick for the full duration, any position/velocity change the
     * player attempted is forcibly overridden back to the frozen spot.
     */
    public static void tickFloatLock(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(PERSISTENT_ROOT_KEY)) {
            return;
        }

        CompoundTag modData = persistent.getCompoundOrEmpty(PERSISTENT_ROOT_KEY);
        int ticksRemaining = modData.getIntOr(FLOAT_TICKS_KEY, 0);
        if (ticksRemaining <= 0) {
            persistent.remove(PERSISTENT_ROOT_KEY);
            return;
        }

        double floatX = modData.getDoubleOr(FLOAT_X_KEY, player.getX());
        double floatY = modData.getDoubleOr(FLOAT_Y_KEY, player.getY());
        double floatZ = modData.getDoubleOr(FLOAT_Z_KEY, player.getZ());

        player.teleportTo(floatX, floatY, floatZ);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0;

        ticksRemaining--;
        if (ticksRemaining <= 0) {
            persistent.remove(PERSISTENT_ROOT_KEY);
        } else {
            modData.putInt(FLOAT_TICKS_KEY, ticksRemaining);
            persistent.put(PERSISTENT_ROOT_KEY, modData);
        }
    }

    /**
     * The five expanding rings of a single shockwave cast. Each ring starts RING_STAGGER_TICKS
     * after the previous one and expands outward at RING_EXPAND_SPEED blocks/tick until it
     * passes MAX_RING_RADIUS, catching entities in the shell it sweeps over each tick - so the
     * whole sequence reads as pulses of expanding devastation rather than one static AoE.
     *
     * The candidate entity list is captured once, at cast time, via a single large-AABB query
     * (see STATED_RANGE_BLOCKS) rather than re-querying the world every tick - simpler and
     * cheaper, and the wielder is excluded right here so they can never be affected by their
     * own blast, at any distance, for the shockwave's entire lifetime.
     */
    private static final class ShockwaveState {
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final List<LivingEntity> candidates;
        private final double[] previousRadius = new double[RING_COUNT];
        private int ticksElapsed = 0;

        ShockwaveState(ServerLevel level, ServerPlayer caster) {
            this.level = level;
            this.caster = caster;
            this.centerX = caster.getX();
            this.centerY = caster.getY();
            this.centerZ = caster.getZ();

            UUID casterId = caster.getUUID();
            AABB huge = new AABB(centerX, centerY, centerZ, centerX, centerY, centerZ).inflate(STATED_RANGE_BLOCKS);
            this.candidates = level.getEntitiesOfClass(LivingEntity.class, huge,
                entity -> entity.isAlive() && !entity.getUUID().equals(casterId));
        }

        /**
         * Advances the simulation by one tick.
         * @return true once every ring has finished expanding (safe to discard this instance).
         */
        boolean tick() {
            ticksElapsed++;
            boolean allFinished = true;
            Vec3 center = new Vec3(centerX, centerY, centerZ);

            for (int ring = 0; ring < RING_COUNT; ring++) {
                int startTick = ring * RING_STAGGER_TICKS;
                if (ticksElapsed < startTick) {
                    allFinished = false;
                    continue;
                }

                double currentRadius = (ticksElapsed - startTick) * RING_EXPAND_SPEED;
                if (currentRadius > MAX_RING_RADIUS) {
                    continue; // this ring has already finished expanding
                }

                allFinished = false;
                applyRing(center, previousRadius[ring], currentRadius);
                previousRadius[ring] = currentRadius;
            }

            return allFinished;
        }

        /** Applies the blast effect to any candidate in the shell this ring just swept over. */
        private void applyRing(Vec3 center, double innerRadius, double outerRadius) {
            spawnShockwaveOrb(level, centerX, centerY, centerZ, outerRadius, MAX_RING_RADIUS);

            for (LivingEntity entity : candidates) {
                if (!entity.isAlive()) {
                    continue;
                }
                double distance = entity.position().distanceTo(center);
                if (distance >= innerRadius && distance <= outerRadius) {
                    applyBlastEffect(level, entity, caster);
                }
            }
        }
    }
}
