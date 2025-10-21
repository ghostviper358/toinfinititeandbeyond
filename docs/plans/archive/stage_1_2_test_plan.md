# Stage 1.2 - Infinitite Sword Test Plan

## Test-Driven Development (TDD) Approach

This document outlines the comprehensive test suite created BEFORE implementing the Infinitite Sword feature. Following TDD principles (Red-Green-Refactor), we first write failing tests that define expected behavior, then implement code to make them pass.

## Test Infrastructure Setup

### Dependencies Added to build.gradle
- **JUnit 5 (Jupiter)**: Modern testing framework
  - junit-jupiter-api: 5.10.0
  - junit-jupiter-engine: 5.10.0
  - junit-jupiter-params: 5.10.0
- **Mockito**: For mocking Minecraft objects in tests
  - mockito-core: 5.5.0
  - mockito-junit-jupiter: 5.5.0

### Gradle Test Configuration
```gradle
test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
        showStandardStreams = false
    }
}
```

## Test Suite Overview

### 1. InfinititeToolTierTest.java
**Purpose**: Verify material properties of the Infinitite tier

**Tests:**
1. **testDurability()** - Verifies 3124 uses (2x Diamond sword's 1562)
2. **testMiningSpeed()** - Verifies speed of 8.0f (same as Diamond)
3. **testAttackDamageBonus()** - Verifies 7.0f bonus (for total 11 damage)
4. **testMiningLevel()** - Verifies level 3 (Diamond/Netherite tier)
5. **testEnchantability()** - Verifies value of 15 (better than Diamond's 10)
6. **testRepairIngredient()** - Verifies Infinitite Ingot is used for repairs
7. **testInvalidRepairIngredient()** - Verifies other items cannot repair

**Expected Material Properties:**
```
Durability: 3124 uses
Mining Speed: 8.0f
Attack Damage Bonus: 7.0f
Mining Level: 3 (NEEDS_DIAMOND_TOOL)
Enchantability: 15
Repair Ingredient: Infinitite Ingot
```

### 2. InfiniteSwordItemTest.java
**Purpose**: Verify combat mechanics and item behavior

**Tests:**
1. **testExtendsCorrectClass()** - Verifies extends SwordItem
2. **testAttackDamage()** - Verifies total damage of 11 (4 base + 7 tier)
3. **testDurability()** - Verifies 3124 max durability
4. **testAppliesBlindnessEffect()** - Verifies BLINDNESS applied (60 ticks, amplifier 0)
5. **testAppliesPoisonEffect()** - Verifies POISON applied (60 ticks, amplifier 0)
6. **testAppliesBothEffects()** - Verifies both effects applied simultaneously
7. **testEffectDuration()** - Verifies 60 ticks = 3 seconds
8. **testEffectAmplifier()** - Verifies amplifier 0 = Level 1
9. **testRepairMaterial()** - Verifies uses Infinitite tier

**Expected Combat Mechanics:**
```
Attack Damage: 11 (4 base + 7 from tier)
Durability: 3124
On Hit Effects:
  - BLINDNESS: 60 ticks (3 seconds), Level 0
  - POISON: 60 ticks (3 seconds), Level 0
```

### 3. InfiniteSwordRegistrationTest.java
**Purpose**: Verify proper registration and creative tab placement

**Tests:**
1. **testSwordIsRegistered()** - Verifies INFINITITE_SWORD exists in DeferredRegister
2. **testSwordRegistryName()** - Verifies registry name "jaspertutorial:infinitite_sword"
3. **testSwordInstanceType()** - Verifies is instance of InfiniteSwordItem
4. **testSwordInCombatTab()** - Verifies appears in COMBAT creative tab
5. **testSwordNotInIngredientsTab()** - Verifies does NOT appear in INGREDIENTS
6. **testIngotInIngredientsTab()** - Verifies Ingot still in INGREDIENTS
7. **testSwordStackSize()** - Verifies max stack size of 1 (weapon standard)
8. **testSwordIsDamageable()** - Verifies is a damageable item
9. **testSwordMaxDamage()** - Verifies 3124 max damage value

**Expected Registration:**
```
Registry Name: jaspertutorial:infinitite_sword
Type: InfiniteSwordItem extends SwordItem
Creative Tabs: COMBAT (NOT INGREDIENTS)
Stack Size: 1
Is Damageable: true
Max Damage: 3124
```

## Current Status: RED Phase (Tests Failing)

All tests are currently failing with compilation errors, which is expected and correct for TDD:
- InfinititeToolTier class doesn't exist
- InfiniteSwordItem class doesn't exist
- INFINITITE_SWORD registration doesn't exist
- addCreative method needs visibility adjustment

## Next Steps: GREEN Phase (Make Tests Pass)

1. Create InfinititeToolTier.java implementing Tier interface
2. Create InfiniteSwordItem.java extending SwordItem with hurtEnemy() override
3. Register INFINITITE_SWORD in JasperTutorial.java
4. Update addCreative() method to add sword to COMBAT tab
5. Make addCreative() package-private for testing access
6. Run tests to verify all pass

## Benefits of This TDD Approach

1. **Clear Requirements**: Tests document exactly what the feature should do
2. **Confidence**: When tests pass, we know the feature works correctly
3. **Regression Prevention**: Tests catch bugs if we accidentally break something
4. **Design Guidance**: Writing tests first helps design better APIs
5. **Living Documentation**: Tests serve as examples of how to use the code

## Test Coverage Summary

- **Material Properties**: 7 tests covering all tier attributes
- **Combat Mechanics**: 9 tests covering damage, effects, and durability
- **Registration**: 9 tests covering item registration and creative tabs
- **Total Tests**: 25 comprehensive tests

All tests include descriptive names and documentation explaining what they verify and why it matters.
