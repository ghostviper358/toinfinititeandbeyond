package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Universal Leggings - the second piece of the Universal armor set, crafted from Universal
 * Shards. See docs/requirements/universal_leggings.md.
 *
 * Defensive stats (Armor 999, Toughness 6, +40 max health stacking on top of the chestplate's,
 * 40% knockback resistance, +50% movement speed, unbreakable) are applied via attribute
 * modifiers / the vanilla {@code minecraft:unbreakable} data component registered in
 * ToInfinititeAndBeyond.java (see UNIVERSAL_LEGGINGS), same as UNIVERSAL_CHESTPLATE.
 *
 * Abilities:
 *   1. Star Speed   - the +50% speed above, plus a trail of stars and purple sparkles.
 *   2. Warp Dash    - double-tap sneak to teleport ~12 blocks the way you're looking, hitting
 *                     everything you pass through (2 second cooldown).
 *   3. Cosmic Phase - 30% chance to phase out and dodge any attack from a mob or player.
 *   4. Star Jumps   - 5 extra jumps in mid-air, refilled on landing.
 *   5. Every good potion effect, permanently while worn (see GOOD_EFFECTS).
 *
 * Only the client knows when keys are pressed, so Warp Dash and Star Jumps are detected by
 * UniversalLeggingsClientHandler and sent here via UniversalLeggingsNetworking. Cosmic Phase is
 * damage-event-driven (handleLeggingsHurt, from onLivingHurt in the main mod class); the effects
 * and the trail are tick-driven (tick(), from onPlayerTick) - same split as the chestplate.
 */
public class UniversalLeggingsItem extends Item {

    /** Cosmic Phase: chance per incoming attack (from a mob or player) to dodge it completely. */
    private static final float PHASE_CHANCE = 0.30f; // 30%

    /** How far Warp Dash teleports, in blocks (less if something solid is in the way). */
    private static final double WARP_DISTANCE = 12.0;

    /** Damage dealt to everything Warp Dash passes through. */
    private static final float WARP_DAMAGE = 200.0f;

    private static final int WARP_COOLDOWN_TICKS = 40; // 2 seconds

    /** How many extra jumps you get in mid-air before you have to land again. */
    public static final int EXTRA_JUMPS = 5;

    /**
     * Every good (beneficial) effect, with its level (0 = level I). Left out on purpose:
     * Invisibility (it would hide the wearer, and the set's whole look is glowing cosmic
     * particles), Slow Falling (it would cancel out the extra jumps), and Health Boost (the
     * leggings' extra hearts already come from their max-health bonus). Instant effects like
     * Instant Health aren't lasting effects, so they don't apply either.
     */
    private static final List<EffectLevel> GOOD_EFFECTS = List.of(
        new EffectLevel(MobEffects.SPEED, 1),
        new EffectLevel(MobEffects.HASTE, 2),
        new EffectLevel(MobEffects.STRENGTH, 2),
        new EffectLevel(MobEffects.JUMP_BOOST, 1),
        new EffectLevel(MobEffects.REGENERATION, 1),
        new EffectLevel(MobEffects.RESISTANCE, 1),
        new EffectLevel(MobEffects.FIRE_RESISTANCE, 0),
        new EffectLevel(MobEffects.WATER_BREATHING, 0),
        new EffectLevel(MobEffects.NIGHT_VISION, 0),
        new EffectLevel(MobEffects.SATURATION, 0),
        new EffectLevel(MobEffects.LUCK, 0),
        new EffectLevel(MobEffects.CONDUIT_POWER, 0),
        new EffectLevel(MobEffects.DOLPHINS_GRACE, 0),
        new EffectLevel(MobEffects.HERO_OF_THE_VILLAGE, 0),
        new EffectLevel(MobEffects.ABSORPTION, 1)
    );

    /**
     * Effects are re-applied before they drop below this many ticks. Kept above 200 because Night
     * Vision starts flashing the screen in its last 10 seconds.
     */
    private static final int EFFECT_REFRESH_BELOW_TICKS = 220;
    private static final int EFFECT_DURATION_TICKS = 400;

    /** Game time each player can next Warp Dash at. */
    private static final Map<UUID, Long> WARP_READY_AT = new HashMap<>();

    /** Extra jumps each player has used since they last stood on the ground. */
    private static final Map<UUID, Integer> EXTRA_JUMPS_USED = new HashMap<>();

    private record EffectLevel(Holder<MobEffect> effect, int amplifier) {
    }

    public UniversalLeggingsItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof UniversalLeggingsItem;
    }

    /**
     * Cosmic Phase. Called from LivingHurtEvent (onLivingHurt) in the main mod class whenever the
     * WEARER takes damage from an attacker (a mob, a player, or something they shot).
     *
     * @return true to cancel the event (the attack was dodged)
     */
    public static boolean handleLeggingsHurt(Player player, ServerLevel level, LivingEntity attacker) {
        if (attacker == null || level.random.nextFloat() >= PHASE_CHANCE) {
            return false;
        }
        // Flicker out like a ghost for a moment.
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6, 0, false, false, false));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
            25, 0.35, 0.7, 0.35, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
            6, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    /** Good effects, the Star Speed trail, and refilling Star Jumps on landing. Called every tick from onPlayerTick. */
    public static void tick(Player player, ServerLevel level) {
        if (!isWearing(player)) {
            return;
        }

        for (EffectLevel good : GOOD_EFFECTS) {
            MobEffectInstance current = player.getEffect(good.effect());
            if (current == null || current.getAmplifier() < good.amplifier()
                    || current.getDuration() < EFFECT_REFRESH_BELOW_TICKS) {
                player.addEffect(new MobEffectInstance(good.effect(), EFFECT_DURATION_TICKS, good.amplifier(),
                    false, false, true));
            }
        }

        if (player.onGround()) {
            EXTRA_JUMPS_USED.remove(player.getUUID());
        }

        // Star Speed trail: stars and sparkles left behind at your feet while you're moving.
        Vec3 motion = player.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z > 0.01) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(),
                1, 0.15, 0.1, 0.15, 0.0);
            level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 0.3, player.getZ(),
                1, 0.2, 0.15, 0.2, 0.0);
            if (player.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.5, player.getZ(),
                    1, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    /**
     * Warp Dash, sent by the wearer's client when they double-tap sneak. Teleports them up to
     * {@link #WARP_DISTANCE} blocks the way they're looking (stopping short of walls), and hits
     * everything along the way.
     */
    public static void handleWarpDash(ServerPlayer player) {
        if (!isWearing(player) || !(player.level() instanceof ServerLevel level) || player.isSpectator()) {
            return;
        }
        long now = level.getGameTime();
        Long readyAt = WARP_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 startEye = player.getEyePosition();
        Vec3 wantedEye = startEye.add(look.scale(WARP_DISTANCE));
        Vec3 hit = level.clip(new ClipContext(startEye, wantedEye, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player)).getLocation();
        // Stop a little short of whatever we hit, so we don't end up inside it.
        Vec3 endEye = hit.subtract(look.scale(0.6));
        Vec3 start = player.position();
        Vec3 end = findStandingRoom(level, player, endEye.subtract(0.0, player.getEyeHeight(), 0.0));
        if (end.distanceToSqr(start) < 1.0) {
            return; // nowhere to go - right up against a wall
        }

        // Hit everything the dash passes through.
        AABB path = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, path,
                e -> e.isAlive() && e != player && e.getBoundingBox().inflate(0.8).clip(start.add(0, 1, 0), end.add(0, 1, 0)).isPresent())) {
            victim.hurtServer(level, level.damageSources().playerAttack(player), WARP_DAMAGE);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                victim.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
        }

        // A streak of cosmic particles along the path, then the jump itself.
        Vec3 step = end.subtract(start);
        int points = (int) Math.ceil(step.length() * 2.0);
        for (int i = 0; i <= points; i++) {
            Vec3 at = start.add(step.scale(i / (double) points)).add(0.0, 1.0, 0.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 3, 0.2, 0.4, 0.2, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.1, 0.2, 0.1, 0.0);
        }
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.3F);

        player.teleportTo(end.x, end.y, end.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        level.playSound(null, end.x, end.y, end.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.6F);

        WARP_READY_AT.put(player.getUUID(), now + WARP_COOLDOWN_TICKS);
    }

    /** Nudges a destination up out of the floor (up to 2 blocks) if the player wouldn't fit there. */
    private static Vec3 findStandingRoom(ServerLevel level, Player player, Vec3 feet) {
        for (int up = 0; up <= 4; up++) {
            Vec3 tryFeet = feet.add(0.0, up * 0.5, 0.0);
            if (level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(tryFeet))) {
                return tryFeet;
            }
        }
        return player.position();
    }

    /**
     * Star Jumps, sent by the wearer's client each time it jumps in mid-air (the client moves
     * itself up - player movement is client-side in Minecraft - so this just keeps count, clears
     * the fall that would otherwise hurt on landing, and shows the sparkle burst).
     */
    public static void handleExtraJump(ServerPlayer player) {
        if (!isWearing(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int used = EXTRA_JUMPS_USED.getOrDefault(player.getUUID(), 0);
        if (used >= EXTRA_JUMPS) {
            return;
        }
        EXTRA_JUMPS_USED.put(player.getUUID(), used + 1);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(),
            8, 0.3, 0.05, 0.3, 0.03);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY(), player.getZ(),
            6, 0.3, 0.05, 0.3, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F + used * 0.15F);
    }
}
