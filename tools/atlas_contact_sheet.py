#!/usr/bin/env python3
"""Контактный лист атласа: каждый глиф крупно + его индекс (dec/hex)."""
from PIL import Image, ImageDraw

GW, GH, GRID = 12, 18, 16
SCALE = 4
PAD_X, PAD_Y = 6, 16  # место под подпись
atlas = Image.open("src/main/resources/assets/wrbdrones/textures/overlay/osd_font.png").convert("RGBA")

cw = GW * SCALE + PAD_X
ch = GH * SCALE + PAD_Y
sheet = Image.new("RGBA", (GRID * cw, GRID * ch), (40, 40, 40, 255))
draw = ImageDraw.Draw(sheet)

for c in range(256):
    sx = (c % GRID) * GW
    sy = (c // GRID) * GH
    glyph = atlas.crop((sx, sy, sx + GW, sy + GH)).resize((GW * SCALE, GH * SCALE), Image.NEAREST)
    # фон под глиф, чтобы прозрачные/белые были видны
    cell_x = (c % GRID) * cw
    cell_y = (c // GRID) * ch
    draw.rectangle([cell_x, cell_y, cell_x + cw - 1, cell_y + ch - 1], outline=(90, 90, 90, 255))
    bg = Image.new("RGBA", glyph.size, (20, 20, 20, 255))
    bg.alpha_composite(glyph)
    sheet.paste(bg, (cell_x + PAD_X // 2, cell_y + PAD_Y - 2))
    draw.text((cell_x + 2, cell_y + 2), f"{c} {c:02X}", fill=(255, 230, 120, 255))

sheet.save("tools/atlas_contact_sheet.png")
print("OK", sheet.size)
