# Universal Core Requirements

## Overview
The **Universal Core** turns whoever eats it into a **Universal God - permanently**. No armor or
anything else needed. It's the ultimate item in the mod.

## Looks
- A **3D End Crystal** in your hand: the same swirling pink-purple inside, with **white rings**
  tumbling around it on different axes and a pulsing **golden aura**. Always glows at full
  brightness, even in the dark.
- The core's swirl is an animated texture. **While you're eating it, the rings spin faster and
  faster** (up to 8x), right up until it's gone.
- Takes 3 seconds to eat. Stacks to 1.

## Becoming a God
- Eat it and you're a Universal God **forever** - through relogging and dying.
- The moment you ascend: a pillar of light, a lightning flash, a burst of particles, and the
  whole server is told.
- If you're already a god, eating another Core does nothing and doesn't use it up.
- A permanent **Universal God** status effect shows you're a god.

## God Powers

### True Invincibility
- **Nothing can hurt or kill you** - no damage of any kind, not `/kill`, not the void (fall out
  of the world and you're lifted back to the surface). Never hungry.
- **Except other gods.** Another Universal God can hurt and kill you.
- **If another god kills you, you lose your godhood** for good - you'd need a new Core.

### God Form
- You **glow**, with a golden-purple aura around you and a turning **halo** of light over your
  head.
- Your **hearts turn gold and white** (gold hearts with white outlines; absorption hearts white
  with a gold glint).

### World Control
- **Fly** any time, **3x faster** than creative flight, and **pass straight through blocks while
  flying** (noclip).
- **N** switches between day and night. **Shift + N** cycles the weather: clear -> rain ->
  thunderstorm -> clear.

### Reality Powers
- **H - Meteor:** a meteor streaks down out of the sky onto wherever you're looking (up to 128
  blocks away) and explodes: 400 damage within 6 blocks and sets things on fire. Doesn't break
  blocks. 2 second cooldown.
- **J - Lightning Storm:** 12 real lightning strikes rain down on things around you (within 24
  blocks) over 3 seconds, 100 extra damage each. 10 second cooldown.
- **K - Delete:** whatever mob you're looking at (up to 64 blocks) is wiped from existence - no
  drops. Players can't be deleted.

All keys can be rebound under Options > Controls, in the "To Infinitite and Beyond" section.

## Who the powers hit
Meteor and Lightning Storm hit every other living thing - mobs and other players, other gods
included - but never you, or players in creative/spectator.

## Crafting
Eight Universal Shards around a Nether Star:
```
S S S
S N S
S S S
```

## Design Notes
- "Forever" means forever: there is currently no way to stop being a god except being killed by
  another god. A command to undo it could be added later if wanted.
- Godhood is a flag in the player's saved data. The server also keeps the Universal God effect
  on every god, because effects are sent to every player's game automatically - that's how
  clients know who's a god (for noclip, the keys and the gold hearts).
- The Universal Boots never take flight away from a god.
