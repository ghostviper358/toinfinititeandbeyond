package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Void Sword - A blade forged from compressed void energy.
 *
 * On every hit:
 *   1. Wither II (5 seconds) - drains health with black hearts
 *   2. Soul Drain - heals the attacker for 1 heart
 *   3. Void Pull - yanks the target 3 blocks toward the attacker
 *   4. Darkness (3 seconds) - blinds the target with void energy
 *
 * Every 5th hit on the same target:
 *   5. Void Curse - permanently removes 1 max heart from the target
 */
public class VoidSwordItem extends Item {

    // Tracks hit counts per target entity UUID
    private static final Map<UUID, Integer> hitCounts = new HashMap<>();

    // Unique ID for the permanent health reduction attribute modifier
    private static final ResourceLocation VOID_CURSE_ID =
            ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "void_heart_drain");

    public VoidSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 1. Wither II for 5 seconds (100 ticks)
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));

        // 2. Soul Drain - heal attacker 2 HP (1 heart)
        attacker.heal(2.0f);

        // 3. Void Pull - teleport target toward attacker
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 1.0) {
            double pull = Math.min(3.0, dist - 0.5);
            target.teleportTo(
                target.getX() + (dx / dist) * pull,
                target.getY(),
                target.getZ() + (dz / dist) * pull
            );
        }

        // 4. Darkness for 3 seconds (60 ticks)
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));

        // 5. Void Curse - track hits, drain 1 heart every 5 hits
        UUID targetId = target.getUUID();
        int hits = hitCounts.getOrDefault(targetId, 0) + 1;
        hitCounts.put(targetId, hits);

        if (hits % 5 == 0) {
            var maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null && target.getMaxHealth() > 4.0f) { // don't go below 2 hearts
                // Stack the reduction: -2 HP per 5 hits accumulated
                maxHealth.removeModifier(VOID_CURSE_ID);
                double reduction = -(hits / 5) * 2.0;
                maxHealth.addPermanentModifier(new AttributeModifier(
                    VOID_CURSE_ID,
                    reduction,
                    AttributeModifier.Operation.ADD_VALUE
                ));
                // Clamp current health to new max
                if (target.getHealth() > target.getMaxHealth()) {
                    target.setHealth(target.getMaxHealth());
                }
                // Notify the attacker
                if (attacker instanceof Player player) {
                    player.displayClientMessage(Component.literal("\u00a75The void claims a heart..."), true);
                }
                // Spawn eerie particles on the target
                if (target.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.WITCH,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        25, 0.4, 0.6, 0.4, 0.05);
                }
            }
        }

        super.hurtEnemy(stack, target, attacker);
    }
}