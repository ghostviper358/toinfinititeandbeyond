"""
Generate the Infinitite Monstrosity's custom boss bar sprites (see MonstrosityBossBar.java).
Uses only stdlib (struct + zlib) - no Pillow required, same approach as the other
gen_*_texture.py scripts in this repo. All go in textures/gui/sprites/boss_bar/monstrosity/:

  - portrait.png / portrait_enraged.png - 32x32 pixel art of the golem: its small head sunk
    between huge boulder shoulders, glowing eyes, chest core, back crystals, purple cracks.
    Built from shapes (see portrait()), not hand-placed pixels.
  - portrait_glow.png / portrait_glow_enraged.png - just the glowing pixels, drawn on top with a
    pulsing brightness.
  - frame.png - 204x13 rock frame around the bar, with spiked ends; see-through where the
    182x5 bar shows.
  - fill.png / fill_enraged.png - 182x5 bar fill: cracked purple (phase 1) / blazing pink (phase 2).
  - empty.png - 182x5 the drained part of the bar.
"""
import zlib, struct, os, random

OUT = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "toinfinititeandbeyond",
                   "textures", "gui", "sprites", "boss_bar", "monstrosity")
T = (0, 0, 0, 0)
ROCK = [(22, 18, 28, 255), (34, 29, 42, 255), (45, 39, 55, 255), (57, 50, 68, 255), (70, 62, 82, 255)]
OUTLINE = (10, 6, 14, 255)
CRACK = (200, 90, 255, 255)
CRACK_HOT = (255, 120, 235, 255)
CORE = (250, 200, 255, 255)
CRYSTAL = (190, 80, 245, 255)
CRYSTAL_LIGHT = (235, 160, 255, 255)


def write_png(name, rows):
    height, width = len(rows), len(rows[0])
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in rows)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0]))
           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"Written: {path}")


def grid(w, h):
    return [[T for _ in range(w)] for _ in range(h)]


# --- Portrait: built from shapes, drawn back to front so each part reads clearly:
#     crystals (behind) -> chest -> boulder shoulders -> the small head sunk between them. ---
rng = random.Random(358)


def portrait(enraged):
    base, glow = grid(32, 32), grid(32, 32)
    owner = [[None] * 32 for _ in range(32)]  # which part each pixel belongs to, for outlines
    crack = CRACK_HOT if enraged else CRACK

    def paint(x, y, color, part, glows=False):
        if 0 <= x < 32 and 0 <= y < 32:
            base[y][x] = color
            owner[y][x] = part
            glow[y][x] = color if glows else T

    # Crystals jutting up and outward from behind the shoulders, plus one behind the head.
    for (x0, y0, dx) in ((7, 9, -1), (24, 9, 1), (16, 7, 0)):
        for i in range(8 if dx else 6):
            cx, cy = x0 + (dx * i) // 2, y0 - i
            for w in range(-1, 2 if i < 5 else 1):
                paint(cx + w, cy, CRYSTAL_LIGHT if w == -1 else CRYSTAL, "crystal", glows=True)

    # Chest: a broad block, slightly narrower at the bottom.
    for y in range(15, 32):
        inset = max(0, (y - 26) // 2)
        for x in range(3 + inset, 29 - inset):
            paint(x, y, ROCK[rng.choice([1, 2, 2, 3])], "chest")

    # Two huge boulder shoulders.
    for (cx, cy) in ((6.5, 13.5), (25.5, 13.5)):
        for y in range(32):
            for x in range(32):
                d = ((x - cx) / 7.2) ** 2 + ((y - cy) / 6.4) ** 2
                if d <= 1.0:
                    shade = 4 if d < 0.25 and (x - cx) < 0 and (y - cy) < 0 else rng.choice([2, 3, 3])
                    paint(x, y, ROCK[shade], "shoulder_l" if cx < 16 else "shoulder_r")

    # The head: small, low, sunk between the shoulders, lighter than the body, with a heavy brow.
    for y in range(10, 19):
        for x in range(12, 20):
            if (x in (12, 19)) and y in (10, 18):
                continue  # rounded corners
            paint(x, y, ROCK[rng.choice([3, 3, 4])], "head")
    for x in range(11, 21):
        paint(x, 12, ROCK[0], "head")  # brow
    for ex in (13, 14, 17, 18):
        paint(ex, 13, (255, 255, 255, 255) if enraged else CORE, "head", glows=True)
    for x in (14, 15, 16, 17):
        paint(x, 16, ROCK[0], "head")  # grim mouth

    # The glowing core in its chest, with cracks running out from it.
    for y in range(21, 28):
        for x in range(12, 21):
            dist = abs(x - 16) + abs(y - 24)
            if dist <= 3:
                paint(x, y, CORE if dist <= 1 else crack, "chest", glows=True)
    for (x, y, dx, dy, n) in ((13, 24, -1, 1, 7), (19, 24, 1, 1, 7), (16, 27, 0, 1, 4),
                              (4, 11, 1, 1, 5), (27, 11, -1, 1, 5), (8, 17, 1, 1, 4), (23, 17, -1, 1, 4)):
        for _ in range(n):
            paint(x, y, crack, owner[y][x] if 0 <= y < 32 and 0 <= x < 32 and owner[y][x] else "chest", glows=True)
            x += dx if rng.random() < 0.7 else 0
            y += dy

    # Dark outlines wherever two different parts meet, or a part meets empty space.
    out = [row[:] for row in base]
    for y in range(32):
        for x in range(32):
            here = owner[y][x]
            if here is None:
                if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and owner[y + dy][x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = OUTLINE
            elif here in ("head", "shoulder_l", "shoulder_r") and glow[y][x] == T:
                if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and owner[y + dy][x + dx] not in (here, None)
                       and owner[y + dy][x + dx] != "crystal" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out[y][x] = OUTLINE
    return out, glow


for enraged in (False, True):
    p, g = portrait(enraged)
    suffix = "_enraged" if enraged else ""
    write_png(f"portrait{suffix}.png", p)
    write_png(f"portrait_glow{suffix}.png", g)


# --- Frame: rock border around the 182x5 bar (bar area at x=11..192, y=4..8), spiked ends ---
FW, FH = 204, 13
BAR_X, BAR_Y, BAR_W, BAR_H = 11, 4, 182, 5
frame = grid(FW, FH)
for y in range(FH):
    for x in range(FW):
        in_bar = BAR_X <= x < BAR_X + BAR_W and BAR_Y <= y < BAR_Y + BAR_H
        if in_bar:
            continue
        # Spikes: the ends taper to a point.
        end_dist = min(x, FW - 1 - x)
        if end_dist < 8 and abs(y - FH // 2) > end_dist * 0.8:
            continue
        rim = y in (BAR_Y - 1, BAR_Y + BAR_H) and BAR_X - 1 <= x <= BAR_X + BAR_W
        rim = rim or (x in (BAR_X - 1, BAR_X + BAR_W) and BAR_Y - 1 <= y <= BAR_Y + BAR_H)
        if rim:
            frame[y][x] = OUTLINE
        else:
            frame[y][x] = ROCK[rng.choice([2, 3, 3, 4])]
# Glowing gems at each spike tip.
for gx in (1, 2, FW - 3, FW - 2):
    frame[FH // 2][gx] = CORE
write_png("frame.png", frame)


# --- Bar fills ---
def fill(colors, crack_color):
    g = grid(BAR_W, BAR_H)
    for y in range(BAR_H):
        for x in range(BAR_W):
            g[y][x] = colors[min(len(colors) - 1, y)]
    for _ in range(26):
        x, y = rng.randrange(BAR_W), rng.randrange(BAR_H)
        for _ in range(rng.randint(2, 5)):
            if 0 <= x < BAR_W and 0 <= y < BAR_H:
                g[y][x] = crack_color
            x += 1
            y += rng.choice([-1, 0, 1])
    return g


write_png("fill.png", fill([(215, 120, 255, 255), (170, 70, 230, 255), (140, 45, 205, 255),
                            (115, 30, 180, 255), (90, 20, 150, 255)], (240, 200, 255, 255)))
write_png("fill_enraged.png", fill([(255, 170, 245, 255), (255, 100, 225, 255), (235, 60, 200, 255),
                                    (200, 40, 175, 255), (165, 25, 150, 255)], (255, 240, 255, 255)))
empty = grid(BAR_W, BAR_H)
for y in range(BAR_H):
    for x in range(BAR_W):
        empty[y][x] = (28, 16, 36, 255) if y else (40, 26, 50, 255)
write_png("empty.png", empty)
print("Done!")
