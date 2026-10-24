# Infinitite Monstrosity Requirements

## Overview
The mod's boss: a towering golem of dark rock, split by glowing purple Infinitite cracks, gripping
a giant void sword. Inspired by the general idea of Minecraft Dungeons' Redstone Monstrosity
(hunched rock golem, glowing cracks) - but darker, purple, armed with a sword, and an original
model and texture.

## Looks
- A hunched rock golem: huge chest, a small head sunk low between massive boulder shoulders
  (heavy brow, deep-set glowing eyes, a jutting jaw with glowing teeth),
  long gorilla arms ending in boulder fists, short thick legs, purple Infinitite crystals growing
  out of its back. 5x normal size.
- Glowing purple cracks, eyes, chest core, crystals and sword edge - drawn at full brightness, so
  they shine in the dark. In phase 2, many more cracks blaze pink.
- Every special attack has its own animation (see below).
- Model: `InfinititeMonstrosityModel`. Textures: `gen_infinitite_monstrosity_texture.py`.

## Stats
- 1000 health (500 hearts), 30 attack damage (+50% in phase 2), 30 armor / 10 toughness,
  immune to knockback.
- Lumbering speed 0.25 (+30% in phase 2). Previously set to 3.22 by mistake (14x too fast).
- **Hitbox** 11 blocks wide x 10 tall, to match the short, wide golem (it used to be a tall thin
  box, so its arms, legs and head couldn't be hit).
- **Relentless:** hunts the nearest survival player within 64 blocks - it doesn't need to see you
  first, or be hit.

## Custom Boss Bar
- At the top of the screen: a rock-framed bar with spiked ends and a cracked purple fill, a
  pixel-art portrait of the golem sitting right on top of it, and its name underneath. Shown
  whenever a Monstrosity is within 64 blocks.
- The portrait's eyes, core and cracks pulse with light. In phase 2 the fill turns hot pink, the
  portrait blazes, the pulse quickens, and the name becomes "Infinitite Monstrosity - Enraged".
- Sprites: `gen_monstrosity_boss_bar.py`. Drawn by `BossBars` straight from the boss's
  synced health - it isn't a vanilla boss bar at all.

## Phase 1 (above half health)
- **Melee:** an overhead sword chop. Hits inflict Wither II, Poison II and Weakness III.
- **Ground Slam:** both arms raised, then smashed down - 12 damage and big knockback within 8
  blocks, plus Slowness.
- **Rock Barrage:** hurls three chunks of crying obsidian one after another, properly aimed (it
  allows for gravity and leads you if you're running); each bursts into
  purple sparks (20 damage on a direct hit, 8 to anything nearby). Never breaks blocks.
- **Fist Quake:** both fists pounded into the ground, sending a line of exploding purple cracks
  racing toward its target (14 damage each, launching you up).

## Phase 2 (below half health)
At half health it **roars and powers up**: a huge blast throws everyone back, its cracks blaze,
it gets faster and hits harder, and it adds:
- **Void Beam:** charges up at its chest, then fires a purple beam for ~2.5 seconds that slowly
  sweeps to follow its target (12 damage per hit, sets you on fire). You can outrun the sweep.
- **Black Hole:** raises its sword and stabs it into the ground; a black hole opens in the sky
  above it for 6 seconds, dragging everything within 24 blocks toward it (harder the closer you
  are) and crushing anything that gets within 3.5 blocks (10 damage every half second).
- **Void Lightning:** lightning on up to three nearby players every 6 seconds.

It does one special attack at a time, standing still while it does, with a pause between them
(shorter in phase 2).

## Drops
- **8-16 Infinitite Ingots**
- **2-4 Universal Shards**
- **Monstrosity Heart** - a trophy: its still-glowing purple heart (animated icon). No use yet.
- **"Whispers of the Void"** - a written book by "???": a journal from someone who has seen what
  lives in the void. It hints at something far worse than the Monstrosity without ever naming it.
- **500 XP**, like the Ender Dragon (only when a player lands the kill).

Its death "explosion" is only the look and sound of one - a real explosion used to go off right
after the loot dropped and destroy it.

## Who its attacks hit
Everything except itself, other Monstrosities, and players in creative/spectator.

## Open Questions for Later
- A use for the Monstrosity Heart.
- The thing in the void the book warns about.
