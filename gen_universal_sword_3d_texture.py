"""
Generate the Universal Sword's 3D GeckoLib model texture as an animated 32x(48*16) PNG
frame-strip (16 frames of 32x48, stacked top-to-bottom) plus its .png.mcmeta.
Uses only stdlib (struct + zlib) - no Pillow required, same approach as the other
gen_*_texture.py scripts in this repo.

This paints the box-UV footprint of each of the four cuboids in
universal_sword.geo.json (pommel, grip, crossguard, blade - see that file's "uv"
fields) with a themed, animated pink-purple swirl, consistent with the Universal
Shard/Cosmic Apple/flat-icon look established elsewhere in the mod:
  - Pommel:     a small glowing core (brightest, near-white center).
  - Grip:       dark purple with horizontal wrap-banding (reads as a bound handle).
  - Crossguard: mid purple-magenta metal.
  - Blade:      the main event - a swirling, animated purple/pink energy blade with
                a brighter "fuller" stripe down the center.
Each cuboid's full box-UV bounding rectangle is filled (rather than painting each of
the 6 faces separately), which is simpler and still reads well since the swirl
pattern is organic rather than a realistic photo-texture - any face-boundary seams
just read as part of the swirl.
"""
import zlib, struct, os, math, json

FRAMES = 16
FRAMETIME = 2
TEX_W = 32
TEX_H = 48


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

# Box-UV bounding rectangles for each cuboid, matching universal_sword.geo.json's
# "uv" origins plus each cube's own (w,h,d)-derived footprint: width=2*(w+d), height=d+h.
REGIONS = {
    "pommel": (0, 0, 12, 5),
    "grip": (0, 9, 8, 7),
    "guard": (0, 19, 20, 3),
    "blade": (0, 25, 6, 17),
}


def swirl_level(x, y, cx, cy, rx, ry, phase, spin=2.5, band=6.0):
    dx = (x - cx) / max(rx, 0.001)
    dy = (y - cy) / max(ry, 0.001)
    r = math.sqrt(dx * dx + dy * dy)
    theta = math.atan2(dy, dx)
    swirl = math.sin(theta * spin - r * band + phase)
    return swirl, r


def paint_pommel(grid, phase):
    u0, v0, w, h = REGIONS["pommel"]
    cx, cy = u0 + w / 2.0, v0 + h / 2.0
    for y in range(v0, v0 + h):
        for x in range(u0, u0 + w):
            swirl, r = swirl_level(x, y, cx, cy, w / 2.0, h / 2.0, phase, spin=3.0, band=5.0)
            core = max(0.0, 1.0 - r)
            level = 3.0 + core * 4.0 + swirl * 1.0
            idx = max(0, min(7, int(round(level))))
            grid[y][x] = PALETTE[idx]


def paint_grip(grid, phase):
    u0, v0, w, h = REGIONS["grip"]
    for y in range(v0, v0 + h):
        band = int((y - v0 + int(phase * 1.2)) % 3 == 0)
        base_idx = 1 if band else 0
        for x in range(u0, u0 + w):
            grid[y][x] = PALETTE[base_idx]


def paint_guard(grid, phase):
    u0, v0, w, h = REGIONS["guard"]
    cx, cy = u0 + w / 2.0, v0 + h / 2.0
    for y in range(v0, v0 + h):
        for x in range(u0, u0 + w):
            swirl, r = swirl_level(x, y, cx, cy, w / 2.0, h / 2.0, phase, spin=1.5, band=1.0)
            level = 2.5 + swirl * 1.5
            idx = max(0, min(5, int(round(level))))
            grid[y][x] = PALETTE[idx]


def paint_blade(grid, phase):
    u0, v0, w, h = REGIONS["blade"]
    center_x = u0 + w / 2.0
    for y in range(v0, v0 + h):
        for x in range(u0, u0 + w):
            dx = x - center_x
            # Traveling energy bands flowing along the blade's length (y axis).
            swirl = math.sin((y - v0) * 0.9 - phase * 1.6)
            fuller = max(0.0, 1.0 - abs(dx) / (w / 2.0 + 0.001))
            level = 2.0 + swirl * 2.5 + fuller * 2.5
            idx = max(0, min(7, int(round(level))))
            grid[y][x] = PALETTE[idx]


def build_frame(frame_index):
    phase = 2.0 * math.pi * frame_index / FRAMES
    grid = [[T for _ in range(TEX_W)] for _ in range(TEX_H)]
    paint_pommel(grid, phase)
    paint_grip(grid, phase)
    paint_guard(grid, phase)
    paint_blade(grid, phase)
    return grid


all_rows = []
for frame_index in range(FRAMES):
    all_rows.extend(build_frame(frame_index))

out_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "universal_sword_3d.png"
)
write_png(out_path, TEX_W, TEX_H * FRAMES, all_rows)

# The frame size must be spelled out: without "width"/"height", Minecraft assumes square
# frames (32x32 here), which slices this 32x48 frame-strip up wrong and scrambles every
# frame - the glitch an earlier attempt at animating this texture ran into. No
# interpolation: 16 frames at 2 ticks each is already smooth, and it keeps GeckoLib on its
# simplest frame-swapping path.
meta_path = out_path + ".mcmeta"
with open(meta_path, "w") as f:
    json.dump({"animation": {"frametime": FRAMETIME, "width": TEX_W, "height": TEX_H}}, f, indent=2)
    f.write("\n")
print(f"Written: {meta_path}")
print("Done!")
