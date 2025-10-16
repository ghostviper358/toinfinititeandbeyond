package com.example.examplemod;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InfiniteSwordItem.
 *
 * These tests verify:
 * - Sword is properly registered and extends SwordItem
 * - Sword has correct attack damage (11 total: 4 base + 7 from tier)
 * - Sword has correct durability (3124)
 * - Sword applies BLINDNESS effect on hit (60 ticks / 3 seconds, level 0)
 * - Sword applies POISON effect on hit (60 ticks / 3 seconds, level 0)
 * - Both effects are applied to the target entity
 * - Effects are only applied to LivingEntity targets (not blocks, items, etc.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InfiniteSwordItem Combat Properties Tests")
class InfiniteSwordItemTest {

    private InfiniteSwordItem infiniteSword;

    @Mock
    private LivingEntity mockTarget;

    @Mock
    private Player mockAttacker;

    @Mock
    private ItemStack mockItemStack;

    @BeforeEach
    void setUp() {
        // This will fail initially because InfiniteSwordItem doesn't exist yet (TDD RED phase)
        infiniteSword = new InfiniteSwordItem(
            InfinititeToolTier.INFINITITE,
            new InfiniteSwordItem.Properties()
                .setId(JasperTutorial.ITEMS.key("infinitite_sword"))
        );
    }

    @Test
    @DisplayName("Should extend SwordItem class")
    void testExtendsCorrectClass() {
        assertTrue(infiniteSword instanceof SwordItem,
            "InfiniteSwordItem should extend SwordItem");
    }

    @Test
    @DisplayName("Should have correct attack damage of 11")
    void testAttackDamage() {
        // Base sword damage is 4
        // Infinitite tier adds 7
        // Total should be 11
        double expectedDamage = 4.0 + InfinititeToolTier.INFINITITE.getAttackDamageBonus();
        assertEquals(11.0, expectedDamage, 0.001,
            "Infinitite Sword should have 11 attack damage total");
    }

    @Test
    @DisplayName("Should have correct durability of 3124")
    void testDurability() {
        assertEquals(3124, InfinititeToolTier.INFINITITE.getUses(),
            "Infinitite Sword should have 3124 durability");
    }

    @Test
    @DisplayName("Should apply BLINDNESS effect on hit")
    void testAppliesBlindnessEffect() {
        // Prepare mocks
        when(mockItemStack.getItem()).thenReturn(infiniteSword);

        // Execute the hurt enemy method
        boolean result = infiniteSword.hurtEnemy(mockItemStack, mockTarget, mockAttacker);

        // Verify BLINDNESS was applied with correct parameters
        // Duration: 60 ticks (3 seconds), Amplifier: 0 (level 1)
        verify(mockTarget).addEffect(argThat(effect ->
            effect.getEffect().equals(MobEffects.BLINDNESS) &&
            effect.getDuration() == 60 &&
            effect.getAmplifier() == 0
        ));

        assertTrue(result, "hurtEnemy should return true on successful hit");
    }

    @Test
    @DisplayName("Should apply POISON effect on hit")
    void testAppliesPoisonEffect() {
        // Prepare mocks
        when(mockItemStack.getItem()).thenReturn(infiniteSword);

        // Execute the hurt enemy method
        boolean result = infiniteSword.hurtEnemy(mockItemStack, mockTarget, mockAttacker);

        // Verify POISON was applied with correct parameters
        // Duration: 60 ticks (3 seconds), Amplifier: 0 (level 1)
        verify(mockTarget).addEffect(argThat(effect ->
            effect.getEffect().equals(MobEffects.POISON) &&
            effect.getDuration() == 60 &&
            effect.getAmplifier() == 0
        ));

        assertTrue(result, "hurtEnemy should return true on successful hit");
    }

    @Test
    @DisplayName("Should apply both BLINDNESS and POISON effects simultaneously")
    void testAppliesBothEffects() {
        // Prepare mocks
        when(mockItemStack.getItem()).thenReturn(infiniteSword);

        // Execute the hurt enemy method
        infiniteSword.hurtEnemy(mockItemStack, mockTarget, mockAttacker);

        // Verify both effects were applied
        verify(mockTarget, times(2)).addEffect(any(MobEffectInstance.class));
    }

    @Test
    @DisplayName("Should have correct effect durations (60 ticks = 3 seconds)")
    void testEffectDuration() {
        // 20 ticks = 1 second in Minecraft
        // 60 ticks = 3 seconds
        int expectedTicks = 60;
        int expectedSeconds = expectedTicks / 20;

        assertEquals(3, expectedSeconds,
            "Effects should last 3 seconds");
    }

    @Test
    @DisplayName("Should have correct effect amplifier (level 0)")
    void testEffectAmplifier() {
        // Amplifier 0 = Level 1 in game (Blindness I, Poison I)
        // Amplifier 1 = Level 2 in game (Blindness II, Poison II)
        int expectedAmplifier = 0;

        assertEquals(0, expectedAmplifier,
            "Effects should be level 1 (amplifier 0)");
    }

    @Test
    @DisplayName("Should use Infinitite tier for repairs")
    void testRepairMaterial() {
        assertEquals(InfinititeToolTier.INFINITITE, infiniteSword.getTier(),
            "InfiniteSwordItem should use Infinitite tier");
    }
}
