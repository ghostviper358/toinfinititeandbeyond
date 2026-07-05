"""
Generate the Universal Shard item texture as a 16x16 PNG.
Uses only stdlib (struct + zlib) - no Pillow required (same approach as
gen_void_textures.py).

Look: a jagged crystal shard evoking the End Gateway portal block, but
recolored pink/purple ("purpal") instead of vanilla teal/black, with a
bright glowing core to sell the "glows" requirement. A true animated
swirl (like the real Gateway block) isn't practical for a static item
icon, so the swirl is suggested with concentric spiral-ish color bands.
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

# Pink/purple "purpal" gateway-swirl palette - darkest edge to brightest core
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

CX, CY = 7.5, 7.5


def shard_mask(x, y):
    """Jagged vertical crystal shard silhouette."""
    dx = x - CX
    dy = y - CY
    # Elongated diamond (rhombus) body
    r = abs(dx) / 4.6 + abs(dy) / 7.6
    inside = r <= 1.0
    # Jagged facet notches: shave a few edge pixels asymmetrically so it
    # reads as an irregular crystal shard rather than a perfect diamond.
    notches = {
        (2, 3), (13, 3), (3, 2), (12, 2),
        (2, 12), (13, 12), (3, 13), (12, 13),
        (1, 7), (14, 8),
    }
    if (x, y) in notches:
        inside = False
    return inside


def swirl_color(x, y):
    dx = x - CX
    dy = y - CY
    r = math.sqrt((dx / 6.0) ** 2 + (dy / 8.0) ** 2)
    theta = math.atan2(dy, dx)
    # Spiral banding: angle rotates faster near the center, mimicking the
    # End Gateway's shifting spiral rings, "frozen" at one animation frame.
    swirl = math.sin(theta * 2.5 + (1.0 - min(r, 1.0)) * 9.0)
    # Base brightness rises toward the core (glow), swirl adds banding.
    core = max(0.0, 1.0 - r)  # 1 at center, 0 at r>=1
    level = core * 6.5 + (swirl * 0.9)
    idx = int(round(level))
    idx = max(0, min(len(PALETTE) - 1, idx))
    return PALETTE[idx]


grid = []
for y in range(16):
    row = []
    for x in range(16):
        if shard_mask(x, y):
            row.append(swirl_color(x, y))
        else:
            row.append(T)
    grid.append(row)

# Punch in a few bright "sparkle" highlight pixels near the core for glow readability
sparkle = (255, 235, 255, 255)
for (sx, sy) in [(7, 6), (8, 9), (6, 9)]:
    grid[sy][sx] = sparkle

out_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "universal_shard.png"
)
write_png(out_path, grid)
print("Done!")