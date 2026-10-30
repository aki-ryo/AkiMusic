/* 通用工具 */

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export const icon = (name, cls = "") =>
  `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"></use></svg>`;

export const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

export const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

/** 毫秒 -> m:ss */
export function fmtTime(ms) {
  if (!ms || ms < 0) return "0:00";
  const total = Math.round(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export function fmtCount(n) {
  n = Number(n) || 0;
  if (n >= 1e8) return (n / 1e8).toFixed(1).replace(/\.0$/, "") + "亿";
  if (n >= 1e4) return (n / 1e4).toFixed(1).replace(/\.0$/, "") + "万";
  return String(n);
}

export function debounce(fn, wait = 300) {
  let t;
  const wrapped = (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), wait);
  };
  wrapped.cancel = () => clearTimeout(t);
  return wrapped;
}

export function coverUrl(url, size = 300) {
  if (!url) return "";
  const clean = String(url).replace(/^http:/, "https:");
  if (clean.includes("?param=")) return clean;
  return clean + (clean.includes("?") ? "&" : "?") + `param=${size}y${size}`;
}

export function emptyState(title, sub, iconName = "note") {
  return `<div class="empty">
    <div class="empty-icon">${icon(iconName)}</div>
    <div class="empty-title">${esc(title)}</div>
    ${sub ? `<div class="empty-sub">${esc(sub)}</div>` : ""}
  </div>`;
}

export function skeletons(n = 6) {
  return Array.from({ length: n }, () =>
    `<div class="sk-card"><div class="sk"></div><div class="sk sk-line"></div><div class="sk sk-line" style="width:52%"></div></div>`
  ).join("");
}

let toastTimer = new WeakMap();
export function toast(message, opts = {}) {
  const host = $("#toasts");
  if (!host) return;
  const node = document.createElement("div");
  node.className = "toast";
  node.innerHTML = `${icon(opts.icon || "sparkle")}<span>${esc(message)}</span>`;
  host.appendChild(node);
  const life = opts.duration ?? 2600;
  setTimeout(() => {
    node.classList.add("is-out");
    setTimeout(() => node.remove(), 320);
  }, life);
  // 最多同时三条
  while (host.children.length > 3) host.firstElementChild.remove();
}

/** Material 水波纹 */
export function initRipple() {
  document.addEventListener("pointerdown", (e) => {
    const host = e.target.closest(".ripple");
    if (!host) return;
    const rect = host.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height) * 2.1;
    const wave = document.createElement("span");
    wave.className = "ripple-wave";
    wave.style.width = wave.style.height = `${size}px`;
    wave.style.left = `${e.clientX - rect.left - size / 2}px`;
    wave.style.top = `${e.clientY - rect.top - size / 2}px`;
    host.appendChild(wave);
    setTimeout(() => wave.remove(), 640);
  });
}

/** 把图片预加载成 Image 对象 */
export function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

export function setPlayingIcon(el, playing) {
  if (!el) return;
  el.innerHTML = icon(playing ? "pause" : "play");
}
