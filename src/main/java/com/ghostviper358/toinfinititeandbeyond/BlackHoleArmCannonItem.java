package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Black Hole Arm Cannon - a big cannon worn over the arm (held in the main hand).
 * See docs/requirements/black_hole_cannon.md.
 *
 * It sits between the Void gear and the Universal gear: stronger than a Void Sword, much
 * weaker than the Universal Sword. Each attack mode has its own strength.
 *
 * Sneak + right-click switches attack mode:
 * - Mode 1, Homing Blocks: hold right-click to fire a machine-gun stream of exploding blocks
 *   that home in on whatever you're aiming at (see {@link CannonBlockEntity}). Light hits, fast.
 * - Mode 2, Infinitite Spikes: right-click to make a ring of purple spikes burst out of the
 *   ground under what you're aiming at (see {@link CannonSpikes}). Huge hits, slow, and costs a
 *   quarter of the ammo.
 *
 * Ammo: {@link #MAX_AMMO} shots, stored on the item. Every shot uses one. Ammo slowly
 * refills by itself whenever you aren't firing, so you can never run out for good, but you
 * can't fire forever either. The bar under the item shows how much is left, and so do the
 * little void blocks orbiting the black hole (one per quarter of the ammo).
 *
 * Running out wakes up the black hole: it spins fast and sucks in purple energy, refilling the
 * ammo faster - but the cannon can't fire again until it's completely full.
 *
 * Holding left-click aims down the sight instead of attacking or mining (all client-side - see
 * BlackHoleArmCannonClientHandler). The look and moving parts are in BlackHoleArmCannonRenderer.
 */
public class BlackHoleArmCannonItem extends Item implements GeoItem {

    public static final int MAX_AMMO = 60;
    /** Refills 1 shot every this many ticks (4 shots a second) while not firing. */
    private static final int REGEN_TICKS = 5;
    /** While the black hole is recharging an empty cannon, refills 1 shot every this many ticks (10 a second). */
    private static final int RECHARGE_TICKS = 2;
    /** Fires 1 block every this many ticks while right-click is held (about 7 a second). */
    private static final int FIRE_TICKS = 3;
    /** How far away the cannon can lock on to a target. */
    private static final double LOCK_ON_RANGE = 128.0;

    /** Spikes cost this much ammo, and can be fired once every {@link #SPIKE_COOLDOWN_TICKS} (2 seconds). */
    private static final int SPIKE_AMMO_COST = 15;
    public static final int SPIKE_COOLDOWN_TICKS = 40;

    private static final String AMMO_KEY = "Ammo";
    private static final String RECHARGING_KEY = "Recharging";
    private static final String MODE_KEY = "Mode";
    private static final String SPIKES_READY_KEY = "SpikesReadyAt";
    private static final int AMMO_BAR_COLOR = 0xB040FF; // purple
    private static final int RECHARGE_BAR_COLOR = 0xFF55FF; // bright pink while the black hole refills it

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** The cannon's attack modes, in the order sneak + right-click goes through them. */
    public enum Mode {
        HOMING_BLOCKS("Homing Blocks"),
        SPIKES("Infinitite Spikes");

        public final String title;

        Mode(String title) {
            this.title = title;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public BlackHoleArmCannonItem(Properties properties) {
        super(properties);
    }

    // --- GeckoLib ---

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animation controllers: the fins, black hole and ammo depend on how much ammo is
        // left and how long it's been held, so BlackHoleArmCannonRenderer moves them in code.
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private final GeoItemRenderer<BlackHoleArmCannonItem> renderer = new BlackHoleArmCannonRenderer();

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                return renderer;
            }
        });
    }

    // --- Ammo ---

    public static int getAmmo(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(AMMO_KEY, MAX_AMMO);
    }

    private static void setAmmo(ItemStack stack, int ammo) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(AMMO_KEY, ammo));
    }

    public static Mode getMode(ItemStack stack) {
        int index = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(MODE_KEY, 0);
        return Mode.values()[Math.floorMod(index, Mode.values().length)];
    }

    private static void setMode(ItemStack stack, Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_KEY, mode.ordinal()));
    }

    /** The game time the spikes can next be fired. */
    public static long getSpikesReadyAt(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(SPIKES_READY_KEY, 0L);
    }

    private static void setSpikesReadyAt(ItemStack stack, long gameTime) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(SPIKES_READY_KEY, gameTime));
    }

    /** True while the black hole is refilling an emptied cannon (it can't fire until it's full). */
    public static boolean isRecharging(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(RECHARGING_KEY, false);
    }

    private static void setRecharging(ItemStack stack, boolean recharging) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(RECHARGING_KEY, recharging));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        int ammo = getAmmo(stack);
        if (isRecharging(stack)) {
            if (level.getGameTime() % RECHARGE_TICKS == 0) {
                setAmmo(stack, ammo + 1);
                if (ammo + 1 >= MAX_AMMO) {
                    setRecharging(stack, false);
                    if (entity instanceof Player player) {
                        player.displayClientMessage(Component.literal("§dBlack hole fully charged!"), true);
                    }
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
                }
            }
            return;
        }
        boolean firing = entity instanceof LivingEntity living && living.isUsingItem() && living.getUseItem() == stack;
        if (!firing && ammo < MAX_AMMO && level.getGameTime() % REGEN_TICKS == 0) {
            setAmmo(stack, ammo + 1);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAmmo(stack) < MAX_AMMO;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getAmmo(stack) / MAX_AMMO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return isRecharging(stack) ? RECHARGE_BAR_COLOR : AMMO_BAR_COLOR;
    }

    // --- Firing (right-click) and switching modes (sneak + right-click) ---

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            switchMode(level, player, stack);
            return InteractionResult.SUCCESS;
        }
        if (isRecharging(stack) || getAmmo(stack) <= 0) {
            stillRecharging(level, player);
            return InteractionResult.FAIL;
        }
        if (getMode(stack) == Mode.SPIKES) {
            return fireSpikes(level, player, stack);
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    private static void switchMode(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return;
        }
        Mode mode = getMode(stack).next();
        setMode(stack, mode);
        player.displayClientMessage(Component.literal("§5Mode: §d" + mode.title), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.2f + mode.ordinal() * 0.3f);
    }

    /**
     * Mode 2: one ring of spikes per click. Holding right-click keeps trying every few ticks, so
     * while the spikes are cooling down this just quietly does nothing.
     */
    private static InteractionResult fireSpikes(Level level, Player player, ItemStack stack) {
        if (level.getGameTime() < getSpikesReadyAt(stack)) {
            return InteractionResult.FAIL;
        }
        int ammo = getAmmo(stack);
        boolean free = player.getAbilities().instabuild;
        if (ammo < SPIKE_AMMO_COST && !free) {
            player.displayClientMessage(Component.literal("§cSpikes need " + SPIKE_AMMO_COST + " ammo §7(" + ammo + "/" + MAX_AMMO + ")"), true);
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!CannonSpikes.fire(serverLevel, player)) {
            player.displayClientMessage(Component.literal("§7No ground there for spikes to grow from"), true);
            return InteractionResult.FAIL;
        }
        setSpikesReadyAt(stack, level.getGameTime() + SPIKE_COOLDOWN_TICKS);
        if (!free) {
            ammo -= SPIKE_AMMO_COST;
            setAmmo(stack, ammo);
        }
        if (ammo <= 0) {
            startRecharging(level, player, stack);
        } else {
            player.displayClientMessage(Component.literal("§5" + Mode.SPIKES.title + " §7- §d" + ammo + "§7/" + MAX_AMMO), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // basically forever - you stop by letting go of right-click
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        // Using up ammo changes the item's data every shot - keep firing anyway.
        return newStack.is(this);
    }

    @Override
    public void onUseTick(Level level, LivingEntity shooter, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel) || !(shooter instanceof Player player)) {
            return;
        }
        int ticksHeld = getUseDuration(stack, shooter) - remainingUseDuration;
        if (ticksHeld % FIRE_TICKS != 0) {
            return;
        }

        int ammo = getAmmo(stack);
        if (isRecharging(stack) || ammo <= 0 || getMode(stack) != Mode.HOMING_BLOCKS) {
            player.stopUsingItem();
            return;
        }

        fireHomingBlock(serverLevel, player);
        if (!player.getAbilities().instabuild) {
            setAmmo(stack, ammo - 1);
            ammo--;
        }
        if (ammo <= 0) {
            startRecharging(level, player, stack);
            return;
        }
        player.displayClientMessage(Component.literal("§5" + Mode.HOMING_BLOCKS.title + " §7- §d" + ammo + "§7/" + MAX_AMMO), true);
    }

    /** Out of ammo: the black hole wakes up and starts refilling the cannon. */
    private static void startRecharging(Level level, Player player, ItemStack stack) {
        setRecharging(stack, true);
        player.stopUsingItem();
        player.displayClientMessage(Component.literal("§cOut of ammo! §dThe black hole is recharging..."), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    private static void fireHomingBlock(ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        // Start at the cannon's barrel: just in front of whichever hand is holding it.
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 side = arm == HumanoidArm.RIGHT ? right : right.scale(-1);
        Vec3 from = player.getEyePosition().add(look.scale(0.6)).add(side.scale(0.4)).add(0, -0.35, 0);

        CannonBlockEntity block = new CannonBlockEntity(level, player);
        block.setPos(from);
        block.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, CannonBlockEntity.LAUNCH_SPEED, 1.5f);
        block.setTarget(findTarget(level, player));
        level.addFreshEntity(block);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.6f, 1.4f + level.getRandom().nextFloat() * 0.3f);
    }

    /** The living thing closest to the middle of the crosshair, or null if nothing's there. */
    @Nullable
    private static LivingEntity findTarget(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB searchBox = new AABB(eye, eye.add(look.scale(LOCK_ON_RANGE))).inflate(4.0);

        LivingEntity best = null;
        double bestAngle = 0.98; // only things within about 11 degrees of the crosshair
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(eye);
            double angle = toCandidate.normalize().dot(look);
            if (angle > bestAngle && player.hasLineOfSight(candidate)) {
                bestAngle = angle;
                best = candidate;
            }
        }
        return best;
    }

    private static void stillRecharging(Level level, Player player) {
        player.displayClientMessage(Component.literal("§dThe black hole is still recharging..."), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8f, 0.6f);
    }
}
