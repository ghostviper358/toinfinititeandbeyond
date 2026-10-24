# The Living Void Requirements

## Overview
The second boss, after the Infinitite Monstrosity. A giant eye of darkness with rings of black
shards orbiting it and tentacles reaching out of the dark beneath it. The Monstrosity's lore book
hints at it; this one's book hints at something even worse.

## Summoning
- Throw a **Monstrosity Heart** on the ground with at least **10 Infinitite Ingots** and **1
  Universal Shard** lying close by (within a couple of blocks).
- The offering is taken - exactly 1 heart, 10 ingots and 1 shard; anything extra is left.
- For 4 seconds darkness spirals in on the spot to a slow heartbeat, then lightning cracks and the
  Living Void tears into the world above it. The whole server is told.
- Only one at a time: no new summoning while a ritual is under way or a Living Void is alive
  within 64 blocks (leftover offerings just wait on the ground).
- There's also a spawn egg in the creative tab, for testing.

## Looks
- A giant floating eye: darkness speckled with faint stars and veins of light, a glowing purple
  iris with a black slit pupil, two rings of black shards orbiting on different tilts, and six
  jointed tentacles with glowing suckers that writhe constantly. 4x size.
- The iris, veins, shard edges and suckers glow at full brightness - in its Darkness attack,
  they're all you can see.
- Model: `LivingVoidModel`. Textures: `gen_living_void_texture.py`.
- Its own custom boss bar (see `BossBars`): a pixel-art portrait of the eye and tentacles sitting
  on a starry void frame.

## Stats
- 2000 health (1000 hearts), 20 armor / 10 toughness, immune to knockback, fire and falling.
- Hitbox 8 blocks wide x 10 tall.
- Flies: circles slowly about 6 blocks from its target, hovering just over their head - low
  enough that its tentacles are within sword reach - always turned to stare at it. Hunts the
  nearest survival player within 96 blocks. Never despawns.

## Attacks
It goes through every attack once in a shuffled order before repeating, with a short pause
between (shorter below half health). It often whips its tentacles first if you're in reach.
- **Tentacle Lash:** rears back and whips everything within 8 blocks of its body - 22 damage and
  a big shove.
- **Void Rifts:** rifts tear open on the ground around you and spit out Endermen and Vexes (twice
  as many rifts below half health). Its minions vanish when it dies.
- **Darkness:** gives everyone Darkness and Blindness, then its tentacles strike its target from
  the dark (25 damage).
- **Gravity Flip:** flings everyone within 24 blocks into the sky, then slams them back down
  (15 damage, plus the fall).
- **Void Orbs:** three homing orbs of darkness. Each explodes (14 damage) and leaves a cloud of
  Wither and Darkness.
- **Ground Beam:** a beam from its eye tracks you, with a purple warning ring on the ground. Once
  the ring stops moving you have a moment to dodge - then it explodes (20 damage, launches you)
  and leaves **purple fire** burning for 6 seconds (damage and sets you alight).

## Drops
- **8-16 Universal Shards**
- **"Before the First Light"** - a second written book by "???": even the void is afraid.
  Something older than the universe, locked away before the first star, is breaking free. It never
  says what.
- **1000 XP** (when a player lands the kill).

## Who its attacks hit
Everything except itself, its own minions, and players in creative/spectator.

## Open Questions for Later
- The ancient terror the book warns about.
