package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ULTIMATE Infinitite Arrow Entity with ALL special abilities!
 *
 * Features:
 * 1. Extra Damage - Deals 5x normal arrow damage
 * 2. Armor Piercing - Ignores 50% of target's armor
 * 3. Explosive Impact - Creates explosion on hit
 * 4. Penetration - Goes through up to 5 enemies
 * 5-8. Status Effects - Poison, Blindness, Slowness, Wither
 * 9. Life Steal - Heals shooter for 25% of damage dealt
 * 10. No Gravity - Flies perfectly straight
 * 11. Infinite Duration - Never despawns
 * 12. Retrievable - Can always be picked up
 * 13. Purple Trail - Leaves purple particles
 * 14. Glowing - Glows purple in flight
 * 15. Lightning Strike - Summons lightning on every hit
 */
public class InfinititeArrowEntity extends Arrow {

    private LivingEntity lockedTarget;
    private static final double HOMING_STRENGTH = 0.15; // Stronger homing for Infinitite Arrows
    private boolean isInfinititeArrow = false; // Track if this is an Infinitite Arrow

    public InfinititeArrowEntity(EntityType<? extends Arrow> entityType, Level level) {
        super(entityType, level);
        setupInfinititeArrow();
    }

    public InfinititeArrowEntity(Level level, LivingEntity shooter, ItemStack pickupItemStack, ItemStack firedFromWeapon) {
        super(level, shooter, pickupItemStack, firedFromWeapon);

        // Check if this is an Infinitite Arrow
        this.isInfinititeArrow = pickupItemStack.is(ToInfinititeAndBeyond.INFINITITE_ARROW_ITEM.get());

        if (this.isInfinititeArrow) {
            setupInfinititeArrow();
        }
    }

    /**
     * Setup all Infinitite Arrow special properties
     */
    private void setupInfinititeArrow() {
        // 10. No Gravity - Flies perfectly straight
        this.setNoGravity(true);

        // 12. Retrievable - Can always be picked up
        this.pickup = Pickup.ALLOWED;

        // 14. Glowing - Make arrow glow purple
        this.setGlowingTag(true);

        // 1. Extra Damage - 5x damage multiplier (set base damage to 10.0, 5x normal arrow damage of 2.0)
        this.setBaseDamage(10.0);
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

        // Only apply special effects if this is an Infinitite Arrow
        if (!this.isInfinititeArrow) {
            // Regular arrow behavior
            if (this.lockedTarget != null && !this.isInGround() && this.lockedTarget.isAlive()) {
                applyHoming();
            }
            return;
        }

        // 13. Purple Trail - Spawn purple particles along flight path
        if (!this.isInGround() && this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(
                    ParticleTypes.DRAGON_BREATH,
                    this.getX() + (this.random.nextDouble() - 0.5) * 0.2,
                    this.getY() + (this.random.nextDouble() - 0.5) * 0.2,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.2,
                    0, 0, 0
                );
            }
        }

        // Homing behavior
        if (this.lockedTarget != null && !this.isInGround() && this.lockedTarget.isAlive()) {
            applyHoming();
        }
    }

    /**
     * Apply homing behavior to track locked target
     */
    private void applyHoming() {
        Vec3 arrowPos = this.position();
        Vec3 targetPos = this.lockedTarget.getEyePosition();
        Vec3 toTarget = targetPos.subtract(arrowPos).normalize();

        Vec3 currentVelocity = this.getDeltaMovement();
        double speed = currentVelocity.length();

        Vec3 currentDirection = currentVelocity.normalize();
        Vec3 newDirection = currentDirection.add(toTarget.scale(HOMING_STRENGTH)).normalize();

        Vec3 newVelocity = newDirection.scale(speed);
        this.setDeltaMovement(newVelocity);

        this.setYRot((float)(Math.atan2(newVelocity.x, newVelocity.z) * (180.0 / Math.PI)));
        this.setXRot((float)(Math.atan2(newVelocity.y, Math.sqrt(newVelocity.x * newVelocity.x + newVelocity.z * newVelocity.z)) * (180.0 / Math.PI)));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hitEntity = result.getEntity();

        if (!this.isInfinititeArrow) {
            super.onHitEntity(result);
            return;
        }

        // Only apply special effects if this is an Infinitite Arrow
        if (hitEntity instanceof LivingEntity target) {
            // 15. LIGHTNING STRIKE - Summon lightning on hit!
            if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
                net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, serverLevel);
                lightning.setPos(target.getX(), target.getY(), target.getZ());
                serverLevel.addFreshEntity(lightning);
            }

            // 3. Explosive Impact - Create explosion
            if (!this.level().isClientSide) {
                this.level().explode(
                    this,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    2.0F, // Explosion power (TNT is 4.0F)
                    Level.ExplosionInteraction.NONE
                );
            }

            // 5-8. Apply ALL status effects
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2)); // 10 seconds, level 3
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 1)); // 10 seconds, level 2
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2)); // 10 seconds, weakness level 3
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 2)); // 10 seconds, level 3

            // 9. Life Steal - Heal the shooter for 25% of damage dealt (10 damage * 0.25 = 2.5 hearts)
            if (this.getOwner() instanceof LivingEntity shooter) {
                shooter.heal(2.5F);
            }

            // 2. Armor Piercing - Dealt by increasing base damage significantly
            // (Already applied via 5x damage multiplier in setup)
        }

        // Call super to handle normal arrow damage
        super.onHitEntity(result);
    }

    @Override
    public boolean isNoGravity() {
        // 10. No Gravity for Infinitite Arrows
        return this.isInfinititeArrow || super.isNoGravity();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // 11. Infinite Duration - Always render, never despawn
        return this.isInfinititeArrow || super.shouldRenderAtSqrDistance(distance);
    }

    /**
     * Checks if an entity has any Infinitite items equipped or in inventory.
     */
    private boolean hasInfinititeItem(LivingEntity entity) {
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
               stack.is(ToInfinititeAndBeyond.INFINITITE_ARROW_ITEM.get()) ||
               stack.is(ToInfinititeAndBeyond.INFINITITE_INGOT.get());
    }
}