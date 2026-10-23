"""
Generate the Universal Leggings' animated 16x(16*8) inventory icon frame-strip (8 frames of
16x16, stacked top-to-bottom) plus its .png.mcmeta - the same frame count and timing as the
Universal Chestplate's icon, so the set animates together.

Uses only stdlib (struct + zlib) - no Pillow required, same approach as the other
gen_*_texture.py scripts in this repo. The swirl is the same pink-purple cosmic palette as the
Universal Sword's 3D texture, with a dark outline so the leggings shape reads clearly, a
brighter waistband, and a slow diagonal swirl flowing down the legs.
"""
import zlib, struct, os, math, json

FRAMES = 8
FRAMETIME = 4
SIZE = 16

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
OUTLINE = (18, 2, 34, 255)
T = (0, 0, 0, 0)

# The leggings silhouette: '#' = filled. Waistband across the top, two legs below.
SHAPE = [
    "................",
    "................",
    "...##########...",
    "...##########...",
    "...##########...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "...####..####...",
    "................",
    "................",
]
WAIST_ROWS = (2, 3, 4)


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


def filled(x, y):
    return 0 <= x < SIZE and 0 <= y < SIZE and SHAPE[y][x] == '#'


def build_frame(frame_index):
    phase = 2.0 * math.pi * frame_index / FRAMES
    grid = [[T for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if not filled(x, y):
                continue
            edge = not (filled(x - 1, y) and filled(x + 1, y) and filled(x, y - 1) and filled(x, y + 1))
            if edge:
                grid[y][x] = OUTLINE
                continue
            # A diagonal swirl drifting down the legs as the frames advance.
            swirl = math.sin((x + y) * 0.9 - phase) + 0.6 * math.sin((x - y) * 0.5 + phase * 2.0)
            level = 3.0 + swirl * 1.6
            if y in WAIST_ROWS:
                level += 1.5  # brighter waistband
            grid[y][x] = PALETTE[max(0, min(7, int(round(level))))]
    return grid


all_rows = []
for frame_index in range(FRAMES):
    all_rows.extend(build_frame(frame_index))

out_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "universal_leggings.png"
)
write_png(out_path, SIZE, SIZE * FRAMES, all_rows)

meta_path = out_path + ".mcmeta"
with open(meta_path, "w") as f:
    json.dump({"animation": {"interpolate": True, "frametime": FRAMETIME}}, f, indent=2)
    f.write("\n")
print(f"Written: {meta_path}")
print("Done!")
