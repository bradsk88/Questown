#!/usr/bin/env python3
"""Crop a PNG to a rect without PIL. crop.py <in.png> <out.png> <x0> <y0> <x1> <y1>
(x1/y1 exclusive). RGB only; writes filter-0 PNGs."""
import struct, sys, zlib

def read_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    w = h = None
    bitdepth = colortype = None
    idat = b''
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        data = d[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bitdepth, colortype = struct.unpack('>IIBB', data[:10])
        elif typ == b'IDAT':
            idat += data
        elif typ == b'IEND':
            break
        pos += 12 + ln
    assert bitdepth == 8, "only 8-bit pngs supported"
    ch = {0: 1, 2: 3, 6: 4}[colortype]
    raw = zlib.decompress(idat)
    stride = w * ch
    rows = []
    prev = bytearray(stride)
    o = 0
    for y in range(h):
        f = raw[o]; o += 1
        line = bytearray(raw[o:o+stride]); o += stride
        if f == 1:
            for i in range(ch, stride): line[i] = (line[i] + line[i-ch]) & 255
        elif f == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                b = prev[i]
                c = prev[i-ch] if i >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        prev = line
        rows.append(bytes(line))
    return w, h, ch, rows

def write_png(path, w, h, ch, rows):
    ct = {1: 0, 3: 2, 4: 6}[ch]
    raw = b''
    for r in rows:
        raw += b'\x00' + r
    comp = zlib.compress(raw, 9)
    def chunk(typ, data):
        c = struct.pack('>I', len(data)) + typ + data
        return c + struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)
    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, ct, 0, 0, 0))
    out += chunk(b'IDAT', comp)
    out += chunk(b'IEND', b'')
    open(path, 'wb').write(out)

def main():
    inp, outp = sys.argv[1], sys.argv[2]
    x0, y0, x1, y1 = (int(v) for v in sys.argv[3:7])
    w, h, ch, rows = read_png(inp)
    x1 = min(x1, w); y1 = min(y1, h)
    outw, outh = x1 - x0, y1 - y0
    crop = []
    for y in range(y0, y1):
        crop.append(rows[y][x0*ch:x1*ch])
    write_png(outp, outw, outh, ch, crop)
    print(f"cropped {inp} -> {outp} ({outw}x{outh})")

if __name__ == '__main__':
    main()
