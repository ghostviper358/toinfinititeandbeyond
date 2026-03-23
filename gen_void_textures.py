"""
Generate Void Ore (block) and Void Ingot (item) textures as 16x16 PNGs.
Uses only stdlib (struct + zlib) - no Pillow required.

Void Ingot: netherite ingot shape, corrupted black with blood-red cracks.
Void Ore:   dark charcoal stone with void pockets and crimson corruption.
"""
import zlib, struct, os

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


# ── Void Ingot item texture ───────────────────────────────────────────────────
# Netherite ingot silhouette, corrupted black with blood-red cracks.

T   = (  0,   0,   0,   0)   # transparent
BK  = (  8,   0,  12, 255)   # near-black body
BK2 = ( 18,   2,  22, 255)   # slightly lighter black
BK3 = ( 30,   5,  35, 255)   # dark highlight
PU  = ( 35,   0,  55, 255)   # deep void purple tinge
CR  = (130,   5,  20, 255)   # dark crimson crack
CRB = (210,  15,  30, 255)   # bright blood-red crack centre
RD  = (255,  30,  40, 255)   # hot corruption node

void_ingot_grid = [
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 0
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 1
    [T,   T,   T,   BK,  BK2, BK,  BK,  BK,  BK,  BK,  BK,  BK,  BK,  T,   T,   T ],  # 2
    [T,   T,   BK,  BK,  CR,  BK2, BK,  BK,  BK,  BK,  BK2, CR,  BK,  BK,  T,   T ],  # 3
    [T,   BK,  BK,  BK2, CRB, CR,  BK,  BK,  BK,  BK,  CR,  CRB, BK2, BK,  BK,  T ],  # 4
    [T,   BK2, BK,  CR,  BK,  CRB, CR,  BK,  BK,  CR,  BK,  BK,  BK,  BK2, BK2, T ],  # 5
    [T,   BK,  BK,  BK,  BK,  BK,  CR,  CRB, BK,  BK,  PU,  BK,  BK,  BK,  BK,  T ],  # 6
    [T,   BK,  BK2, BK,  BK,  CR,  CRB, CR,  BK,  BK,  BK,  PU,  BK,  BK2, BK,  T ],  # 7
    [T,   BK,  BK,  BK,  PU,  BK,  CR,  BK,  BK,  BK,  BK,  BK,  BK,  BK,  BK,  T ],  # 8
    [T,   BK2, BK,  CR,  BK,  BK,  BK,  CR,  BK,  BK,  BK2, BK,  BK,  BK,  BK2, T ],  # 9
    [T,   BK,  BK,  BK,  CR,  CRB, BK,  BK,  CR,  BK,  BK,  BK,  BK2, BK,  BK,  T ],  # 10
    [T,   BK,  BK,  BK,  BK,  CR,  BK,  BK,  BK,  CR,  BK,  BK,  BK,  BK,  BK,  T ],  # 11
    [T,   T,   BK,  BK,  BK,  BK,  CR,  BK,  BK,  BK,  BK,  BK,  BK,  BK,  T,   T ],  # 12
    [T,   T,   T,   BK,  BK2, BK,  BK,  BK,  BK,  BK,  BK,  BK2, BK,  T,   T,   T ],  # 13
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 14
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 15
]

ingot_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "void_ingot.png"
)
write_png(ingot_path, void_ingot_grid)


# ── Void Ore block texture ────────────────────────────────────────────────────
# Dark charcoal stone with void pockets leaking blood-red corruption.

S  = ( 50,  48,  52, 255)   # dark charcoal stone
S2 = ( 38,  36,  40, 255)   # darker stone
S3 = ( 62,  60,  65, 255)   # lighter stone chip
VK = (  8,   0,  12, 255)   # void black pocket
VC = ( 20,   0,  30, 255)   # void core (slightly lighter)
CR = (130,   5,  20, 255)   # dark crimson crack
CRB= (210,  15,  30, 255)   # bright crack centre
PU = ( 35,   0,  55, 255)   # deep purple wisp

void_ore_grid = [
    [S,  S2, S,  S3, S2, S,  S,  S2, S,  S3, S,  S2, S,  S,  S2, S ],  # 0
    [S2, S,  S3, S,  S,  S2, S3, S,  S2, S,  S,  S3, S2, S,  S,  S2],  # 1
    [S,  S3, S,  VK, VK, S2, S,  S3, S,  S2, S,  S,  VK, VK, S3, S ],  # 2
    [S2, S,  VK, VC, CR, VK, S,  S2, S3, S,  VK, VC, CR, VK, S,  S2],  # 3
    [S,  S2, VK, CR, CRB,VK, S3, S,  S,  VK, CR, CRB,VK, S3, S2, S ],  # 4
    [S3, S,  VK, VK, CR, S,  S2, S3, S,  S,  VK, CR, S2, S,  S3, S ],  # 5
    [S,  S2, S,  S,  S3, PU, CR, S2, S3, S,  PU, CR, S,  S2, S,  S3],  # 6
    [S2, S,  S3, S,  CR, PU, CR, S,  S2, CR, PU, CR, S3, S,  S2, S ],  # 7
    [S,  S3, S,  PU, CR, S2, S,  S3, PU, CR, S2, S,  S3, S,  S,  S2],  # 8
    [S2, S,  S,  S,  CR, PU, S2, S,  CR, PU, S,  S3, S,  S2, S,  S ],  # 9
    [S,  S3, S2, S,  S,  S3, S,  S2, S,  S,  S3, S,  S2, S,  S3, S ],  # 10
    [S2, S,  VK, VK, S,  S2, S3, S,  VK, VK, S2, S,  S3, S,  S2, S3],  # 11
    [S,  VK, VC, CR, VK, S,  S2, VK, VC, CR, VK, S,  S2, S3, S,  S ],  # 12
    [S3, VK, CR, CRB,VK, S3, S,  VK, CR, CRB,VK, S3, S,  S2, S3, S2],  # 13
    [S,  S2, VK, VK, S,  S,  S3, S,  VK, VK, S,  S2, S,  S3, S,  S ],  # 14
    [S2, S,  S3, S,  S2, S,  S,  S2, S,  S3, S2, S,  S3, S,  S2, S ],  # 15
]

ore_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "block", "void_ore.png"
)
write_png(ore_path, void_ore_grid)

# ── Void Block texture ───────────────────────────────────────────────────────
# Compressed void energy - dark black with vivid blood-red veins

VB  = ( 18,   2,  25, 255)   # void black base (slightly lighter so veins stand out)
VB2 = ( 30,   4,  38, 255)   # lighter black
VB3 = ( 42,   6,  50, 255)   # dark highlight
VP  = ( 55,   0,  90, 255)   # deep purple vein
CR  = (160,  15,  25, 255)   # dark crimson vein
CRB = (230,  40,  50, 255)   # bright vein centre
RD  = (255,  70,  30, 255)   # hot corruption node

void_block_grid = [
    [VB,  VB2, VB,  CR,  VB,  VB3, VB,  VB2, VB,  CR,  VB,  VB3, VB,  VB2, VB,  VB ],  # 0
    [VB2, VB,  CR,  CRB, CR,  VB,  VB2, VB,  CR,  CRB, CR,  VB,  VB2, VB,  VB3, VB2],  # 1
    [VB,  CR,  CRB, VP,  CRB, CR,  VB,  CR,  CRB, VP,  CRB, CR,  VB,  VB3, VB,  VB ],  # 2
    [CR,  CRB, VP,  VB,  VP,  CRB, CR,  CRB, VP,  VB,  VP,  CRB, CR,  VB2, VB,  CR ],  # 3
    [VB,  CR,  CRB, VP,  CRB, CR,  VB,  CR,  CRB, VP,  CRB, CR,  VB,  VB,  CR,  VB2],  # 4
    [VB2, VB,  CR,  CRB, CR,  VB3, VB,  VB2, CR,  CRB, CR,  VB,  VB3, CR,  VB2, VB ],  # 5
    [VB,  VB3, VB,  CR,  VB2, VB,  RD,  VB,  VB2, CR,  VB,  VB3, CR,  CRB, VB,  VB ],  # 6
    [VB3, VB,  VB2, VB,  CR,  VB,  VB,  VB3, VB,  VB,  CR,  CRB, VP,  CRB, CR,  VB3],  # 7
    [VB,  VB2, VB3, VB,  VB,  CR,  CRB, VB,  VB3, CR,  CRB, VP,  CRB, CR,  VB2, VB ],  # 8
    [VB2, VB,  VB,  VB3, CR,  CRB, VP,  CR,  CRB, VP,  VB,  CRB, CR,  VB,  VB3, VB2],  # 9
    [VB,  VB3, VB,  CR,  CRB, VP,  CRB, CRB, VP,  CRB, CR,  VB,  VB,  VB2, VB,  VB ],  # 10
    [VB3, VB,  CR,  CRB, VP,  CRB, CR,  CR,  CRB, VP,  CRB, CR,  VB3, VB,  VB3, VB3],  # 11
    [VB,  CR,  CRB, VP,  CRB, CR,  VB2, VB,  CR,  CRB, VP,  CRB, CR,  VB2, VB,  VB ],  # 12
    [CR,  CRB, VP,  CRB, CR,  VB,  VB3, CR,  CRB, VP,  CRB, CR,  VB3, VB,  VB2, CR ],  # 13
    [VB2, CR,  CRB, CR,  VB,  VB2, CR,  CRB, CR,  CRB, CR,  VB2, VB,  VB3, CR,  VB2],  # 14
    [VB,  VB2, CR,  VB,  VB3, CR,  CRB, CR,  VB,  CR,  VB2, VB,  CR,  VB2, VB,  VB ],  # 15
]

block_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "block", "void_block.png"
)
write_png(block_path, void_block_grid)

# ── Void Sword item texture ───────────────────────────────────────────────────
# Handheld sword silhouette - void purple blade matching the armor palette

T   = (  0,   0,   0,   0)   # transparent
HK  = ( 18,   7,  30, 255)   # dark purple handle (matches armor C2)
HK2 = ( 35,  12,  55, 255)   # slightly lighter handle (armor C3)
GD  = ( 65,  22, 100, 255)   # mid purple guard (armor C4)
BK  = (  8,   3,  12, 255)   # near-black blade body (armor C1)
BK2 = ( 35,  12,  55, 255)   # dark purple blade (armor C3)
CR  = (115,  45, 170, 255)   # bright purple edge (armor C5)
CRB = (160,  85, 220, 255)   # glowing purple edge (armor C6)
RD  = (200, 130, 255, 255)   # brightest lavender tip (armor C7)

void_sword_grid = [
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   RD,  T,   T ],  # 0  tip
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   CRB, BK,  CRB, T ],  # 1
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   CR,  BK,  BK2, T,   T ],  # 2
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   CRB, BK,  BK2, T,   T,   T ],  # 3
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   CR,  BK,  BK2, T,   T,   T,   T ],  # 4
    [T,   T,   T,   T,   T,   T,   T,   T,   CRB, BK,  BK2, T,   T,   T,   T,   T ],  # 5
    [T,   T,   T,   T,   T,   T,   T,   CR,  BK,  BK2, T,   T,   T,   T,   T,   T ],  # 6
    [T,   T,   T,   T,   T,   GD,  GD,  BK,  BK2, T,   T,   T,   T,   T,   T,   T ],  # 7  guard top
    [T,   T,   T,   T,   GD,  GD,  BK,  BK2, GD,  T,   T,   T,   T,   T,   T,   T ],  # 8  guard
    [T,   T,   T,   T,   T,   GD,  BK,  BK2, T,   T,   T,   T,   T,   T,   T,   T ],  # 9  guard bottom
    [T,   T,   T,   T,   T,   HK,  HK2, T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 10 handle
    [T,   T,   T,   T,   HK,  HK2, T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 11
    [T,   T,   T,   HK,  HK2, T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 12
    [T,   T,   HK,  HK2, T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 13
    [T,   HK,  HK2, T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 14
    [HK,  HK2, T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 15 pommel
]

sword_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "void_sword.png"
)
write_png(sword_path, void_sword_grid)

# ── Void Boots item texture ───────────────────────────────────────────────────
# Boot silhouette - matching the void armor purple palette exactly

T   = (  0,   0,   0,   0)   # transparent
D1  = (  8,   3,  12, 255)   # darkest body (matches helmet)
D2  = ( 18,   7,  30, 255)   # dark body
D3  = ( 35,  12,  55, 255)   # mid purple shadow
D4  = ( 65,  22, 100, 255)   # medium purple
H1  = (160,  85, 220, 255)   # bright purple highlight
H2  = (200, 130, 255, 255)   # brightest lavender accent

void_boots_grid = [
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 0
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 1
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 2
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 3
    [T,   T,   H1,  H2,  T,   T,   T,   T,   T,   H1,  H2,  T,   T,   T,   T,   T ],  # 4  ankle opening rim
    [T,   H1,  D2,  D3,  H1,  T,   T,   T,   H1,  D2,  D3,  H1,  T,   T,   T,   T ],  # 5
    [T,   D4,  D1,  D4,  D2,  T,   T,   T,   D4,  D1,  D4,  D2,  T,   T,   T,   T ],  # 6
    [T,   D2,  D3,  D2,  D4,  T,   T,   T,   D2,  D3,  D2,  D4,  T,   T,   T,   T ],  # 7
    [T,   H1,  D2,  D1,  D2,  D3,  D3,  D2,  D1,  D2,  D1,  H1,  T,   T,   T,   T ],  # 8  upper cuff band
    [T,   D4,  D3,  D2,  D4,  D1,  D4,  D1,  D2,  D4,  D3,  D4,  T,   T,   T,   T ],  # 9
    [T,   D2,  D4,  D3,  D1,  D4,  D3,  D4,  D1,  D3,  D4,  D2,  T,   T,   T,   T ],  # 10
    [T,   H1,  D2,  D1,  D3,  D2,  H1,  D2,  D3,  D1,  D2,  H1,  T,   T,   T,   T ],  # 11 purple accent stripe
    [T,   D3,  D4,  D2,  D1,  D3,  D4,  D3,  D1,  D2,  D4,  D3,  T,   T,   T,   T ],  # 12
    [T,   D2,  D3,  D4,  D2,  D1,  D2,  D1,  D2,  D4,  D3,  D2,  T,   T,   T,   T ],  # 13
    [T,   H2,  H1,  D4,  D3,  D4,  H1,  H1,  D4,  D3,  H1,  H2,  T,   T,   T,   T ],  # 14  sole highlight
    [T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T,   T ],  # 15
]

boots_path = os.path.join(
    os.path.dirname(__file__),
    "src", "main", "resources", "assets", "toinfinititeandbeyond",
    "textures", "item", "void_boots.png"
)
write_png(boots_path, void_boots_grid)

print("Done!")