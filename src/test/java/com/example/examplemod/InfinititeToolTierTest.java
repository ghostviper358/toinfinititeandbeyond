package com.example.examplemod;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InfinititeToolTier.
 *
 * These tests verify the material properties of the Infinitite tier:
 * - Durability (uses) = 3124 (twice that of Diamond Sword which has 1562)
 * - Mining speed = 8.0f (between Diamond 8.0f and Netherite 9.0f)
 * - Attack damage bonus = 7.0f (for total of 11 damage with sword base of 4)
 * - Mining level = 3 (same as Diamond and Netherite)
 * - Enchantability = 15 (higher than Diamond's 10, same as Gold)
 * - Repair ingredient = Infinitite Ingot
 */
@DisplayName("InfinititeToolTier Material Properties Tests")
class InfinititeToolTierTest {

    private Tier infinititeTier;

    @BeforeEach
    void setUp() {
        // This will fail initially because InfinititeToolTier doesn't exist yet (TDD RED phase)
        infinititeTier = InfinititeToolTier.INFINITITE;
    }

    @Test
    @DisplayName("Should have correct durability of 3124 uses (2x Diamond)")
    void testDurability() {
        // Diamond sword has 1562 durability
        // Infinitite should have twice that: 3124
        assertEquals(3124, infinititeTier.getUses(),
            "Infinitite tools should last twice as long as Diamond tools");
    }

    @Test
    @DisplayName("Should have mining speed of 8.0f")
    void testMiningSpeed() {
        // Mining speed affects how fast blocks are broken
        // Diamond is 8.0f, Netherite is 9.0f
        // Infinitite should be 8.0f (balanced between the two)
        assertEquals(8.0f, infinititeTier.getSpeed(), 0.001f,
            "Infinitite should have mining speed of 8.0f");
    }

    @Test
    @DisplayName("Should have attack damage bonus of 7.0f for total 11 damage")
    void testAttackDamageBonus() {
        // Sword base damage is 4
        // Diamond tier adds 3.0f (total 7 damage)
        // Netherite tier adds 4.0f (total 8 damage)
        // Infinitite tier adds 7.0f (total 11 damage)
        assertEquals(7.0f, infinititeTier.getAttackDamageBonus(), 0.001f,
            "Infinitite should have attack damage bonus of 7.0f for total 11 damage");
    }

    @Test
    @DisplayName("Should have mining level 3 (Diamond/Netherite tier)")
    void testMiningLevel() {
        // Level 0 = Wood/Gold
        // Level 1 = Stone
        // Level 2 = Iron
        // Level 3 = Diamond/Netherite
        assertEquals(BlockTags.NEEDS_DIAMOND_TOOL, infinititeTier.getIncorrectBlocksForDrops(),
            "Infinitite should have mining level 3 (Diamond tier)");
    }

    @Test
    @DisplayName("Should have enchantability value of 15")
    void testEnchantability() {
        // Enchantability affects how good enchantments you can get
        // Wood = 15, Stone = 5, Iron = 14, Diamond = 10, Gold = 22, Netherite = 15
        // Infinitite should be 15 (same as Netherite, better than Diamond)
        assertEquals(15, infinititeTier.getEnchantmentValue(),
            "Infinitite should have enchantability of 15 for good enchantments");
    }

    @Test
    @DisplayName("Should use Infinitite Ingot as repair ingredient")
    void testRepairIngredient() {
        Ingredient repairIngredient = infinititeTier.getRepairIngredient();

        assertNotNull(repairIngredient, "Repair ingredient should not be null");

        // Test that Infinitite Ingot is accepted as repair material
        assertTrue(repairIngredient.test(JasperTutorial.INFINITITE_INGOT.get().getDefaultInstance()),
            "Infinitite tools should be repairable with Infinitite Ingot");
    }

    @Test
    @DisplayName("Should not accept other items as repair ingredient")
    void testInvalidRepairIngredient() {
        Ingredient repairIngredient = infinititeTier.getRepairIngredient();

        // Test that other items are NOT accepted
        assertFalse(repairIngredient.test(JasperTutorial.SAPPHIRE.get().getDefaultInstance()),
            "Infinitite tools should not be repairable with Sapphire");
    }
}
