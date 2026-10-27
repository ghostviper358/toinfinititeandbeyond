package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a purple Infinitite Spike, grown by the Black Hole Arm Cannon's spike attack (see
 * CannonSpikes). Spikes are shaped like pointed dripstone - a thick base, a middle and a sharp
 * tip - and can be walked into but hurt anything that does.
 *
 * Each spike block crumbles away into purple dust by itself a few seconds after it appears. That's
 * a scheduled block tick, so it happens even if you walk away and come back later.
 */
public class InfinititeSpikeBlock extends Block {

    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;

    /** How long a spike lasts before it crumbles (3 seconds). */
    private static final int LIFETIME_TICKS = 60;
    /** Damage for walking into a spike after it's grown (the big hit when it erupts is in CannonSpikes). */
    private static final float TOUCH_DAMAGE = 4.0f;

    private static final VoxelShape TIP = Block.column(6.0, 0.0, 16.0);
    private static final VoxelShape MIDDLE = Block.column(10.0, 0.0, 16.0);
    private static final VoxelShape BASE = Block.column(12.0, 0.0, 16.0);

    public InfinititeSpikeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(THICKNESS, DripstoneThickness.TIP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(THICKNESS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(THICKNESS)) {
            case BASE -> BASE;
            case TIP, TIP_MERGE -> TIP;
            default -> MIDDLE;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        // Only when the spike first appears - not when it grows and this block becomes a thicker part.
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, LIFETIME_TICKS + level.getRandom().nextInt(10));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        Vec3 middle = Vec3.atCenterOf(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), middle.x, middle.y, middle.z, 12, 0.25, 0.4, 0.25, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.x, middle.y, middle.z, 6, 0.2, 0.4, 0.2, 0.02);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.5f, 0.7f + random.nextFloat() * 0.3f);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity) {
            entity.hurtServer(serverLevel, level.damageSources().stalagmite(), TOUCH_DAMAGE);
        }
    }
}
