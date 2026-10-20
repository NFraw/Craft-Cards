"""Resource-level checks for gaps, duplicate faces and internal rims on connected tables.

Run with: python -m unittest discover -s tools -p test_ddz_table_models.py
Uses only the Python standard library; no running Minecraft client is required.
"""
import json
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1] / 'common/src/main/resources/assets/crafty_cards'
DIRECTIONS = ('north', 'east', 'south', 'west')


def read(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8'))


def matches(condition, state):
    if 'OR' in condition:
        return any(matches(part, state) for part in condition['OR'])
    return all(state[key] == (value == 'true') for key, value in condition.items())


def top_faces(model, rotation=0):
    for element in model['elements']:
        if 'up' not in element['faces']:
            continue
        x0, y0, z0 = element['from']
        x1, y1, z1 = element['to']
        assert y0 == y1 == 16
        corners = [(x0, z0), (x0, z1), (x1, z0), (x1, z1)]
        for _ in range(rotation // 90):
            corners = [(16-z, x) for x, z in corners]
        uv = element['faces']['up']['uv']
        material = 'cloth' if uv[0] >= 8 and uv[1] < 8 else 'wood'
        yield (min(x for x, z in corners), min(z for x, z in corners),
               max(x for x, z in corners), max(z for x, z in corners), material)


def world_top(state):
    result = []
    for part in read('blockstates/ddz_table.json')['multipart']:
        if 'when' in part and not matches(part['when'], state):
            continue
        apply = part['apply']
        model = apply['model'].split(':')[1]
        result.extend(top_faces(read('models/' + model + '.json'), apply.get('y', 0)))
    return result


def material_at(faces, x, z):
    hits = [material for x0, z0, x1, z1, material in faces
            if x0 <= x+.5 < x1 and z0 <= z+.5 < z1]
    if len(hits) != 1:
        raise AssertionError(f'Top at ({x},{z}) must be covered exactly once, got {hits}')
    return hits[0]


class TableModelsTest(unittest.TestCase):
    def test_all_sixteen_connection_states_have_a_closed_top(self):
        for mask in range(16):
            state = {name: bool(mask & (1 << i)) for i, name in enumerate(DIRECTIONS)}
            faces = world_top(state)
            for x in range(16):
                for z in range(16):
                    material_at(faces, x, z)

    def test_rectangles_have_only_an_outer_rim(self):
        for width, depth in ((1, 1), (1, 3), (1, 9), (9, 1), (2, 3), (3, 2), (3, 3)):
            for tx in range(width):
                for tz in range(depth):
                    faces = world_top(dict(north=tz > 0, east=tx < width-1,
                                           south=tz < depth-1, west=tx > 0))
                    for x in range(16):
                        for z in range(16):
                            wx, wz = tx*16+x, tz*16+z
                            border = wx < 2 or wz < 2 or wx >= width*16-2 or wz >= depth*16-2
                            self.assertEqual('wood' if border else 'cloth', material_at(faces, x, z),
                                             (width, depth, tx, tz, x, z))

    def test_inventory_matches_single_table_and_preserves_display(self):
        item = read('models/item/ddz_table.json')
        combined = list(top_faces(read('models/block/ddz_table.json')))
        world = world_top(dict.fromkeys(DIRECTIONS, False))
        for x in range(16):
            for z in range(16):
                self.assertEqual(material_at(world, x, z), material_at(combined, x, z))
        self.assertEqual([30, 225, 0], item['display']['gui']['rotation'])
        for hand in ('righthand', 'lefthand'):
            self.assertEqual([.35]*3, item['display']['firstperson_'+hand]['scale'])
            self.assertEqual([.25]*3, item['display']['thirdperson_'+hand]['scale'])

    def test_referenced_textures_and_particle_exist(self):
        for path in (ROOT / 'models/block').glob('ddz_table*.json'):
            model = json.loads(path.read_text(encoding='utf-8'))
            for texture in model['textures'].values():
                self.assertTrue((ROOT / 'textures' / (texture.split(':')[1]+'.png')).is_file())
            for element in model['elements']:
                for face in element['faces'].values():
                    self.assertEqual('#atlas', face['texture'])
                    self.assertTrue(all(0 <= n <= 16 for n in face['uv']))
        particle = ROOT / 'textures/block/ddz_table_particle.png'
        self.assertEqual((16, 16), struct.unpack('>II', particle.read_bytes()[16:24]))


if __name__ == '__main__':
    unittest.main()
