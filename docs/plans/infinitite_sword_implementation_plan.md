# Comprehensive Implementation Plan for Infinitite Sword Mod

## Requirements Analysis Summary

From the requirements document, I need to implement:

1. **Infinitite Ingot**: A new material created from 4 Diamonds + 5 Iron Ingots
2. **Infinitite Sword**: A powerful sword with 11 attack damage, double diamond sword durability, and special effects
3. **Special Effects**: Blindness and Poison effects lasting 3 seconds on hit
4. **Visual Design**: Black blade with purple veins and mystical purple glow
5. **Crafting Recipe**: Standard sword recipe using 2 Infinitite Ingots + 1 Stick

## Current Codebase Analysis

The existing mod structure follows standard Minecraft Forge patterns:
- Main mod class: `JasperTutorial.java` with MOD_ID "jaspertutorial"
- Uses DeferredRegister for items, blocks, and creative tabs
- Has existing examples of item registration (sapphire)
- Asset structure follows standard Minecraft conventions under `assets/examplemod/`

## Detailed Step-by-Step Implementation Plan

### Phase 1: Core Material Implementation

#### Step 1.1: Create Infinitite Ingot Item
**Files to modify:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\java\com\example\examplemod\JasperTutorial.java`

**Implementation approach:**
- Add a new `RegistryObject<Item> INFINITITE_INGOT` registration
- Use standard `Item` class with basic properties
- Register it in the existing ITEMS DeferredRegister
- Add to the creative tab

**Technical details:**
- Follow the same pattern as the existing sapphire item
- Use unique registry key "infinitite_ingot"

#### Step 1.2: Create Infinitite Sword Item
**Files to modify:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\java\com\example\examplemod\JasperTutorial.java`

**Files to create:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\java\com\example\examplemod\item\InfiniteSwordItem.java`

**Implementation approach:**
- Create custom `InfiniteSwordItem` class extending `SwordItem`
- Override `hurtEnemy()` method to apply Blindness and Poison effects
- Create custom `Tier` implementation for Infinitite material properties
- Register sword item in main mod class

**Technical details:**
- Attack damage: 11 (requires Tier with attack damage bonus of 7, since SwordItem adds 4 base)
- Durability: 3124 (2x diamond sword's 1562)
- Harvest level: Same as diamond (3)
- Enchantability: High value for magical weapon
- Repair material: Infinitite Ingot

### Phase 2: Special Effects Implementation

#### Step 2.1: Implement Combat Effects
**Implementation in `InfiniteSwordItem.java`:**
- Override `hurtEnemy(ItemStack, LivingEntity, LivingEntity)` method
- Apply `MobEffects.BLINDNESS` for 60 ticks (3 seconds)
- Apply `MobEffects.POISON` for 60 ticks (3 seconds)
- Both effects at level 1 (index 0)
- Return true to indicate successful hit

**Technical details:**
- Use `LivingEntity.addEffect(new MobEffectInstance())`
- Check if target is a `LivingEntity` before applying effects
- Effects should stack with existing effects

### Phase 3: Material Tier Implementation

#### Step 3.1: Create Infinitite Material Tier
**Files to create:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\java\com\example\examplemod\item\InfinititeToolTier.java`

**Implementation approach:**
- Implement `Tier` interface
- Define all material properties (durability, speed, attack bonus, harvest level, enchantability)
- Set repair ingredient to Infinitite Ingot

**Technical details:**
- Uses: 3124 (double diamond)
- Speed: 8.0f (same as diamond)
- Attack damage bonus: 7.0f (to achieve 11 total with sword base)
- Harvest level: 3 (diamond equivalent)
- Enchantability: 15 (higher than diamond's 10)

### Phase 4: Crafting Recipes Implementation

#### Step 4.1: Infinitite Ingot Recipe
**Files to create:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\resources\data\jaspertutorial\recipes\infinitite_ingot.json`

**Implementation approach:**
- Shaped crafting recipe in 3x3 grid
- Pattern: 4 diamonds in corners, 5 iron ingots in cross/plus pattern
- Output: 1 Infinitite Ingot

**Technical details:**
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "DID",
    "III",
    "DID"
  ],
  "key": {
    "D": {"item": "minecraft:diamond"},
    "I": {"item": "minecraft:iron_ingot"}
  },
  "result": {
    "item": "jaspertutorial:infinitite_ingot"
  }
}
```

#### Step 4.2: Infinitite Sword Recipe
**Files to create:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\resources\data\jaspertutorial\recipes\infinitite_sword.json`

**Implementation approach:**
- Standard sword recipe pattern
- 2 Infinitite Ingots stacked vertically + 1 stick at bottom center
- Output: 1 Infinitite Sword

**Technical details:**
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    " I ",
    " I ",
    " S "
  ],
  "key": {
    "I": {"item": "jaspertutorial:infinitite_ingot"},
    "S": {"item": "minecraft:stick"}
  },
  "result": {
    "item": "jaspertutorial:infinitite_sword"
  }
}
```

### Phase 5: Visual Assets Implementation

#### Step 5.1: Item Models
**Files to create:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\resources\assets\jaspertutorial\models\item\infinitite_ingot.json`
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\resources\assets\jaspertutorial\models\item\infinitite_sword.json`

**Implementation approach:**
- Standard "item/generated" parent for ingot
- Standard "item/handheld" parent for sword
- Reference texture files in respective item texture directories

#### Step 5.2: Textures (Placeholder Structure)
**Directories to create:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\resources\assets\jaspertutorial\textures\item\`

**Files needed (will require custom artwork):**
- `infinitite_ingot.png`: Black metal with purple veins
- `infinitite_sword.png`: Black blade with purple veins and mystical glow effect

#### Step 5.3: Localization
**Files to modify:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\resources\assets\jaspertutorial\lang\en_us.json`

**Implementation approach:**
- Add display names for both items
- Follow existing naming pattern

**Technical details:**
```json
{
  "item.jaspertutorial.sapphire": "Sapphire",
  "item.jaspertutorial.infinitite_ingot": "Infinitite Ingot",
  "item.jaspertutorial.infinitite_sword": "Infinitite Sword"
}
```

### Phase 6: Integration with Existing Mod Structure

#### Step 6.1: Update Creative Tab
**Files to modify:**
- `C:\Data\Repositories\forge-tutorial-1.21.X\src\main\java\com\example\examplemod\JasperTutorial.java`

**Implementation approach:**
- Add both Infinitite items to the existing creative tab
- Add them to the displayItems method in EXAMPLE_TAB

#### Step 6.2: Namespace Consistency
**Technical details:**
- All items use "jaspertutorial" namespace (matching MOD_ID)
- Asset paths follow "jaspertutorial" instead of "examplemod"
- Recipe data paths use "jaspertutorial" namespace

### Phase 7: Testing and Quality Assurance

#### Step 7.1: Functional Testing Plan
**Test scenarios to validate:**
1. Infinitite Ingot crafting recipe works correctly
2. Infinitite Sword crafting recipe works correctly
3. Sword deals 11 attack damage
4. Sword durability is approximately 3124 uses
5. Blindness effect applies for 3 seconds on hit
6. Poison effect applies for 3 seconds on hit
7. Items appear in creative tab
8. Items have proper names and tooltips
9. Sword can be enchanted properly
10. Sword can be repaired with Infinitite Ingots

#### Step 7.2: Edge Case Testing
**Additional test cases:**
- Effects work on different entity types (players, mobs, animals)
- Effects stack properly with existing status effects
- Sword durability decreases correctly
- Crafting recipes don't conflict with existing recipes
- Items have proper creative tab organization

## Technical Implementation Notes

**File Structure Summary:**
```
src/main/java/com/example/examplemod/
├── JasperTutorial.java (modified)
├── item/
│   ├── InfiniteSwordItem.java (new)
│   └── InfinititeToolTier.java (new)

src/main/resources/
├── assets/jaspertutorial/
│   ├── lang/en_us.json (modified)
│   ├── models/item/
│   │   ├── infinitite_ingot.json (new)
│   │   └── infinitite_sword.json (new)
│   └── textures/item/
│       ├── infinitite_ingot.png (new - requires artwork)
│       └── infinitite_sword.png (new - requires artwork)
└── data/jaspertutorial/recipes/
    ├── infinitite_ingot.json (new)
    └── infinitite_sword.json (new)
```

**Integration Points:**
- Uses existing DeferredRegister pattern for items
- Integrates with existing creative tab system
- Follows established asset naming conventions
- Compatible with existing mod infrastructure

**Future Extensibility:**
- InfinititeToolTier can be reused for other tools (pickaxe, axe, etc.)
- Effect system can be extended for additional magical properties
- Material system ready for armor implementation

This plan provides a complete roadmap for implementing the Infinitite Sword mod while maintaining consistency with the existing codebase structure and following Minecraft Forge best practices. Each phase builds upon the previous ones, allowing for iterative development and testing.