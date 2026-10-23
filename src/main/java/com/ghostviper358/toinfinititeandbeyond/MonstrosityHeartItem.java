package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Monstrosity Heart - a trophy dropped by the Infinitite Monstrosity: its still-glowing purple
 * heart. No use yet (a future recipe or boss could need it); it just shimmers.
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
}
