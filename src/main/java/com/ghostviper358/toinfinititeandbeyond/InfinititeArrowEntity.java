package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Custom arrow entity for the Infinitite Bow.
 *
 * Features:
 * - No gravity (flies straight)
 * - Homes in on locked target
 * - Ignores entities with Infinitite items
 */
public class InfinititeArrowEntity extends Arrow {

    private LivingEntity lockedTarget;
    private static final double HOMING_STRENGTH = 0.1; // How aggressively arrow tracks target

    public InfinititeArrowEntity(EntityType<? extends Arrow> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public InfinititeArrowEntity(Level level, LivingEntity shooter, net.minecraft.world.item.ItemStack pickupItemStack, net.minecraft.world.item.ItemStack firedFromWeapon) {
        super(level, shooter, pickupItemStack, firedFromWeapon);
        this.setNoGravity(true);
    }

    /**
     * Sets the locked target for this arrow to home in on.
     */
    public void setLockedTarget(LivingEntity target) {
        this.lockedTarget = target;
    }

    /**
     * Gets the locked target.
     */
    public LivingEntity getLockedTarget() {
        return this.lockedTarget;
    }

    @Override
    public void tick() {
        super.tick();

        // If we have a locked target and arrow is still in flight
        if (this.lockedTarget != null && !this.isInGround() && this.lockedTarget.isAlive()) {
            // Calculate direction to target
            Vec3 arrowPos = this.position();
            Vec3 targetPos = this.lockedTarget.getEyePosition();
            Vec3 toTarget = targetPos.subtract(arrowPos).normalize();

            // Get current velocity
            Vec3 currentVelocity = this.getDeltaMovement();
            double speed = currentVelocity.length();

            // Blend current direction with target direction for smooth homing
            Vec3 currentDirection = currentVelocity.normalize();
            Vec3 newDirection = currentDirection.add(toTarget.scale(HOMING_STRENGTH)).normalize();

            // Apply new velocity maintaining original speed
            Vec3 newVelocity = newDirection.scale(speed);
            this.setDeltaMovement(newVelocity);

            // Update arrow rotation to match flight direction
            this.setYRot((float)(Math.atan2(newVelocity.x, newVelocity.z) * (180.0 / Math.PI)));
            this.setXRot((float)(Math.atan2(newVelocity.y, Math.sqrt(newVelocity.x * newVelocity.x + newVelocity.z * newVelocity.z)) * (180.0 / Math.PI)));
        }
    }

    /**
     * Checks if an entity has any Infinitite items equipped or in inventory.
     */
    private boolean hasInfinititeItem(LivingEntity entity) {
        // Check all equipment slots using EquipmentSlot enum
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            net.minecraft.world.item.ItemStack itemStack = entity.getItemBySlot(slot);
            if (!itemStack.isEmpty() && isInfinititeItem(itemStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an ItemStack is an Infinitite item.
     */
    private boolean isInfinititeItem(net.minecraft.world.item.ItemStack stack) {
        return stack.is(ToInfinititeAndBeyond.INFINITITE_SWORD.get()) ||
               stack.is(ToInfinititeAndBeyond.INFINITITE_BOW.get()) ||
               stack.is(ToInfinititeAndBeyond.INFINITITE_INGOT.get());
    }
}
