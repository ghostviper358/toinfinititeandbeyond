# Black Hole Arm Cannon - Requirements Document

## Overview
The Black Hole Arm Cannon is an extremely powerful late-game weapon that fits over the player's arm. It features four different attack modes, a regenerating ammo system, and unique mechanics including block absorption and a dangerous black hole ability.

## Remake Decisions (2026-10-27)
- **Tier**: Between the Void gear and the Universal gear - stronger than the Void Sword, much weaker than the Universal Sword.
- **Each attack has its own strength** (fast attacks hit softer, big attacks hit harder).
- **Holding**: Held in the main hand like a weapon (no special arm slot); the 3D model wraps over the arm.
- **Built one mode at a time**, playtesting after each.
- **Crafting Recipe** (decided):
  ```
  V N V      V = Void Ingot, N = Nether Star
  I B I      I = Infinitite Ingot, B = Infinitite Bow
  V I V
  ```
- **Ammo**: 60 shots, never breaks. Refills 4 shots a second while you're not firing. Purple bar under the item shows ammo.

### Look (design v3)
- Gauntlet over the fist, big long barrel with glowing rings, and 4 long fins (one per corner) that hang past the muzzle and bend in like claws
- Comes to life when held: the fins slide out and a spinning black hole appears between the fin tips - black inside, see-through purple outside
- The ammo floats around the black hole as 4 little void blocks (one per quarter of the ammo)
- Model made from a cube list (gen_black_hole_arm_cannon_model.py) - pictures with preview_item_model.py

### Recharging
- Run out of ammo -> the black hole spins fast and sucks in purple energy, refilling 10 shots a second
- Can't fire again until it's completely full

### Aiming
- Hold left-click to aim down the sight (no attacking/mining with the cannon): zooms in and moves the cannon to the middle of the screen
- The crosshair is replaced by floating purple pixels that close in when aiming. Each attack mode gets its own sight.

### Mode 1: Homing Blocks - DONE
- Hold right-click: fires about 7 blocks a second (1 ammo each)
- 8 damage on a direct hit + 4 splash damage within 2 blocks (no block breaking)
- Locks on to whatever's closest to the crosshair (up to 128 blocks away), flies up to 1000 blocks

### Switching Modes
- Sneak + right-click switches to the next attack mode (shows the mode name above the hotbar)
- Each mode has its own floating-pixel sight

### Mode 2: Infinitite Spikes - DONE
- Right-click: after a 0.5 second warning (purple sparks on the ground), a ring of purple spikes bursts out of the ground under what you're aiming at (up to 64 blocks away)
- One big spike (4 tall) in the middle and 8 smaller ones (2 tall) around it - about 5 blocks wide
- 40 damage to everything caught in the eruption, and they get thrown into the air
- Costs 15 ammo, one attack every 2 seconds
- The spikes stay for 3 seconds (walking into them hurts, 4 damage), then crumble into purple dust
- Sight: 8 pixels in a ring with a wave running around it; dims while cooling down

### Mode 3: Black Hole - DONE
- **Unlock**: beat the Living Void (every player within 64 blocks when it dies unlocks it for good). Always available in creative. Locked modes are skipped when switching.
- Right-click: a black hole appears where you're aiming (up to 48 blocks away) and lasts 5 seconds
- Pulls in mobs, players and dropped items within 16 blocks, spiralling them into the middle. Only pulls YOU if you're within 8 blocks of it
- Keeps hurting: 10 damage every half second to everything in the middle (5x5x5)
- Always eats the blocks around it (grows out to 3.5 blocks) - not bedrock/unbreakable blocks, liquids, or blocks that hold items like chests. Every block eaten = 1 ammo back into your cannon
- Costs 30 ammo, one every 6 seconds
- Looks like the cannon's little black hole, only big: black core, see-through purple shell, energy streaming in
- Sight: pixels spiralling into a dark hollow middle

### Mode 4: Void Lightning - DONE
- **Unlock**: killing any boss (Infinitite Monstrosity, Living Void, Universal Destroyer, Wither, Ender Dragon, Warden, Elder Guardian) or another player unlocks it for 10 minutes. Each kill starts the 10 minutes over. When it runs out you get a message and it's locked again. No unlock needed in creative.
- Switching to it shows how much unlock time is left
- Right-click: black lightning strikes instantly where you're aiming (up to 128 blocks away)
- 200 damage to everything within 12 blocks of where it lands
- Then it jumps, one strike every 3 ticks, to up to 8 more monsters or players outside that 12-block area (each jump up to 24 blocks, always to the closest one it hasn't hit yet) - 200 damage each
- Never hurts you or your pets, and never sets anything on fire
- Costs a full cannon (60 ammo), one every 10 seconds
- Look: a lightning bolt that's black on the inside with glowing purple edges; the sky and screen flash purple
- Sight: a little flickering zig-zag bolt of pixels with four corner brackets

## Basic Properties
- **Equipment Slot**: Worn on the arm, fits over the player's hand
- **Appearance**: TBD (texture to be designed later)
- **Rarity**: Extremely rare/end-game weapon

## Crafting Recipe
```
Ingredients:
- 1 Infinitite Bow
- 1 Nether Star
- 3 Infinitite Ingots

Crafting Pattern: TBD
```

## Ammunition System
- **Type**: Regenerating ammo
- **Behavior**: Ammo counter slowly refills automatically over time (like a cooldown system)
- **Fuel Source**: Blocks absorbed by the black hole charge-up ability fuel the cannon
- **Note**: Player can never run completely out, but cannot spam attacks endlessly

## Controls
- **Mode Switching**: Shift + Hold Click to cycle through attack modes
- **Fire**: Click to fire current attack mode
- **Charge**: Hold click to charge up (for applicable attacks)

## Attack Modes

### Attack Mode 1: Homing Infinitite Blocks
**Description**: Fires explosive projectiles at machine gun speed

**Properties**:
- Fire rate: Very fast (machine gun-like)
- Range: 1000 blocks
- Projectile: Infinitite blocks that home in on target
- Damage: Explosion damage on contact
- Targeting: Where player is looking (crosshair aim)

**Behavior**:
- Projectiles track toward aimed target
- Explodes on contact with any entity or block
- High rate of fire allows for sustained barrage

### Attack Mode 2: Infinitite Spikes
**Description**: Summons damaging spikes from the ground beneath the target

**Properties**:
- Appearance: Like pointed dripstone but purple colored
- Targeting: Where player is looking (must aim at target)
- Warning Time: 0.5 seconds before spikes emerge
- Warning Indicator: Particles or visual cue at spike location
- Damage: TBD (should be significant for precision attack)

**Behavior**:
1. Player aims at target and fires
2. Brief particle effect appears at ground beneath target (0.5 second warning)
3. Purple spikes emerge from ground, damaging entities in the area
4. Spikes remain for TBD duration before disappearing

**Technical Notes**:
- Must detect ground level beneath target
- Spikes should work on various surface types
- Warning gives skilled players tiny chance to dodge

### Attack Mode 3: Black Hole
**Description**: Creates a dangerous gravitational vortex that pulls in everything nearby

**Properties**:
- Size: 5x5x5 blocks
- Duration: 5 seconds
- Damage: Single hit equal to half of target's current health
- Pull Effect: Attracts ALL nearby entities (including the shooter!)
- Targeting: Where player is looking (placed at aimed location)

**Behavior**:
1. Player fires to create black hole at aimed location
2. Black hole pulls in all entities within range (exact pull range TBD)
3. Entities pulled into center take damage equal to 50% of their current health
4. **Special Mechanic**: Players (including shooter) must shoot AT the black hole to avoid being pulled in
   - Shooting at the black hole creates knockback/resistance to pull
   - Failure to shoot results in being pulled in and taking damage
5. After 5 seconds, black hole dissipates

**Block Absorption** (Charge-up Feature):
- Hold click to charge up the black hole
- Charged black hole sucks in all blocks except bedrock within range
- Absorbed blocks are destroyed and converted into cannon fuel/ammo
- Creates significant terrain destruction
- Block absorption powers up the ammo regeneration system

**Technical Notes**:
- Extremely dangerous to user - risk/reward mechanic
- Pull strength should be strong enough to require active shooting to resist
- Friendly fire is ACTIVE - affects all players
- Block destruction range TBD (should be balanced with 5x5x5 entity damage zone)

### Attack Mode 4: Void Lightning
**Description**: Summons instant black lightning strike on target

**Properties**:
- Appearance: Lightning bolt that is black on the inside
- Strike Speed: Instant (no travel time)
- Damage: Minimum 4 hearts (8 HP) to each entity hit
- Range: Affects all entities within 100 blocks of strike point
- Targeting: Where player is looking (crosshair aim)

**Unlock System**:
- **Multiplayer**: Must repeatedly kill players to maintain access
  - Killing a player unlocks Attack Mode 4
  - Mode locks again after period of non-use or TBD condition
  - Requires ongoing player kills to keep unlocked
- **Singleplayer**: Kill boss mobs instead
  - Killing Ender Dragon unlocks mode
  - Killing Wither unlocks mode
  - Other bosses TBD
  - Unlock persistence TBD (permanent vs temporary)

**Behavior**:
1. Player aims at target location
2. Void lightning strikes instantly at aimed point
3. All entities within 100-block radius take minimum 4 hearts damage
4. Standard lightning effects (fire, charged creeper conversion if applicable)
5. Visual effect shows black interior of lightning bolt

**Technical Notes**:
- Most powerful attack mode - requires unlock
- Instant strike prevents dodging
- Large area of effect makes it devastating
- Unlock system creates progression goal for PvP and boss fights

## Technical Implementation Considerations

### Complexity Level
This is an EXTREMELY complex item requiring:
- Custom equipment slot or creative use of existing slots
- Mode switching system with UI feedback
- Four completely different attack types with unique behaviors
- Ammo/fuel regeneration system
- Block absorption and destruction mechanics
- Gravitational pull physics
- Unlock/lock progression system
- Multiplayer synchronization for all features

### Major Technical Challenges
1. **Equipment Slot**: Creating arm-mounted equipment may require custom rendering
2. **Black Hole Physics**: Pulling entities toward point requires custom gravity/velocity manipulation
3. **Block Absorption**: Detecting, removing, and converting blocks to fuel
4. **Mode Switching**: Tracking current mode and switching with specific input combination
5. **Unlock Tracking**: Persistent data storage for Attack Mode 4 unlock status
6. **Targeting System**: Different attacks need consistent targeting reticle
7. **Homing Projectiles**: Missiles that track targets require custom entity with AI
8. **Ground Detection**: Spikes need to find ground level beneath target
9. **Multi-Entity Damage**: Lightning hitting everything in radius efficiently

### Development Phases (Suggested)
1. **Phase 1**: Basic weapon item, mode switching, simple projectile attack
2. **Phase 2**: Ammo regeneration system
3. **Phase 3**: Spike attack with ground detection and warning particles
4. **Phase 4**: Black hole entity pull mechanics (without block absorption)
5. **Phase 5**: Block absorption and fuel conversion
6. **Phase 6**: Lightning attack with area damage
7. **Phase 7**: Unlock system for Attack Mode 4
8. **Phase 8**: Polish, balancing, and bug fixes

### Balance Considerations
- Ammo regeneration rate needs careful tuning to prevent spam
- Black hole self-damage creates risk but may be frustrating
- Attack Mode 4 unlock requirements should be challenging but achievable
- Machine gun fire rate should consume ammo quickly to force regeneration breaks
- All damage values need testing and adjustment

## Open Questions / TBD
1. Exact ammo regeneration rate (shots per minute? cooldown per shot?)
2. Spike emergence animation and duration
3. Black hole pull radius and strength
4. Black hole block absorption radius
5. How long Attack Mode 4 stays unlocked before requiring more kills
6. Exact crafting pattern layout
7. Durability system (if any) in addition to ammo
8. Sound effects for each attack mode
9. Particle effects and visual feedback
10. UI indicators for current mode and ammo level
11. Whether black hole damages blocks or only absorbs during charge-up
12. Maximum ammo capacity
13. Starting ammo amount when crafted
14. Whether different attacks consume different amounts of ammo

## Notes
- This weapon is intended to be the most powerful item in the mod
- Requires Infinitite Bow as prerequisite (enforces progression)
- Nether Star requirement ensures late-game status (must kill Wither)
- Multiple attack modes provide versatility for different situations
- Risk/reward mechanics (black hole self-pull) add skill requirements
- PvP unlock system creates multiplayer engagement goals

---

**Document Status**: Initial requirements gathered
**Date**: 2025-11-08
**Designer**: Jasper
**Documentor**: Albert
