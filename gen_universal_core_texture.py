"""
Generate the Universal Core's textures. Uses only stdlib (struct + zlib) - no Pillow required,
same approach as the other gen_*_texture.py scripts in this repo.

1. universal_core_3d.png (+ .mcmeta) - the 3D GeckoLib model's skin, an animated 64x(64*8)
   frame-strip (8 frames of 64x64). Painted on the box-UV footprint of each cube in
   universal_core.geo.json:
     - core:       an End Crystal-style swirling pink-purple core, the part that animates.
     - ring_inner,
       ring_outer: white frames - a bright white border on every face, see-through inside, so
                   they read as rings/edges tumbling around the core like an End Crystal's glass.
     - aura:       a faint see-through golden shell with brighter gold edges.
2. universal_core.png - a flat 16x16 icon. The game shows the 3D model everywhere, but item
   models still need a flat texture (it's used for things like the eating particles).
3. mob_effect/universal_god.png - the 18x18 "Universal God" status effect icon: a golden halo
   over a glowing purple-white star.
4. gui/sprites/hud/god_heart/*.png - a god's 9x9 gold-and-white health-bar hearts (gold hearts
   with white outlines, white absorption hearts with a gold glint).
"""
import zlib, struct, os, math, json

FRAMES = 8
FRAMETIME = 3
TEX = 64
T = (0, 0, 0, 0)

# Box-UV origin and cube size for each cube, matching universal_core.geo.json.
CUBES = {
    "core": ((0, 0), 4),
    "ring_inner": ((0, 8), 6),
    "ring_outer": ((0, 20), 8),
    "aura": ((0, 36), 10),
}

CORE_PALETTE = [
    (70, 10, 90, 255),
    (120, 20, 140, 255),
    (175, 45, 185, 255),
    (220, 90, 220, 255),
    (250, 160, 245, 255),
    (255, 225, 255, 255),
]
WHITE = (255, 255, 255, 255)
SOFT_WHITE = (235, 235, 250, 255)
GOLD_EDGE = (255, 210, 90, 140)
GOLD_FILL = (255, 200, 70, 45)

ASSETS = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "toinfinititeandbeyond", "textures")


def write_png(path, width, height, pixel_rows):
    def make_chunk(tag, data):
        c = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', c)

    ihdr_data = struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0])
    raw_rows = b''
    for row in pixel_rows:
        raw_rows += b'\x00'
        for (r, g, b, a) in row:
            raw_rows += bytes([r, g, b, a])
    png = (
        b'\x89PNG\r\n\x1a\n'
        + make_chunk(b'IHDR', ihdr_data)
        + make_chunk(b'IDAT', zlib.compress(raw_rows, 9))
        + make_chunk(b'IEND', b'')
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"Written: {path}")


def faces(origin, size):
    """The six size x size face squares of a box-UV cube: top/bottom row, then the four sides."""
    u, v = origin
    s = size
    return [(u + s, v), (u + 2 * s, v), (u, v + s), (u + s, v + s), (u + 2 * s, v + s), (u + 3 * s, v + s)]


def paint_core(grid, phase):
    origin, size = CUBES["core"]
    for fx, fy in faces(origin, size):
        c = (size - 1) / 2.0
        for y in range(size):
            for x in range(size):
                dx, dy = x - c, y - c
                r = math.sqrt(dx * dx + dy * dy)
                swirl = math.sin(math.atan2(dy, dx) * 2.0 - r * 1.8 + phase)
                level = 2.5 + swirl * 1.5 + (1.0 - r / 3.0) * 1.5
                grid[fy + y][fx + x] = CORE_PALETTE[max(0, min(5, int(round(level))))]


def paint_ring(grid, name):
    origin, size = CUBES[name]
    for fx, fy in faces(origin, size):
        for y in range(size):
            for x in range(size):
                edge = x in (0, size - 1) or y in (0, size - 1)
                corner = x in (0, size - 1) and y in (0, size - 1)
                if corner:
                    grid[fy + y][fx + x] = WHITE
                elif edge:
                    grid[fy + y][fx + x] = SOFT_WHITE


def paint_aura(grid):
    origin, size = CUBES["aura"]
    for fx, fy in faces(origin, size):
        for y in range(size):
            for x in range(size):
                edge = x in (0, size - 1) or y in (0, size - 1)
                grid[fy + y][fx + x] = GOLD_EDGE if edge else GOLD_FILL


def build_3d_frame(frame_index):
    phase = 2.0 * math.pi * frame_index / FRAMES
    grid = [[T for _ in range(TEX)] for _ in range(TEX)]
    paint_core(grid, phase)
    paint_ring(grid, "ring_inner")
    paint_ring(grid, "ring_outer")
    paint_aura(grid)
    return grid


rows = []
for i in range(FRAMES):
    rows.extend(build_3d_frame(i))
path_3d = os.path.join(ASSETS, "item", "universal_core_3d.png")
write_png(path_3d, TEX, TEX * FRAMES, rows)
# Frame size spelled out - otherwise Minecraft assumes square frames the width of the image.
# (They are square here, but being explicit keeps it safe if the layout ever changes.)
with open(path_3d + ".mcmeta", "w") as f:
    json.dump({"animation": {"frametime": FRAMETIME, "width": TEX, "height": TEX}}, f, indent=2)
    f.write("\n")
print(f"Written: {path_3d}.mcmeta")


# --- Flat 16x16 icon: a glowing core inside a white ring with a gold halo ---
icon = [[T for _ in range(16)] for _ in range(16)]
for y in range(16):
    for x in range(16):
        dx, dy = x - 7.5, y - 7.5
        diamond = abs(dx) + abs(dy)
        if diamond <= 3.0:
            icon[y][x] = CORE_PALETTE[max(0, min(5, int(round(5 - diamond))))]
        elif 4.5 <= diamond <= 5.5:
            icon[y][x] = WHITE
        elif 6.0 <= diamond <= 7.0:
            icon[y][x] = (255, 205, 80, 200)
write_png(os.path.join(ASSETS, "item", "universal_core.png"), 16, 16, icon)


# --- 18x18 Universal God effect icon: golden halo over a purple-white star ---
effect = [[T for _ in range(18)] for _ in range(18)]
for y in range(18):
    for x in range(18):
        # Halo: a flattened golden ring near the top.
        hx, hy = (x - 8.5) / 5.5, (y - 3.5) / 1.8
        h = math.sqrt(hx * hx + hy * hy)
        if 0.7 <= h <= 1.15:
            effect[y][x] = (255, 215, 90, 255)
            continue
        # Star: a four-pointed sparkle below it.
        sx, sy = abs(x - 8.5), abs(y - 11.0)
        star = min(sx, sy) * 2.2 + max(sx, sy) * 0.45
        if star <= 3.4:
            level = 5 - int(star * 1.3)
            effect[y][x] = CORE_PALETTE[max(1, min(5, level))]
write_png(os.path.join(ASSETS, "mob_effect", "universal_god.png"), 18, 18, effect)


# --- 9x9 gold-and-white god hearts for the health bar (see UniversalGodHearts.java) ---
HEART = [
    ".###.###.",
    "#########",
    "#########",
    "#########",
    ".#######.",
    "..#####..",
    "...###...",
    "....#....",
    ".........",
]


def in_heart(x, y):
    return 0 <= x < 9 and 0 <= y < 9 and HEART[y][x] == '#'


def is_edge(x, y):
    return in_heart(x, y) and not all(in_heart(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def heart_sprite(fill, shine, half=False):
    """A heart filled with `fill` (inside the outline), with a `shine` highlight near the top left."""
    grid = [[T for _ in range(9)] for _ in range(9)]
    for y in range(9):
        for x in range(9):
            if not in_heart(x, y) or is_edge(x, y) or (half and x > 4):
                continue
            grid[y][x] = shine if (x, y) in ((2, 2), (2, 3), (3, 2)) else fill(y)
    return grid


def container():
    grid = [[T for _ in range(9)] for _ in range(9)]
    for y in range(9):
        for x in range(9):
            if is_edge(x, y):
                grid[y][x] = (255, 255, 255, 255)  # white outline
            elif in_heart(x, y):
                grid[y][x] = (70, 50, 15, 255)  # dark, empty inside
    return grid


def gold(y):
    # Bright gold at the top fading to deep gold at the tip.
    return [(255, 236, 140, 255), (255, 220, 90, 255), (250, 200, 60, 255), (235, 175, 40, 255),
            (220, 150, 30, 255), (200, 130, 25, 255), (190, 120, 20, 255), (180, 110, 15, 255),
            (180, 110, 15, 255)][y]


def white(y):
    return [(255, 255, 255, 255), (255, 255, 255, 255), (248, 246, 255, 255), (240, 236, 252, 255),
            (230, 225, 248, 255), (220, 214, 244, 255), (212, 205, 240, 255), (205, 198, 236, 255),
            (205, 198, 236, 255)][y]


HEARTS_DIR = os.path.join(ASSETS, "gui", "sprites", "hud", "god_heart")
GOLD_SHINE = (255, 255, 240, 255)
WHITE_SHINE = (255, 225, 120, 255)  # white hearts get a little gold glint
write_png(os.path.join(HEARTS_DIR, "container.png"), 9, 9, container())
write_png(os.path.join(HEARTS_DIR, "full.png"), 9, 9, heart_sprite(gold, GOLD_SHINE))
write_png(os.path.join(HEARTS_DIR, "half.png"), 9, 9, heart_sprite(gold, GOLD_SHINE, half=True))
write_png(os.path.join(HEARTS_DIR, "absorbing_full.png"), 9, 9, heart_sprite(white, WHITE_SHINE))
write_png(os.path.join(HEARTS_DIR, "absorbing_half.png"), 9, 9, heart_sprite(white, WHITE_SHINE, half=True))
print("Done!")
