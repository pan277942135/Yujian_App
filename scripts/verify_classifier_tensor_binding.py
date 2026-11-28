#!/usr/bin/env python3
"""Fail the build when frozen classifier FlatBuffer tensors drift from runtime binding."""
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
MODEL = ASSETS / 'fish_classifier.tflite'
SHA = '29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18'
BINDINGS = (('images', 'serving_default_args_0', [1, 3, 224, 224]),
            ('logits', 'serving_default_output_0_output', [1, 15]))


def inspect(data):
    def u32(pos): return struct.unpack_from('<I', data, pos)[0]
    def i32(pos): return struct.unpack_from('<i', data, pos)[0]
    def u16(pos): return struct.unpack_from('<H', data, pos)[0]
    def field(table, index):
        vt = table - i32(table)
        entry = vt + 4 + index * 2
        return table + u16(entry) if entry < vt + u16(vt) and u16(entry) else None
    def ref(pos): return pos + u32(pos)
    def vec(table, index):
        v = ref(field(table, index))
        return v + 4, u32(v)
    def table_at(base, index): return ref(base + index * 4)
    def tensor(subgraph, tensor_index):
        tensors, _ = vec(subgraph, 0)
        t = table_at(tensors, tensor_index)
        shape, length = vec(t, 0)
        dims = [i32(shape + i * 4) for i in range(length)]
        name_pos = ref(field(t, 3))
        name = data[name_pos + 4:name_pos + 4 + u32(name_pos)].decode('utf-8')
        dtype_field = field(t, 1)
        dtype = data[dtype_field] if dtype_field is not None else 0
        return name, dims, dtype
    assert data[4:8] == b'TFL3', 'invalid TFLite identifier'
    model = u32(0)
    subgraphs, length = vec(model, 2)
    assert length == 1, f'expected one subgraph, got {length}'
    subgraph = table_at(subgraphs, 0)
    result = []
    for field_index in (1, 2):
        indexes, length = vec(subgraph, field_index)
        assert length == 1, f'expected one tensor endpoint, got {length}'
        result.append(tensor(subgraph, i32(indexes)))
    return result


def verify(model=MODEL, contract=ASSETS / 'tensor_contract.json', apk=None):
    if apk is None:
        data = model.read_bytes()
    else:
        with zipfile.ZipFile(apk) as archive:
            data = archive.read('assets/fish_classifier.tflite')
    assert hashlib.sha256(data).hexdigest() == SHA, 'frozen model SHA mismatch'
    meta = json.loads(contract.read_text())
    assert meta['model_id'] == 'MODEL_CROP_M1_v0.2'
    assert meta['inputs'][0]['layout'] == 'NCHW'
    for (logical, runtime, shape), tensor, row in zip(BINDINGS, inspect(data), (meta['inputs'][0], meta['outputs'][0])):
        assert row['name'] == logical and row['shape'] == shape and row['dtype'] == 'float32', f'logical contract mismatch: {logical}'
        assert tensor == (runtime, shape, 0), f'runtime tensor mismatch: {logical}: {tensor}'
    print('CLASSIFIER_REAL_TFLITE_TENSOR_BINDING_GATE=PASS')


if __name__ == '__main__':
    verify(apk=Path(sys.argv[2]) if len(sys.argv) == 3 and sys.argv[1] == '--apk' else None)
