package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Universal Chestplate - the first piece of the Universal armor set, crafted from Universal
 * Shards. See docs/requirements/universal_chestplate.md.
 *
 * Defensive stats (Armor 999, Toughness 6, +40 max health, 40% knockback resistance,
 * unbreakable) are all applied via attribute modifiers / the vanilla
 * {@code minecraft:unbreakable} data component registered in ToInfinititeAndBeyond.java
 * (see UNIVERSAL_CHESTPLATE) - same pattern as UNIVERSAL_SWORD, nothing to do here for those.
 *
 * Always-on passive abilities (no charge-up, no cooldown):
 *   1. Cosmic Ward   - 25% chance to fully negate ANY incoming damage, any source/type.
 *   2. Cosmic Rebuke - always blocks ranged/projectile damage outright, and reflects 50%
 *                      of incoming melee damage back at the attacker.
 *   3. Cosmic Aegis  - permanent immunity to Poison/Wither/Blindness, plus constant
 *                      passive Regeneration.
 *   4. Starlight Aura - constant purple particle aura, purely visual.
 *
 * Cosmic Ward and Cosmic Rebuke are damage-event-driven (handleChestplateHurt, called from
 * onLivingHurt in the main mod class). Cosmic Aegis and Starlight Aura are tick-driven
 * (tick(), called from onPlayerTick in the main mod class) - same split used by the existing
 * Void/Infinitite armor pieces.
 */
public class UniversalChestplateItem extends Item {

    /** Cosmic Ward: chance per incoming hit (any source/type) to fully negate the damage. */
    private static final float WARD_CHANCE = 0.25f; // 25%

    /** Cosmic Rebuke: fraction of incoming melee damage reflected back at the attacker. */
    private static final float REBUKE_REFLECT_PERCENT = 0.50f; // 50%

    public UniversalChestplateItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof UniversalChestplateItem;
    }

    /**
     * Cosmic Ward + Cosmic Rebuke. Called from LivingHurtEvent (onLivingHurt) in the main
     * mod class whenever the WEARER takes damage.
     *
     * @return true to cancel the event (fully block the hit)
     */
    public static boolean handleChestplateHurt(LivingHurtEvent event, Player player, ServerLevel level, Entity attacker) {
        Entity direct = event.getSource().getDirectEntity();
        boolean isRanged = direct instanceof Projectile;

        // Cosmic Rebuke: all ranged/projectile damage is blocked outright, every time.
        if (isRanged) {
            spawnBlockParticles(level, player);
            return true;
        }

        // Cosmic Ward: 25% chance to fully negate ANY incoming damage (melee here, since
        // ranged already returned above - matches the doc's note that Ward can still apply
        // as a second layer on top of Rebuke's always-block for ranged, it just never gets
        // the chance to matter since ranged never reaches this point).
        if (level.random.nextFloat() < WARD_CHANCE) {
            spawnBlockParticles(level, player);
            return true;
        }

        // Cosmic Rebuke: reflect 50% of incoming melee damage back at the attacker.
        if (attacker instanceof LivingEntity livingAttacker) {
            float reflected = event.getAmount() * REBUKE_REFLECT_PERCENT;
            livingAttacker.hurt(player.damageSources().thorns(player), reflected);
        }

        return false;
    }

    /**
     * Cosmic Aegis (status immunity + passive regen) and Starlight Aura (particles).
     * Called every tick from onPlayerTick in the main mod class, same pattern as
     * InfinititeBootsItem.tick()/InfinititeLeggingsItem.handleGravitySlam().
     */
    public static void tick(Player player, ServerLevel level) {
        if (!isWearing(player)) return;

        // Cosmic Aegis: permanent immunity to Poison, Wither, and Blindness while worn.
        // Stripping the effect the tick after it lands (rather than blocking the apply
        // itself) mirrors InfinititeBootsItem's Levitation on/off toggle - simple and
        // effective, at the cost of a one-tick flicker of the effect icon.
        if (player.hasEffect(MobEffects.POISON)) player.removeEffect(MobEffects.POISON);
        if (player.hasEffect(MobEffects.WITHER)) player.removeEffect(MobEffects.WITHER);
        if (player.hasEffect(MobEffects.BLINDNESS)) player.removeEffect(MobEffects.BLINDNESS);

        // Cosmic Aegis: constant slow (amplifier 0) passive Regeneration at all times,
        // refreshed before it can run out - same "addEffectIfNeeded" idiom as
        // InfinititeArmorItem's permanent buffs.
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen == null || regen.getDuration() < 40) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, false, true));
        }

        // Starlight Aura: constant purple particle aura, purely visual, no gameplay effect.
        if (player.tickCount % 4 == 0) {
            spawnAuraParticles(level, player);
        }
    }

    /** Purple burst shown whenever Cosmic Ward or Cosmic Rebuke blocks a hit entirely. */
    private static void spawnBlockParticles(ServerLevel level, Player player) {
        level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 1.0, player.getZ(),
            12, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.WITCH,
            player.getX(), player.getY() + 1.0, player.getZ(),
            6, 0.3, 0.5, 0.3, 0.02);
    }

    /** Starlight Aura: two slowly-orbiting arms of purple particles around the wearer. */
    private static void spawnAuraParticles(ServerLevel level, Player player) {
        double angle = (player.tickCount * 9.0) * Math.PI / 180.0;
        double bob = (player.tickCount % 40) / 40.0 * 1.8;
        for (int i = 0; i < 2; i++) {
            double a = angle + i * Math.PI;
            double px = player.getX() + 0.9 * Math.cos(a);
            double pz = player.getZ() + 0.9 * Math.sin(a);
            double py = player.getY() + 0.2 + bob;

            level.sendParticles(ParticleTypes.WITCH, px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }
}