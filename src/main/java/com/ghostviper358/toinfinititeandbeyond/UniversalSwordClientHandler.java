package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Client-side charge tracking for the Universal Sword's shockwave ultimate.
 * See docs/requirements/universal_sword_shockwave.md.
 *
 * Vanilla has no built-in "continuous hold" detection for the attack key the way it does for
 * right-click items (that's Item#getUseDuration/releaseUsing - see InfinititeBowItem for that
 * *different*, right-click charge pattern elsewhere in this mod). Only the client knows whether
 * the attack key is physically held down, so this polls
 * Minecraft.getInstance().options.keyAttack every client tick while the local player is holding
 * the Universal Sword in their main hand, and shows the charge-up visuals (growing energy orb +
 * sword glow) locally. Once the hold reaches UniversalSwordItem.CHARGE_DURATION_TICKS, it sends
 * the network trigger packet to the server exactly once and resets, so the ability actually
 * fires server-side (see UniversalSwordNetworking / UniversalSwordItem.handleShockwaveTrigger).
 *
 * Releasing the attack key (or switching away from the sword) before the charge completes
 * cancels it outright - the charge counter just resets to zero with no partial-charge effect.
 * This is meant to read as a deliberate, weighty "hold and unleash" ultimate rather than an
 * attack that's always at least a little bit charged.
 *
 * Design note: the charge-up particles here are deliberately local-only cosmetics (spawned via
 * ClientLevel#addParticle, which never leaves this client), so only the charging player sees
 * the buildup. The actual payoff - the five rings, the storm, the kills/floats - is spawned
 * server-side via ServerLevel#sendParticles once the charge fires, which broadcasts to everyone
 * nearby. This avoids needing a second (server -> all nearby clients) packet just to mirror
 * charge-in-progress cosmetics to bystanders, which didn't seem worth the extra complexity for
 * a purely decorative buildup effect.
 */
public final class UniversalSwordClientHandler {

    private static int chargeTicks = 0;

    /**
     * Whether we were holding attack-with-the-sword-out last tick - tracked separately from
     * chargeTicks so the server-side storm (see UniversalSwordItem.handleStartCharge/
     * handleStopCharge) is tied purely to "is the key currently held", independent of whether a
     * full charge has fired yet. This lets the storm keep running across repeated auto-fires (as
     * long as the player never actually lets go) and clears the instant they do let go.
     */
    private static boolean holding = false;

    private UniversalSwordClientHandler() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        boolean holdingNow = player != null && minecraft.level != null && isHoldingUniversalSword(player)
                && minecraft.options.keyAttack.isDown();

        if (holdingNow != holding) {
            holding = holdingNow;
            if (holding) {
                UniversalSwordNetworking.sendStartChargeToServer();
            } else {
                UniversalSwordNetworking.sendStopChargeToServer();
            }
        }

        if (!holdingNow) {
            chargeTicks = 0;
            return;
        }

        chargeTicks++;
        spawnChargeParticles(minecraft, player, chargeTicks);

        if (chargeTicks >= UniversalSwordItem.CHARGE_DURATION_TICKS) {
            chargeTicks = 0;
            UniversalSwordNetworking.sendTriggerToServer();
        }
    }

    private static boolean isHoldingUniversalSword(Player player) {
        return player.getMainHandItem().is(ToInfinititeAndBeyond.UNIVERSAL_SWORD.get());
    }

    /**
     * Purely cosmetic local particles: a purple/white energy orb around the player that grows
     * as the charge progresses, plus an occasional sparkle out along the look direction to read
     * as the sword itself glowing (there's no 3D model/pose to actually light up without a
     * proper animation library - GeckoLib is explicitly deferred to a future task; see the
     * requirements doc).
     */
    private static void spawnChargeParticles(Minecraft minecraft, LocalPlayer player, int ticksCharged) {
        double progress = Math.min(1.0, ticksCharged / (double) UniversalSwordItem.CHARGE_DURATION_TICKS);
        double radius = 0.3 + progress * 1.3;

        double cx = player.getX();
        double cy = player.getY() + player.getEyeHeight() * 0.6;
        double cz = player.getZ();

        var random = player.getRandom();
        for (int i = 0; i < 2; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = random.nextDouble() * Math.PI;
            double px = cx + radius * Math.sin(phi) * Math.cos(theta);
            double py = cy + radius * Math.cos(phi) * 0.6;
            double pz = cz + radius * Math.sin(phi) * Math.sin(theta);

            minecraft.level.addParticle(ParticleTypes.PORTAL, px, py, pz, 0.0, 0.01, 0.0);
            if (ticksCharged % 4 == 0) {
                minecraft.level.addParticle(ParticleTypes.WITCH, px, py, pz, 0.0, 0.0, 0.0);
            }
        }

        // Sword "glow" - a sparkle out along the look direction, roughly where the held item
        // would be, since there's no model attachment point to spawn particles directly on the
        // blade without a real animation rig.
        if (ticksCharged % 2 == 0) {
            Vec3 look = player.getLookAngle();
            minecraft.level.addParticle(ParticleTypes.END_ROD,
                player.getX() + look.x * 0.8, player.getEyeY() - 0.3 + look.y * 0.8, player.getZ() + look.z * 0.8,
                0.0, 0.02, 0.0);
        }
    }
}