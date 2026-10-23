package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
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
 * the Universal Sword in their main hand. Once the key has been held a moment (see
 * CHARGE_START_DELAY_TICKS) it shows the charge-up visuals (growing energy orb +
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

    /**
     * How long the attack key has been held down with the sword out. Charging only starts once
     * it's been held for {@link #CHARGE_START_DELAY_TICKS}, so a normal click plays an attack from
     * the combo (see UniversalSwordAnimations) instead of flashing the charge pose and storm.
     */
    private static int heldTicks = 0;

    private static final int CHARGE_START_DELAY_TICKS = 6; // 0.3 seconds

    private UniversalSwordClientHandler() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null) {
            heldTicks = 0;
            holding = false;
            UniversalSwordAnimations.clearAll();
            return;
        }

        for (Player nearby : minecraft.level.players()) {
            spawnEnchantLetters(minecraft, nearby);
        }

        boolean keyHeld = isHoldingUniversalSword(player) && minecraft.options.keyAttack.isDown();
        heldTicks = keyHeld ? heldTicks + 1 : 0;
        boolean holdingNow = heldTicks >= CHARGE_START_DELAY_TICKS;

        if (holdingNow != holding) {
            holding = holdingNow;
            UniversalSwordAnimations.setCharging(player.getId(), holding);
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

    /** Floating enchanting-table letters drifting into the sword - a trickle normally, a stream while charging. */
    private static final float LETTERS_PER_TICK = 0.6f;
    private static final int LETTERS_PER_TICK_CHARGING = 5;

    /**
     * Enchanting-table letters (the same glyphs that float from bookshelves into an enchanting
     * table) drifting into the Universal Sword of any player holding it, the local player
     * included. The vanilla ENCHANT particle flies from (position + velocity) toward position, so
     * each letter is spawned at the sword and given an offset out to where it should start.
     * While charging, many more stream in to the sword raised overhead.
     */
    private static void spawnEnchantLetters(Minecraft minecraft, Player player) {
        if (!isHoldingUniversalSword(player) || player.isInvisible()) {
            return;
        }
        var random = player.getRandom();
        boolean charging = UniversalSwordAnimations.chargeGlow(player, 0.0f) > 0.0f;

        Vec3 sword;
        if (charging) {
            sword = player.position().add(0.0, player.getBbHeight() + 0.9, 0.0);
        } else if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            // In first person, aim at where the sword sits on screen (lower right of the view).
            Vec3 look = player.getLookAngle();
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
            sword = player.getEyePosition().add(look.scale(0.9)).add(right.scale(side * 0.45)).add(0.0, -0.35, 0.0);
        } else {
            // Roughly the sword hand, out in front of the body and to the sword side.
            float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
            float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
            sword = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0)
                .add(forward.scale(0.5)).add(right.scale(side * 0.4));
        }

        int count = charging ? LETTERS_PER_TICK_CHARGING : (random.nextFloat() < LETTERS_PER_TICK ? 1 : 0);
        double spread = charging ? 2.5 : 1.2;
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() - 0.5) * 2.0 * spread;
            double dy = (random.nextDouble() - 0.3) * spread;
            double dz = (random.nextDouble() - 0.5) * 2.0 * spread;
            minecraft.level.addParticle(ParticleTypes.ENCHANT, sword.x, sword.y, sword.z, dx, dy, dz);
        }
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