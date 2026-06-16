#!/usr/bin/env python3
"""Конвертер шрифта MAX7456 (.mcm) в атлас-текстуру для OSD дронов.

Формат MAX7456:
  - первая строка — заголовок "MAX7456";
  - далее 256 символов, по 64 строки на символ;
  - каждая строка — 8 бит (4 пикселя по 2 бита);
  - символ 12x18 = 54 значащие строки, остальные 10 — паддинг.

Значения 2-битных пикселей:
  00 -> чёрный (контур), 10 -> белый (заливка), 01/11 -> прозрачный.

Атлас: сетка 16x16 глифов, каждый глиф 12x18 -> 192x288 px, RGBA.
"""
import sys
from PIL import Image

CHAR_W, CHAR_H = 12, 18
GRID = 16
LINES_PER_CHAR = 64

def main(src, dst):
    with open(src, "r") as f:
        lines = [l.strip() for l in f if l.strip()]
    assert lines[0] == "MAX7456", "не файл MAX7456"
    data = lines[1:]
    assert len(data) == 256 * LINES_PER_CHAR, f"ожидалось 16384 строк, есть {len(data)}"

    atlas = Image.new("RGBA", (GRID * CHAR_W, GRID * CHAR_H), (0, 0, 0, 0))
    px = atlas.load()

    for c in range(256):
        base = c * LINES_PER_CHAR
        gx = (c % GRID) * CHAR_W
        gy = (c // GRID) * CHAR_H
        for row in range(CHAR_H):
            # 12 пикселей в ряду = 3 строки по 4 пикселя
            bits = data[base + row * 3] + data[base + row * 3 + 1] + data[base + row * 3 + 2]
            for col in range(CHAR_W):
                two = bits[col * 2: col * 2 + 2]
                if two == "00":
                    rgba = (0, 0, 0, 255)        # контур
                elif two == "10":
                    rgba = (255, 255, 255, 255)  # заливка
                else:
                    rgba = (0, 0, 0, 0)          # прозрачно
                px[gx + col, gy + row] = rgba

    atlas.save(dst)
    print(f"OK: {dst} ({atlas.width}x{atlas.height})")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
