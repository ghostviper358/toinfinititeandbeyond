package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Cosmic Apple - an emergency-use "ultimate" consumable crafted from a Universal Shard.
 * See docs/requirements/universal_shard_and_cosmic_apple.md ("Cosmic Apple (Item)").
 *
 * On eating, grants (all at once, for 1 minute):
 * - Speed, Strength, Regeneration, Fire Resistance
 * - Cosmic Invincibility (complete damage immunity - see {@link CosmicInvincibilityEffect})
 * - Glowing
 * plus a one-time purple particle wave, and a PERMANENT, uncapped, stacking +30 max
 * health bonus that survives relogs and (via {@code PlayerEvent.Clone} in
 * {@link ToInfinititeAndBeyond}) death/respawn.
 */
public class CosmicAppleItem extends Item {

    /** How long the temporary buffs (speed/strength/regen/fire resist/invincibility/glowing) last. */
    public static final int BUFF_DURATION_TICKS = 20 * 60; // 1 minute

    /** Permanent max health granted per apple eaten. Stacks with no cap. */
    public static final double MAX_HEALTH_BONUS_PER_APPLE = 30.0D;

    // Root key under the player's persistent (survives-relog) NBT data where we keep our own stuff.
    private static final String PERSISTENT_ROOT_KEY = ToInfinititeAndBeyond.MOD_ID;
    private static final String STACK_COUNT_KEY = "cosmic_apple_stacks";

    // Fixed, well-known modifier id so re-applying it (e.g. on login/respawn) simply
    // replaces the previous value instead of stacking duplicate modifiers.
    public static final ResourceLocation MAX_HEALTH_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "cosmic_apple_max_health");

    public CosmicAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Precious/magical shimmer, matching the Universal Shard it's crafted from.
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Handles hunger/saturation restore (from FoodProperties) and shrinking the stack.
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide() && entity instanceof Player player && level instanceof ServerLevel serverLevel) {
            applyEatEffects(player);
            spawnEatParticleWave(serverLevel, player);
            grantPermanentMaxHealthStack(player);
        }

        return result;
    }

    /**
     * All the temporary "ultimate power" effects, applied simultaneously.
     */
    private void applyEatEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, BUFF_DURATION_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BUFF_DURATION_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF_DURATION_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, BUFF_DURATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, BUFF_DURATION_TICKS, 0, false, true, true));

        // Complete damage immunity for the duration - see CosmicInvincibilityEffect and
        // ToInfinititeAndBeyond.onLivingHurt(), which cancels all damage while this is active.
        player.addEffect(new MobEffectInstance(
            ToInfinititeAndBeyond.COSMIC_INVINCIBILITY.getHolder().orElseThrow(), BUFF_DURATION_TICKS, 0, false, true, true));
    }

    /**
     * One-shot wave/pulse of purple particles centered on the player at the moment of eating.
     * Spawns several expanding rings all at once rather than over multiple ticks, so no
     * scheduling/tick-tracking infrastructure is needed for this purely cosmetic burst.
     */
    private void spawnEatParticleWave(ServerLevel level, Player player) {
        double cx = player.getX();
        double cy = player.getY() + 1.0;
        double cz = player.getZ();

        int ringPoints = 20;
        double[] radii = {0.5, 1.0, 1.5, 2.0, 2.5};
        for (double radius : radii) {
            for (int i = 0; i < ringPoints; i++) {
                double angle = (2.0 * Math.PI * i) / ringPoints;
                double px = cx + radius * Math.cos(angle);
                double pz = cz + radius * Math.sin(angle);
                level.sendParticles(ParticleTypes.PORTAL, px, cy, pz, 1, 0.0, 0.1, 0.0, 0.02);
            }
        }
        // Extra sparkle burst directly on the player for a brighter "pulse" moment.
        level.sendParticles(ParticleTypes.WITCH, cx, cy, cz, 40, 0.6, 0.8, 0.6, 0.05);
    }

    /**
     * Permanently increases the player's max health by MAX_HEALTH_BONUS_PER_APPLE.
     * Stacks with no cap: every apple eaten increments a persistent counter, and the
     * max-health modifier is recomputed from the new total (rather than adding a new
     * unique modifier per apple), which keeps re-application on login/respawn idempotent.
     */
    private void grantPermanentMaxHealthStack(Player player) {
        int stacks = getStacks(player) + 1;
        setStacks(player, stacks);
        applyMaxHealthModifier(player, stacks);

        // Also heal the player by the amount just gained, so it immediately feels like a
        // reward rather than an invisible stat change (current health never exceeds the
        // new, higher max since we only add to it).
        player.setHealth(Math.min(player.getHealth() + (float) MAX_HEALTH_BONUS_PER_APPLE, player.getMaxHealth()));
    }

    /** Reads how many Cosmic Apples this player has ever eaten, from persistent (relog-safe) NBT. */
    public static int getStacks(Player player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag modData = persistent.getCompoundOrEmpty(PERSISTENT_ROOT_KEY);
        return modData.getIntOr(STACK_COUNT_KEY, 0);
    }

    /** Writes the Cosmic Apple stack count into persistent (relog-safe) NBT. */
    public static void setStacks(Player player, int stacks) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag modData = persistent.getCompoundOrEmpty(PERSISTENT_ROOT_KEY);
        modData.putInt(STACK_COUNT_KEY, stacks);
        persistent.put(PERSISTENT_ROOT_KEY, modData);
    }

    /**
     * (Re)applies the max-health attribute modifier so its value matches
     * {@code stacks * MAX_HEALTH_BONUS_PER_APPLE}. Safe to call repeatedly (e.g. on
     * world join or after respawn) since it replaces rather than duplicates itself.
     */
    public static void applyMaxHealthModifier(Player player, int stacks) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }

        if (stacks > 0) {
            // Replaces any previous modifier with this id instead of stacking duplicates,
            // which is what makes re-applying this on every login/respawn idempotent.
            instance.addOrReplacePermanentModifier(new AttributeModifier(
                MAX_HEALTH_MODIFIER_ID,
                stacks * MAX_HEALTH_BONUS_PER_APPLE,
                AttributeModifier.Operation.ADD_VALUE
            ));
        } else {
            instance.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
    }

    /**
     * Re-syncs the max-health modifier from the persistent stack counter. Called from
     * PlayerEvent.Clone (death/respawn) and player login, since a fresh Player entity
     * instance gets a brand-new AttributeMap that won't automatically carry over a
     * modifier that was only ever added directly to the old instance.
     */
    public static void resyncMaxHealthModifier(Player player) {
        applyMaxHealthModifier(player, getStacks(player));
    }
}