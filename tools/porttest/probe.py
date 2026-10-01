#!/usr/bin/env python3
"""porttest pixel probe - the ruler that vision is not.

probe.py <png> [x0 y0 x1 y1] [--band NAME|lo,hi] [--mask W H] [--max N]

Bands (RGB per channel, inclusive):
  hover   200-235   0x80FFFFFF highlight blended over the ~174 card gray -
                    the WorkScreen hover box IS this band, NOT pure white:
                    a 246+ probe reads a working hover as "absent"
  white   246-255   pure UI white (card borders)
  satur   chroma    any saturated pixel (max-min channel > 30) - faces,
                    icons, text
  skin    tan/brown skin-ish pixels (villager face)
  flat    100-235 grayish (|r-g|<10 and |g-b|<10) - window interiors

Default region is the whole image. Prints the match count and bbox;
--mask W H prints a '#'/'.' character grid at reduced resolution (the
face-mask comparison method). All coordinates are CANVAS px (634x675
reference), the same space `cursor <x> <y>` writes.
"""
import sys
sys.path.insert(0, __import__('os').path.dirname(__file__))
from crop import read_png

BANDS = {
    'hover': (200, 235, False),
    'white': (246, 255, False),
    'flat': (100, 235, True),
}

def in_band(c, band):
    lo, hi, kind = band
    r, g, b = c
    if kind is False:  # flat value band
        return lo <= r <= hi and lo <= g <= hi and lo <= b <= hi
    if kind is True:   # grayish
        return abs(r - g) < 10 and abs(g - b) < 10 and r > 100 and r < 236
    if kind == 'satur':
        return (max(r, g, b) - min(r, g, b)) > 30
    if kind == 'skin':
        return 100 < r < 230 and g > 40 and b < r and (r - b) > 30
    return False

def parse_band(spec):
    if spec in BANDS:
        return (BANDS[spec][0], BANDS[spec][1], BANDS[spec][2])
    if spec in ('satur', 'skin'):
        return (0, 255, spec)
    lo, hi = (int(v) for v in spec.split(','))
    return (lo, hi, False)

def main():
    a = sys.argv[1:]
    if not a or not a[0].endswith('.png'):
        print(__doc__)
        sys.exit(2)
    a1 = a[1:]
    path = a[0]
    region = None
    band = None  # hover: the default that burned us (0x80FFFFFF over ~174 gray)
    mask = None
    maxr = 0
    pos = []
    i = 0
    while i < len(a1):
        t = a1[i]
        if t.startswith('--'):
            i += 2
            if t == '--band':
                band = parse_band(a1[i - 1])
            elif t == '--mask':
                mask = int(a1[i - 1])
            elif t == '--max':
                maxr = int(a1[i - 1])
            continue
        pos.append(int(t))
        i += 1
    if band is None:
        band = parse_band('hover')
    if len(pos) == 4:
        region = tuple(pos)

    w, h, ch, rows = read_png(path)
    x0, y0, x1, y1 = region if region else (0, 0, w, h)
    best = None
    count = 0
    samples = []
    for y in range(max(0, y0), min(h, y1)):
        line = rows[y]
        for x in range(max(0, x0), min(w, x1)):
            c = (line[x * ch], line[x * ch + 1], line[x * ch + 2])
            if in_band(c, band):
                count += 1
                if best is None:
                    best = [x, y, x, y]
                else:
                    best[0] = min(best[0], x); best[1] = min(best[1], y)
                    best[2] = max(best[2], x); best[3] = max(best[3], y)
                if maxr and len(samples) < maxr:
                    samples.append((x, y))
    print(f"image {w}x{h}  band={band}")
    print(f"matches: {count}")
    if best:
        print(f"bbox: x[{best[0]}..{best[2]}] y[{best[1]}..{best[3]}]  "
              f"({best[2] - best[0] + 1}x{best[3] - best[1] + 1})")
    if mask:
        step = max(1, (x1 - x0) // mask)
        for y in range(y0, min(h, y1), step):
            line = ''
            for x in range(x0, min(w, x1), step):
                c = (rows[y][x * ch], rows[y][x * ch + 1], rows[y][x * ch + 2])
                line += '#' if in_band(c, band) else '.'
            print(line)
    if samples:
        print("samples:", samples)

if __name__ == '__main__':
    main()
