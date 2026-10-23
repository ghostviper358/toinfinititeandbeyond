package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Client-side key handling for the Universal Leggings. See docs/requirements/universal_leggings.md.
 *
 * - Warp Dash: two presses of sneak within {@link #DOUBLE_TAP_TICKS} sends the dash to the
 *   server (UniversalLeggingsItem.handleWarpDash does the actual teleport).
 * - Star Jumps: pressing jump while in mid-air launches you upward again, up to
 *   UniversalLeggingsItem.EXTRA_JUMPS times before you land. Player movement is decided by the
 *   client in Minecraft, so the jump itself happens here; the server is told so it can count
 *   it and clear the fall damage.
 */
public final class UniversalLeggingsClientHandler {

    /** Max gap between the two sneak presses of a double-tap (0.35 seconds). */
    private static final int DOUBLE_TAP_TICKS = 7;

    /** Upward speed of a Star Jump - a bit stronger than a normal jump. */
    private static final double EXTRA_JUMP_POWER = 0.65;

    private static boolean sneakWasDown = false;
    private static boolean jumpWasDown = false;
    private static int ticksSinceSneakTap = Integer.MAX_VALUE;
    private static int extraJumpsUsed = 0;

    private UniversalLeggingsClientHandler() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !UniversalLeggingsItem.isWearing(player)
                || minecraft.screen != null) {
            sneakWasDown = false;
            jumpWasDown = false;
            ticksSinceSneakTap = Integer.MAX_VALUE;
            return;
        }

        boolean sneakDown = minecraft.options.keyShift.isDown();
        if (ticksSinceSneakTap != Integer.MAX_VALUE) {
            ticksSinceSneakTap++;
        }
        if (sneakDown && !sneakWasDown) {
            if (ticksSinceSneakTap <= DOUBLE_TAP_TICKS) {
                UniversalLeggingsNetworking.sendWarpDashToServer();
                ticksSinceSneakTap = Integer.MAX_VALUE;
            } else {
                ticksSinceSneakTap = 0;
            }
        }
        sneakWasDown = sneakDown;

        if (player.onGround() || player.isInWater() || player.onClimbable() || player.getAbilities().flying) {
            extraJumpsUsed = 0;
        }
        boolean jumpDown = minecraft.options.keyJump.isDown();
        // Only a fresh press counts, so holding jump after a normal jump doesn't burn extra jumps.
        if (jumpDown && !jumpWasDown && !player.onGround() && !player.isInWater() && !player.onClimbable()
                && !player.getAbilities().flying && extraJumpsUsed < UniversalLeggingsItem.EXTRA_JUMPS) {
            extraJumpsUsed++;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, EXTRA_JUMP_POWER, motion.z);
            player.resetFallDistance();
            UniversalLeggingsNetworking.sendExtraJumpToServer();
        }
        jumpWasDown = jumpDown;
    }
}
