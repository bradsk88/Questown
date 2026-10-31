#!/usr/bin/env python3
"""zoom.py <in.png> <out.png> <x0> <y0> <x1> <y1> [factor=3]

Crop a region of a screenshot and upscale it (nearest neighbour) so small GUI text is readable.
No PIL on this box: reuses tools/porttest/crop.py's PNG reader/writer.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'porttest'))
from crop import read_png, write_png  # noqa: E402

src, dst = sys.argv[1], sys.argv[2]
x0, y0, x1, y1 = map(int, sys.argv[3:7])
f = int(sys.argv[7]) if len(sys.argv) > 7 else 3
w, h, ch, rows = read_png(src)
x1, y1 = min(x1, w), min(y1, h)
out = []
for y in range(y0, y1):
    r = rows[y]
    line = b''.join(bytes(r[x * ch:x * ch + 3]) * f for x in range(x0, x1))
    out.extend([line] * f)
write_png(dst, (x1 - x0) * f, (y1 - y0) * f, 3, out)
print(dst)
