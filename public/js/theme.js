/* 动态取色：从专辑封面提取主色相，喂给 MD3 色彩系统 */

import { loadImage } from "./util.js";

const root = document.documentElement;
const cache = new Map();
let lastUrl = "";

function srgbToLinear(v) {
  v /= 255;
  return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
}

/** sRGB -> OKLCH */
export function rgbToOklch(r, g, b) {
  const R = srgbToLinear(r);
  const G = srgbToLinear(g);
  const B = srgbToLinear(b);

  const l = Math.cbrt(0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B);
  const m = Math.cbrt(0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B);
  const s = Math.cbrt(0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B);

  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;

  const C = Math.hypot(a, bb);
  let h = (Math.atan2(bb, a) * 180) / Math.PI;
  if (h < 0) h += 360;
  return { L, C, h };
}

async function extract(url) {
  const src = "/img?u=" + encodeURIComponent(url + (url.includes("?") ? "&" : "?") + "param=100y100");
  const img = await loadImage(src);
  const size = 40;
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  ctx.drawImage(img, 0, 0, size, size);
  const { data } = ctx.getImageData(0, 0, size, size);

  const bins = new Map();
  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3];
    if (a < 200) continue;
    const { L, C, h } = rgbToOklch(data[i], data[i + 1], data[i + 2]);
    if (L < 0.16 || L > 0.95) continue;
    if (C < 0.02) continue;
    const w = C * C * (1 - Math.abs(L - 0.56) * 1.05);
    if (w <= 0) continue;
    const bin = Math.round(h / 15) % 24;
    const cur = bins.get(bin) || { w: 0, x: 0, y: 0, C: 0, L: 0, n: 0 };
    const rad = (h * Math.PI) / 180;
    cur.w += w;
    cur.x += Math.cos(rad) * w;
    cur.y += Math.sin(rad) * w;
    cur.C += C * w;
    cur.L += L * w;
    cur.n += 1;
    bins.set(bin, cur);
  }

  let best = null;
  for (const v of bins.values()) if (!best || v.w > best.w) best = v;
  if (!best || best.w < 0.0006) return null;

  const hue = ((Math.atan2(best.y, best.x) * 180) / Math.PI + 360) % 360;
  const chroma = best.C / best.w;
  return { h: hue, c: chroma };
}

export const Theme = {
  current: { h: 342, c: 0.15 },

  apply({ h, c }, { persist = false } = {}) {
    this.current = {
      h: Math.round(h * 10) / 10,
      c: Math.min(0.19, Math.max(0.075, c)),
    };
    root.style.setProperty("--h", String(this.current.h));
    root.style.setProperty("--c", String(this.current.c));
    if (persist) localStorage.setItem("tide.hue", JSON.stringify(this.current));
  },

  reset() {
    this.apply({ h: 342, c: 0.15 });
  },

  /** 根据封面地址取色并应用，失败则静默保持 */
  async fromCover(url) {
    if (!url || url === lastUrl) return;
    lastUrl = url;
    if (cache.has(url)) {
      const cached = cache.get(url);
      if (cached) this.apply(cached);
      return;
    }
    try {
      const res = await extract(url);
      cache.set(url, res);
      if (res && url === lastUrl) this.apply(res);
    } catch {
      cache.set(url, null);
    }
  },
};
