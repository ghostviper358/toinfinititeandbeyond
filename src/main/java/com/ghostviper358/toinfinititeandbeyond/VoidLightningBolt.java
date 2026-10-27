package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The look of one Void Lightning strike (see VoidLightning, which does the damage): a lightning
 * bolt from the sky that's black on the inside with glowing purple edges, drawn by
 * VoidLightningBoltRenderer. It flickers for a moment, then it's gone. Seeing one flashes the
 * sky purple. Purely for show - it never sets anything on fire and is never saved.
 */
public class VoidLightningBolt extends Entity {

    private static final int LIFETIME_TICKS = 8;

    /** Picks the bolt's zig-zag shape. Each client picks its own, like vanilla lightning. */
    public final long seed;

    public VoidLightningBolt(EntityType<? extends VoidLightningBolt> type, Level level) {
        super(type, level);
        this.seed = this.random.nextLong();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && this.tickCount == 1) {
            BlackHoleArmCannonClientHandler.onVoidLightning(this);
        }
        if (!this.level().isClientSide() && this.tickCount >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    // --- Plain entity bits: it can't be hurt, has no synced data, and is never saved ---

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }
}
