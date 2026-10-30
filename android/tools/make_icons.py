# -*- coding: utf-8 -*-
"""AkiMusic 图标：iOS squircle（超椭圆）+ 双音符。

输出：
  android/res/mipmap-*/ic_launcher.png
  android/tools/icon-512.png
  ios/AkiMusic/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))

SS = 4  # 超采样倍率


def lerp(a, b, t):
    return a + (b - a) * t


def squircle_mask(size, inset=0.075, n=5.0):
    """iOS 风格超椭圆蒙版。"""
    s = size * SS
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    half = s / 2.0
    a = half * (1 - inset * 2)
    pts = []
    steps = 720
    for i in range(steps):
        t = 2 * math.pi * i / steps
        ct, st = math.cos(t), math.sin(t)
        x = half + a * math.copysign(abs(ct) ** (2 / n), ct)
        y = half + a * math.copysign(abs(st) ** (2 / n), st)
        pts.append((x, y))
    d.polygon(pts, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def gradient(size, top, bottom, diag=0.35):
    """斜向线性渐变。"""
    s = size * 2
    g = Image.new("RGB", (s, s))
    px = g.load()
    for y in range(s):
        for x in range(s):
            t = (y / s) * 0.78 + (x / s) * diag
            t = max(0.0, min(1.0, t))
            px[x, y] = (
                int(lerp(top[0], bottom[0], t)),
                int(lerp(top[1], bottom[1], t)),
                int(lerp(top[2], bottom[2], t)),
            )
    return g.resize((size, size), Image.LANCZOS)


def rotate_rect(cx, cy, w, h, deg):
    r = math.radians(deg)
    pts = []
    for dx, dy in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)):
        pts.append((cx + dx * math.cos(r) - dy * math.sin(r),
                    cy + dx * math.sin(r) + dy * math.cos(r)))
    return pts


def draw_note(layer, size):
    """双音符：两根符干 + 符尾横梁 + 两个符头。坐标 0..1 归一化。"""
    s = size * SS
    d = ImageDraw.Draw(layer)
    u = lambda v: v * s

    head_rx = u(0.086)
    head_ry = u(0.072)
    h1 = (u(0.350), u(0.700))
    h2 = (u(0.650), u(0.640))
    stem_w = u(0.038)
    top_y = u(0.300)
    beam_h = u(0.082)

    # 符干
    for hx, hy in (h1, h2):
        d.rounded_rectangle(
            [hx - stem_w / 2, top_y, hx + stem_w / 2, hy],
            radius=stem_w / 2, fill=(255, 255, 255, 255),
        )
    # 符尾横梁：水平压在两根符干顶端，边缘与符干齐平
    beam = rotate_rect(
        (h1[0] + h2[0]) / 2, top_y + beam_h / 2,
        math.dist(h1, h2) + stem_w, beam_h, 0.0,
    )
    d.polygon(beam, fill=(255, 255, 255, 255))
    # 符头
    for hx, hy in (h1, h2):
        d.ellipse([hx - head_rx, hy - head_ry, hx + head_rx, hy + head_ry], fill=(255, 255, 255, 255))


def make(size):
    base = gradient(size, (255, 110, 122), (162, 10, 38), diag=0.26).convert("RGBA")

    # 左上柔光，模拟玻璃高光
    glow = Image.new("L", (size, size), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-size * 0.40, -size * 0.60, size * 0.86, size * 0.58], fill=46)
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.16))
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    base = Image.composite(white, base, glow)

    note = Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))
    draw_note(note, size)
    note = note.resize((size, size), Image.LANCZOS)
    base = Image.alpha_composite(base, note)

    base.putalpha(squircle_mask(size))
    return base


def main():
    icon = make(1024)

    for folder, px in {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }.items():
        path = os.path.join(ROOT, "android", "res", folder, "ic_launcher.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        icon.resize((px, px), Image.LANCZOS).save(path)
        print("·", folder, px)

    icon.resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, "icon-512.png"))
    icon.save(os.path.join(HERE, "icon-1024.png"))

    ios_dir = os.path.join(ROOT, "ios", "AkiMusic", "Resources", "Assets.xcassets", "AppIcon.appiconset")
    if os.path.isdir(ios_dir):
        icon.save(os.path.join(ios_dir, "AppIcon-1024.png"))
        print("· iOS AppIcon-1024.png")
    print("完成")


if __name__ == "__main__":
    main()
