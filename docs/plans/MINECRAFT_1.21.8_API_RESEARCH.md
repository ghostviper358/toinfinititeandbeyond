# Minecraft 1.21.8 Forge API Research Findings

## Summary

Minecraft 1.21.8 uses a **completely different system** than older versions for creating custom weapons and tools. The old `SwordItem`, `Tier`, and `ForgeTier` classes **DO NOT EXIST** in this version.

## Key Changes in Minecraft 1.21+

### 1. Data Components System (Replaces NBT)

Minecraft 1.21 introduced **Data Components** which replaced the old NBT tag system for item properties.

**What Works:**
- `net.minecraft.world.item.Item` - Base item class ✅
- `net.minecraft.world.item.component.ItemAttributeModifiers` - For attributes ✅
- `net.minecraft.world.entity.ai.attributes.Attribute` - Attribute types ✅
- `net.minecraft.world.entity.ai.attributes.AttributeModifier` - Modifiers ✅
- `net.minecraft.world.entity.EquipmentSlotGroup` - Slot definitions ✅
- `net.minecraft.core.component.DataComponents` - Component registry ✅

**What DOESN'T Exist:**
- `net.minecraft.world.item.SwordItem` - ❌ DOES NOT EXIST
- `net.minecraft.world.item.Tier` - ❌ DOES NOT EXIST
- `net.minecraftforge.common.ForgeTier` - ❌ DOES NOT EXIST
- `net.minecraftforge.common.TierSortingRegistry` - ❌ DOES NOT EXIST

### 2. How to Create Custom Weapons in 1.21.8

**Approach**: Extend `Item` class directly and use Data Components for properties.

**Required Components for a Sword:**
1. **Attack Damage**: Via `ItemAttributeModifiers` component
2. **Durability**: Via `Item.Properties` durability settings
3. **Combat Effects**: Override `hurtEnemy()` method
4. **Stack Size**: Weapons are typically `stacksTo(1)`

### 3. Code Pattern for Custom Sword

```java
public class CustomSwordItem extends Item {
    public CustomSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Apply custom effects here
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));

        // Damage the item (durability loss)
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);

        return true;
    }
}
```

**Registration with Attributes:**
```java
public static final RegistryObject<Item> CUSTOM_SWORD = ITEMS.register("custom_sword",
    () -> new CustomSwordItem(new Item.Properties()
        .setId(ITEMS.key("custom_sword"))
        .durability(3124) // Max durability
        .attributes(ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(
                    ResourceLocation.withDefaultNamespace("attack_damage"),
                    7.0, // +7 attack damage
                    AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
            )
            .build()
        )
    )
);
```

## Implementation Plan for Infinitite Sword

### What We Need:
1. **One Java Class**: `InfiniteSwordItem extends Item`
   - Constructor accepting `Item.Properties`
   - Override `hurtEnemy()` for BLINDNESS + POISON effects
   - Handle durability damage

2. **Registration** in `JasperTutorial.java`:
   - Use `Item.Properties` with:
     - `.durability(3124)` for double diamond durability
     - `.attributes(...)` for 11 attack damage (7.0 + 4.0 base)
     - `.stacksTo(1)` for weapon stack size

3. **No Separate Tier Class Needed** - everything is in properties

## Testing the Implementation

**Verified Imports** (these all compile successfully):
```java
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
```

## References

- Minecraft Wiki: Data Component Format
- Minecraft Wiki: Attributes
- Forge Forums: 1.21 Item Attributes discussion
- GitHub Gist: Tool System Explanation (older versions, but concepts apply)

## Conclusion

**Minecraft 1.21.8 uses a simpler, more unified system**:
- All items extend `Item`
- Properties set via `Item.Properties` and Data Components
- No separate Tier hierarchy needed
- Attributes applied via `ItemAttributeModifiers`

This is actually **easier** than the old system once you understand it!
