#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 AkiMusic 的启动图标（渐变 + 声波），输出各密度 mipmap。"""

import math
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "res")

C1 = (255, 77, 141)   # 珊瑚粉
C2 = (124, 92, 255)   # 紫罗兰


def lerp(a, b, t):
    return a + (b - a) * t


def make_icon(size):
    px = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size
            v = (y + 0.5) / size

            # 圆角方形遮罩（超椭圆，接近 squircle）
            dx = abs(u - 0.5) * 2
            dy = abs(v - 0.5) * 2
            d = (dx ** 4.2 + dy ** 4.2) ** (1 / 4.2)
            if d > 1.0:
                continue
            edge = min(1.0, (1.0 - d) * size * 0.9)
            alpha = int(255 * edge)

            # 渐变
            t = (u * 0.55 + v * 0.45)
            r = lerp(C1[0], C2[0], t)
            g = lerp(C1[1], C2[1], t)
            b = lerp(C1[2], C2[2], t)

            # 左上角柔光
            glow = max(0.0, 1.0 - math.hypot(u - 0.12, v - 0.05) * 1.55)
            r = min(255, r + glow * 70)
            g = min(255, g + glow * 60)
            b = min(255, b + glow * 55)

            # 两条声波
            for (amp, phase, thick, alpha_w) in ((0.085, 0.0, 0.055, 1.0), (0.058, 0.55, 0.026, 0.4)):
                cy = 0.54 + amp * math.sin((u * 1.9 - 0.12 + phase) * math.pi * 2)
                dist_px = abs(v - cy) * size
                thick_px = thick * size
                if dist_px < thick_px:
                    k = alpha_w * min(1.0, (thick_px - dist_px) / 1.4)
                    r = lerp(r, 255, k)
                    g = lerp(g, 255, k)
                    b = lerp(b, 255, k)

            i = (y * size + x) * 4
            px[i] = int(r)
            px[i + 1] = int(g)
            px[i + 2] = int(b)
            px[i + 3] = alpha
    return px


def write_png(path, size, px):
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        raw.extend(px[y * size * 4:(y + 1) * size * 4])

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as fh:
        fh.write(png)


def main():
    targets = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in targets.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        px = make_icon(size)
        write_png(os.path.join(out_dir, "ic_launcher.png"), size, px)
        print("wrote", folder, size)

    # 顺手给 README / Release 用一张大图
    big = make_icon(512)
    write_png(os.path.join(HERE, "icon-512.png"), 512, big)
    print("wrote icon-512.png")


if __name__ == "__main__":
    main()
