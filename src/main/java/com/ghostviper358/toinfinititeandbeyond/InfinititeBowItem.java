package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Custom Infinitite Bow - A powerful ranged weapon with lock-on targeting.
 *
 * Features:
 * - 3124 durability (same as Infinitite Sword)
 * - Lock-on targeting system - only targets entities without Infinitite items
 * - Fires arrows with no gravity (straight flight)
 * - Arrows home in on locked target
 */
public class InfinititeBowItem extends BowItem {

    private static final double LOCK_ON_RANGE = 50.0; // Range for target detection

    public InfinititeBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entityLiving, int timeLeft) {
        if (entityLiving instanceof Player player) {
            // Check if player has arrows or is in creative mode
            ItemStack projectileStack = player.getProjectile(stack);
            boolean hasAmmo = player.getAbilities().instabuild || !projectileStack.isEmpty();

            if (!hasAmmo) {
                return false;
            }

            // Calculate charge power (how long the bow was drawn)
            int chargeTime = this.getUseDuration(stack, entityLiving) - timeLeft;
            float power = getPowerForTime(chargeTime);

            if (power < 0.1F) {
                return false;
            }

            if (!level.isClientSide) {
                // Find valid target (entity without Infinitite items)
                LivingEntity target = findValidTarget(player, level);

                // Create custom arrow with proper ItemStacks
                ItemStack arrowStack = projectileStack.isEmpty() ? new ItemStack(Items.ARROW) : projectileStack.copy();
                InfinititeArrowEntity arrow = new InfinititeArrowEntity(level, player, arrowStack, stack);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);

                // Set the locked target if we found one
                if (target != null) {
                    arrow.setLockedTarget(target);
                }

                // Set arrow properties
                arrow.setCritArrow(power == 1.0F);

                // Add arrow to world
                level.addFreshEntity(arrow);

                // Play sound
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);

                // Damage bow
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));

                // Consume arrow if not in creative
                if (!player.getAbilities().instabuild) {
                    projectileStack.shrink(1);
                    if (projectileStack.isEmpty()) {
                        player.getInventory().removeItem(projectileStack);
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Finds a valid target in front of the player.
     * Valid targets are entities that don't have any Infinitite items.
     */
    private LivingEntity findValidTarget(Player player, Level level) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = playerPos.add(lookVec.scale(LOCK_ON_RANGE));

        // Create bounding box for target search
        AABB searchBox = new AABB(playerPos, endPos).inflate(5.0);

        // Find all living entities in range
        List<LivingEntity> entities = level.getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            entity -> entity != player && entity.isAlive()
        );

        // Find the closest valid target (no Infinitite items) that player is looking at
        LivingEntity closestTarget = null;
        double closestDistance = Double.MAX_VALUE;
        double closestAngle = 0.5; // Cone of ~60 degrees

        for (LivingEntity entity : entities) {
            // Check if entity has any Infinitite items
            if (hasInfinititeItem(entity)) {
                continue; // Skip entities with Infinitite items
            }

            // Check if player is looking towards this entity
            Vec3 toEntity = entity.position().subtract(playerPos).normalize();
            double dotProduct = lookVec.dot(toEntity);

            if (dotProduct > closestAngle) {
                double distance = entity.distanceTo(player);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestTarget = entity;
                    closestAngle = dotProduct;
                }
            }
        }

        return closestTarget;
    }

    /**
     * Checks if an entity has any Infinitite items equipped or in inventory.
     */
    private boolean hasInfinititeItem(LivingEntity entity) {
        // Check all equipment slots using EquipmentSlot enum
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack itemStack = entity.getItemBySlot(slot);
            if (!itemStack.isEmpty() && isInfinititeItem(itemStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an ItemStack is an Infinitite item.
     */
    private boolean isInfinititeItem(ItemStack stack) {
        return stack.is(ToInfinititeAndBeyond.INFINITITE_SWORD.get()) ||
               stack.is(ToInfinititeAndBeyond.INFINITITE_BOW.get()) ||
               stack.is(ToInfinititeAndBeyond.INFINITITE_INGOT.get());
    }

}
