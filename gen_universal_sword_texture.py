"""
Generate the Universal Sword item texture as an animated 16x128 PNG frame-strip
(8 frames of 16x16, stacked top-to-bottom) plus its .png.mcmeta.
Uses only stdlib (struct + zlib) - no Pillow required (same approach as
gen_void_textures.py / gen_universal_shard_texture.py / gen_cosmic_apple_texture.py).

Look: the same handheld-sword silhouette as Void Sword/Infinitite Sword (diagonal
blade, guard, handle - see gen_void_textures.py's void_sword_grid), but recolored
with the Universal Shard/Cosmic Apple's pink-purple "purpal" swirl palette, and
genuinely *animated* - the swirl rotates frame to frame (like a frozen-then-thawed
End Gateway) and a bright pulse of energy travels from the handle up to the tip,
looping every 8 frames. See docs/requirements/universal_shard_and_cosmic_apple.md,
"Universal Sword (Item)" -> "Visual Appearance".
"""
import zlib, struct, os, math

FRAMES = 8
FRAMETIME = 4  # ticks per frame - matches universal_chestplate.png.mcmeta


def write_png(path, width, height, pixel_rows):
    """Write a width x height RGBA pixel grid (list of rows, each a list of
    (r,g,b,a) tuples) as a PNG file."""

    def make_chunk(tag, data):
        c = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', c)

    ihdr_data = struct.pack('>II', width, height) + bytes([8, 6, 0, 0, 0])

    raw_rows = b''
    for row in pixel_rows:
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

# Same pink/purple "purpal" gateway-swirl palette as the Universal Shard/Cosmic
# Apple, so the sword visually reads as forged from the same material.
PALETTE = [
    (30, 4, 55, 255),      # 0 deep void-purple edge
    (60, 8, 95, 255),      # 1 dark purple
    (95, 15, 140, 255),    # 2 purple
    (140, 25, 170, 255),   # 3 purple-magenta
    (185, 50, 200, 255),   # 4 magenta-pink
    (220, 90, 225, 255),   # 5 bright pink
    (245, 150, 245, 255),  # 6 hot pink glow
    (255, 210, 255, 255),  # 7 near-white glowing core
]
SPARKLE = (255, 235, 255, 255)

# Handheld sword silhouette - identical shape to void_sword_grid/infinitite_sword
# (diagonal blade from bottom-left handle to top-right tip, with a guard around
# the middle) so the Universal Sword reads unmistakably as a sword in-hand.
MASK_ROWS = {
    0: [13],
    1: [12, 13, 14],
    2: [11, 12, 13],
    3: [10, 11, 12],
    4: [9, 10, 11],
    5: [8, 9, 10],
    6: [7, 8, 9],
    7: [5, 6, 7, 8],
    8: [4, 5, 6, 7, 8],
    9: [5, 6, 7],
    10: [5, 6],
    11: [4, 5],
    12: [3, 4],
    13: [2, 3],
    14: [1, 2],
    15: [0, 1],
}

# One "core" pixel per row, ordered from the pommel (row 15) up to the tip
# (row 0) - the path the traveling energy pulse (below) runs along.
PULSE_PATH = [(0, 15), (1, 14), (2, 13), (3, 12), (4, 11), (5, 10), (6, 9),
              (5, 8), (6, 7), (7, 6), (8, 5), (9, 4), (10, 3), (11, 2), (12, 1), (13, 0)]

CX, CY = 6.0, 8.0  # swirl origin - roughly where the guard meets the handle


def swirl_color(x, y, phase):
    dx = x - CX
    dy = y - CY
    r = math.sqrt((dx / 9.0) ** 2 + (dy / 9.0) ** 2)
    theta = math.atan2(dy, dx)
    # Rotating spiral banding - phase advances each frame so the swirl visibly
    # turns, the same way the real End Gateway's portal rings shift over time.
    swirl = math.sin(theta * 2.5 - r * 6.0 + phase)
    core = max(0.0, 1.0 - r)
    level = 2.0 + core * 3.0 + swirl * 2.5
    idx = int(round(level))
    idx = max(0, min(len(PALETTE) - 1, idx))
    return PALETTE[idx]


def build_frame(frame_index):
    phase = 2.0 * math.pi * frame_index / FRAMES
    grid = [[T for _ in range(16)] for _ in range(16)]
    for y, xs in MASK_ROWS.items():
        for x in xs:
            grid[y][x] = swirl_color(x, y, phase)

    # Traveling energy pulse: one bright highlight that marches up the blade
    # from handle to tip over the 8-frame loop, reads as the "glow" pulsing
    # through the weapon rather than just a static shimmer.
    pulse_x, pulse_y = PULSE_PATH[frame_index * len(PULSE_PATH) // FRAMES]
    grid[pulse_y][pulse_x] = SPARKLE

    return grid


all_frames_pixels = []
for frame_index in range(FRAMES):
    all_frames_pixels.extend(build_frame(frame_index))

out_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "universal_sword.png"
)
write_png(out_path, 16, 16 * FRAMES, all_frames_pixels)

mcmeta_path = out_path + ".mcmeta"
with open(mcmeta_path, 'w') as f:
    f.write('{\n  "animation": {\n    "interpolate": true,\n    "frametime": %d\n  }\n}\n' % FRAMETIME)
print(f"Written: {mcmeta_path}")

print("Done!")
