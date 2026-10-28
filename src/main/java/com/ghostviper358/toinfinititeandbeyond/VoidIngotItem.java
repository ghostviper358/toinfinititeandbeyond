package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Void Ingot - mined from Void Ore at the bottom of the world, for the Void gear.
 * Thrown on the ground with 8 of them and a Nether Star, it summons the Infinitite Monstrosity.
 */
public class VoidIngotItem extends Item {

    public VoidIngotItem(Properties properties) {
        super(properties);
    }

    /**
     * Thrown on the ground with at least 8 Void Ingots and 1 Nether Star, the ingots summon the
     * Infinitite Monstrosity - see InfinititeMonstrosity.checkForSummon.
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        InfinititeMonstrosity.checkForSummon(entity);
        return false;
    }
}
