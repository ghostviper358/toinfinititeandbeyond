package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * The Destroyer's Crown - the trophy the Universal Destroyer drops: its crown of broken black
 * spikes, shrunk down to fit you. A helmet with strong armor (see DESTROYERS_CROWN in
 * ToInfinititeAndBeyond.java) and no special powers - just a faint purple glow around whoever
 * wears it, so everyone knows who saved the universe.
 */
public class DestroyersCrownItem extends Item {

    public DestroyersCrownItem(Properties properties) {
        super(properties);
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof DestroyersCrownItem;
    }

    /** Called every player tick on the server: the purple glow. */
    public static void tick(Player player, ServerLevel level) {
        if (player.tickCount % 5 != 0 || !isWearing(player)) {
            return;
        }
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getEyeY() + 0.5, player.getZ(), 1, 0.25, 0.1, 0.25, 0.0);
        if (player.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getEyeY() + 0.4, player.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
        }
    }
}
