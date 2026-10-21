# Universal Sword - Overhead Shockwave Ability Requirements

## Overview
A brand new charged "ultimate" ability for the **Universal Sword** (already implemented in
`UniversalSwordItem.java` - see `docs/requirements/universal_shard_and_cosmic_apple.md` for its
existing base stats and on-hit effects). This doc covers two things layered on top of that
existing sword:

1. **A new held/3D animation** for the sword - a proper overhead "casting" pose, sword glow,
   storm clouds, and an energy orb.
2. **A brand new charge-up combat ability** - holding down the attack button charges up a
   massive area-of-effect shockwave: five expanding rings of purple energy and fire that reach
   out an enormous distance and devastate anything they touch.

This is explicitly **two separate chunks of work** bolted together (a visual/animation system
and a new gameplay mechanic), not a small tweak to the existing sword. Both are being planned
together here since Jasper wants both, but they can be built and tested as separate steps.

## Part 1: The Animation

### Trigger / Pose
- Activated by **holding down the attack button** (a charge-up, not a normal swing/hit).
- While charging, the player holds the Universal Sword up in one hand, above their head - a
  clear "casting a spell" pose, not the normal swing stance.

### Visual Effects While Charging/Casting
- The sword itself **glows**.
- **Storm clouds** gather overhead. This means **both**:
  - A real change to the world's weather (the sky actually darkens and it starts
    thundering/raining), and
  - A visual cloud/particle effect directly above the player's head, for extra drama even if the
    real weather effect is subtle or delayed.
- An **orb of energy** forms and grows around the player while they hold the pose.

### Technical Note - GeckoLib Required
- The current mod has **no 3D animation library** - every item so far uses a plain vanilla item
  model (a flat textured shape, same as a normal Minecraft sword in your hand). Vanilla item
  models cannot play a custom "raise the sword overhead" pose on their own.
- To make the sword actually move into this held-overhead pose, and to glow/pulse as part of the
  3D model itself (not just a flat texture), the project will need to add **GeckoLib** as a new
  dependency. This is not currently part of the project - flagging this for Minion as a
  prerequisite/setup step before this animation can be built, separate from the shockwave logic
  itself.
- Simpler pieces of the visual package (particle bursts, the glow, the weather change) do **not**
  require GeckoLib and can likely be built independently of the pose animation.

## Part 2: The Shockwave Ability

### How It Triggers
- Player holds down the attack key/button to charge (exact charge-up time before it fires is
  TBD - to be tuned for game feel during implementation; should feel like a deliberate, weighty
  "ultimate move," not instant).
- Once charged/released, the shockwave fires.

### The Shockwave Itself
- **Five expanding rings** of energy, released one after another, expanding outward from the
  player.
- **Visuals**: purple energy and fire, matching the Universal Sword/Shard's established
  pink-purple cosmic look.
- **Range: 10,000 blocks.**
  - Design note (flagged during requirements discussion, decision made to keep as-is): Minecraft
    normally only actively simulates roughly a few hundred blocks around a player at once,
    regardless of how far a stated ability range reaches. In practice, a 10,000 block range
    behaves the same as "everything currently loaded around the player" - it will almost never
    actually reach 10,000 real blocks away because nothing that far out is normally being
    simulated. Jasper reviewed this tradeoff and chose to keep the number at 10,000 anyway, since
    the intent is "hits everything nearby that exists right now."

### What Happens to Things Caught in the Blast

**The wielder is completely immune to their own shockwave.** It never kills, floats, or stuns
the player who cast it, no matter how close they are to the blast center. This was confirmed
directly with Jasper as the final, deliberate rule after walking through the "what about your
friend Alex" scenario - the ability is explicitly an **"everyone but me"** ultimate: a genuine
big-power-fantasy move where the caster stands untouched at the center of their own devastation.

Everyone and everything **else** in range is affected, no exceptions:
- **Hostile mobs, neutral/passive mobs, tamed pets, and villagers** caught in the waves.
- **Other players**, including the wielder's own friends/teammates standing right next to them.
  Confirmed intentional, not a misunderstanding - Jasper wants this to hit friend and foe alike
  (just never the wielder). Playing alongside a friend means that friend gets caught in it too
  every time this is used.

The blast has **two effects that both apply** to whatever it touches (other than the wielder):
- **Instant kill / destruction** - mirrors the existing "Cosmic Blast" instant-destroy effect
  already on the Universal Sword's normal hit (see `UniversalSwordItem.hurtEnemy()`), but
  guaranteed and area-wide instead of a 12% per-hit chance.
- **Float + total loss of control**: lifted 1 block into the air and all control of their
  character removed for **10 seconds**.
- **Remaining implementation detail** (flagging rather than guessing): exactly how "instant
  kill" and "float for 10 seconds, no control" apply together for the same target - e.g.
  whether non-player mobs/animals/villagers are simply destroyed outright while players
  specifically get the float+10-second-stun treatment (since an instantly-destroyed player can't
  also float afterward). Worth a quick confirmation pass with Jasper once this is being built.

## Implementation Notes
- **New dependency required**: GeckoLib, for the 3D overhead pose/glow animation (Part 1). Not
  currently in the project - needs to be added before that piece can be built.
- The shockwave ability (Part 2) is a wholly new gameplay system (charge-up input handling,
  area-of-effect entity scanning, floating/no-control status effect on players) - distinct from
  the sword's existing per-hit effects in `hurtEnemy()`, and can be built/tested independently of
  the GeckoLib animation work if it's easier to sequence that way.
- Suggested build order (not mandated, just a sensible default): simple visual pieces first
  (particles, weather change, glow) -> shockwave gameplay logic and balance testing -> GeckoLib
  setup and full 3D pose animation last, since it's the biggest new piece of infrastructure.

## Design Notes / Flags to Revisit
- **Friendly fire is intentional but flagged for review.** Confirmed final rule: the wielder is
  always immune; everyone else - enemies, mobs, pets, villagers, and other players/friends -
  is hit every time, no exceptions. Like the Cosmic Apple's uncapped health stacking, this is
  being built exactly as requested, but should be revisited if, once it's actually playable with
  a friend, it turns out to make co-op play frustrating rather than fun. Easy to add a
  "hostiles only" toggle later if so.
- Exact charge-up duration before the shockwave fires: TBD, tune during implementation.
- Exact reconciliation of "instant kill" vs. "float + 10 second stun" for players vs. other
  entities: TBD, see note above.

## Open Questions for Later
- Should there be any cooldown on this ability, or can it be used repeatedly back-to-back?
- Does the real weather change (storm/thunder) affect the whole loaded world, or just clear up
  automatically after some time?
- Should the instant-kill portion have any exceptions (e.g., bosses like Infinitite Monstrosity)
  given the sword's existing Cosmic Blast already treats instant-kill as a chance-based effect
  rather than guaranteed?