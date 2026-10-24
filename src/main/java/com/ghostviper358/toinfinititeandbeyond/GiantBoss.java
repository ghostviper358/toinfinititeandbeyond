package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A boss with more health than Minecraft allows. Vanilla caps every mob's max health at 1024, so
 * a giant boss keeps its real health in a separate pool, {@link #getBossMaxHealth()} big:
 *
 * Its vanilla health always stays full. Every change vanilla makes to it - damage after armor and
 * every other reduction, healing, /kill - comes through {@link #setHealth}, which applies the
 * change to the real pool instead, and only lets the vanilla health drop to 0 (so the boss dies)
 * once the pool is empty. The pool is synced to clients (for the boss bar) and saved with the boss.
 */
public abstract class GiantBoss extends Monster implements ModBoss {

    private static final EntityDataAccessor<Float> BOSS_HEALTH = SynchedEntityData.defineId(GiantBoss.class, EntityDataSerializers.FLOAT);
    private static final String BOSS_HEALTH_KEY = "BossHealth";

    protected GiantBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /** This boss's real max health - may be far over vanilla's 1024 limit. */
    protected abstract float getBossMaxHealth();

    public float getBossHealth() {
        return this.entityData.get(BOSS_HEALTH);
    }

    @Override
    public float getBossHealthFraction() {
        return Mth.clamp(getBossHealth() / getBossMaxHealth(), 0.0f, 1.0f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS_HEALTH, getBossMaxHealth());
    }

    @Override
    public void setHealth(float health) {
        float change = health - this.getHealth();
        float bossHealth = Mth.clamp(getBossHealth() + change, 0.0f, getBossMaxHealth());
        this.entityData.set(BOSS_HEALTH, bossHealth);
        super.setHealth(bossHealth <= 0.0f ? 0.0f : this.getMaxHealth());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat(BOSS_HEALTH_KEY, getBossHealth());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(BOSS_HEALTH, input.getFloatOr(BOSS_HEALTH_KEY, getBossMaxHealth()));
    }
}
