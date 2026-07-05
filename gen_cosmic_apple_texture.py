"""
Generate the Cosmic Apple item texture as a 16x16 PNG.
Uses only stdlib (struct + zlib) - no Pillow required (same approach as
gen_void_textures.py / gen_universal_shard_texture.py).

Look: a normal apple/golden-apple SHAPE (round body, short stem, little
leaf) but the body is filled with the same pink/purple "purpal" swirling
portal look as the Universal Shard it's crafted from, instead of a normal
red/gold apple texture.
"""
import zlib, struct, os, math


def write_png(path, pixels):
    """Write a 16x16 RGBA pixel grid as a PNG file."""
    width = height = 16

    def make_chunk(tag, data):
        c = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', c)

    ihdr_data = struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0])

    raw_rows = b''
    for row in pixels:
        raw_rows += b'\x00'
        for (r, g, b, a) in row:
            raw_rows += bytes([r, g, b, a])

    compressed = zlib.compress(raw_rows, 9)

    png = (
        b'\x89PNG\r\n\x1a\n'
        + make_chunk(b'IHDR', ihdr_data)
        + make_chunk(b'IDAT', compressed)
        + make_chunk(b'IEND', b'')
    )

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"Written: {path}")


T = (0, 0, 0, 0)  # transparent

# Same pink/purple "purpal" gateway-swirl palette as the Universal Shard,
# so the Cosmic Apple visually reads as "made from" it.
PALETTE = [
    (30, 4, 55, 255),     # 0 deep void-purple edge
    (60, 8, 95, 255),     # 1 dark purple
    (95, 15, 140, 255),   # 2 purple
    (140, 25, 170, 255),  # 3 purple-magenta
    (185, 50, 200, 255),  # 4 magenta-pink
    (220, 90, 225, 255),  # 5 bright pink
    (245, 150, 245, 255), # 6 hot pink glow
    (255, 210, 255, 255), # 7 near-white glowing core
]

STEM = (72, 48, 20, 255)      # brown stem
STEM_DARK = (48, 30, 12, 255)
LEAF = (60, 170, 70, 255)     # green leaf
LEAF_DARK = (35, 130, 45, 255)
SPARKLE = (255, 235, 255, 255)

CX, CY = 7.5, 9.0


def apple_mask(x, y):
    """Round apple body silhouette (like the vanilla apple/golden apple icon),
    with a small notch carved out of the top-center for the stem."""
    dx = x - CX
    dy = y - CY
    r = math.sqrt((dx / 5.6) ** 2 + (dy / 5.8) ** 2)
    inside = r <= 1.0
    # Stem notch: carve a small dip out of the top so the stem has somewhere to sit.
    if y <= 3 and abs(dx) <= 1.0:
        inside = False
    return inside


def swirl_color(x, y):
    dx = x - CX
    dy = y - CY
    r = math.sqrt((dx / 5.0) ** 2 + (dy / 5.2) ** 2)
    theta = math.atan2(dy, dx)
    swirl = math.sin(theta * 2.5 + (1.0 - min(r, 1.0)) * 9.0)
    core = max(0.0, 1.0 - r)
    level = core * 6.0 + (swirl * 0.9)
    idx = int(round(level))
    idx = max(0, min(len(PALETTE) - 1, idx))
    return PALETTE[idx]


grid = [[T for _ in range(16)] for _ in range(16)]

for y in range(16):
    for x in range(16):
        if apple_mask(x, y):
            grid[y][x] = swirl_color(x, y)

# Stem: a couple of brown pixels poking up out of the notch.
for (sx, sy, col) in [(7, 1, STEM_DARK), (7, 2, STEM), (8, 1, STEM)]:
    grid[sy][sx] = col

# Leaf: small green accent beside the stem.
for (sx, sy, col) in [(9, 1, LEAF), (9, 2, LEAF_DARK), (10, 1, LEAF)]:
    grid[sy][sx] = col

# Sparkle highlight near the core for glow readability, matching the Shard's look.
for (sx, sy) in [(6, 8), (7, 10), (5, 9)]:
    grid[sy][sx] = SPARKLE

out_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "cosmic_apple.png"
)
write_png(out_path, grid)
print("Done!")