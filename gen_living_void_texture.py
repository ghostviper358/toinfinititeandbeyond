"""
Generate the Living Void's textures. Uses only stdlib (struct + zlib) - no Pillow required, same
approach as the other gen_*_texture.py scripts in this repo.

The cube list below must match LivingVoidModel.createBodyLayer(). Outputs:
  - entity/living_void.png       - 64x64: an eye of darkness speckled with faint stars, a glowing
                                   purple iris with a black slit pupil, black shards with glowing
                                   edges, and dark tentacles with glowing suckers.
  - entity/living_void_glow.png  - only the glowing parts (iris, veins of light, shard edges,
                                   suckers), drawn at full brightness.
  - item/living_void_spawn_egg.png - an inky egg with a glowing purple eye.
  - gui/sprites/boss_bar/living_void/ - its boss bar: a 32x32 pixel-art portrait of the eye and
    its tentacles (+ a glow-only version), a starry void frame, the fill and the drained bar.
"""
import zlib, struct, os, math, random

T = (0, 0, 0, 0)
ROOT = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "toinfinititeandbeyond", "textures")

VOID = [(6, 3, 10), (10, 5, 16), (14, 8, 22), (20, 11, 30), (26, 14, 38)]
STAR = (210, 190, 255, 255)
VEIN = (150, 60, 220, 255)
IRIS_OUTER = (120, 40, 200, 255)
IRIS_MID = (185, 80, 245, 255)
IRIS_INNER = (240, 170, 255, 255)
PUPIL = (2, 0, 4, 255)
SUCKER = (200, 110, 255, 255)
EDGE = (170, 80, 240, 255)

rng = random.Random(1337)


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


# ============================ The entity texture (64x64) ============================
W = H = 64
base, glow = grid(W, H), grid(W, H)


def put(x, y, color, glows=False):
    if 0 <= x < W and 0 <= y < H:
        base[y][x] = color
        if glows:
            glow[y][x] = color


def void_pixel():
    """Darkness, very occasionally a faint star."""
    if rng.random() < 0.04:
        return STAR
    return VOID[rng.choice([0, 1, 1, 2, 2, 3])] + (255,)


# The eye: darkness with veins of light creeping across every face.
for _, fx, fy, fw, fh in faces(0, 0, 12, 12, 12):
    for y in range(fy, fy + fh):
        for x in range(fx, fx + fw):
            put(x, y, void_pixel())
    for _ in range(2):
        x, y = fx + rng.randrange(fw), fy + rng.randrange(fh)
        for _ in range(rng.randint(5, 10)):
            if fx <= x < fx + fw and fy <= y < fy + fh:
                put(x, y, VEIN, glows=True)
            x += rng.choice([-1, 0, 1])
            y += rng.choice([-1, 0, 1])

# The iris (front face 8x8 at (1, 25)): rings of purple light around a black slit pupil.
for _, fx, fy, fw, fh in faces(0, 24, 8, 8, 1):
    for y in range(fy, fy + fh):
        for x in range(fx, fx + fw):
            put(x, y, VOID[2] + (255,))
_, ix, iy, iw, ih = faces(0, 24, 8, 8, 1)[3]
for y in range(ih):
    for x in range(iw):
        r = math.hypot(x - 3.5, y - 3.5)
        if r > 4.2:
            continue
        if abs(x - 3.5) < 1.0 and 0 < y < 7:
            put(ix + x, iy + y, PUPIL)  # the slit pupil stays black - it doesn't glow
        elif r < 2.2:
            put(ix + x, iy + y, IRIS_INNER, glows=True)
        elif r < 3.3:
            put(ix + x, iy + y, IRIS_MID, glows=True)
        else:
            put(ix + x, iy + y, IRIS_OUTER, glows=True)
put(ix + 2, iy + 2, (255, 255, 255, 255), glows=True)  # a glint

# Shards: black obsidian with glowing edges.
for (u, v, s) in ((48, 0, 2), (48, 4, 3)):
    for _, fx, fy, fw, fh in faces(u, v, s, s, s):
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                edge = x in (fx, fx + fw - 1) or y in (fy, fy + fh - 1)
                if edge and s > 2:
                    put(x, y, EDGE, glows=True)
                else:
                    put(x, y, (12, 6, 18, 255) if (x + y) % 2 else (22, 12, 32, 255))

# Tentacles: darkness, with a row of glowing suckers down the inner face.
for (u, v, w, h) in ((18, 24, 4, 6), (34, 24, 3, 6), (46, 24, 2, 6), (54, 24, 1, 5)):
    for face, fx, fy, fw, fh in faces(u, v, w, h, w):
        for y in range(fy, fy + fh):
            for x in range(fx, fx + fw):
                put(x, y, VOID[rng.choice([1, 2, 2, 3])] + (255,))
        if face == "right":
            for y in range(fy + 1, fy + fh, 2):
                put(fx + fw // 2, y, SUCKER, glows=True)
    if w == 1:
        # The very tips glow.
        for _, fx, fy, fw, fh in faces(u, v, w, h, w):
            put(fx, fy + fh - 1, IRIS_INNER, glows=True)

write_png(os.path.join(ROOT, "entity", "living_void.png"), base)
write_png(os.path.join(ROOT, "entity", "living_void_glow.png"), glow)


# ============================ Spawn egg (16x16) ============================
egg = grid(16, 16)
for y in range(16):
    for x in range(16):
        half_width = 5.5 * math.sqrt(max(0.0, 1.0 - ((y - 8.5) / 7.0) ** 2)) * (0.75 + 0.25 * (y / 15.0))
        if abs(x - 7.5) > half_width or not (1 <= y <= 15):
            continue
        edge = abs(x - 7.5) > half_width - 1.0 or y in (1, 15)
        egg[y][x] = (2, 0, 4, 255) if edge else (STAR if rng.random() < 0.06 else VOID[rng.choice([1, 2, 3])] + (255,))
for y in range(6, 11):
    for x in range(5, 11):
        r = math.hypot(x - 7.5, y - 8.0)
        if r <= 2.6:
            egg[y][x] = PUPIL if abs(x - 7.5) < 1.0 else (IRIS_INNER if r < 1.8 else IRIS_MID)
write_png(os.path.join(ROOT, "item", "living_void_spawn_egg.png"), egg)


# ============================ Boss bar sprites ============================
BAR = os.path.join(ROOT, "gui", "sprites", "boss_bar", "living_void")

# Portrait: the eye with its shard ring, tentacles curling down out of the dark.
portrait, pglow = grid(32, 32), grid(32, 32)


def pput(x, y, color, glows=False):
    if 0 <= x < 32 and 0 <= y < 32:
        portrait[y][x] = color
        if glows:
            pglow[y][x] = color


# Tentacles first (behind the eye): six wavy lines curling down and out.
for i in range(6):
    start_x = 9 + i * 2.8
    lean = (i - 2.5) * 0.9
    for step in range(18):
        y = 18 + step
        x = start_x + lean * step / 6.0 + math.sin(step * 0.5 + i) * 1.3
        thickness = 2 if step < 9 else 1
        for dx in range(thickness):
            pput(int(x) + dx, y, VOID[3] + (255,))
        if step % 3 == 1:
            pput(int(x), y, SUCKER, glows=True)
# The eye: a dark sphere, lit faintly from the top left.
for y in range(32):
    for x in range(32):
        r = math.hypot(x - 15.5, y - 12.5)
        if r <= 10.5:
            shade = 4 if (x - 15.5) + (y - 12.5) < -9 else (3 if r < 5 else rng.choice([1, 2, 2]))
            pput(x, y, STAR if rng.random() < 0.03 else VOID[shade] + (255,))
# The iris and slit pupil.
for y in range(32):
    for x in range(32):
        r = math.hypot(x - 15.5, y - 13.0)
        if r > 5.2:
            continue
        if abs(x - 15.5) < 1.0 and abs(y - 13.0) < 4.5:
            pput(x, y, PUPIL)
        elif r < 2.6:
            pput(x, y, IRIS_INNER, glows=True)
        elif r < 4.0:
            pput(x, y, IRIS_MID, glows=True)
        else:
            pput(x, y, IRIS_OUTER, glows=True)
pput(13, 10, (255, 255, 255, 255), glows=True)
# Orbiting shards on a tilted ring.
for i in range(7):
    angle = i * (math.pi * 2.0 / 7.0)
    x = int(round(15.5 + math.cos(angle) * 14.0))
    y = int(round(12.5 + math.sin(angle) * 5.0))
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        pput(x + dx, y + dy, EDGE if (dx, dy) == (0, 0) else (14, 6, 22, 255), glows=(dx, dy) == (0, 0))
# Dark outline round the silhouette.
outlined = [row[:] for row in portrait]
for y in range(32):
    for x in range(32):
        if portrait[y][x] == T and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and portrait[y + dy][x + dx] != T
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            outlined[y][x] = (0, 0, 0, 255)
write_png(os.path.join(BAR, "portrait.png"), outlined)
write_png(os.path.join(BAR, "portrait_glow.png"), pglow)

# Frame: inky black stone flecked with stars, around the 182x5 bar, spiked ends.
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
        frame[y][x] = (0, 0, 0, 255) if rim else (STAR if rng.random() < 0.05 else VOID[rng.choice([1, 2, 3, 4])] + (255,))
for gx in (1, 2, FW - 3, FW - 2):
    frame[FH // 2][gx] = IRIS_INNER
write_png(os.path.join(BAR, "frame.png"), frame)

# Fill: deep violet fading to black, with stars drifting in it.
fill = grid(BAR_W, BAR_H)
fill_colors = [(170, 90, 240), (130, 55, 205), (100, 35, 170), (70, 20, 130), (45, 10, 90)]
for y in range(BAR_H):
    for x in range(BAR_W):
        fill[y][x] = (255, 240, 255, 255) if rng.random() < 0.04 else fill_colors[y] + (255,)
write_png(os.path.join(BAR, "fill.png"), fill)

empty = grid(BAR_W, BAR_H)
for y in range(BAR_H):
    for x in range(BAR_W):
        empty[y][x] = (8, 4, 14, 255) if y else (16, 9, 26, 255)
write_png(os.path.join(BAR, "empty.png"), empty)
print("Done!")
