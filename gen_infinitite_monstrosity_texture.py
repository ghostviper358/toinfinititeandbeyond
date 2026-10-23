"""
Generate the Infinitite Monstrosity's textures (128x128, box-UV) plus the Monstrosity Heart icon.
Uses only stdlib (struct + zlib) - no Pillow required, same approach as the other
gen_*_texture.py scripts in this repo.

The cube list below must match InfinititeMonstrosityModel.createBodyLayer(). Outputs:
  - infinitite_monstrosity.png              - the body: dark, mottled rock with purple cracks
                                              (and fainter, dormant cracks that light up in phase 2).
  - infinitite_monstrosity_glow.png         - only the glowing parts (cracks, eyes, chest core,
                                              crystals, sword edge), drawn at full brightness.
  - infinitite_monstrosity_glow_enraged.png - phase 2: the dormant cracks blaze too.
  - item/infinitite_monstrosity_spawn_egg.png - a dark rock egg speckled with glowing purple.
  - item/monstrosity_heart.png (+ .mcmeta)  - the trophy: a dark heart of rock with a pulsing
                                              purple glow, 8 frames.
"""
import zlib, struct, os, math, random, json

W = H = 128
T = (0, 0, 0, 0)
ROOT = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "toinfinititeandbeyond", "textures")

# name: (u, v, w, h, d, kind)
CUBES = {
    "waist":      (0, 26, 14, 8, 10, "rock"),
    "chest":      (0, 0, 20, 14, 12, "rock"),
    "head":       (64, 0, 8, 8, 8, "head"),
    "brow":       (22, 86, 10, 3, 3, "rock"),
    "jaw":        (48, 86, 8, 3, 5, "jaw"),
    "crystal1":   (104, 44, 3, 7, 3, "crystal"),
    "crystal2":   (104, 54, 3, 7, 3, "crystal"),
    "crystal3":   (116, 44, 3, 7, 3, "crystal"),
    "r_shoulder": (48, 26, 10, 8, 10, "rock"),
    "r_arm":      (0, 44, 8, 14, 8, "rock"),
    "r_fist":     (64, 44, 10, 10, 10, "rock"),
    "l_shoulder": (88, 26, 10, 8, 10, "rock"),
    "l_arm":      (32, 44, 8, 14, 8, "rock"),
    "l_fist":     (0, 66, 10, 10, 10, "rock"),
    "r_leg":      (40, 66, 8, 11, 8, "rock"),
    "l_leg":      (72, 66, 8, 11, 8, "rock"),
    "handle":     (104, 66, 2, 8, 2, "handle"),
    "guard":      (0, 86, 8, 2, 3, "guard"),
    "blade":      (120, 64, 3, 30, 1, "blade"),
    "tip":        (104, 76, 1, 4, 1, "blade"),
}

ROCK = [(28, 24, 34), (36, 31, 44), (44, 38, 53), (52, 45, 62), (61, 53, 72)]
CRACK = (205, 95, 255, 255)
CRACK_CORE = (245, 190, 255, 255)
DORMANT = (60, 36, 76, 255)
ENRAGED = (255, 110, 235, 255)


def write_png(path, width, height, rows):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in rows)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0]))
           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"Written: {path}")


def faces(u, v, w, h, d):
    """(name, x, y, width, height) of each face in Java box-UV layout."""
    return [
        ("top", u + d, v, w, d),
        ("bottom", u + d + w, v, w, d),
        ("right", u, v + d, d, h),
        ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h),
        ("back", u + 2 * d + w, v + d, w, h),
    ]


rng = random.Random(358)
base = [[T for _ in range(W)] for _ in range(H)]
glow = [[T for _ in range(W)] for _ in range(H)]
enraged = [[T for _ in range(W)] for _ in range(H)]


def put(grid, x, y, color):
    if 0 <= x < W and 0 <= y < H:
        grid[y][x] = color


def crack(fx, fy, fw, fh, active):
    """A jagged crack wandering across one face. Active cracks glow always; dormant ones only when enraged."""
    x = fx + rng.randrange(fw)
    y = fy + rng.randrange(fh)
    dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
    for _ in range(rng.randint(4, max(5, (fw + fh) // 2))):
        if not (fx <= x < fx + fw and fy <= y < fy + fh):
            break
        if active:
            put(base, x, y, CRACK)
            put(glow, x, y, CRACK)
            put(enraged, x, y, CRACK_CORE)
        else:
            put(base, x, y, DORMANT)
            put(enraged, x, y, ENRAGED)
        if rng.random() < 0.35:
            dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        x += dx
        y += dy


# Hand-drawn face (head front, 8x8) and jaw front (8x3). Key:
#   B brow shadow  b dark  r rock (left as painted)  D mouth darkness
#   E eye (brightest)  e eye (dimmer)  c glowing crack  T glowing tooth
FACE = [
    "BBBBBBBB",
    "BEEBBEEB",
    "bEeBBeEb",
    "rbbrrbbr",
    "rrrccrrr",
    "rcrrrrcr",
    "rrDDDDrr",
    "rDDDDDDr",
]
JAW = [
    "TDTDDTDT",
    "rrrrrrrr",
    "brrrrrrb",
]


def draw_face_pixel(x, y, ch):
    if ch == 'r':
        return
    if ch == 'B':
        put(base, x, y, (14, 10, 18, 255))
    elif ch == 'b':
        put(base, x, y, (24, 18, 30, 255))
    elif ch == 'D':
        put(base, x, y, (10, 4, 14, 255))
        put(enraged, x, y, (120, 40, 150, 255))  # a furnace glow in its mouth once enraged
    elif ch in 'EeTc':
        color = {'E': CRACK_CORE, 'e': CRACK, 'T': (235, 200, 255, 255), 'c': CRACK}[ch]
        hot = {'E': (255, 255, 255, 255), 'e': CRACK_CORE, 'T': (255, 255, 255, 255), 'c': ENRAGED}[ch]
        put(base, x, y, color)
        put(glow, x, y, color)
        put(enraged, x, y, hot)


for name, (u, v, w, h, d, kind) in CUBES.items():
    for face, fx, fy, fw, fh in faces(u, v, w, h, d):
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                if kind in ("rock", "head", "jaw"):
                    # Mottled stone: random speckle plus a darker rim on each face.
                    shade = rng.choice([0, 1, 1, 2, 2, 2, 3, 3, 4])
                    if x in (fx, fx + fw - 1) or y in (fy, fy + fh - 1):
                        shade = max(0, shade - 2)
                    put(base, x, y, ROCK[shade] + (255,))
                elif kind == "crystal":
                    # Bright faceted crystal, lighter down one side.
                    c = (180, 70, 240, 255) if (x - fx) < fw / 2 else (225, 140, 255, 255)
                    if y == fy:
                        c = (250, 215, 255, 255)
                    put(base, x, y, c)
                    put(glow, x, y, c)
                    put(enraged, x, y, c)
                elif kind == "handle":
                    put(base, x, y, (40, 22, 30, 255) if (y - fy) % 3 else (70, 45, 30, 255))
                elif kind == "guard":
                    put(base, x, y, (60, 30, 80, 255))
                elif kind == "blade":
                    edge = x in (fx, fx + fw - 1)
                    put(base, x, y, (150, 60, 220, 255) if edge else (24, 8, 38, 255))
                    if edge:
                        put(glow, x, y, (170, 80, 240, 255))
                        put(enraged, x, y, (255, 130, 250, 255))
        if kind == "rock":
            for _ in range((fw * fh) // 110 + (1 if fw * fh >= 40 else 0)):
                crack(fx, fy, fw, fh, active=True)
            for _ in range((fw * fh) // 70 + (1 if fw * fh >= 30 else 0)):
                crack(fx, fy, fw, fh, active=False)

    if kind == "head":
        # The face: a deep shadow under the brow, two big glowing eyes, a glowing crack down the
        # middle, and a dark mouth above the jaw. Symmetric, so it reads the same either way round.
        _, fx, fy, fw, fh = faces(u, v, w, h, d)[3]
        for row, line in enumerate(FACE):
            for col, ch in enumerate(line):
                draw_face_pixel(fx + col, fy + row, ch)
    if kind == "jaw":
        # A row of glowing teeth along the top of the jaw's front.
        _, fx, fy, fw, fh = faces(u, v, w, h, d)[3]
        for row, line in enumerate(JAW):
            for col, ch in enumerate(line):
                draw_face_pixel(fx + col, fy + row, ch)
    if name == "chest":
        # The glowing Infinitite core in the middle of its chest.
        _, fx, fy, fw, fh = faces(u, v, w, h, d)[3]
        cx, cy = fx + fw // 2, fy + fh // 2
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                dist = abs(x - cx + 0.5) + abs(y - cy + 0.5)
                if dist <= 3.5:
                    c = CRACK_CORE if dist <= 1.5 else CRACK
                    put(base, x, y, c)
                    put(glow, x, y, c)
                    put(enraged, x, y, (255, 255, 255, 255) if dist <= 1.5 else ENRAGED)

write_png(os.path.join(ROOT, "entity", "infinitite_monstrosity.png"), W, H, base)
write_png(os.path.join(ROOT, "entity", "infinitite_monstrosity_glow.png"), W, H, glow)
write_png(os.path.join(ROOT, "entity", "infinitite_monstrosity_glow_enraged.png"), W, H, enraged)


# --- Monstrosity Heart: a dark rock heart with a pulsing purple glow (8 frames, 16x16) ---
HEART = [
    "................",
    "................",
    "...####..####...",
    "..######.#####..",
    ".##############.",
    ".##############.",
    ".##############.",
    "..############..",
    "...##########...",
    "....########....",
    ".....######.....",
    "......####......",
    ".......##.......",
    "................",
    "................",
    "................",
]
FRAMES = 8
rows = []
heart_rng = random.Random(7)
speckle = [[heart_rng.choice([0, 1, 1, 2, 2, 3]) for _ in range(16)] for _ in range(16)]
for frame in range(FRAMES):
    beat = 0.5 + 0.5 * math.sin(2.0 * math.pi * frame / FRAMES)
    for y in range(16):
        row = []
        for x in range(16):
            if HEART[y][x] != '#':
                row.append(T)
                continue
            dist = math.sqrt((x - 7.5) ** 2 + (y - 6.5) ** 2)
            glow_amount = max(0.0, 1.0 - dist / (3.0 + beat * 2.5))
            r, g, b = ROCK[speckle[y][x] + 1]
            row.append((int(r + (230 - r) * glow_amount), int(g + (110 - g) * glow_amount),
                        int(b + (255 - b) * glow_amount), 255))
        rows.append(row)
heart_path = os.path.join(ROOT, "item", "monstrosity_heart.png")
write_png(heart_path, 16, 16 * FRAMES, rows)
with open(heart_path + ".mcmeta", "w") as f:
    json.dump({"animation": {"interpolate": True, "frametime": 3}}, f, indent=2)
    f.write("\n")
print(f"Written: {heart_path}.mcmeta")


# --- Spawn egg: a dark rock egg speckled with glowing purple (16x16) ---
egg_rng = random.Random(12)
egg = [[T for _ in range(16)] for _ in range(16)]
for y in range(16):
    for x in range(16):
        # An egg: narrower at the top.
        half_width = 5.5 * math.sqrt(max(0.0, 1.0 - ((y - 8.5) / 7.0) ** 2)) * (0.75 + 0.25 * (y / 15.0))
        if abs(x - 7.5) > half_width or not (1 <= y <= 15):
            continue
        edge = abs(x - 7.5) > half_width - 1.0 or y in (1, 15)
        egg[y][x] = (12, 8, 16, 255) if edge else ROCK[egg_rng.choice([1, 2, 2, 3, 4])] + (255,)
for (sx, sy) in ((6, 4), (9, 7), (5, 9), (10, 11), (7, 12), (8, 5)):
    if egg[sy][sx] != T:
        egg[sy][sx] = CRACK
for (sx, sy) in ((6, 4), (9, 7)):
    egg[sy][sx + 1] = CRACK_CORE
write_png(os.path.join(ROOT, "item", "infinitite_monstrosity_spawn_egg.png"), 16, 16, egg)
print("Done!")
