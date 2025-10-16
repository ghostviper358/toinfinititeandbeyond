package com.example.examplemod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Infinitite Sword registration.
 *
 * These tests verify:
 * - Sword is properly registered in the ITEMS registry
 * - Sword has correct registry name (jaspertutorial:infinitite_sword)
 * - Sword appears in COMBAT creative tab (NOT INGREDIENTS)
 * - Infinitite Ingot appears in INGREDIENTS tab
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InfiniteSword Registration and Creative Tab Tests")
class InfiniteSwordRegistrationTest {

    @Mock
    private BuildCreativeModeTabContentsEvent mockEvent;

    @Test
    @DisplayName("INFINITITE_SWORD should be registered in DeferredRegister")
    void testSwordIsRegistered() {
        // This will fail initially because INFINITITE_SWORD doesn't exist yet (TDD RED phase)
        assertNotNull(JasperTutorial.INFINITITE_SWORD,
            "INFINITITE_SWORD should be registered in JasperTutorial");

        assertNotNull(JasperTutorial.INFINITITE_SWORD.get(),
            "INFINITITE_SWORD should return a valid item instance");
    }

    @Test
    @DisplayName("INFINITITE_SWORD should have correct registry name")
    void testSwordRegistryName() {
        Item sword = JasperTutorial.INFINITITE_SWORD.get();

        ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(
            JasperTutorial.MOD_ID,
            "infinitite_sword"
        );

        // Get the registry name of the sword
        ResourceLocation actualId = BuiltInRegistries.ITEM.getKey(sword);

        assertEquals(expectedId, actualId,
            "Sword should be registered as jaspertutorial:infinitite_sword");
    }

    @Test
    @DisplayName("INFINITITE_SWORD should be an instance of InfiniteSwordItem")
    void testSwordInstanceType() {
        Item sword = JasperTutorial.INFINITITE_SWORD.get();

        assertTrue(sword instanceof InfiniteSwordItem,
            "Registered sword should be an instance of InfiniteSwordItem");
    }

    @Test
    @DisplayName("INFINITITE_SWORD should appear in COMBAT creative tab")
    void testSwordInCombatTab() {
        // Mock the event to return COMBAT tab
        when(mockEvent.getTabKey()).thenReturn(CreativeModeTabs.COMBAT);

        // Call the addCreative method
        JasperTutorial.addCreative(mockEvent);

        // Verify the sword was added to the tab
        verify(mockEvent).accept(JasperTutorial.INFINITITE_SWORD.get());
    }

    @Test
    @DisplayName("INFINITITE_SWORD should NOT appear in INGREDIENTS creative tab")
    void testSwordNotInIngredientsTab() {
        // Mock the event to return INGREDIENTS tab
        when(mockEvent.getTabKey()).thenReturn(CreativeModeTabs.INGREDIENTS);

        // Call the addCreative method
        JasperTutorial.addCreative(mockEvent);

        // Verify the sword was NOT added to the INGREDIENTS tab
        verify(mockEvent, never()).accept(JasperTutorial.INFINITITE_SWORD.get());
    }

    @Test
    @DisplayName("INFINITITE_INGOT should still appear in INGREDIENTS creative tab")
    void testIngotInIngredientsTab() {
        // Mock the event to return INGREDIENTS tab
        when(mockEvent.getTabKey()).thenReturn(CreativeModeTabs.INGREDIENTS);

        // Call the addCreative method
        JasperTutorial.addCreative(mockEvent);

        // Verify the ingot is still added to INGREDIENTS
        verify(mockEvent).accept(JasperTutorial.INFINITITE_INGOT.get());
    }

    @Test
    @DisplayName("INFINITITE_SWORD should be stackable to 1 (weapon standard)")
    void testSwordStackSize() {
        Item sword = JasperTutorial.INFINITITE_SWORD.get();
        ItemStack swordStack = new ItemStack(sword);

        assertEquals(1, swordStack.getMaxStackSize(),
            "Swords should have max stack size of 1");
    }

    @Test
    @DisplayName("INFINITITE_SWORD should be damageable")
    void testSwordIsDamageable() {
        Item sword = JasperTutorial.INFINITITE_SWORD.get();
        ItemStack swordStack = new ItemStack(sword);

        assertTrue(swordStack.isDamageableItem(),
            "Sword should be a damageable item");
    }

    @Test
    @DisplayName("INFINITITE_SWORD should have correct max damage")
    void testSwordMaxDamage() {
        Item sword = JasperTutorial.INFINITITE_SWORD.get();
        ItemStack swordStack = new ItemStack(sword);

        assertEquals(3124, swordStack.getMaxDamage(),
            "Sword should have 3124 max durability");
    }
}
