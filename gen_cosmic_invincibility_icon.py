"""
Generate the Cosmic Invincibility status-effect icon as an 18x18 PNG
(vanilla mob-effect icons are 18x18). Uses only stdlib (struct + zlib).

Look: a small glowing purple shield/star glyph, keeping with the Cosmic
Apple / Universal Shard pink-purple "purpal" theme.
"""
import zlib, struct, os, math


def write_png(path, size, pixels):
    width = height = size

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


SIZE = 18
T = (0, 0, 0, 0)

PALETTE = [
    (30, 4, 55, 255),
    (60, 8, 95, 255),
    (95, 15, 140, 255),
    (140, 25, 170, 255),
    (185, 50, 200, 255),
    (220, 90, 225, 255),
    (245, 150, 245, 255),
    (255, 210, 255, 255),
]

CX = CY = (SIZE - 1) / 2.0


def shield_mask(x, y):
    """Rounded shield silhouette: wide at top, tapering to a point at the bottom."""
    dx = (x - CX) / (SIZE * 0.34)
    ny = (y - 1) / (SIZE - 3)  # 0 at top, 1 near bottom
    if ny < 0 or ny > 1:
        return False
    # Shield width shrinks as we go down, coming to a point at the very bottom.
    width_at_y = 1.0 - (ny ** 1.6) * 0.95
    return abs(dx) <= width_at_y


def color_for(x, y):
    dx = x - CX
    dy = y - CY
    r = math.sqrt((dx / (SIZE * 0.32)) ** 2 + (dy / (SIZE * 0.42)) ** 2)
    core = max(0.0, 1.0 - r)
    theta = math.atan2(dy, dx)
    swirl = math.sin(theta * 2.0 + (1.0 - min(r, 1.0)) * 7.0)
    level = core * 6.0 + swirl * 0.8
    idx = int(round(level))
    idx = max(0, min(len(PALETTE) - 1, idx))
    return PALETTE[idx]


grid = [[T for _ in range(SIZE)] for _ in range(SIZE)]
for y in range(SIZE):
    for x in range(SIZE):
        if shield_mask(x, y):
            grid[y][x] = color_for(x, y)

# Bright core sparkle for glow readability.
cx_i, cy_i = int(round(CX)), int(round(CY)) - 1
for (ox, oy) in [(0, 0), (-1, 1), (1, -1)]:
    xi, yi = cx_i + ox, cy_i + oy
    if 0 <= xi < SIZE and 0 <= yi < SIZE:
        grid[yi][xi] = (255, 235, 255, 255)

out_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "mob_effect", "cosmic_invincibility.png"
)
write_png(out_path, SIZE, grid)
print("Done!")