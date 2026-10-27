"""
Generate the Black Hole Arm Cannon texture as a 32x32 PNG.
Uses only stdlib (struct + zlib) - no Pillow required (same approach as
gen_void_textures.py).

The texture is an atlas of 8x8 squares the 3D model picks pieces out of (see
gen_black_hole_arm_cannon_model.py):
  (0, 0)  METAL - dark void metal with rivets (gauntlet, barrel)
  (8, 0)  PLATE - purple armor plating (body, fins)
  (0, 8)  GLOW  - glowing magenta energy (rings, vents, fin tips)
  (8, 8)  HOLE  - a black hole: black centre, purple swirl (muzzle tip)
  (16, 0) CORE  - the floating black hole's inside: pure black with faint sparks
  (16, 8) SHELL - the floating black hole's outside: see-through swirling purple
  (24, 0) AMMO  - a little void block (the floating ammo)
The rest is left empty.
"""
import zlib, struct, os, math

SIZE = 32


def write_png(path, pixels):
    """Write a SIZE x SIZE RGBA pixel grid as a PNG file."""
    def make_chunk(tag, data):
        c = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', c)

    ihdr_data = struct.pack('>II', SIZE, SIZE) + bytes([8, 6, 0, 0, 0])
    raw_rows = b''
    for row in pixels:
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


def metal(x, y):
    if x in (1, 6) and y in (1, 6):
        return (150, 110, 190, 255)  # rivets
    if x == 0 or y == 0 or x == 7 or y == 7:
        return (55, 30, 80, 255)
    return (32, 18, 48, 255) if (x + y) % 3 else (40, 22, 58, 255)


def plate(x, y):
    if y == 0:
        return (175, 120, 220, 255)  # highlight
    if y == 3:
        return (60, 30, 90, 255)     # panel line
    return (115, 60, 160, 255) if (x + y) % 2 else (100, 50, 145, 255)


def glow(x, y):
    g = 1.0 - abs(y - 3.5) / 4.0     # brightest in the middle rows
    return (int(170 + 85 * g), int(40 + 150 * g), int(200 + 55 * g), 255)


def hole(x, y):
    d = math.hypot(x - 3.5, y - 3.5)
    angle = math.atan2(y - 3.5, x - 3.5)
    if d < 1.3:
        return (0, 0, 0, 255)
    if d < 2.6:
        swirl = (math.sin(angle * 3 + d * 2) + 1) / 2
        return (int(120 + 120 * swirl), int(20 + 60 * swirl), int(180 + 70 * swirl), 255)
    return (45, 20, 70, 255)


def core(x, y):
    if (x, y) in ((2, 5), (5, 2), (6, 6)):
        return (90, 40, 130, 255)    # faint sparks
    return (0, 0, 0, 255)


def shell(x, y):
    d = math.hypot(x - 3.5, y - 3.5)
    angle = math.atan2(y - 3.5, x - 3.5)
    swirl = (math.sin(angle * 2 + d * 1.5) + 1) / 2
    return (int(150 + 90 * swirl), int(50 + 60 * swirl), 255, int(70 + 70 * swirl))


def ammo(x, y):
    if x == 0 or y == 0 or x == 7 or y == 7:
        return (200, 120, 255, 255)  # glowing edge
    return (50, 15, 80, 255) if (x * 3 + y) % 4 else (80, 30, 120, 255)


REGIONS = {
    (0, 0): metal, (8, 0): plate, (0, 8): glow, (8, 8): hole,
    (16, 0): core, (16, 8): shell, (24, 0): ammo,
}


def pixel(x, y):
    painter = REGIONS.get((x // 8 * 8, y // 8 * 8))
    return painter(x % 8, y % 8) if painter else (0, 0, 0, 0)


pixels = [[pixel(x, y) for x in range(SIZE)] for y in range(SIZE)]
here = os.path.dirname(os.path.abspath(__file__))
write_png(os.path.join(here, 'src/main/resources/assets/toinfinititeandbeyond/textures/item/black_hole_arm_cannon.png'), pixels)
