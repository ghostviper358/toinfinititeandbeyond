"""
Render a Minecraft Java item/block model (the JSON "elements" kind) to a PNG
preview, so a model can be designed without Blockbench.

Usage: python preview_item_model.py <model.json> <texture.png> <out.png> [<second_model.json> <label>]

Draws four views side by side: from the side, at an angle, from the front, and
"on the arm" (with a Steve-coloured forearm where the cannon wraps, so you can
judge the size). With a second model (like the same item in another state), two
more views of it are added underneath, titled with <label>. Element rotations aren't supported - keep cubes axis-aligned.
"""
import json, math, sys
from PIL import Image, ImageDraw

PANEL = 440         # each view is PANEL x PANEL
BACKGROUND = (38, 38, 46)

# Minecraft-style face shading, so the shape reads in 3D.
SHADE = {'up': 1.0, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6, 'down': 0.5}
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0),
           'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def face_corners(name, f, t):
    """Top-left, top-right, bottom-left, bottom-right corners of a face, matching
    how Minecraft lays the face's UV rectangle onto it."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    if name == 'north':
        return (x2, y2, z1), (x1, y2, z1), (x2, y1, z1), (x1, y1, z1)
    if name == 'south':
        return (x1, y2, z2), (x2, y2, z2), (x1, y1, z2), (x2, y1, z2)
    if name == 'east':
        return (x2, y2, z2), (x2, y2, z1), (x2, y1, z2), (x2, y1, z1)
    if name == 'west':
        return (x1, y2, z1), (x1, y2, z2), (x1, y1, z1), (x1, y1, z2)
    if name == 'up':
        return (x1, y2, z1), (x2, y2, z1), (x1, y2, z2), (x2, y2, z2)
    return (x1, y1, z2), (x2, y1, z2), (x1, y1, z1), (x2, y1, z1)  # down


def lerp3(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def make_view(yaw, pitch, centre):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def rotate(p):
        x, y, z = (p[0] - centre[0], p[1] - centre[1], p[2] - centre[2])
        x, z = x * cy + z * sy, -x * sy + z * cy      # turn around the up axis
        y, z = y * cp - z * sp, y * sp + z * cp       # tilt to look down a bit
        return x, y, z

    return rotate


def quads(elements, tex, texture_size=16):
    """Every texel of every face as (3D corners, colour, face name, face centre)."""
    out = []
    tw, th = tex.size
    for el in elements:
        for name, face in el.get('faces', {}).items():
            tl, tr, bl, br = face_corners(name, el['from'], el['to'])
            face_centre = lerp3(tl, br, 0.5)
            if 'color' in face:
                out.append(((tl, tr, br, bl), face['color'], name, face_centre))
                continue
            u1, v1, u2, v2 = face['uv']
            # UVs are in 0-16 units, unless the model says otherwise with "texture_size".
            k = tw / texture_size
            cols = max(1, round(abs(u2 - u1)))
            rows = max(1, round(abs(v2 - v1)))
            for j in range(rows):
                for i in range(cols):
                    u = u1 + (i + 0.5) * (u2 - u1) / cols
                    v = v1 + (j + 0.5) * (v2 - v1) / rows
                    px = tex.getpixel((min(tw - 1, int(u * k)), min(th - 1, int(v * k))))
                    if px[3] == 0:
                        continue
                    s0, s1, t0, t1 = i / cols, (i + 1) / cols, j / rows, (j + 1) / rows
                    def at(s, t):
                        return lerp3(lerp3(tl, tr, s), lerp3(bl, br, s), t)
                    out.append(((at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)), px, name, face_centre))
    return out


def render(all_quads, yaw, pitch, centre, title, scale):
    img = Image.new('RGB', (PANEL, PANEL), BACKGROUND)
    draw = ImageDraw.Draw(img, 'RGBA')  # RGBA so see-through texels blend
    rotate = make_view(yaw, pitch, centre)
    nrot = make_view(yaw, pitch, (0, 0, 0))
    drawn = []
    for corners, colour, name, face_centre in all_quads:
        if nrot(NORMALS[name])[2] <= 0.001:
            continue  # facing away from us
        pts = [rotate(c) for c in corners]
        # Sort whole faces (not single texels) back to front, so faces don't cut into each other.
        depth = rotate(face_centre)[2]
        shade = SHADE[name]
        alpha = colour[3] if len(colour) == 4 else 255
        col = tuple(int(c * shade) for c in colour[:3]) + (alpha,)
        drawn.append((depth, [(PANEL / 2 + p[0] * scale, PANEL / 2 + 20 - p[1] * scale) for p in pts], col))
    for _, pts, col in sorted(drawn, key=lambda d: d[0]):
        draw.polygon(pts, fill=col, outline=col if col[3] == 255 else None)
    draw.text((12, 10), title, fill=(230, 230, 240))
    return img


def bounds_centre(elements):
    xs = [c for el in elements for c in (el['from'][0], el['to'][0])]
    ys = [c for el in elements for c in (el['from'][1], el['to'][1])]
    zs = [c for el in elements for c in (el['from'][2], el['to'][2])]
    return ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2)


def main(model_path, texture_path, out_path, second_path=None, second_label=None):
    model = json.load(open(model_path))
    tex = Image.open(texture_path).convert('RGBA')
    elements = model['elements']

    # A forearm (Steve's blue sleeve) coming out of the back of the cannon, at the size
    # the cannon is drawn on the arm in third person, so you can judge its size. Only the
    # part sticking out is drawn (this simple renderer can't hide things inside a cube).
    back = max(el['to'][2] for el in elements)
    arm = [{'from': [5.2, 5.2, back], 'to': [10.8, 10.8, back + 18],
            'faces': {n: {'color': (40, 140, 160)} for n in NORMALS}}]

    texture_size = model.get('texture_size', [16])[0]
    model_quads = quads(elements, tex, texture_size)
    arm_quads = quads(arm, tex)

    centre = bounds_centre(elements)
    # Zoom so the model's longest diagonal fills most of a panel (screen pixels per model unit).
    span = math.dist([min(c for el in elements for c in (el['from'][i], el['to'][i])) for i in range(3)],
                     [max(c for el in elements for c in (el['from'][i], el['to'][i])) for i in range(3)])
    scale = min(16, PANEL * 0.85 / span)

    views = [
        render(model_quads, 90, 10, centre, 'Side', scale),
        render(model_quads, 135, 25, centre, 'Angle', scale),
        render(model_quads, 180, 10, centre, 'Front (barrel)', scale),
        render(model_quads + arm_quads, 120, 20, (centre[0], centre[1], centre[2] + 6), 'On your arm', scale * 0.75),
    ]
    if second_path:
        second_model = json.load(open(second_path))
        second = quads(second_model['elements'], tex, second_model.get('texture_size', [16])[0])
        views += [
            render(second, 135, 25, centre, f'{second_label} - Angle', scale),
            render(second, 90, 10, centre, f'{second_label} - Side', scale),
        ]
    rows = (len(views) + 1) // 2
    sheet = Image.new('RGB', (PANEL * 2, PANEL * rows), BACKGROUND)
    for i, v in enumerate(views):
        sheet.paste(v, ((i % 2) * PANEL, (i // 2) * PANEL))
    sheet.save(out_path)
    print(f'Written: {out_path}')


if __name__ == '__main__':
    main(*sys.argv[1:6])
