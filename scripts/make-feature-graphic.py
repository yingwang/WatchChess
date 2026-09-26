# 商店横幅图 1024x500。从仓库根目录跑：python3 scripts/make-feature-graphic.py
# 配色跟 GameScreen.kt 对齐：菜单墨青底、象牙白棋盘、白底红字与白底黑字的棋子、青绿表冠光标。
from PIL import Image, ImageDraw, ImageFont
S = 2                      # 先按两倍画，再缩回 1024x500 抗锯齿
W, H = 1024 * S, 500 * S
MENU_BG = (0x0E, 0x16, 0x14)
BOARD_BG = (0xF0, 0xED, 0xE3)
GRID = (0x52, 0x67, 0x5F)
FACE = (0xFF, 0xF8, 0xE8)
RED = (0xB8, 0x32, 0x2A)
BLACK = (0x1E, 0x23, 0x26)
SHADOW = (0xD6, 0xD2, 0xC6)
CURSOR = (0x25, 0x7B, 0x65)
HEADING = (0x9C, 0xC3, 0xBB)
TEXT = (0xE8, 0xEF, 0xED)
TEXT_DIM = (0x8A, 0x9B, 0x97)

im = Image.new("RGB", (W, H), MENU_BG)
d = ImageDraw.Draw(im)

# 右侧棋盘：一块截出来的象棋盘，棋子落在交叉点上
bx0, by0, bx1, by1 = 524 * S, 15 * S, 994 * S, 485 * S
d.rectangle([bx0, by0, bx1, by1], fill=BOARD_BG)
cell = 100 * S
off = 35 * S
lw = 2 * S
for i in range(5):
    x = bx0 + off + i * cell
    d.line([x, by0, x, by1], fill=GRID, width=lw)
    y = by0 + off + i * cell
    d.line([bx0, y, bx1, y], fill=GRID, width=lw)

song = "/System/Library/Fonts/Supplemental/Songti.ttc"
def piece(ix, iy, ch, red):
    cx, cy = bx0 + off + ix * cell, by0 + off + iy * cell
    r = int(cell * 0.45)
    ink = RED if red else BLACK
    sh = int(cell * 0.05)
    d.ellipse([cx - r, cy - r + sh, cx + r, cy + r + sh], fill=SHADOW)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=FACE, outline=ink, width=int(3.6 * S))
    r2 = int(r * 0.80)
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], outline=ink, width=int(1.6 * S))
    f = ImageFont.truetype(song, int(cell * 0.52), index=1)
    d.text((cx, cy), ch, font=f, fill=ink, anchor="mm")
    return cx, cy, r

piece(1, 1, "車", False)
cx, cy, r = piece(2, 2, "炮", True)
piece(3, 3, "馬", False)
# 表冠光标：绕着被选中的那颗子一圈粗环
rc = int(cell * 0.54)
d.ellipse([cx - rc, cy - rc, cx + rc, cy + rc], outline=CURSOR, width=int(5 * S))

# 左侧文字
title = ImageFont.truetype("/System/Library/Fonts/Supplemental/Baskerville.ttc", 64 * S)
sub = ImageFont.truetype("/System/Library/Fonts/HelveticaNeue.ttc", 32 * S)
d.text((62 * S, 222 * S), "WatchChess", font=title, fill=HEADING, anchor="ls")
d.text((62 * S, 297 * S), "Xiangqi on your wrist", font=sub, fill=TEXT, anchor="ls")
d.text((62 * S, 347 * S), "Turn the crown. Tap to play.", font=sub, fill=TEXT_DIM, anchor="ls")

im.resize((1024, 500), Image.LANCZOS).save("store-assets/feature-1024x500.png")
