package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Void Seal - the last lock on the Universal Destroyer's prison, dropped by the Living Void.
 * Place it, then break it, and the Destroyer's summoning ritual begins (see
 * UniversalDestroyer.startRitual).
 *
 * It drops nothing when broken (the seal is used up), except when a Destroyer is already nearby -
 * then the seal holds and pops back out. Explosions and pistons can't break or move it, so only a
 * player can set the Destroyer free.
 */
public class VoidSealBlock extends Block {

    public VoidSealBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && !UniversalDestroyer.startRitual(serverLevel, pos)) {
            player.displayClientMessage(Component.literal("§5The seal holds - the Destroyer is already here."), true);
            if (!player.isCreative()) {
                popResource(level, pos, new ItemStack(this));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Darkness seeping up out of the seal. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                pos.getZ() + random.nextDouble(), 0.0, 0.03, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
        }
    }
}
