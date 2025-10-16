# CORRECTED Infinitite Sword Implementation Plan - MinecraftForge 1.21.8

**IMPORTANT**: This plan is specifically for **MinecraftForge 1.21.8-58.1.0** (NOT NeoForge)

## Current Status (What's Already Done)

### ✅ Completed
1. **Stage 1.1 - Infinitite Ingot Registration** (COMPLETE)
   - `INFINITITE_INGOT` registered in JasperTutorial.java (lines 37-41)
   - Added to INGREDIENTS creative tab (line 66)
   - Localization added to en_us.json
   - Item models and assets exist

2. **Stage 1.2 - Sword Registration Attempted** (NEEDS FIX - Wrong APIs)
   - `INFINITITE_SWORD` registered in JasperTutorial.java (lines 44-50)
   - Added to COMBAT creative tab (lines 68-70)
   - ⚠️ **PROBLEM**: InfiniteSwordItem.java uses NeoForge/wrong API classes
   - ⚠️ **PROBLEM**: InfinititeToolTier.java uses NeoForge/wrong API classes
   - **DOES NOT COMPILE** - need to fix with correct Forge 1.21.8 APIs

3. **Test Infrastructure** (COMPLETE but may need API fixes)
   - JUnit 5 + Mockito dependencies added to build.gradle
   - 25 unit tests written (currently failing due to compilation errors)

## Problem Analysis

### Issue: Wrong API Classes Used
The current implementation tries to use:
- `net.minecraft.world.item.SwordItem` - **DOES NOT EXIST in Forge 1.21.8**
- `net.minecraft.world.item.Tier` - **DOES NOT EXIST in Forge 1.21.8**

These are NeoForge or older Minecraft version APIs.

### Solution Needed
We need to find the **CORRECT** Forge 1.21.8 API classes for:
1. Sword item base class
2. Tool tier interface/enum
3. Potion effects application

## Next Steps (In Order)

###  Step 1: Research Correct Forge 1.21.8 APIs
**Goal**: Find the actual class names and packages used in MinecraftForge 1.21.8

**Method**:
1. Search official Forge documentation for 1.21.8
2. Look at Forge's own source code examples
3. Check gradle cache for actual Minecraft/Forge JARs
4. Use IDE autocomplete to explore available classes

**What we need to find**:
- Correct sword item class (or if we use vanilla `Item` + custom logic)
- Correct tier system (might be completely different in 1.21.8)
- Correct way to add potion effects to entities

### Step 2: Update InfinititeToolTier.java
**Current file location**: `src/main/java/com/example/examplemod/InfinititeToolTier.java`

**Needs**:
- Replace `implements Tier` with correct Forge 1.21.8 interface/approach
- Verify all method signatures match Forge 1.21.8 API
- Properties needed:
  - Durability: 3124
  - Mining speed: 8.0f
  - Attack damage bonus: 7.0f
  - Mining level: Diamond equivalent
  - Enchantability: 15
  - Repair ingredient: INFINITITE_INGOT

### Step 3: Update InfiniteSwordItem.java
**Current file location**: `src/main/java/com/example/examplemod/InfiniteSwordItem.java`

**Needs**:
- Replace `extends SwordItem` with correct Forge 1.21.8 approach
- Override correct method for attack handling (might not be `hurtEnemy`)
- Apply effects correctly:
  - BLINDNESS for 60 ticks (3 seconds), level 0
  - POISON for 60 ticks (3 seconds), level 0

**Possible approaches** (need to verify which works):
1. Extend a Forge sword class (if exists)
2. Extend vanilla `Item` and implement all sword behavior manually
3. Use Forge events/hooks to add behavior to vanilla sword

### Step 4: Verify Compilation
Run `./gradlew.bat compileJava` to ensure code compiles

### Step 5: Run Unit Tests
Run `./gradlew.bat test` to verify all 25 tests pass

### Step 6: Test In-Game
Run `./gradlew.bat runClient` and verify:
- Sword appears in COMBAT tab
- Sword has correct damage (11)
- Sword applies effects on hit
- Sword can be repaired with Infinitite Ingot

## Remaining Work After API Fix

### Assets (Already Partially Done)
- ✅ Localization exists
- ✅ Item models exist for ingot
- ⚠️ Need to create sword item model
- ⚠️ Need to verify texture structure

### Crafting Recipes (Not Started)
- ⚠️ Infinitite Ingot recipe (4 diamonds + 5 iron)
- ⚠️ Infinitite Sword recipe (2 ingots + 1 stick)

## Key Constraints

1. **MUST use MinecraftForge 1.21.8-58.1.0 APIs only**
2. **NO NeoForge documentation/examples**
3. **Verify every import statement is correct**
4. **Test compilation before proceeding**

## Reference Information

- **Forge Version**: 58.1.0
- **Minecraft Version**: 1.21.8
- **Java Version**: 21 (Eclipse Adoptium)
- **Mod ID**: jaspertutorial
- **Package**: com.example.examplemod

## Files Currently Needing Fixes

1. `src/main/java/com/example/examplemod/InfinititeToolTier.java` - Wrong API
2. `src/main/java/com/example/examplemod/InfiniteSwordItem.java` - Wrong API
3. Test files may need updates after API corrections

---

**NEXT ACTION**: Research correct Forge 1.21.8 sword/tier APIs before proceeding with implementation.
