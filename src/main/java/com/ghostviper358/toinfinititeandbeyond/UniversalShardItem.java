package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Universal Shard - a fragment of pure cosmic/multiversal power.
 *
 * This is the very first build step for the Universal/Cosmic gear line
 * (see docs/requirements/universal_shard_and_cosmic_apple.md). It is not
 * mined or crafted - its sole future source is a drop from the
 * Universe Destroyer boss, which has not been built yet. For now this
 * item simply exists so it can be referenced by later recipes (like the
 * Cosmic Apple).
 *
 * Visual: pink/purple "purpal" texture evoking the End Gateway portal
 * block's swirling look, rendered with the enchantment-glint ("foil")
 * shimmer so the item reads as glowing/magical in the inventory and
 * when held. True animated swirling (like the real Gateway block) is
 * left as a future enhancement - see the requirements doc.
 */
public class UniversalShardItem extends Item {

    public UniversalShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Always shimmer with the enchantment glint effect to sell the "glows" requirement.
        return true;
    }
}