package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;

/**
 * Client-side parts of the Black Hole Arm Cannon:
 *
 * - Aiming down the sight: holding left-click with the cannon out zooms in a little and slides
 *   the cannon to the middle of the screen (see BlackHoleArmCannonRenderer) instead of attacking
 *   or mining. Only this client knows the key is held, and aiming changes nothing in the world,
 *   so nothing is sent to the server.
 * - The sight: the normal crosshair is swapped for floating purple pixels that close in on the
 *   middle as you aim. Each attack mode has its own sight.
 * - Recharging: while a cannon's black hole is refilling it, purple energy streams into the black
 *   hole from all around - for every player holding one, so everyone nearby can see it.
 */
public final class BlackHoleArmCannonClientHandler {

    private static final ResourceLocation SIGHT_LAYER = ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "cannon_sight");

    /** How much of the way to fully aimed each tick (4 ticks to aim). */
    private static final float AIM_SPEED = 0.25f;
    /** How much aiming zooms in (0.25 = a quarter narrower view). */
    private static final float AIM_ZOOM = 0.25f;

    /** Energy particles streaming into a recharging black hole each tick. */
    private static final int RECHARGE_PARTICLES_PER_TICK = 3;

    private static float aim;
    private static float prevAim;

    private BlackHoleArmCannonClientHandler() {
    }

    // --- Who's holding it ---

    @Nullable
    private static InteractionHand cannonHand(Player player) {
        if (player.getMainHandItem().is(ToInfinititeAndBeyond.BLACK_HOLE_ARM_CANNON.get())) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().is(ToInfinititeAndBeyond.BLACK_HOLE_ARM_CANNON.get())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * Left-click aims with the cannon in your main hand, or in your off hand when your main hand
     * is empty. With something else in your main hand (like a sword), left-click is left alone.
     */
    private static boolean canAim(Player player) {
        InteractionHand hand = cannonHand(player);
        return hand == InteractionHand.MAIN_HAND || (hand == InteractionHand.OFF_HAND && player.getMainHandItem().isEmpty());
    }

    /** How far into aiming down the sight the local player is, from 0 (not aiming) to 1. */
    public static float aimProgress(float partialTick) {
        return Mth.lerp(partialTick, prevAim, aim);
    }

    // --- Events ---

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            aim = prevAim = 0.0f;
            return;
        }

        prevAim = aim;
        boolean aiming = canAim(player) && minecraft.screen == null && minecraft.options.keyAttack.isDown();
        aim = Mth.clamp(aim + (aiming ? AIM_SPEED : -AIM_SPEED), 0.0f, 1.0f);

        for (Player nearby : minecraft.level.players()) {
            InteractionHand hand = cannonHand(nearby);
            if (hand == null) {
                BlackHoleArmCannonRenderer.forget(nearby.getId());
            } else if (BlackHoleArmCannonItem.isRecharging(nearby.getItemInHand(hand))) {
                spawnRechargeParticles(minecraft, nearby, hand);
            }
        }
    }

    /** Holding left-click with the cannon aims instead of attacking or breaking blocks. */
    public static boolean onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && canAim(player)) {
            event.setSwingHand(false);
            return true; // cancel the attack
        }
        return false;
    }

    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (aim > 0.0f && event.getPlayer() == Minecraft.getInstance().player) {
            event.setNewFovModifier(event.getNewFovModifier() * (1.0f - AIM_ZOOM * aim));
        }
    }

    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw layers = event.getLayeredDraw();
        layers.addConditionTo(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.CROSSHAIR, () -> !showSight());
        layers.addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, SIGHT_LAYER, ForgeLayeredDraw.CROSSHAIR,
            BlackHoleArmCannonClientHandler::renderSight);
    }

    // --- The sight ---

    private static boolean showSight() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && cannonHand(minecraft.player) != null
            && minecraft.options.getCameraType().isFirstPerson();
    }

    /**
     * Each attack mode has its own sight made of floating pixels. Aiming pulls the pixels in tight
     * and makes them brighter. While the black hole is recharging, they go dim and drift wide.
     */
    private static void renderSight(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!showSight()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ItemStack cannon = player.getItemInHand(cannonHand(player));
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float aimed = aimProgress(partialTick);
        float time = (player.tickCount + partialTick) / 20.0f;
        boolean recharging = BlackHoleArmCannonItem.isRecharging(cannon);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;

        switch (BlackHoleArmCannonItem.getMode(cannon)) {
            case HOMING_BLOCKS -> renderHomingBlocksSight(graphics, cx, cy, time, aimed, recharging);
            case SPIKES -> renderSpikesSight(graphics, cx, cy, time, aimed, recharging,
                cooldown(cannon, BlackHoleArmCannonItem.Mode.SPIKES, partialTick));
            case BLACK_HOLE -> renderBlackHoleSight(graphics, cx, cy, time, aimed, recharging,
                cooldown(cannon, BlackHoleArmCannonItem.Mode.BLACK_HOLE, partialTick));
        }
    }

    /** How much of a mode's cooldown is left, from 1 (just fired) to 0 (ready). */
    private static float cooldown(ItemStack cannon, BlackHoleArmCannonItem.Mode mode, float partialTick) {
        long readyAt = BlackHoleArmCannonItem.getReadyAt(cannon, mode);
        return Mth.clamp((readyAt - Minecraft.getInstance().level.getGameTime() - partialTick) / mode.cooldownTicks, 0.0f, 1.0f);
    }

    /** Mode 1 (Homing Blocks): four floating pixels slowly circling the middle, bobbing in and out, with a dot in the middle. */
    private static void renderHomingBlocksSight(GuiGraphics graphics, int cx, int cy, float time, float aimed, boolean recharging) {
        float radius = Mth.lerp(aimed, 11.0f, 5.0f) + Mth.sin(time * 4.0f) * 1.2f + (recharging ? 6.0f : 0.0f);
        int brightness = recharging ? 0x60 : (int) Mth.lerp(aimed, 0xB0, 0xFF);
        int pixel = ARGB.color(brightness, 0xD0, 0x60, 0xFF);
        int middle = ARGB.color(brightness, 0xFF, 0xB0, 0xFF);

        for (int i = 0; i < 4; i++) {
            float angle = time * 0.8f + i * Mth.HALF_PI;
            int px = cx + Math.round(Mth.cos(angle) * radius);
            int py = cy + Math.round(Mth.sin(angle) * radius);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, pixel);
        }
        graphics.fill(cx, cy, cx + 1, cy + 1, middle);
    }

    /**
     * Mode 2 (Infinitite Spikes): eight pixels in a ring, like the ring of spikes, each one
     * popping upward in turn so a wave runs around it, with a bigger pixel in the middle for the
     * big spike. While the spikes are cooling down, the ring goes dim and slowly fills back in.
     */
    private static void renderSpikesSight(GuiGraphics graphics, int cx, int cy, float time, float aimed,
                                          boolean recharging, float cooldown) {
        float radius = Mth.lerp(aimed, 14.0f, 8.0f) + (recharging ? 6.0f : 0.0f);
        boolean dim = recharging || cooldown > 0.0f;
        int brightness = dim ? 0x60 : (int) Mth.lerp(aimed, 0xB0, 0xFF);
        int pixel = ARGB.color(brightness, 0xC0, 0x50, 0xFF);
        int ready = ARGB.color((int) Mth.lerp(aimed, 0xB0, 0xFF), 0xC0, 0x50, 0xFF);
        int middle = ARGB.color(brightness, 0xFF, 0xB0, 0xFF);
        // While cooling down, this many of the eight pixels have filled back in at full brightness.
        int filled = recharging ? 0 : Math.round((1.0f - cooldown) * 8);

        for (int i = 0; i < 8; i++) {
            float angle = i * Mth.TWO_PI / 8 - Mth.HALF_PI;
            float pop = Math.max(0.0f, Mth.sin(time * 6.0f - i * 0.8f)) * 3.0f;
            int px = cx + Math.round(Mth.cos(angle) * radius);
            int py = cy + Math.round(Mth.sin(angle) * radius - pop);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, cooldown > 0.0f && i < filled ? ready : pixel);
        }
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, middle);
    }

    /**
     * Mode 3 (Black Hole): pixels spiralling in toward a dark hollow square in the middle, like
     * things being sucked into a black hole. While cooling down, the spiral slows and dims.
     */
    private static void renderBlackHoleSight(GuiGraphics graphics, int cx, int cy, float time, float aimed,
                                             boolean recharging, float cooldown) {
        boolean dim = recharging || cooldown > 0.0f;
        int brightness = dim ? 0x60 : (int) Mth.lerp(aimed, 0xB0, 0xFF);
        float size = Mth.lerp(aimed, 1.0f, 0.6f) + (recharging ? 0.5f : 0.0f);
        float speed = dim ? 0.3f : 1.0f;
        int pixels = 10;
        for (int i = 0; i < pixels; i++) {
            // Each pixel slides inward along the spiral, then starts again at the outside.
            float along = (time * speed * 0.6f + i / (float) pixels) % 1.0f;
            float radius = (4.0f + 14.0f * (1.0f - along)) * size;
            float angle = along * Mth.TWO_PI * 1.5f + i * (Mth.TWO_PI / 2);
            int fade = (int) (brightness * Mth.clamp(along * 3.0f, 0.3f, 1.0f));
            int px = cx + Math.round(Mth.cos(angle) * radius);
            int py = cy + Math.round(Mth.sin(angle) * radius);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, ARGB.color(fade, 0xB0, 0x40, 0xFF));
        }
        // The hole: a dark middle with a purple edge.
        int edge = ARGB.color(brightness, 0xE0, 0x90, 0xFF);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, edge);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, ARGB.color(0xE0, 0x05, 0x00, 0x0A));
    }

    // --- Recharging ---

    /**
     * Purple energy streaming into the black hole from all around. Portal particles fly from where
     * they're given a "speed" offset to toward where they're spawned, so each one is spawned at the
     * black hole and given an offset out to where it should start.
     */
    private static void spawnRechargeParticles(Minecraft minecraft, Player player, InteractionHand hand) {
        Vec3 blackHole = blackHolePosition(minecraft, player, hand);
        var random = player.getRandom();
        for (int i = 0; i < RECHARGE_PARTICLES_PER_TICK; i++) {
            Vec3 from = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                .scale(1.5 + random.nextDouble());
            minecraft.level.addParticle(ParticleTypes.PORTAL, blackHole.x, blackHole.y, blackHole.z, from.x, from.y, from.z);
        }
    }

    /** Roughly where a player's cannon's black hole is (out past the end of the barrel). */
    private static Vec3 blackHolePosition(Minecraft minecraft, Player player, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            Vec3 look = player.getLookAngle();
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            float centred = 1.0f - aim;
            return player.getEyePosition().add(look.scale(1.4)).add(right.scale(side * 0.35 * centred)).add(0.0, -0.3 * centred, 0.0);
        }
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        return player.position().add(0.0, player.getBbHeight() * 0.45, 0.0)
            .add(forward.scale(1.1)).add(right.scale(side * 0.37));
    }
}
