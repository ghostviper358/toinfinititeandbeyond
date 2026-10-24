package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Monstrosity Heart - dropped by the Infinitite Monstrosity: its still-glowing purple heart.
 * Thrown on the ground with 10 Infinitite Ingots and a Universal Shard, it summons the Living Void.
 * See docs/requirements/infinitite_monstrosity.md.
 */
public class MonstrosityHeartItem extends Item {

    public MonstrosityHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Thrown on the ground with at least 10 Infinitite Ingots and 1 Universal Shard, the heart
     * summons the Living Void - see LivingVoid.checkForSummon.
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        LivingVoid.checkForSummon(entity);
        return false;
    }
}
