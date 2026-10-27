"""
Generate the Infinitite Spike block textures (16x16 PNGs) - the purple spikes the Black Hole Arm
Cannon's spike attack grows out of the ground. Uses only stdlib (struct + zlib) - no Pillow
required (same approach as gen_void_textures.py).

Like pointed dripstone, a spike is made of three parts stacked up - a wide base, a middle and a
sharp tip - each drawn as a tapering crystal on a see-through background (the block model shows
it on two crossed planes, like dripstone). Darker on the left, lighter on the right, with a
glowing streak up the middle.
"""
import zlib, struct, os

SIZE = 16


def write_png(path, pixels):
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


EDGE = (45, 15, 75, 255)
DARK = (80, 25, 125, 255)
MID = (125, 50, 180, 255)
LIGHT = (170, 95, 225, 255)
GLOW = (235, 170, 255, 255)


def spike(bottom_width, top_width):
    """A crystal tapering from bottom_width (at the bottom row) to top_width (at the top row)."""
    pixels = []
    for y in range(SIZE):
        t = (SIZE - 1 - y) / (SIZE - 1)          # 0 at the bottom, 1 at the top
        width = bottom_width + (top_width - bottom_width) * t
        left = 8 - width / 2
        right = 8 + width / 2
        row = []
        for x in range(SIZE):
            if not (left <= x + 0.5 <= right):
                row.append((0, 0, 0, 0))
                continue
            across = (x + 0.5 - left) / max(width, 0.01)  # 0 on the left edge, 1 on the right
            if across < 0.15 or across > 0.9:
                row.append(EDGE)
            elif 0.45 <= across <= 0.6:
                row.append(GLOW if (x + y) % 3 else LIGHT)
            elif across < 0.45:
                row.append(DARK if (x + y * 2) % 4 else MID)
            else:
                row.append(LIGHT if (x * 3 + y) % 4 else MID)
        pixels.append(row)
    return pixels


here = os.path.dirname(os.path.abspath(__file__))
folder = os.path.join(here, 'src/main/resources/assets/toinfinititeandbeyond/textures/block')
write_png(os.path.join(folder, 'infinitite_spike_base.png'), spike(13, 10))
write_png(os.path.join(folder, 'infinitite_spike_middle.png'), spike(10, 7))
write_png(os.path.join(folder, 'infinitite_spike_tip.png'), spike(7, 1))
