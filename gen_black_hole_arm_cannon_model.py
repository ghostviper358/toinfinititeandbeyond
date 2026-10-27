"""
Generate the Black Hole Arm Cannon's 3D model from a simple list of cubes:
  - geckolib/models/item/black_hole_arm_cannon.geo.json - the real model, drawn by GeckoLib
    so its parts can move (see BlackHoleArmCannonRenderer)
  - models/item/black_hole_arm_cannon.json - just where it sits in your hand, the inventory, etc.
  - preview_black_hole_arm_cannon_held.json / _stowed.json - plain Java models of the two
    looks, only for drawing pictures with preview_item_model.py:
      python preview_item_model.py preview_black_hole_arm_cannon_held.json
          src/main/resources/assets/toinfinititeandbeyond/textures/item/black_hole_arm_cannon.png
          preview_black_hole_arm_cannon.png preview_black_hole_arm_cannon_stowed.json "Put away"

The barrel points toward -Z (north) and the arm goes in from +Z (south). Each cube says which
8x8 square of the 32x32 texture it uses (see gen_black_hole_arm_cannon_texture.py).
"""
import json, os

METAL = (0, 0)
PLATE = (8, 0)
GLOW = (0, 8)
HOLE = (8, 8)
CORE = (16, 0)
SHELL = (16, 8)
AMMO = (24, 0)
TEXTURE_SIZE = 32

# Everything is moved back by this much, so the cannon sits over the hand rather than in front of it.
Z_SHIFT = 6

# The fins slide this far back toward the arm when the cannon is put away
# (BlackHoleArmCannonRenderer.FIN_RETRACT must match).
FIN_RETRACT = 9

# (name, from, to, texture square, {face: texture square} overrides)
BODY = [
    ('gauntlet',     [3.5, 3.5, 6],     [12.5, 12.5, 17],  METAL, {}),
    ('top_plate',    [4, 12.5, 7],      [12, 13.5, 16],    PLATE, {}),
    ('vent_east',    [12.5, 6, 9],      [13, 10, 15],      GLOW,  {}),
    ('vent_west',    [3, 6, 9],         [3.5, 10, 15],     GLOW,  {}),
    ('core_ring',    [4, 4, 4],         [12, 12, 6],       GLOW,  {}),
    ('body',         [4, 4, -1],        [12, 12, 4],       PLATE, {}),
    ('barrel',       [5, 5, -11],       [11, 11, -1],      METAL, {}),
    ('barrel_ring1', [4.5, 4.5, -4],    [11.5, 11.5, -3],  GLOW,  {}),
    ('barrel_ring2', [4.5, 4.5, -8],    [11.5, 11.5, -7],  GLOW,  {}),
    ('muzzle',       [4, 4, -13],       [12, 12, -11],     PLATE, {'north': HOLE}),
]

# Four long fins, one on each corner of the barrel. They hang out past the muzzle and bend
# inward at the tips, like claws holding the black hole. Each fin is its own moving part.
FINS = {}
for name, x, y, tip_dx, tip_dy in [('ne', 11.5, 11.5, -1, -1), ('nw', 3, 11.5, 1, -1),
                                   ('se', 11.5, 3, -1, 1), ('sw', 3, 3, 1, 1)]:
    FINS['fin_' + name] = [
        ('bar', [x, y, -21], [x + 1.5, y + 1.5, -2], PLATE, {}),
        ('tip', [x + tip_dx, y + tip_dy, -22], [x + tip_dx + 1.5, y + tip_dy + 1.5, -20], GLOW, {}),
    ]

# The black hole floating between the fin tips: a black core inside a see-through purple shell.
BLACK_HOLE_CENTRE = [8, 8, -17]
BLACK_HOLE_CORE = ('core', [6.5, 6.5, -18.5], [9.5, 9.5, -15.5], CORE, {})
BLACK_HOLE_SHELL = ('shell', [5.5, 5.5, -19.5], [10.5, 10.5, -14.5], SHELL, {})

# The ammo: four little void blocks orbiting the black hole, one for each quarter of the ammo.
AMMO_BLOCKS = {
    'ammo_0': ('block', [7.5, 11.5, -17.5], [8.5, 12.5, -16.5], AMMO, {}),
    'ammo_1': ('block', [11.5, 7.5, -17.5], [12.5, 8.5, -16.5], AMMO, {}),
    'ammo_2': ('block', [7.5, 3.5, -17.5],  [8.5, 4.5, -16.5],  AMMO, {}),
    'ammo_3': ('block', [3.5, 7.5, -17.5],  [4.5, 8.5, -16.5],  AMMO, {}),
}

# Where the cannon sits in first person, third person, the inventory, etc.
DISPLAY = {
    'firstperson_righthand': {'rotation': [0, 0, 0], 'translation': [2, 0, -2], 'scale': [0.6, 0.6, 0.6]},
    'firstperson_lefthand':  {'rotation': [0, 0, 0], 'translation': [2, 0, -2], 'scale': [0.6, 0.6, 0.6]},
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 0, 3], 'scale': [0.6, 0.6, 0.6]},
    'thirdperson_lefthand':  {'rotation': [0, 0, 0], 'translation': [0, 0, 3], 'scale': [0.6, 0.6, 0.6]},
    'gui':    {'rotation': [25, 135, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'ground': {'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
    'fixed':  {'rotation': [0, 90, 0], 'scale': [0.4, 0.4, 0.4]},
}

FACES = ['north', 'south', 'east', 'west', 'up', 'down']
FACE_SIZE = {  # which two axes each face spans (width, height)
    'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2),
}


def shifted(v, dz=0):
    return [v[0], v[1], v[2] + Z_SHIFT + dz]


def face_uv(square, f, t, face):
    """[u, v, width, height] of the bit of an 8x8 texture square a face uses."""
    a, b = FACE_SIZE[face]
    w = min(8, abs(t[a] - f[a]))
    h = min(8, abs(t[b] - f[b]))
    u, v = square
    if square in (HOLE, CORE, SHELL):  # centre round patterns on the face
        u, v = u + (8 - w) / 2, v + (8 - h) / 2
    return [u, v, w, h]


# --- GeckoLib model ---

def geo_cube(cube):
    _, f, t, square, overrides = cube
    f, t = shifted(f), shifted(t)
    uv = {}
    for face in FACES:
        u, v, w, h = face_uv(overrides.get(face, square), f, t, face)
        uv[face] = {'uv': [u, v], 'uv_size': [w, h]}
    # Bedrock models are mirrored left-right compared with Java ones, and centred on the item.
    return {
        'origin': [8 - t[0], f[1] - 8, f[2] - 8],
        'size': [t[0] - f[0], t[1] - f[1], t[2] - f[2]],
        'uv': uv,
    }


def geo_point(p):
    p = shifted(p)
    return [8 - p[0], p[1] - 8, p[2] - 8]


def geo_bone(name, parent, pivot, cubes):
    bone = {'name': name, 'pivot': geo_point(pivot), 'cubes': [geo_cube(c) for c in cubes]}
    if parent:
        bone['parent'] = parent
    return bone


def build_geo():
    centre = BLACK_HOLE_CENTRE
    bones = [geo_bone('root', None, [8, 8, 8], []),
             geo_bone('body', 'root', [8, 8, 8], BODY)]
    for name, cubes in FINS.items():
        bones.append(geo_bone(name, 'root', [8, 8, 8], cubes))
    bones.append(geo_bone('black_hole', 'root', centre, []))
    bones.append(geo_bone('black_hole_core', 'black_hole', centre, [BLACK_HOLE_CORE]))
    bones.append(geo_bone('black_hole_shell', 'black_hole', centre, [BLACK_HOLE_SHELL]))
    bones.append(geo_bone('ammo_ring', 'root', centre, []))
    for name, cube in AMMO_BLOCKS.items():
        f, t = cube[1], cube[2]
        middle = [(f[i] + t[i]) / 2 for i in range(3)]
        bones.append(geo_bone(name, 'ammo_ring', middle, [cube]))
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.black_hole_arm_cannon',
                'texture_width': TEXTURE_SIZE,
                'texture_height': TEXTURE_SIZE,
                'visible_bounds_width': 4,
                'visible_bounds_height': 3,
                'visible_bounds_offset': [0, 0.5, 0],
            },
            'bones': bones,
        }],
    }


# --- Plain Java models (for previews) ---

def java_element(cube, dz=0):
    name, f, t, square, overrides = cube
    f, t = shifted(f, dz), shifted(t, dz)
    faces = {}
    for face in FACES:
        u, v, w, h = face_uv(overrides.get(face, square), f, t, face)
        faces[face] = {'uv': [u, v, u + w, v + h], 'texture': '#0'}
    return {'name': name, 'from': f, 'to': t, 'faces': faces}


def build_java(held):
    fins = [c for cs in FINS.values() for c in cs]
    elements = [java_element(c) for c in BODY]
    if held:
        elements += [java_element(c) for c in fins]
        elements += [java_element(BLACK_HOLE_CORE), java_element(BLACK_HOLE_SHELL)]
        elements += [java_element(c) for c in AMMO_BLOCKS.values()]
    else:
        elements += [java_element(c, FIN_RETRACT) for c in fins]
    return {
        'texture_size': [TEXTURE_SIZE, TEXTURE_SIZE],
        'textures': {'0': 'toinfinititeandbeyond:item/black_hole_arm_cannon'},
        'elements': elements,
    }


def write(path, data):
    with open(path, 'w') as out:
        json.dump(data, out, indent=2)
    print(f'Written: {path}')


here = os.path.dirname(os.path.abspath(__file__))
assets = os.path.join(here, 'src/main/resources/assets/toinfinititeandbeyond')
write(os.path.join(assets, 'geckolib/models/item/black_hole_arm_cannon.geo.json'), build_geo())
write(os.path.join(assets, 'models/item/black_hole_arm_cannon.json'), {
    'textures': {'particle': 'toinfinititeandbeyond:item/black_hole_arm_cannon'},
    'display': DISPLAY,
})
write(os.path.join(here, 'preview_black_hole_arm_cannon_held.json'), build_java(held=True))
write(os.path.join(here, 'preview_black_hole_arm_cannon_stowed.json'), build_java(held=False))
