# Universal Chestplate Requirements

## Overview
The first piece of the **Universal armor set** - the armor line crafted from Universal Shards,
meant to sit above Void and Infinitite gear as the true top-tier armor in the mod (per the
"Future Chapters" plan in `docs/requirements/universal_shard_and_cosmic_apple.md`, which called
for the set to clearly outclass Void armor - "Void should feel like wooden stuff by comparison").
Matches the pink-purple cosmic look and absurd, over-the-top power level already established by
the Universal Shard, Cosmic Apple, and Universal Sword (999 attack damage, uncapped health
stacking, unbreakable, etc.) - this chestplate is built to the same "ancient multiversal power"
scale, not a modest incremental upgrade over the existing sets.

Only the chestplate is being spec'd in this doc. Helmet/leggings/boots for the Universal set are
future chapters, same as the existing plan.

## Defensive Stats
- **Armor: 999** - deliberately matches the Universal Sword's 999 attack damage number rather
  than a "realistic" incremental armor value; this is the set's signature over-the-top number.
- **Armor Toughness: 6** (Void chestplate: 3, Infinitite chestplate: 5 - clearly on top).
- **+40 max health** while worn (Infinitite chestplate: +10). This is a worn bonus tied to having
  the piece equipped, distinct from the Cosmic Apple's separate permanent, uncapped health
  stacking - the two effects are not the same mechanic and both apply if the player has both.
- **40% knockback resistance** (Infinitite chestplate: 20%).
- **Durability: unbreakable** - never loses durability, same mechanism as the Universal Sword
  (vanilla's `minecraft:unbreakable` data component, not a numeric durability value).

## Special Abilities
All of the following are always active while the chestplate is worn (no charge-up, no cooldown -
purely passive/reactive, unlike the Sword's charge-up shockwave):

### Cosmic Ward
25% chance to fully negate *any* incoming damage entirely (not a reduction - the hit is
completely blocked, full stop), regardless of damage source or type.

### Cosmic Rebuke
- All ranged/projectile damage is blocked outright, every time (matches and doesn't stack with
  Cosmic Ward's chance-based block - ranged is simply always immune, mirroring the Infinitite
  Chestplate's existing ranged-immunity ability but making it part of the Universal set too).
- 50% of incoming melee damage is reflected back at the attacker (Infinitite chestplate reflects
  25% - this doubles it).

### Cosmic Aegis
- Permanent immunity to **Poison**, **Wither**, and **Blindness** while worn. Deliberate thematic
  callback: these are the exact three status effects (plus Weakness) the Universal Sword inflicts
  on enemies in its on-hit effects - the wearer's own ultimate armor protects them from their own
  sword's signature effects.
- Constant slow passive **Regeneration** at all times while worn (not just after taking damage).

### Starlight Aura
Constant purple particle aura/effect around the wearer at all times while worn, purely visual -
no gameplay effect, just makes the wearer visibly radiate cosmic energy.

## Visual Appearance
- Same pink-purple cosmic look as the rest of the Universal line.
- **Animated swirling texture**, not a static texture - matches the Universal Sword and Universal
  Shard's existing animated End-Gateway-style swirl (see
  `docs/requirements/universal_shard_and_cosmic_apple.md`, "Visual Appearance" sections for the
  Shard and Sword) rather than the flat/static textures used on the Cosmic Apple or older armor
  sets (Void, Infinitite). Same technical approach as the sword: a texture frame-strip plus
  `.png.mcmeta` animation metadata.

## Crafting
Crafted from Universal Shard(s), same material source as the Cosmic Apple and Universal Sword.
Exact recipe/shard quantity TBD when building (same open item as the Sword/Apple's recipes).

## Design Notes
- This chestplate is intentionally more powerful than every existing chestplate in the mod on
  every axis (armor, toughness, health bonus, knockback resistance, reflection %, and it adds a
  chance-based full-damage-negation ability neither existing chestplate has). This is by design,
  matching the established "Universal gear is absurdly overpowered on purpose" pattern set by the
  999-damage unbreakable sword and the uncapped Cosmic Apple - not an oversight to rebalance.
- Cosmic Ward (chance-based full block) and Cosmic Rebuke's ranged-always-block are two separate
  mechanisms that can both apply to melee hits (Ward can trigger on melee too) but only Rebuke
  applies to ranged (Ward's 25% chance still also applies as a second layer on top of ranged,
  potentially blocking a hit that would've already been blocked - not a bug, just means ranged
  attackers effectively never land a hit at all).

## Open Questions for Later
- Exact Universal Shard-to-Chestplate crafting recipe (how many shards, any other ingredients) -
  same open question as the Sword and Cosmic Apple's recipes.
- Helmet/Leggings/Boots for the rest of the Universal set - future chapters, stats/abilities TBD
  when reached.