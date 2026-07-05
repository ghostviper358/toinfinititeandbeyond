# Universal Shard & Cosmic Apple Requirements

## Overview
The **Universal Shard** is a legendary material dropped only by defeating a future boss called
**the Universe Destroyer** — an "ancient multiversal power" tier item meant to sit above
Void and Infinitite gear as the true top-tier material in the mod. It is not mined; it is earned.

The **Cosmic Apple** is the first item crafted from Universal Shards: an emergency-use "ultimate"
food that turns the player into a glowing, temporarily invincible powerhouse and permanently
grows their max health over time.

This doc covers the Shard (material) and the Cosmic Apple (first use of that material).
The sword, armor set, and the Universe Destroyer boss itself are separate, future requirement
docs — see "Future Chapters" below for the planned build order.

## Universal Shard (Material)

### Description
- Not an "ore" in the traditional minable sense — it never spawns in world generation.
- Sole source: dropped by the Universe Destroyer boss (boss not yet built — see Future Chapters).
- Represents a fragment of pure cosmic/multiversal power.

### Visual Appearance
- Texture based on the End Gateway portal block: animated, swirling movement effect.
- Color: pink/purple ("purpal") instead of the vanilla teal/black gateway colors.
- Glows.

### Implementation Notes
- Since it cannot be crafted or mined, the very first build step is simply getting this item
  to exist in-game (even as a placeholder, no boss drop logic required yet) so later recipes
  (like the Cosmic Apple) have an ingredient to reference.
- Animated swirling texture may need a custom texture/shader approach similar to how vanilla
  handles the End Gateway block — flag for technical investigation during implementation.

## Cosmic Apple (Item)

### Description
An emergency-use "ultimate" consumable crafted using Universal Shard(s). Meant to be saved for
real emergencies or big boss fights, similar in spirit to the vanilla Enchanted Golden Apple, but
significantly more powerful.

### Crafting
- Requires Universal Shard as an ingredient (exact recipe/shard quantity TBD when building).

### Visual Appearance
- Base model: apple shape (like a normal apple/golden apple).
- Texture: matches the Universal Shard's pink-purple swirling portal look, instead of a normal
  apple texture.
- On eating: triggers a wave/pulse of purple energy (particle effect).

### Effects on Eating
All effects below apply simultaneously when eaten:
- **Speed**
- **Strength**
- **Regeneration**
- **Fire Resistance**
- **Full invincibility** (complete damage immunity) for **1 minute**
- **Glowing outline effect** on the player for the duration of that invincible minute
- **+30 max health, permanently** — and this **stacks with no cap**: every additional Cosmic
  Apple eaten adds another +30 max health on top of whatever the player already has, forever.
  (Design note: vanilla max health is normally 20 — this is intentionally a huge, dramatic power
  boost fitting for a boss-tier reward. No cap requested; revisit only if it ever causes
  in-game problems.)

### Usage/Rarity
- Rare, precious, "only use it when you have to" — an emergency/big-moment item, not a casual
  snack.

## Universal Sword (Item)

### Description
The ultimate weapon of the mod, crafted from Universal Shard(s). Meant to clearly outclass every
existing sword in the mod (Void Sword: 12 damage/2500 durability; Infinitite Sword: 11
damage/3124 durability) — this is designed as an absurd, god-tier "final boss slayer" weapon,
intentionally over-the-top to match the "ancient multiversal power" theme.

### Combat Stats
- **Attack Damage**: 999
- **Durability**: Unbreakable (never breaks, no durability loss)

### On-Hit Effects (all apply together on every successful hit, in addition to raw damage)
- **Chance to instantly delete/destroy the target** in a burst of purple energy
- **Drains health from the target directly into the wielder** (life steal)
- **Teleports/flings the target away** in a flash of cosmic energy (knockback/displacement)
- **Poison**
- **Blindness**
- **Wither**
- **Weakness**
- Design note: since 999 damage already one-shots anything with normal health, the status
  effects and special on-hit powers matter most against high-health or damage-resistant targets
  (e.g. future bosses like the Universe Destroyer), and give the sword flashy, varied impact
  beyond just the raw number.

### Visual Appearance
- Same pink-purple swirling portal look established by the Universal Shard and Cosmic Apple.
- Unlike the Shard/Apple (which use static textures), the sword should have a genuinely
  **animated** texture — the swirl should visibly move, similar to the vanilla End Gateway block.
- Implementation note: true animated item textures in Minecraft are built using a texture
  frame-strip plus a `.png.mcmeta` file describing how the frames cycle. More technical than the
  static textures used so far; exact frame count/speed to be worked out during implementation.

## Future Chapters (Not Yet Built)
Planned build order for the full Universal/Cosmic line, agreed with Jasper:
1. **Universal Shard item** (this doc) — built. First thing added in Minecraft.
2. **Cosmic Apple** (this doc) — built. Crafted from the Shard.
3. **Universal Sword** (this doc) — spec complete, ready to build next.
4. **Armor set** — helmet, chestplate, leggings, boots, piece by piece. Must outclass Void armor
   (Void should feel like "wooden stuff" by comparison). Specific stat/power targets TBD.
5. **The Universe Destroyer boss** — the actual drop source for Universal Shards. Needs: whether
   it's a new entity separate from Infinitite Monstrosity or related to it, an arena/summoning
   method, attack patterns, health, and drop table. Biggest chapter — built last since gear
   design can happen independently, but the loop only closes once the boss can actually drop
   Shards.

## Open Questions for Later
- Is the Universe Destroyer a new/separate boss from Infinitite Monstrosity, or a related
  cousin/upgrade?
- Where/how is the Universe Destroyer summoned or found (special arena, portal, rare spawn)?
- What are the specific stat targets for the Universal armor (armor points, durability, special
  worn effects) to make it clearly outclass Void? (Sword stats now confirmed above.)
- Exact Universal Shard-to-Cosmic-Apple crafting recipe (how many shards, any other ingredients).