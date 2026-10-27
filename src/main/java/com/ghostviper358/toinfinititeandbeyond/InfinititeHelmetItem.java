package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Infinitite Helmet - Three epic abilities plus ore X-ray vision!
 *
 * 2. MIND SHIELD:   Nearby mobs randomly get confused and walk away
 * 3. STAR VISION:   Permanent night vision + see mobs through walls (they glow!)
 * 4. COSMIC ECHO:   Taking damage summons lightning to strike nearby mobs
 *
 * ORE VISION: Colored sparkles reveal hidden ores through walls:
 *   Iron=white, Gold=yellow, Diamond=blue, Copper=orange,
 *   Ancient Debris=brown, Lapis=purple (Infinitite placeholder)
 */
public class InfinititeHelmetItem extends Item {

    // Ore vision scan radius (blocks)
    private static final int ORE_SCAN_RADIUS = 12;
    // Ore scan interval (every 40 ticks = 2 seconds)
    private static final int ORE_SCAN_INTERVAL = 40;

    // Colored dust particles for each ore type (packed RGB int, scale)
    private static final DustParticleOptions IRON_PARTICLE =
        new DustParticleOptions(0xE8E8E8, 1.2f);  // white
    private static final DustParticleOptions GOLD_PARTICLE =
        new DustParticleOptions(0xFFD900, 1.2f);  // yellow
    private static final DustParticleOptions DIAMOND_PARTICLE =
        new DustParticleOptions(0x1AA6FF, 1.2f);  // blue
    private static final DustParticleOptions COPPER_PARTICLE =
        new DustParticleOptions(0xFF731A, 1.2f);  // orange
    private static final DustParticleOptions DEBRIS_PARTICLE =
        new DustParticleOptions(0x734A1A, 1.2f);  // brown
    private static final DustParticleOptions INFINITITE_PARTICLE =
        new DustParticleOptions(0x9933E6, 1.4f);  // purple

    public InfinititeHelmetItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof InfinititeHelmetItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        if (!isWearing(player)) return;

        // STAR VISION: permanent night vision
        addEffectIfNeeded(player, new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));

        // STAR VISION: make nearby mobs glow (visible through walls)
        if (player.tickCount % 60 == 0) {
            applyGlowToNearbyMobs(player, level);
        }

        // MIND SHIELD: randomly confuse nearby hostile mobs
        if (player.tickCount % 40 == 0) {
            applyMindShield(player, level);
        }

        // ORE VISION: scan for ores and show colored particles
        if (player.tickCount % ORE_SCAN_INTERVAL == 0) {
            scanAndShowOres(player, level);
        }
    }

    private void addEffectIfNeeded(Player player, MobEffectInstance effect) {
        var existing = player.getEffect(effect.getEffect());
        if (existing == null || existing.getDuration() < 60) {
            player.addEffect(effect);
        }
    }

    /**
     * STAR VISION: Apply Glowing effect to nearby living entities.
     * Glowing makes them visible through walls with an outline.
     */
    private void applyGlowToNearbyMobs(Player player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(20.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());
        for (LivingEntity mob : nearby) {
            if (mob.getEffect(MobEffects.GLOWING) == null) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
            }
        }
    }

    /**
     * MIND SHIELD: Nearby mobs get confused and disoriented.
     * 20% chance per mob every 2 seconds.
     */
    private void applyMindShield(Player player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(10.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());
        for (LivingEntity mob : nearby) {
            if (level.random.nextFloat() < 0.20f) {
                // Confuse the mob: nausea, slowness, blindness
                mob.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1, false, false));
            }
        }
    }

    /**
     * ORE VISION: Scan nearby blocks for ores and show colored dust particles.
     * Each ore type gets its own unique color.
     */
    private void scanAndShowOres(Player player, ServerLevel level) {
        BlockPos center = player.blockPosition();
        int r = ORE_SCAN_RADIUS;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-r, -r, -r),
                center.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            DustParticleOptions particle = getOreParticle(state);
            if (particle != null) {
                // Spawn particle at center-top of the ore block
                level.sendParticles(particle,
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                    1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /**
     * Returns the correct colored particle for each ore type, or null if not an ore.
     */
    private DustParticleOptions getOreParticle(BlockState state) {
        if (state.is(BlockTags.IRON_ORES))    return IRON_PARTICLE;
        if (state.is(BlockTags.GOLD_ORES))    return GOLD_PARTICLE;
        if (state.is(BlockTags.DIAMOND_ORES)) return DIAMOND_PARTICLE;
        if (state.is(BlockTags.COPPER_ORES))  return COPPER_PARTICLE;
        if (state.is(Blocks.ANCIENT_DEBRIS))  return DEBRIS_PARTICLE;
        if (state.is(BlockTags.LAPIS_ORES))   return INFINITITE_PARTICLE; // placeholder - purple for now
        return null;
    }

    /**
     * COSMIC ECHO: Called from LivingHurtEvent when the player wearing the helmet takes damage.
     * Strikes up to 3 nearby mobs with lightning and blasts them back!
     */
    public static void handleCosmicEcho(Player player, float damage, ServerLevel level) {
        if (damage < 1.0f) return; // only trigger on real hits

        AABB box = player.getBoundingBox().inflate(8.0);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive());

        // Sort by closest first, strike up to 3
        nearby.sort((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player)));

        int strikes = 0;
        for (LivingEntity target : nearby) {
            if (strikes >= 3) break;

            // Summon lightning at target
            LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            lightning.setPos(target.getX(), target.getY(), target.getZ());
            level.addFreshEntity(lightning);

            // Extra knockback away from player
            Vec3 dir = target.position().subtract(player.position()).normalize();
            target.knockback(2.5, -dir.x, -dir.z);
            strikes++;
        }
    }
}
