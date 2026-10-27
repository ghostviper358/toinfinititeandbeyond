package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Universal Core - eat it to become a Universal God, permanently. See
 * docs/requirements/universal_core.md.
 *
 * Looks like a 3D End Crystal: the same swirling core, with white rings tumbling around it and
 * a pulsing golden aura (a GeckoLib model - see UniversalCoreModel / UniversalCoreRenderer). The
 * rings spin faster and faster while it's being eaten.
 *
 * Godhood is stored as a flag in the player's persistent data, so it survives relogging and
 * death (onPlayerClone copies persistent data to the respawned player). While a player is a god,
 * {@link #tick} keeps the "Universal God" status effect on them - that's how every client knows
 * who's a god - and the god powers apply:
 *   - True invincibility: nothing can hurt or kill you (not even /kill or the void), except
 *     another god - and a god killed by another god loses their godhood. Never hungry.
 *   - God form: glowing, a golden-purple aura and a halo of light.
 *   - World control: fly (super fast, and straight through blocks while flying); day/night and
 *     weather on a key.
 *   - Reality powers on keys: summon a meteor, call a lightning storm, delete a mob.
 * The keys are in UniversalCoreClientHandler and reach the server via UniversalCoreNetworking.
 */
public class UniversalCoreItem extends Item implements GeoItem {

    private static final String GOD_KEY = ToInfinititeAndBeyond.MOD_ID + "_universal_god";

    /** Gods fly three times as fast as creative flight. */
    private static final float GOD_FLYING_SPEED = 0.15f;

    /** Meteor: how far you can aim it, how long it takes to fall, and its blast. */
    private static final double METEOR_RANGE = 128.0;
    private static final int METEOR_FALL_TICKS = 20;
    private static final double METEOR_RADIUS = 6.0;
    private static final float METEOR_DAMAGE = 400.0f;
    private static final int METEOR_COOLDOWN_TICKS = 40;

    /** Lightning storm: strikes, spacing, range, extra damage per strike, and cooldown. */
    private static final int STORM_STRIKES = 12;
    private static final int STORM_STRIKE_SPACING_TICKS = 5;
    private static final double STORM_RADIUS = 24.0;
    private static final float STORM_DAMAGE = 100.0f;
    private static final int STORM_COOLDOWN_TICKS = 200;

    /** Delete: how far away you can look at a mob to delete it. */
    private static final double DELETE_RANGE = 64.0;
    private static final int DELETE_COOLDOWN_TICKS = 10;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static final List<Meteor> FALLING_METEORS = new ArrayList<>();
    private static final List<Storm> ACTIVE_STORMS = new ArrayList<>();

    public UniversalCoreItem(Properties properties) {
        super(properties);
    }

    // ===================================== Model & animation ====================================

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animation controllers: the rings' spin speeds up while the Core is being eaten, which
        // a fixed-speed animation file can't do smoothly - UniversalCoreRenderer turns them in code.
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final GeoItemRenderer<UniversalCoreItem> renderer = new UniversalCoreRenderer();

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return renderer;
            }
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ======================================== Becoming a god ===================================

    public static boolean isGod(Player player) {
        // The server goes by the saved flag; clients (which never see persistent data) go by the
        // status effect the server keeps on every god.
        return player.getPersistentData().getBooleanOr(GOD_KEY, false)
            || ToInfinititeAndBeyond.UNIVERSAL_GOD.getHolder().map(player::hasEffect).orElse(false);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return super.finishUsingItem(stack, level, entity);
        }
        if (isGod(player)) {
            // Already a god - don't waste the Core.
            player.displayClientMessage(Component.literal("§eYou are already a Universal God."), true);
            return stack;
        }
        ItemStack result = super.finishUsingItem(stack, level, entity);
        player.getPersistentData().putBoolean(GOD_KEY, true);
        ascend(serverLevel, player);
        return result;
    }

    /** The moment of ascension: a pillar of light, a shockwave of particles, and the whole server is told. */
    private static void ascend(ServerLevel level, ServerPlayer player) {
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(player.getX(), player.getY(), player.getZ());
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);

        for (double dy = 0; dy < 30.0; dy += 0.5) {
            level.sendParticles(ParticleTypes.END_ROD, true, true, player.getX(), player.getY() + dy, player.getZ(),
                2, 0.15, 0.1, 0.15, 0.0);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, true, true, player.getX(), player.getY() + 1.0, player.getZ(),
            150, 1.0, 1.5, 1.0, 0.6);
        level.sendParticles(ParticleTypes.WITCH, true, true, player.getX(), player.getY() + 1.0, player.getZ(),
            80, 2.0, 1.0, 2.0, 0.1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);

        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§6§l" + player.getName().getString() + " §d§lhas ascended and become a Universal God!"), false);
    }

    // ========================================= Every tick ======================================

    /** Server side, every tick: keep a god's status, flight, food and look up to date. */
    public static void tick(Player player, ServerLevel level) {
        if (!player.getPersistentData().getBooleanOr(GOD_KEY, false)) {
            return;
        }

        // The status effect is how clients know who's a god - put it straight back if milk or
        // anything else clears it. Glowing is part of the god form look.
        var godEffect = ToInfinititeAndBeyond.UNIVERSAL_GOD.getHolder().orElseThrow();
        if (!player.hasEffect(godEffect)) {
            player.addEffect(new MobEffectInstance(godEffect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        }
        if (!player.hasEffect(MobEffects.GLOWING)) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        }

        // Never hungry.
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);

        // God flight: always allowed, and three times as fast.
        var abilities = player.getAbilities();
        if (!abilities.mayfly || abilities.getFlyingSpeed() != GOD_FLYING_SPEED) {
            abilities.mayfly = true;
            abilities.setFlyingSpeed(GOD_FLYING_SPEED);
            player.onUpdateAbilities();
        }

        // The void can't have you either - fall out of the world and you're lifted back to the surface.
        if (player.getY() < level.getMinY() - 10) {
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                BlockPos.containing(player.getX(), 0, player.getZ()));
            double y = Math.max(surface.getY(), level.getMinY()) + 1.0;
            player.teleportTo(player.getX(), y, player.getZ());
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }

        if (player.tickCount % 2 == 0) {
            spawnGodForm(level, player);
        }
    }

    /** The god form look: a turning halo of light over your head and a golden-purple aura. */
    private static void spawnGodForm(ServerLevel level, Player player) {
        double haloY = player.getY() + player.getBbHeight() + 0.35;
        double spin = player.tickCount * 0.15;
        for (int i = 0; i < 6; i++) {
            double angle = spin + i * (Math.PI * 2.0 / 6.0);
            level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(angle) * 0.4, haloY,
                player.getZ() + Math.sin(angle) * 0.4, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(),
            1, 0.5, 0.8, 0.5, 0.0);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(),
            1, 0.5, 0.8, 0.5, 0.0);
    }

    /**
     * Noclip, on both the server and the god's own client: a god who's flying passes straight
     * through blocks. Runs from LivingTickEvent because Player.tick() switches block collision
     * back on at the very start of every tick, and this event comes just after that (but before
     * the player actually moves). The server needs it too, or it would pull the player back out
     * of the blocks.
     */
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Player player && player.getAbilities().flying && isGod(player)) {
            player.noPhysics = true;
        }
    }

    // ===================================== True invincibility ==================================

    /** Whatever hurt us, traced back to who's responsible (the shooter of an arrow, etc.). */
    private static boolean fromAnotherGod(Player victim, Entity source) {
        return source instanceof Player attacker && attacker != victim && isGod(attacker);
    }

    /**
     * Nothing can hurt a god - any damage, from anything, including /kill and the void - except
     * another god.
     *
     * @return true to cancel the damage
     */
    public static boolean onLivingAttack(LivingAttackEvent event) {
        return event.getEntity() instanceof Player player && !player.level().isClientSide() && isGod(player)
            && !fromAnotherGod(player, event.getSource().getEntity());
    }

    /**
     * A god only dies to another god - and when they do, they lose their godhood for good (they'd
     * need a new Universal Core). If a god somehow dies to anything else, they don't.
     *
     * @return true to cancel the death
     */
    public static boolean onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isGod(player)) {
            return false;
        }
        Entity killer = event.getSource().getEntity();
        if (!fromAnotherGod(player, killer)) {
            player.setHealth(player.getMaxHealth());
            return true;
        }
        dethrone(player, (Player) killer);
        return false;
    }

    /**
     * Takes godhood away from a god killed by another god. Done as they die, before respawning -
     * the respawned player is built from this one's saved data and abilities, so they come back
     * as an ordinary player.
     */
    private static void dethrone(ServerPlayer player, Player killer) {
        player.getPersistentData().remove(GOD_KEY);
        ToInfinititeAndBeyond.UNIVERSAL_GOD.getHolder().ifPresent(player::removeEffect);
        player.removeEffect(MobEffects.GLOWING);
        var abilities = player.getAbilities();
        abilities.setFlyingSpeed(0.05f); // vanilla's normal flying speed
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
        player.level().getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§c§l" + player.getName().getString() + " §7has been stripped of godhood by §6§l"
                + killer.getName().getString() + "§7!"), false);
    }

    // ======================================== Key powers =======================================

    private static boolean onCooldown(ServerPlayer player, String power, int cooldownTicks) {
        String key = player.getUUID() + "/" + power;
        long now = player.level().getGameTime();
        if (now < COOLDOWNS.getOrDefault(key, 0L)) {
            return true;
        }
        COOLDOWNS.put(key, now + cooldownTicks);
        return false;
    }

    /**
     * Who a god's powers hit: every other living thing - mobs and other players, gods included
     * (another god is the only thing that can hurt a god) - but never the caster, or players in
     * creative/spectator.
     */
    private static boolean isTarget(Entity entity, Player caster) {
        if (entity == caster || !entity.isAlive() || !(entity instanceof LivingEntity)) {
            return false;
        }
        return !(entity instanceof Player other) || (!other.isCreative() && !other.isSpectator());
    }

    /** Meteor: a blazing meteor falls out of the sky onto wherever you're looking. */
    public static void handleMeteor(ServerPlayer player) {
        if (!isGod(player) || !(player.level() instanceof ServerLevel level) || onCooldown(player, "meteor", METEOR_COOLDOWN_TICKS)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 target = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(METEOR_RANGE)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        FALLING_METEORS.add(new Meteor(level, player, target));
        level.playSound(null, target.x, target.y, target.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0F, 0.4F);
    }

    /** Lightning storm: a dozen real lightning strikes rain down on everything around you. */
    public static void handleLightningStorm(ServerPlayer player) {
        if (!isGod(player) || !(player.level() instanceof ServerLevel level) || onCooldown(player, "storm", STORM_COOLDOWN_TICKS)) {
            return;
        }
        ACTIVE_STORMS.add(new Storm(level, player));
        player.displayClientMessage(Component.literal("§e§lThe heavens obey you!"), true);
    }

    /** Delete: whatever mob you're looking at is wiped from existence (players and bosses can't be deleted). */
    public static void handleDelete(ServerPlayer player) {
        if (!isGod(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(DELETE_RANGE));
        Vec3 blocked = level.clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, blocked,
            new AABB(eye, blocked).inflate(1.0), e -> e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof ModBoss)
                && e.isAlive(), 0.3f);
        if (hit == null || onCooldown(player, "delete", DELETE_COOLDOWN_TICKS)) {
            return;
        }
        Entity victim = hit.getEntity();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
            victim.getZ(), 60, victim.getBbWidth() * 0.5, victim.getBbHeight() * 0.5, victim.getBbWidth() * 0.5, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, true, true, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
            victim.getZ(), 20, 0.2, 0.3, 0.2, 0.1);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5F, 0.5F);
        victim.discard();
    }

    /** World control: switch day and night, or (holding sneak) cycle the weather. */
    public static void handleWorldControl(ServerPlayer player, boolean weather) {
        if (!isGod(player) || onCooldown(player, "world", 10)) {
            return;
        }
        // Time and weather are shared by the whole world, and kept by the overworld.
        ServerLevel overworld = player.level().getServer().overworld();
        if (weather) {
            if (overworld.isThundering()) {
                overworld.setWeatherParameters(12000, 0, false, false);
                player.displayClientMessage(Component.literal("§bThe skies clear."), true);
            } else if (overworld.isRaining()) {
                overworld.setWeatherParameters(0, 6000, true, true);
                player.displayClientMessage(Component.literal("§5A thunderstorm rolls in!"), true);
            } else {
                overworld.setWeatherParameters(0, 6000, true, false);
                player.displayClientMessage(Component.literal("§9Rain begins to fall."), true);
            }
        } else {
            long time = overworld.getDayTime();
            boolean isDay = time % 24000L < 12000L;
            long startOfToday = time - time % 24000L;
            overworld.setDayTime(isDay ? startOfToday + 13000L : startOfToday + 24000L + 1000L);
            player.displayClientMessage(Component.literal(isDay ? "§9Night falls." : "§eThe sun rises."), true);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /**
     * Forgets everything still in progress. Called when the server stops, so nothing keeps
     * running against a closed world after you quit to the title screen.
     */
    public static void clearAll() {
        FALLING_METEORS.clear();
        ACTIVE_STORMS.clear();
    }

    /** Advances falling meteors and lightning storms. Called once per server tick. */
    public static void tickPowers() {
        FALLING_METEORS.removeIf(Meteor::tick);
        ACTIVE_STORMS.removeIf(Storm::tick);
    }

    /** One meteor streaking down from high up and off to the side, then exploding on impact. */
    private static final class Meteor {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 target;
        final Vec3 start;
        int ticks = 0;

        Meteor(ServerLevel level, ServerPlayer caster, Vec3 target) {
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.start = target.add(-15.0, 45.0, -10.0);
        }

        /** @return true once it has hit (safe to discard this instance). */
        boolean tick() {
            ticks++;
            Vec3 at = start.lerp(target, Math.min(1.0, ticks / (double) METEOR_FALL_TICKS));
            level.sendParticles(ParticleTypes.FLAME, true, true, at.x, at.y, at.z, 20, 0.6, 0.6, 0.6, 0.05);
            level.sendParticles(ParticleTypes.LAVA, true, true, at.x, at.y, at.z, 3, 0.4, 0.4, 0.4, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y, at.z, 5, 0.5, 0.5, 0.5, 0.02);
            if (ticks < METEOR_FALL_TICKS) {
                return false;
            }

            // A real explosion for the boom and the knockback - but it never breaks blocks.
            level.explode(caster, target.x, target.y, target.z, 5.0f, Level.ExplosionInteraction.NONE);
            for (Entity entity : level.getEntities(caster, new AABB(target, target).inflate(METEOR_RADIUS),
                    e -> isTarget(e, caster) && e.position().distanceTo(target) <= METEOR_RADIUS)) {
                LivingEntity victim = (LivingEntity) entity;
                victim.hurtServer(level, level.damageSources().playerAttack(caster), METEOR_DAMAGE);
                victim.igniteForTicks(160);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, target.x, target.y, target.z, 2, 1.0, 0.5, 1.0, 0.0);
            level.sendParticles(ParticleTypes.FLAME, true, true, target.x, target.y + 0.5, target.z, 150, 3.0, 1.0, 3.0, 0.15);
            return true;
        }
    }

    /** A lightning storm: strikes one target (or random spot) near the caster every few ticks. */
    private static final class Storm {
        final ServerLevel level;
        final ServerPlayer caster;
        int strikesLeft = STORM_STRIKES;
        int ticks = 0;

        Storm(ServerLevel level, ServerPlayer caster) {
            this.level = level;
            this.caster = caster;
        }

        /** @return true once every strike has landed (safe to discard this instance). */
        boolean tick() {
            if (!caster.isAlive() || caster.level() != level) {
                return true;
            }
            if (ticks++ % STORM_STRIKE_SPACING_TICKS != 0) {
                return false;
            }

            List<Entity> targets = level.getEntities(caster, caster.getBoundingBox().inflate(STORM_RADIUS),
                e -> isTarget(e, caster));
            Vec3 strikeAt;
            LivingEntity victim = null;
            if (!targets.isEmpty()) {
                victim = (LivingEntity) targets.get(level.random.nextInt(targets.size()));
                strikeAt = victim.position();
            } else {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double distance = 4.0 + level.random.nextDouble() * (STORM_RADIUS - 4.0);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    BlockPos.containing(caster.getX() + Math.cos(angle) * distance, 0, caster.getZ() + Math.sin(angle) * distance));
                strikeAt = Vec3.atBottomCenterOf(ground);
            }

            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            bolt.setPos(strikeAt.x, strikeAt.y, strikeAt.z);
            bolt.setCause(caster);
            level.addFreshEntity(bolt);
            if (victim != null) {
                victim.invulnerableTime = 0;
                victim.hurtServer(level, level.damageSources().playerAttack(caster), STORM_DAMAGE);
            }

            strikesLeft--;
            return strikesLeft <= 0;
        }
    }
}
