"""
Generate the Universal Destroyer's textures, and the Void Seal's, the Destroyer's Crown's and the
broken blade's. Uses only stdlib (struct + zlib) - no Pillow required, same approach as the other
gen_*_texture.py scripts.

The cube list below must match UniversalDestroyerModel.createBodyLayer(). The model's sheet is
128 x 128 units, but the entity textures are painted at 4x detail (512 x 512 pixels) - Minecraft
stretches them to fit, so every face gets four times the pixels. Outputs:
  - entity/universal_destroyer.png       - carved black stone plates veined with purple, horns and
                                           a crown of broken spikes, burning eyes, a great rune on
                                           its chest plate, riveted gunmetal armor, and a black
                                           blade carved with glowing runes, the Universal Core
                                           set in its guard.
  - entity/universal_destroyer_glow.png  - only the glowing parts (veins, eyes, runes, cores).
  - entity/universal_destroyer_glow_annihilation.png - phase 3: the dormant veins blaze too, and
                                           its eyes burn red.
  - entity/universal_destroyer_glow_overcharged.png  - below 10,000 health: its whole body glows
                                           (drawn pulsing on top of the others).
  - item/universal_destroyer_spawn_egg.png - a black stone egg veined with purple.
  - block/void_seal.png                  - black obsidian with a ring of purple runes and cracks.
  - block/destroyer_sword_fragment.png   - the broken blade's black metal, a glowing rune down it.
  - item/destroyers_crown.png            - a jagged black crown with glowing purple gems.
  - entity/equipment/humanoid/destroyers_crown.png - the crown when worn (64x32 armor layer).
  - gui/sprites/boss_bar/universal_destroyer/ - its boss bar: a 32x32 portrait of its horned,
    crowned head (+ glow, + phase 3 versions with red eyes), a black rune frame, the fills, the
    drained bar.
"""
import zlib, struct, os, math, random

T = (0, 0, 0, 0)
ROOT = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "toinfinititeandbeyond", "textures")

STONE = [(10, 8, 14), (16, 13, 21), (22, 18, 28), (29, 24, 36), (37, 31, 45)]
METAL = [(14, 13, 18), (24, 22, 30), (36, 34, 44), (52, 50, 62)]
VEIN = (175, 75, 255, 255)
VEIN_CORE = (235, 180, 255, 255)
DORMANT = (48, 26, 66, 255)
BLAZE = (255, 120, 240, 255)
EYE = (250, 220, 255, 255)
EYE_RED = (255, 70, 70, 255)
CORE = [(120, 20, 140), (175, 45, 185), (220, 90, 220), (250, 160, 245), (255, 225, 255)]

rng = random.Random(4096)


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in rows)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0]))
           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"Written: {path}")


def grid(w, h):
    return [[T for _ in range(w)] for _ in range(h)]


def faces(u, v, w, h, d):
    """(name, x, y, width, height) of each face in Java box-UV layout."""
    return [("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d), ("right", u, v + d, d, h),
            ("front", u + d, v + d, w, h), ("left", u + d + w, v + d, d, h), ("back", u + 2 * d + w, v + d, w, h)]


def shade(color, amount):
    return tuple(max(0, min(255, int(c * amount))) for c in color[:3]) + (255,)


# name: (u, v, w, h, d, kind) - must match UniversalDestroyerModel (shared UVs are listed once)
CUBES = {
    "chest":       (0, 0, 16, 11, 9, "stone"),
    "chest_plate": (0, 20, 14, 6, 1, "plate"),
    "chest_core":  (120, 0, 3, 3, 1, "gem"),
    "head":        (50, 0, 7, 7, 7, "head"),
    "brow":        (50, 14, 8, 2, 2, "brow"),
    "jaw":         (30, 20, 6, 2, 5, "jaw"),
    "horn":        (116, 20, 2, 6, 2, "horn"),
    "horn_tip":    (124, 8, 1, 4, 1, "horn_tip"),
    "spike_big":   (44, 27, 2, 5, 2, "spike"),
    "spike_small": (120, 8, 1, 3, 1, "spike"),
    "belt":        (78, 0, 13, 4, 8, "metal"),
    "buckle":      (70, 14, 4, 3, 1, "gem"),
    "tasset":      (52, 20, 3, 5, 7, "plate"),
    "thigh":       (72, 20, 6, 7, 6, "stone"),
    "shin":        (96, 20, 5, 6, 5, "stone"),
    "knee":        (0, 27, 6, 3, 1, "plate"),
    "foot":        (52, 34, 6, 2, 7, "metal"),
    "pauldron":    (78, 34, 7, 5, 8, "plate"),
    "upper_arm":   (0, 40, 5, 7, 5, "stone"),
    "gauntlet":    (20, 40, 6, 9, 6, "metal"),
    "handle":      (22, 27, 2, 9, 2, "grip"),
    "pommel":      (110, 12, 3, 2, 3, "metal"),
    "guard":       (80, 12, 12, 2, 3, "metal"),
    "guard_wing":  (14, 27, 2, 4, 2, "metal"),
    "sword_core":  (30, 27, 3, 3, 4, "gem"),
    "blade_low":   (108, 34, 5, 14, 1, "blade"),
    "blade_top":   (52, 44, 5, 14, 1, "blade"),
    "blade_tip":   (120, 4, 3, 3, 1, "blade"),
}

R = 4                      # pixels per model unit
W = H = 128 * R
base, glow, annihilation, overcharged = grid(W, H), grid(W, H), grid(W, H), grid(W, H)

# Little runes, carved into the blade and the armor (3 wide x 5 tall).
GLYPHS = [
    ["###", "#..", "###", "..#", "###"],
    ["#.#", "#.#", "###", ".#.", ".#."],
    [".#.", "###", ".#.", "#.#", "#.#"],
    ["##.", "#.#", "##.", "#.#", "##."],
    ["#..", "##.", "#.#", "##.", "#.."],
    [".##", "#..", ".#.", "..#", "##."],
]


def put(x, y, color, glows=None):
    """glows: None, 'lit' (glows in every phase), 'dormant' (only blazes in phase 3) or 'eye'."""
    if not (0 <= x < W and 0 <= y < H):
        return
    if glows == 'dormant':
        base[y][x] = DORMANT
        annihilation[y][x] = BLAZE
        return
    base[y][x] = color
    if glows == 'lit':
        glow[y][x] = color
        annihilation[y][x] = color
    elif glows == 'eye':
        glow[y][x] = color
        annihilation[y][x] = EYE_RED
    elif glows == 'halo':
        # A faint glow round a vein (the glow layers add their colour, so dark purple = soft light).
        dim = (70, 25, 105, 255)
        if glow[y][x] == T:
            glow[y][x] = dim
        if annihilation[y][x] == T:
            annihilation[y][x] = (110, 40, 120, 255)


def halo(x, y, kind):
    """Soft light round a lit pixel, on the pixels next to it that don't glow themselves."""
    if kind != 'lit':
        return
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if 0 <= nx < W and 0 <= ny < H and glow[ny][nx] == T:
            glow[ny][nx] = (60, 20, 95, 255)
            if annihilation[ny][nx] == T:
                annihilation[ny][nx] = (95, 30, 110, 255)


def noise_tile(seed, size=6):
    """Blocky value noise: a few levels of grain so the stone doesn't look flat."""
    r = random.Random(seed)
    return [[r.random() for _ in range(64)] for _ in range(64)]


GRAIN = noise_tile(1)


def grain(x, y):
    a = GRAIN[(y // 3) % 64][(x // 3) % 64]
    b = GRAIN[(y // 7 + 17) % 64][(x // 7 + 29) % 64]
    return a * 0.6 + b * 0.4


FACE_LIGHT = {"top": 1.25, "bottom": 0.6, "front": 1.0, "back": 0.8, "right": 0.9, "left": 0.9}


def paint_stone(face, X, Y, FW, FH):
    """Carved stone plates: irregular blocks with dark seams, lit edges, and grain."""
    light = FACE_LIGHT[face]
    block_w = rng.choice([10, 12, 14])
    block_h = rng.choice([8, 10, 12])
    for y in range(FH):
        row_shift = (y // block_h) * (block_w // 2 + rng.randint(0, 0))
        for x in range(FW):
            bx, by = (x + row_shift) % block_w, y % block_h
            g = grain(X + x, Y + y)
            level = 1 + int(g * 3.2)
            c = STONE[min(4, level)]
            if bx == 0 or by == 0:
                c = shade(STONE[0], 0.7)                    # seam
            elif by == 1 or bx == 1:
                c = STONE[min(4, level + 1)]                # the lit top/left edge of the plate
            elif by == block_h - 1 or bx == block_w - 1:
                c = STONE[max(0, level - 1)]                # its shadowed bottom/right edge
            put(X + x, Y + y, shade(c, light))
    # Cracks: thin dark lines wandering across.
    for _ in range(max(1, FW * FH // 600)):
        x, y = rng.randrange(FW), rng.randrange(FH)
        for _ in range(rng.randint(6, 16)):
            if 0 <= x < FW and 0 <= y < FH:
                put(X + x, Y + y, (4, 3, 6, 255))
            x += rng.choice([-1, 0, 1])
            y += rng.choice([0, 1, 1])


def paint_veins(X, Y, FW, FH, count, kind):
    """Veins of light: wandering lines with a bright core and a soft glow round them."""
    for _ in range(count):
        x, y = X + rng.randrange(FW), Y + rng.randrange(FH)
        dx, dy = rng.choice([-1, 1]), rng.choice([-1, 1])
        for _ in range(rng.randint(FW // 2 + 6, FW + FH)):
            if X <= x < X + FW and Y <= y < Y + FH:
                put(x, y, VEIN_CORE if rng.random() < 0.35 else VEIN, glows=kind)
                if X <= x + 1 < X + FW:
                    put(x + 1, y, VEIN, glows=kind)
                halo(x, y, kind)
                halo(x + 1, y, kind)
            if rng.random() < 0.3:
                dx = rng.choice([-1, 0, 1])
            if rng.random() < 0.3:
                dy = rng.choice([-1, 0, 1])
            if dx == 0 and dy == 0:
                dy = 1
            x += dx
            y += dy


def paint_metal(face, X, Y, FW, FH, rivets):
    """Gunmetal: brushed streaks, bright rim, dark inner line, rivets in the corners."""
    light = FACE_LIGHT[face]
    for y in range(FH):
        for x in range(FW):
            streak = GRAIN[(x * 3 + 5) % 64][(y // 9) % 64]
            c = METAL[1] if streak < 0.5 else METAL[2]
            if x == 0 or y == 0:
                c = METAL[3]
            elif x == FW - 1 or y == FH - 1:
                c = METAL[0]
            elif x == 1 or y == 1 or x == FW - 2 or y == FH - 2:
                c = shade(METAL[0], 0.8)
            put(X + x, Y + y, shade(c, light))
    if rivets and FW >= 10 and FH >= 10:
        for rx, ry in ((3, 3), (FW - 4, 3), (3, FH - 4), (FW - 4, FH - 4)):
            put(X + rx, Y + ry, (120, 115, 135, 255))
            put(X + rx + 1, Y + ry, METAL[3] + (255,))
            put(X + rx, Y + ry + 1, METAL[0] + (255,))


def paint_glyph(X, Y, glyph, kind='lit'):
    for gy, row in enumerate(glyph):
        for gx, c in enumerate(row):
            if c == '#':
                put(X + gx, Y + gy, VEIN_CORE if gy == 2 else VEIN, glows=kind)
                halo(X + gx, Y + gy, kind)


def paint_gem(X, Y, FW, FH):
    """A glowing core: rings of light round a white-hot heart, with a glint."""
    for y in range(FH):
        for x in range(FW):
            r = math.hypot(x - (FW - 1) / 2, y - (FH - 1) / 2) / max(1.0, min(FW, FH) / 2)
            c = CORE[max(0, min(4, 4 - int(r * 4.2)))] + (255,)
            put(X + x, Y + y, c, glows='lit')
    put(X + FW // 3, Y + FH // 3, (255, 255, 255, 255), glows='lit')


for name, (u, v, w, h, d, kind) in CUBES.items():
    for face, fx, fy, fw, fh in faces(u, v, w, h, d):
        X, Y, FW, FH = fx * R, fy * R, fw * R, fh * R
        if FW == 0 or FH == 0:
            continue
        if kind in ("stone", "head", "jaw", "brow"):
            paint_stone(face, X, Y, FW, FH)
            area = FW * FH
            paint_veins(X, Y, FW, FH, max(1, area // 700), 'lit')
            paint_veins(X, Y, FW, FH, max(1, area // 500), 'dormant')
        elif kind in ("metal", "plate"):
            paint_metal(face, X, Y, FW, FH, rivets=kind == "plate")
            if kind == "plate" and face in ("front", "top", "right", "left") and FW >= 16 and FH >= 8:
                # A line of runes engraved along the plate.
                for i, gx in enumerate(range(X + 5, X + FW - 7, 6)):
                    paint_glyph(gx, Y + FH // 2 - 2, GLYPHS[(i + u) % len(GLYPHS)])
        elif kind == "grip":
            for y in range(FH):
                for x in range(FW):
                    wrap = (x + y) % 6 < 3
                    put(X + x, Y + y, (34, 18, 40, 255) if wrap else (20, 10, 24, 255))
        elif kind == "gem":
            paint_gem(X, Y, FW, FH)
        elif kind in ("spike", "horn", "horn_tip"):
            for y in range(FH):
                for x in range(FW):
                    t = y / max(1, FH - 1)          # 0 at the tip end (top), 1 at the root
                    c = shade(STONE[0], 0.5 + 0.7 * t)
                    if x == 0:
                        c = shade(STONE[2], 0.6 + 0.6 * t)
                    if face in ("front", "back", "right", "left") and (y % 8 == 3) and kind == "horn":
                        c = shade(STONE[3], 0.9)    # ridges round the horn
                    put(X + x, Y + y, c)
            if face == "top":
                # Broken tops - jagged, a faint ember of purple left inside.
                for y in range(FH):
                    for x in range(FW):
                        if (x + y) % 3 == 0:
                            put(X + x, Y + y, DORMANT, glows='dormant')
            if kind == "horn_tip" and face != "bottom":
                for x in range(FW):
                    put(X + x, Y, VEIN_CORE, glows='lit')
                    put(X + x, Y + 1, VEIN, glows='lit')
        elif kind == "blade":
            for y in range(FH):
                for x in range(FW):
                    edge = x <= 1 or x >= FW - 2
                    fuller = abs(x - (FW - 1) / 2) < 3.0
                    c = (95, 92, 110, 255) if edge else (shade(METAL[0], 0.7) if fuller else METAL[1] + (255,))
                    if not edge and not fuller and GRAIN[(x * 5) % 64][(y // 5) % 64] > 0.7:
                        c = METAL[2] + (255,)
                    put(X + x, Y + y, c)
            if face in ("front", "back") and FW >= 12:
                # Runes carved down the fuller.
                for i, gy in enumerate(range(Y + 2, Y + FH - 6, 7)):
                    paint_glyph(X + FW // 2 - 1, gy, GLYPHS[(i + v) % len(GLYPHS)])

# The face (front of the head, 28 x 28 pixels): burning slanted eyes under the brow, cheek cracks.
_, hx, hy, hw, hh = faces(*CUBES["head"][:5])[3]
X, Y = hx * R, hy * R
for side in (0, 1):
    for i in range(7):
        ex = X + (4 + i if side == 0 else 24 - i)
        ey = Y + 13 + (i // 3 if side == 0 else i // 3)
        for dy in range(2):
            put(ex, ey + dy, EYE if i < 5 else VEIN_CORE, glows='eye')
            halo(ex, ey + dy, 'lit')
    # Glowing tears of light running down from the eyes.
    tx = X + (7 if side == 0 else 20)
    for ty in range(Y + 16, Y + 26):
        if ty % 2 == 0:
            put(tx, ty, VEIN, glows='lit')
# The brow's front: a deep scowl, shadowed.
_, bx, by, bw, bh = faces(*CUBES["brow"][:5])[3]
for x in range(bw * R):
    put(bx * R + x, by * R + bh * R - 1, (2, 1, 4, 255))
    put(bx * R + x, by * R + bh * R - 2, (6, 4, 9, 255))
# The jaw's front: a jagged mouth glowing from inside.
_, jx, jy, jw, jh = faces(*CUBES["jaw"][:5])[3]
for x in range(2, jw * R - 2):
    tooth = (x // 3) % 2 == 0
    for y in range(2, 5 if tooth else 6):
        put(jx * R + x, jy * R + y, VEIN if y < 4 else VEIN_CORE, glows='lit')
# A great rune on the chest plate: a ring with a cross through it, round the core.
_, cx0, cy0, cw, ch = faces(*CUBES["chest_plate"][:5])[3]
X, Y, FW, FH = cx0 * R, cy0 * R, cw * R, ch * R
for y in range(FH):
    for x in range(FW):
        r = math.hypot(x - (FW - 1) / 2, (y - (FH - 1) / 2) * 1.0)
        if 9.0 <= r <= 10.5 or (r < 9.0 and (abs(x - (FW - 1) / 2) < 1.0 or abs(y - (FH - 1) / 2) < 1.0)):
            put(X + x, Y + y, VEIN_CORE if r < 3 else VEIN, glows='lit')
            halo(X + x, Y + y, 'lit')

# Overcharged: every painted pixel gets a soft violet light, and the glowing parts burn white.
for y in range(H):
    for x in range(W):
        if base[y][x] != T:
            overcharged[y][x] = (95, 45, 150, 255)
        if glow[y][x] != T and glow[y][x][0] > 120:
            overcharged[y][x] = (255, 230, 255, 255)

write_png(os.path.join(ROOT, "entity", "universal_destroyer.png"), base)
write_png(os.path.join(ROOT, "entity", "universal_destroyer_glow.png"), glow)
write_png(os.path.join(ROOT, "entity", "universal_destroyer_glow_annihilation.png"), annihilation)
write_png(os.path.join(ROOT, "entity", "universal_destroyer_glow_overcharged.png"), overcharged)


# ============================ The broken blade block (16x16) ============================
frag = grid(16, 16)
for y in range(16):
    for x in range(16):
        frag[y][x] = METAL[1] + (255,) if GRAIN[y][x * 2] < 0.55 else METAL[2] + (255,)
        if y in (0, 15):
            frag[y][x] = (95, 92, 110, 255)
for y in range(1, 15):
    frag[y][7] = shade(METAL[0], 0.7)
    frag[y][8] = shade(METAL[0], 0.7)
for gy, row in enumerate(GLYPHS[0]):
    for gx, c in enumerate(row):
        if c == '#':
            frag[5 + gy][6 + gx] = VEIN_CORE if gy == 2 else VEIN
write_png(os.path.join(ROOT, "block", "destroyer_sword_fragment.png"), frag)


# ============================ Spawn egg (16x16) ============================
egg = grid(16, 16)
for y in range(16):
    for x in range(16):
        half_width = 5.5 * math.sqrt(max(0.0, 1.0 - ((y - 8.5) / 7.0) ** 2)) * (0.75 + 0.25 * (y / 15.0))
        if abs(x - 7.5) > half_width or not (1 <= y <= 15):
            continue
        edge = abs(x - 7.5) > half_width - 1.0 or y in (1, 15)
        egg[y][x] = (2, 1, 4, 255) if edge else STONE[rng.choice([1, 2, 3])] + (255,)
for (x, y) in ((6, 4), (7, 5), (7, 6), (8, 7), (9, 8), (5, 9), (6, 10), (7, 10), (10, 11), (9, 12)):
    egg[y][x] = VEIN
egg[8][7] = CORE[4] + (255,)
egg[8][8] = CORE[3] + (255,)
write_png(os.path.join(ROOT, "item", "universal_destroyer_spawn_egg.png"), egg)


# ============================ Void Seal block (16x16) ============================
seal = grid(16, 16)
for y in range(16):
    for x in range(16):
        r = math.hypot(x - 7.5, y - 7.5)
        if 5.0 <= r <= 6.0:
            seal[y][x] = VEIN if (int(math.atan2(y - 7.5, x - 7.5) * 4) % 2) else VEIN_CORE
        elif r < 1.6:
            seal[y][x] = CORE[4] + (255,) if r < 0.8 else CORE[2] + (255,)
        elif r < 4.2 and (abs(x - y) < 0.8 or abs(x + y - 15) < 0.8):
            seal[y][x] = VEIN
        else:
            seal[y][x] = (6, 3, 10, 255) if rng.random() < 0.5 else (14, 8, 22, 255)
for x in range(16):
    for y in (0, 15):
        seal[y][x] = (2, 1, 4, 255)
        seal[x][y] = (2, 1, 4, 255)
write_png(os.path.join(ROOT, "block", "void_seal.png"), seal)


# ============================ Crown item icon (16x16) ============================
CROWN_SHAPE = [
    "................",
    "................",
    ".#....#...#....#",
    ".#...##..##...#.",
    ".##..##..##..##.",
    ".##.###.####.##.",
    ".######.#######.",
    ".##############.",
    ".##############.",
    ".#o##o####o##o#.",
    ".##############.",
    ".##############.",
    "..############..",
    "................",
    "................",
    "................",
]
crown = grid(16, 16)
for y, row in enumerate(CROWN_SHAPE):
    for x, c in enumerate(row):
        if c == '#':
            crown[y][x] = (8, 6, 12, 255) if (x + y) % 3 else (24, 20, 32, 255)
        elif c == 'o':
            crown[y][x] = VEIN_CORE
# Outline so it reads against dark backgrounds.
outlined = [row[:] for row in crown]
for y in range(16):
    for x in range(16):
        if crown[y][x] == T and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and crown[y + dy][x + dx] != T
                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            outlined[y][x] = (90, 40, 140, 255)
write_png(os.path.join(ROOT, "item", "destroyers_crown.png"), outlined)


# ============================ Crown when worn (64x32 armor layer) ============================
# Only the head cube (box-UV at 0,0, 8x8x8): a band round the upper sides with jagged spikes.
armor = grid(64, 32)
for face, fx, fy, fw, fh in faces(0, 0, 8, 8, 8):
    if face in ("top", "bottom"):
        continue
    for x in range(fx, fx + fw):
        spike = (x - fx) % 3 == 1
        for y in range(fy, fy + 4):
            if y - fy == 0 and not spike:
                continue
            armor[y][x] = (8, 6, 12, 255) if (x + y) % 3 else (26, 22, 34, 255)
        if (x - fx) % 4 == 2:
            armor[fy + 2][x] = VEIN_CORE
write_png(os.path.join(ROOT, "entity", "equipment", "humanoid", "destroyers_crown.png"), armor)


# ============================ Boss bar sprites ============================
BAR = os.path.join(ROOT, "gui", "sprites", "boss_bar", "universal_destroyer")


def portrait(angry):
    img, gl = grid(32, 32), grid(32, 32)

    def pput(x, y, color, glows=False):
        if 0 <= x < 32 and 0 <= y < 32:
            img[y][x] = color
            if glows:
                gl[y][x] = color

    prng = random.Random(77)
    # Shoulders and the top of the chest along the bottom.
    for y in range(24, 32):
        for x in range(2, 30):
            if y - 24 >= (abs(x - 15.5) - 9) * 0.8:
                pput(x, y, STONE[prng.choice([1, 2, 3])] + (255,))
    # The head: a block of black stone.
    for y in range(9, 26):
        for x in range(8, 24):
            pput(x, y, STONE[4 if x < 10 or y < 11 else prng.choice([1, 2, 2, 3])] + (255,))
    # Horns sweeping out and up from the sides of its head.
    for i in range(9):
        for side in (-1, 1):
            hx = int(round(15.5 + side * (8 + i * 0.8)))
            hy = 14 - i
            pput(hx, hy, (14, 11, 18, 255))
            pput(hx, hy + 1, (24, 20, 30, 255))
    for side in (-1, 1):
        pput(int(round(15.5 + side * 15.0)), 5, VEIN_CORE, glows=True)
    # The crown of broken spikes.
    for sx, height in ((9, 6), (12, 4), (15, 8), (18, 5), (21, 7)):
        for y in range(9 - height, 9):
            pput(sx, y, (6, 5, 9, 255))
            pput(sx + 1, y, (16, 13, 20, 255))
    # Veins.
    vein = BLAZE if angry else VEIN
    for (x, y) in ((10, 12), (11, 13), (11, 14), (21, 19), (22, 20), (22, 21), (14, 22), (13, 23), (17, 27), (18, 28), (8, 27)):
        pput(x, y, vein, glows=True)
    # The brow, the eyes, the mouth.
    for x in range(9, 23):
        pput(x, 14, STONE[0] + (255,))
    eye = EYE_RED if angry else EYE
    for ex in (11, 18):
        for dx in range(3):
            pput(ex + dx, 16, eye, glows=True)
        pput(ex + 1, 17, VEIN_CORE if not angry else BLAZE, glows=True)
    for x in range(12, 20):
        pput(x, 21, vein if x % 2 else STONE[0] + (255,), glows=x % 2 == 1)
    # The chest rune peeking up from the bottom.
    for x in range(13, 19):
        pput(x, 30, vein, glows=True)
    pput(15, 29, VEIN_CORE, glows=True)
    pput(16, 29, VEIN_CORE, glows=True)
    # Dark outline round the silhouette.
    out = [row[:] for row in img]
    for y in range(32):
        for x in range(32):
            if img[y][x] == T and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and img[y + dy][x + dx] != T
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = (0, 0, 0, 255)
    return out, gl


for angry, suffix in ((False, ""), (True, "_enraged")):
    p, g = portrait(angry)
    write_png(os.path.join(BAR, f"portrait{suffix}.png"), p)
    write_png(os.path.join(BAR, f"portrait_glow{suffix}.png"), g)

# Frame: black stone carved with glowing purple runes, around the 182x5 bar, spiked ends.
FW, FH = 204, 13
BAR_X, BAR_Y, BAR_W, BAR_H = 11, 4, 182, 5
frame = grid(FW, FH)
for y in range(FH):
    for x in range(FW):
        if BAR_X <= x < BAR_X + BAR_W and BAR_Y <= y < BAR_Y + BAR_H:
            continue
        end_dist = min(x, FW - 1 - x)
        if end_dist < 8 and abs(y - FH // 2) > end_dist * 0.8:
            continue
        rim = y in (BAR_Y - 1, BAR_Y + BAR_H) and BAR_X - 1 <= x <= BAR_X + BAR_W
        rim = rim or (x in (BAR_X - 1, BAR_X + BAR_W) and BAR_Y - 1 <= y <= BAR_Y + BAR_H)
        frame[y][x] = (0, 0, 0, 255) if rim else STONE[rng.choice([1, 2, 3, 4])] + (255,)
for x in range(BAR_X + 4, BAR_X + BAR_W - 4, 9):
    frame[1][x] = VEIN
    frame[1][x + 1] = VEIN_CORE
    frame[FH - 2][x + 4] = VEIN
for gx in (1, 2, FW - 3, FW - 2):
    frame[FH // 2][gx] = VEIN_CORE
write_png(os.path.join(BAR, "frame.png"), frame)


def fill_bar(colors):
    bar = grid(BAR_W, BAR_H)
    for y in range(BAR_H):
        for x in range(BAR_W):
            bar[y][x] = (255, 235, 255, 255) if rng.random() < 0.03 else colors[y] + (255,)
    return bar


write_png(os.path.join(BAR, "fill.png"), fill_bar([(200, 110, 255), (160, 70, 230), (125, 45, 200), (95, 25, 160), (65, 12, 115)]))
write_png(os.path.join(BAR, "fill_enraged.png"), fill_bar([(255, 120, 120), (230, 60, 80), (195, 30, 70), (150, 15, 60), (100, 8, 45)]))
empty = grid(BAR_W, BAR_H)
for y in range(BAR_H):
    for x in range(BAR_W):
        empty[y][x] = (8, 5, 12, 255) if y else (18, 12, 26, 255)
write_png(os.path.join(BAR, "empty.png"), empty)
print("Done!")
