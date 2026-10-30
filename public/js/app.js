/* 潮汐 TIDE · 应用主逻辑 */

import { $, $$, icon, esc, fmtTime, clamp, toast, initRipple, debounce } from "./util.js";
import { Api } from "./api.js";
import { Store, bootTheme } from "./store.js";
import { Theme } from "./theme.js";
import { player } from "./player.js";
import * as Views from "./views.js";

/* ---------------- 路由 ---------------- */

const TITLES = {
  home: "发现",
  search: "搜索",
  charts: "排行榜",
  liked: "我喜欢的音乐",
  recent: "最近播放",
  playlists: "我的歌单",
  playlist: "歌单",
  album: "专辑",
  artist: "歌手",
};

function parseRoute() {
  const raw = location.hash || "#/home";
  const [path, qs] = raw.replace(/^#/, "").split("?");
  const parts = path.split("/").filter(Boolean);
  return {
    name: parts[0] || "home",
    id: parts[1] || "",
    query: new URLSearchParams(qs || ""),
  };
}

function navigate(hash, { replace = false } = {}) {
  if (location.hash === hash) {
    render();
    return;
  }
  if (replace) history.replaceState(null, "", hash);
  else location.hash = hash;
  if (replace) render();
}

/* ---------------- 元素 ---------------- */

const viewEl = $("#view");
const scrollEl = $("#scroll");
const np = $("#np");
const queueSheet = $("#queueSheet");
const scrim = $("#sheetScrim");
const searchInput = $("#searchInput");

let renderSeq = 0;
let loaderEl = null;

function setLoader(on) {
  if (!loaderEl) {
    loaderEl = document.createElement("div");
    loaderEl.className = "loader";
    document.body.appendChild(loaderEl);
  }
  loaderEl.classList.toggle("is-on", on);
}

/* ---------------- 渲染 ---------------- */

async function render({ resetScroll = true } = {}) {
  const route = parseRoute();
  const token = ++renderSeq;

  setActiveNav(route);
  setTitle(TITLES[route.name] || "潮汐");
  syncSearchInput(route);
  setLoader(true);

  let view;
  try {
    switch (route.name) {
      case "home":
        view = await Views.HomeView();
        break;
      case "charts":
        view = await Views.ChartsView();
        break;
      case "playlist":
        view = await Views.PlaylistView(route.id);
        break;
      case "album":
        view = await Views.AlbumView(route.id);
        break;
      case "artist":
        view = await Views.ArtistView(route.id);
        break;
      case "liked":
        view = await Views.LibraryView("liked");
        break;
      case "recent":
        view = await Views.LibraryView("recent");
        break;
      case "playlists":
        view = Views.SavedPlaylistsView();
        break;
      case "search":
        view = await Views.SearchView(route.query.get("q")?.trim() || "");
        break;
      default:
        view = { html: Views.SearchLanding() };
    }

    if (token !== renderSeq) return;
    viewEl.innerHTML = view.html;
    viewEl.classList.remove("view-enter");
    void viewEl.offsetWidth;
    viewEl.classList.add("view-enter");
    view.mount?.();

    const detailTitle = viewEl.querySelector(".detail-title");
    if (detailTitle) setTitle(detailTitle.textContent.trim());
    if (resetScroll) scrollEl.scrollTop = 0;
  } catch (err) {
    if (token !== renderSeq) return;
    viewEl.innerHTML = errorView(err);
    console.error(err);
  } finally {
    if (token === renderSeq) setLoader(false);
  }
}

function errorView(err) {
  const msg = String(err?.message || err);
  const offline = /Failed to fetch|NetworkError|load failed/i.test(msg);
  return `<div class="fatal">
    <div class="empty-icon" style="margin:0 auto">${icon("search")}</div>
    <h2>${offline ? "连不上本地服务" : "这一页加载失败"}</h2>
    <p>${
      offline
        ? "AkiMusic 需要本地服务来访问网易云音乐接口。在项目目录运行下面的命令，然后刷新页面。"
        : esc(msg || "未知错误")
    }</p>
    ${offline ? "<code>python server.py</code>" : ""}
    <p style="margin-top:16px"><button class="btn btn--tonal ripple" onclick="location.reload()">重新加载</button></p>
  </div>`;
}

function setTitle(text) {
  $("#topbarTitle").textContent = text;
  document.title = `${text} · AkiMusic`;
}

function setActiveNav(route) {
  $$(".nav-item").forEach((b) => {
    const r = (b.dataset.route || "").replace("#/", "");
    b.classList.toggle("is-active", r === route.name);
  });
  $$(".pl-item").forEach((b) => {
    b.classList.toggle("is-active", b.dataset.route === `#/playlist/${route.id}`);
  });
}

function syncSearchInput(route) {
  if (document.activeElement === searchInput) return;
  const q = route.name === "search" ? route.query.get("q") || "" : "";
  if (searchInput.value !== q) searchInput.value = q;
  $("#searchClear").hidden = !searchInput.value;
}

/* ---------------- 侧边栏歌单 ---------------- */

function renderSidebarPlaylists() {
  const list = Store.get("savedPlaylists");
  const box = $("#navPlaylists");
  if (!list.length) {
    box.innerHTML = `<div style="padding:2px 12px;font-size:12px;line-height:1.6;color:var(--on-surface-faint)">收藏的歌单会出现在这里</div>`;
    return;
  }
  box.innerHTML = list
    .map(
      (p) => `<button class="pl-item ripple" data-route="#/playlist/${p.id}" title="${esc(p.name)}">
        <img src="${p.cover}" alt="" loading="lazy"/><span>${esc(p.name)}</span>
      </button>`
    )
    .join("");
  setActiveNav(parseRoute());
}

/* ---------------- 播放动作 ---------------- */

async function playPlaylist(id, { shuffle = false } = {}) {
  try {
    const pl = await Api.playlist(id);
    if (!pl?.tracks.length) return toast("这个歌单暂时没有可播放的曲目");
    const list = shuffle ? shuffled(pl.tracks) : pl.tracks;
    await player.setQueue(list, 0, { label: pl.name });
    toast(shuffle ? `随机播放「${pl.name}」` : `正在播放「${pl.name}」`, { icon: "play" });
  } catch (e) {
    toast("歌单加载失败，换个试试");
  }
}

async function playAlbum(id, { shuffle = false } = {}) {
  try {
    const al = await Api.album(id);
    if (!al?.tracks.length) return toast("这张专辑暂时没有可播放的曲目");
    const list = shuffle ? shuffled(al.tracks) : al.tracks;
    await player.setQueue(list, 0, { label: al.name });
    toast(`正在播放「${al.name}」`, { icon: "play" });
  } catch {
    toast("专辑加载失败");
  }
}

async function playArtist(id) {
  try {
    const songs = await Api.artistTop(id);
    if (!songs.length) return toast("这位歌手暂时没有可播放的曲目");
    await player.setQueue(songs, 0);
    toast("正在播放热门单曲", { icon: "play" });
  } catch {
    toast("加载失败");
  }
}

function shuffled(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/* ---------------- 事件委托 ---------------- */

document.addEventListener("click", async (e) => {
  const el = e.target.closest(
    "[data-quick-playlist],[data-quick-album],[data-play-artist],[data-search-word],[data-save-playlist],[data-scroll],[data-like],[data-queue-next],[data-list][data-idx],[data-route]"
  );
  if (!el) return;

  if (el.dataset.quickPlaylist) {
    e.preventDefault();
    return playPlaylist(el.dataset.quickPlaylist, { shuffle: el.dataset.shuffle === "1" });
  }
  if (el.dataset.quickAlbum) {
    e.preventDefault();
    return playAlbum(el.dataset.quickAlbum, { shuffle: el.dataset.shuffle === "1" });
  }
  if (el.dataset.playArtist) {
    e.preventDefault();
    return playArtist(el.dataset.playArtist);
  }
  if (el.dataset.searchWord) {
    e.preventDefault();
    searchInput.value = el.dataset.searchWord;
    return navigate(`#/search?q=${encodeURIComponent(el.dataset.searchWord)}`);
  }
  if (el.dataset.savePlaylist) {
    e.preventDefault();
    try {
      const pl = await Api.playlist(el.dataset.savePlaylist);
      const saved = Store.toggleSaved(pl);
      el.dataset.saved = String(saved);
      el.innerHTML = `${icon(saved ? "check" : "plus")}${saved ? "已收藏" : "收藏"}`;
      renderSidebarPlaylists();
      toast(saved ? `已收藏「${pl.name}」` : "已取消收藏", { icon: saved ? "check" : "close" });
    } catch {
      toast("收藏失败");
    }
    return;
  }
  if (el.dataset.scroll) {
    e.preventDefault();
    const shelf = el.closest(".shelf");
    shelf?.querySelector(".shelf-scroll")?.scrollBy({ left: Number(el.dataset.scroll) * 620, behavior: "smooth" });
    return;
  }
  if (el.dataset.like) {
    e.preventDefault();
    e.stopPropagation();
    const id = Number(el.dataset.like);
    const track = Views.findTrack(id) || (player.current?.id === id ? player.current : null);
    if (!track) return;
    const liked = Store.toggleLike(track);
    $$(`[data-like="${id}"]`).forEach((b) => {
      b.classList.toggle("is-liked", liked);
      b.innerHTML = icon(liked ? "heart-fill" : "heart");
      b.closest(".trk")?.classList.toggle("is-liked", liked);
    });
    syncLikeButtons();
    toast(liked ? "已加入我喜欢的音乐" : "已取消喜欢", { icon: "heart", duration: 1700 });
    return;
  }
  if (el.dataset.queueNext !== undefined) {
    e.preventDefault();
    e.stopPropagation();
    const list = Views.getList(el.dataset.list);
    const track = list[Number(el.dataset.queueNext)];
    if (!track) return;
    const at = player.index >= 0 ? player.index + 1 : 0;
    player.queue.splice(at, 0, track);
    player.emit("queue");
    toast("已设为下一首播放", { icon: "queue", duration: 1700 });
    return;
  }
  if (el.dataset.list && el.dataset.idx !== undefined) {
    const list = Views.getList(el.dataset.list);
    const idx = Number(el.dataset.idx);
    const track = list[idx];
    if (!track) return;
    if (player.current?.id === track.id) return player.toggle();
    await player.setQueue(list, idx);
    return;
  }
  if (el.dataset.route) {
    e.preventDefault();
    return navigate(el.dataset.route);
  }
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Enter" || e.key === " ") {
    const row = document.activeElement?.closest?.("[data-list][data-idx]");
    if (row && !["INPUT", "TEXTAREA"].includes(document.activeElement.tagName)) {
      e.preventDefault();
      row.click();
    }
  }
});

/* ---------------- 播放器 UI ---------------- */

const on = (sel, ev, fn) => $(sel)?.addEventListener(ev, fn);

function bindSlider(elm, onScrub, getRatio) {
  if (!elm) return null;
  const fill = elm.querySelector(".slider-fill");
  const thumb = elm.querySelector(".slider-thumb");
  let dragging = false;
  let last = 0;

  const set = (r) => {
    last = r;
    if (fill) fill.style.width = `${r * 100}%`;
    if (thumb) thumb.style.left = `${r * 100}%`;
  };
  const ratioOf = (ev) => {
    const rect = elm.getBoundingClientRect();
    return clamp((ev.clientX - rect.left) / rect.width, 0, 1);
  };

  elm.addEventListener("pointerdown", (ev) => {
    dragging = true;
    elm.classList.add("is-drag");
    elm.setPointerCapture?.(ev.pointerId);
    const r = ratioOf(ev);
    set(r);
    onScrub(r, false);
  });
  elm.addEventListener("pointermove", (ev) => {
    if (!dragging) return;
    const r = ratioOf(ev);
    set(r);
    onScrub(r, false);
  });
  elm.addEventListener("pointerup", (ev) => {
    if (!dragging) return;
    dragging = false;
    elm.classList.remove("is-drag");
    const r = ratioOf(ev);
    set(r);
    onScrub(r, true);
  });
  elm.addEventListener("pointercancel", () => {
    dragging = false;
    elm.classList.remove("is-drag");
  });
  elm._sync = () => {
    if (dragging) return;
    const r = clamp(getRatio() || 0, 0, 1);
    if (Math.abs(r - last) > 0.0005) set(r);
  };
  return elm;
}

const progressSlider = bindSlider(
  $("#pSlider"),
  (r, final) => {
    $("#pCur").textContent = fmtTime(r * player.duration);
    if (final) player.seekMs(r * player.duration);
  },
  () => (player.duration ? player.position / player.duration : 0)
);

const npSlider = bindSlider(
  $("#npSlider"),
  (r, final) => {
    $("#npCur").textContent = fmtTime(r * player.duration);
    if (final) player.seekMs(r * player.duration);
  },
  () => (player.duration ? player.position / player.duration : 0)
);

const volSlider = bindSlider(
  $("#vSlider"),
  (r) => player.setVolume(r),
  () => player.volume
);

function syncLikeButtons() {
  const liked = player.current ? Store.isLiked(player.current.id) : false;
  ["#pLike", "#npLike"].forEach((sel) => {
    const b = $(sel);
    if (!b) return;
    b.classList.toggle("is-liked", liked);
    b.innerHTML = icon(liked ? "heart-fill" : "heart");
  });
}

function syncModeButtons() {
  ["#pShuffle", "#npShuffle"].forEach((sel) => $(sel)?.classList.toggle("is-on", player.shuffle));
  ["#pRepeat", "#npRepeat"].forEach((sel) => {
    const b = $(sel);
    if (!b) return;
    b.innerHTML = icon(player.repeat === "one" ? "repeat-one" : "repeat");
    b.classList.toggle("is-on", player.repeat !== "off");
    b.title = player.repeat === "off" ? "循环：关闭" : player.repeat === "all" ? "循环：列表" : "循环：单曲";
  });
}

function syncVolume() {
  if (volSlider?._sync) volSlider._sync();
  const b = $("#pMute");
  if (b) b.innerHTML = icon(player.volume === 0 ? "volume-off" : "volume");
}

function syncTrackUI() {
  const t = player.current;
  if (!t) return;
  $("#pTitle").textContent = t.name;
  $("#pArtist").textContent = t.artistText;
  const art = $("#pArt");
  art.src = t.cover;
  art.hidden = false;
  const ph = $("#player .player-art-ph");
  if (ph) ph.style.display = "none";

  $("#npTitle").textContent = t.name;
  $("#npArtist").textContent = t.artistText;
  $("#npAlbum").textContent = t.album ? `来自专辑《${t.album}》` : "";
  $("#npArt").src = t.cover;
  $("#npBg").src = t.cover;

  syncLikeButtons();
  syncModeButtons();
  $("#npCur").textContent = "0:00";
  $("#npDur").textContent = fmtTime(t.duration);

  // 正在播放的行刷新
  $$(".trk").forEach((row) => {
    const list = Views.getList(row.dataset.list);
    const tr = list[Number(row.dataset.idx)];
    const isCur = tr && tr.id === t.id;
    row.classList.toggle("is-playing", !!isCur);
  });
}

function syncPlayUI() {
  const playing = player.playing;
  ["#pPlay", "#npPlay", "#mPlay"].forEach((sel) => {
    const b = $(sel);
    if (b) b.innerHTML = icon(playing ? "pause" : "play");
    if (b) b.title = playing ? "暂停" : "播放";
  });
  $("#player .player-art")?.classList.toggle("is-playing", playing);
  np.classList.toggle("is-playing", playing);
  $$(".trk .eq").forEach((eq) => eq.classList.toggle("is-paused", !playing));
}

function syncTimeUI() {
  const pos = player.position;
  const dur = player.duration;
  $("#pCur").textContent = fmtTime(pos);
  $("#pDur").textContent = fmtTime(dur);
  $("#npCur").textContent = fmtTime(pos);
  $("#npDur").textContent = fmtTime(dur);
  progressSlider?._sync?.();
  npSlider?._sync?.();
  updateLyricHighlight(pos);
}

/* ---------------- 歌词 ---------------- */

let lyricLines = [];
let activeLyric = -1;

function renderLyrics(lines) {
  lyricLines = lines;
  activeLyric = -1;
  const box = $("#npLyrics");
  if (!lines.length) {
    box.innerHTML = `<div class="lyr-empty">这首歌暂时没有歌词。<br/>不过旋律本身，也是语言。</div>`;
    return;
  }
  box.innerHTML = lines
    .map(
      (l, i) =>
        `<div class="lyr-line ${l.text ? "" : "is-instr"}" data-lyr="${i}">${esc(l.text || "♪")}</div>`
    )
    .join("");
  box.scrollTop = 0;
}

function updateLyricHighlight(pos) {
  if (!lyricLines.length || !np.classList.contains("is-open")) return;
  let idx = -1;
  for (let i = 0; i < lyricLines.length; i++) {
    if (lyricLines[i].t <= pos + 60) idx = i;
    else break;
  }
  if (idx === activeLyric) return;
  const box = $("#npLyrics");
  const prev = box.querySelector(".lyr-line.is-active");
  if (prev) prev.classList.remove("is-active");
  const line = box.querySelector(`[data-lyr="${idx}"]`);
  if (!line) return;
  line.classList.add("is-active");
  activeLyric = idx;

  const target = box.scrollTop + line.getBoundingClientRect().top - box.getBoundingClientRect().top - box.clientHeight / 2 + line.offsetHeight / 2;
  box.scrollTo({ top: Math.max(0, target), behavior: "smooth" });
}

$("#npLyrics")?.addEventListener("click", (e) => {
  const line = e.target.closest("[data-lyr]");
  if (!line) return;
  const i = Number(line.dataset.lyr);
  if (lyricLines[i]) player.seekMs(lyricLines[i].t);
});

/* ---------------- 队列 ---------------- */

function renderQueue() {
  const items = player.queue
    .map((t, i) => {
      const cur = i === player.index;
      return `<button class="q-item ${cur ? "is-playing" : ""}" data-qi="${i}">
        <img src="${t.cover}" alt="" loading="lazy"/>
        <div class="q-item-body">
          <div class="q-item-title">${esc(t.name)}</div>
          <div class="q-item-sub">${esc(t.artistText)}</div>
        </div>
        ${cur ? `<span class="eq ${player.playing ? "" : "is-paused"}"><i></i><i></i><i></i></span>` : ""}
      </button>`;
    })
    .join("");

  const empty = `<div class="lyr-empty">队列还是空的。</div>`;
  $("#npQueue").innerHTML = items || empty;
  $("#qsList").innerHTML = items || empty;
  $("#qsCount").textContent = `${player.queue.length} 首`;
  $("#npSource").textContent = player.label ? `网易云音乐 · ${player.label}` : "网易云音乐";
}

document.addEventListener("click", (e) => {
  const item = e.target.closest("[data-qi]");
  if (!item) return;
  player.playIndex(Number(item.dataset.qi));
});

/* ---------------- 正在播放面板 ---------------- */

function openNP() {
  if (!player.current) return toast("先挑一首歌吧", { icon: "note" });
  np.classList.add("is-open");
  np.setAttribute("aria-hidden", "false");
  setTimeout(() => {
    activeLyric = -1;
    updateLyricHighlight(player.position);
  }, 420);
}

function closeNP() {
  np.classList.remove("is-open");
  np.setAttribute("aria-hidden", "true");
}

function openQueue() {
  renderQueue();
  queueSheet.classList.add("is-open");
  queueSheet.setAttribute("aria-hidden", "false");
  scrim.hidden = false;
}

function closeQueue() {
  queueSheet.classList.remove("is-open");
  queueSheet.setAttribute("aria-hidden", "true");
  scrim.hidden = true;
}

/* ---------------- 添加歌单对话框 ---------------- */

function openAddPlaylist() {
  const wrap = document.createElement("div");
  wrap.className = "dlg-scrim";
  wrap.innerHTML = `<div class="dlg" role="dialog" aria-modal="true">
    <h3 class="dlg-title">添加一个歌单</h3>
    <p class="dlg-sub">粘贴网易云歌单链接或 ID，它会留在侧边栏。</p>
    <label class="field"><input id="plInput" placeholder="3778678 或 https://music.163.com/playlist?id=..." /></label>
    <div class="dlg-actions">
      <button class="btn btn--text ripple" data-cancel>取消</button>
      <button class="btn btn--filled ripple" data-ok>添加</button>
    </div>
  </div>`;
  document.body.appendChild(wrap);
  const input = wrap.querySelector("#plInput");
  setTimeout(() => input.focus(), 60);

  const close = () => wrap.remove();
  const submit = async () => {
    const raw = input.value.trim();
    const id = (raw.match(/(\d{5,})/) || [])[1];
    if (!id) return toast("看起来不是一个歌单链接");
    const btn = wrap.querySelector("[data-ok]");
    btn.textContent = "添加中…";
    btn.disabled = true;
    try {
      const pl = await Api.playlist(id);
      if (!Store.isSaved(pl.id)) Store.toggleSaved(pl);
      renderSidebarPlaylists();
      close();
      toast(`已添加「${pl.name}」`, { icon: "check" });
    } catch {
      btn.textContent = "添加";
      btn.disabled = false;
      toast("歌单不存在或加载失败");
    }
  };

  wrap.addEventListener("click", (e) => {
    if (e.target === wrap || e.target.closest("[data-cancel]")) close();
    if (e.target.closest("[data-ok]")) submit();
  });
  wrap.addEventListener("keydown", (e) => {
    if (e.key === "Enter") submit();
    if (e.key === "Escape") close();
  });
}

/* ---------------- 绑定 ---------------- */

function bindControls() {
  on("#pPlay", "click", () => player.toggle());
  on("#npPlay", "click", () => player.toggle());
  on("#mPlay", "click", () => player.toggle());
  on("#pNext", "click", () => player.next());
  on("#npNext", "click", () => player.next());
  on("#pPrev", "click", () => player.prev());
  on("#npPrev", "click", () => player.prev());
  on("#npOpen", "click", openNP);
  on("#npClose", "click", closeNP);
  on("#pLyrics", "click", openNP);
  on("#pQueue", "click", openQueue);
  on("#npQueueBtn", "click", openQueue);
  on("#qsClose", "click", closeQueue);
  scrim.addEventListener("click", closeQueue);

  ["#pShuffle", "#npShuffle"].forEach((sel) => on(sel, "click", () => {
    player.setShuffle(!player.shuffle);
    toast(player.shuffle ? "随机播放：开" : "随机播放：关", { icon: "shuffle", duration: 1400 });
  }));
  ["#pRepeat", "#npRepeat"].forEach((sel) => on(sel, "click", () => {
    const mode = player.cycleRepeat();
    toast(mode === "off" ? "循环：关" : mode === "all" ? "列表循环" : "单曲循环", { icon: "repeat", duration: 1400 });
  }));
  ["#pLike", "#npLike"].forEach((sel) =>
    on(sel, "click", () => {
      if (!player.current) return toast("还没有正在播放的歌", { icon: "note" });
      const liked = Store.toggleLike(player.current);
      syncLikeButtons();
      $$(`[data-like="${player.current.id}"]`).forEach((b) => {
        b.classList.toggle("is-liked", liked);
        b.innerHTML = icon(liked ? "heart-fill" : "heart");
      });
      toast(liked ? "已加入我喜欢的音乐" : "已取消喜欢", { icon: "heart", duration: 1600 });
    })
  );
  on("#pMute", "click", () => player.toggleMute());

  on("#backBtn", "click", () => history.back());
  on("#fwdBtn", "click", () => history.forward());
  on("#addPlaylist", "click", openAddPlaylist);
  on("#menuBtn", "click", () => $("#sidebar").classList.toggle("is-open"));

  // 滚动：顶栏吸附 + 标签栏收缩/展开（iOS 26 起的行为，iOS 27 更顺）
  const topbarEl = $(".topbar");
  const tabbarEl = $("#tabbar");
  let lastY = 0;
  scrollEl.addEventListener(
    "scroll",
    () => {
      const y = scrollEl.scrollTop;
      topbarEl.classList.toggle("is-stuck", y > 24);
      if (tabbarEl) {
        if (y > lastY + 8 && y > 64) tabbarEl.classList.add("is-compact");
        else if (y < lastY - 8) tabbarEl.classList.remove("is-compact");
      }
      lastY = y;
    },
    { passive: true }
  );

  // 液态玻璃透明度（iOS 27 设置里的那个滑块）
  const clarity = $("#clarity");
  if (clarity) {
    const saved = Number(Store.get("clarity") ?? 72);
    const apply = (v) => {
      document.documentElement.style.setProperty("--glass-clarity", String(v / 100));
      clarity.style.setProperty("--clarity-pct", v + "%");
    };
    clarity.value = String(saved);
    apply(saved);
    clarity.addEventListener("input", () => {
      const v = Number(clarity.value);
      apply(v);
      Store.set("clarity", v);
    });
  }

  on("#themeBtn", "click", () => {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    Store.set("theme", next);
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute("content", next === "dark" ? "#000000" : "#f2f2f7");
    $("#themeBtn").innerHTML = icon(next === "dark" ? "sun" : "moon");
  });

  // 搜素
  $("#searchForm").addEventListener("submit", (e) => {
    e.preventDefault();
    const q = searchInput.value.trim();
    if (!q) return;
    navigate(`#/search?q=${encodeURIComponent(q)}`);
    searchInput.blur();
  });
  searchInput.addEventListener("input", () => {
    $("#searchClear").hidden = !searchInput.value;
    const route = parseRoute();
    if (route.name === "search") liveSearch();
  });
  $("#searchClear").addEventListener("click", () => {
    searchInput.value = "";
    $("#searchClear").hidden = true;
    searchInput.focus();
    const route = parseRoute();
    if (route.name === "search") navigate("#/search", { replace: true });
  });

  // 侧边栏点击后移动端收起
  $("#sidebar").addEventListener("click", (e) => {
    if (e.target.closest("[data-route]") && window.innerWidth <= 900) {
      $("#sidebar").classList.remove("is-open");
    }
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (!scrim.hidden) return closeQueue();
      if (np.classList.contains("is-open")) return closeNP();
    }
    if (e.code === "Space" && !["INPUT", "TEXTAREA"].includes(document.activeElement.tagName)) {
      e.preventDefault();
      player.toggle();
    }
    if (e.code === "ArrowRight" && e.altKey) player.next();
    if (e.code === "ArrowLeft" && e.altKey) player.prev();
  });
}

const liveSearch = debounce(() => {
  const q = searchInput.value.trim();
  if (parseRoute().name !== "search") return;
  navigate(`#/search${q ? "?q=" + encodeURIComponent(q) : ""}`, { replace: true });
}, 520);

/* ---------------- 播放器事件 ---------------- */

player.addEventListener("track", () => {
  syncTrackUI();
  renderQueue();
  document.title = player.current ? `${player.current.name} · ${player.current.artistText}` : "AkiMusic";
});
player.addEventListener("state", syncPlayUI);
player.addEventListener("time", syncTimeUI);
player.addEventListener("duration", syncTimeUI);
player.addEventListener("volume", syncVolume);
player.addEventListener("mode", syncModeButtons);
player.addEventListener("lyric", (e) => renderLyrics(e.detail.lines || []));
player.addEventListener("queue", () => {
  renderQueue();
  syncPlayUI();
});

/* ---------------- 启动 ---------------- */

function boot() {
  bootTheme();
  initRipple();
  bindControls();
  $("#themeBtn").innerHTML = icon(document.documentElement.dataset.theme === "dark" ? "sun" : "moon");
  renderSidebarPlaylists();
  syncVolume();
  syncModeButtons();
  syncLikeButtons();

  if (!location.hash) history.replaceState(null, "", "#/home");
  window.addEventListener("hashchange", () => render());
  render();

  // 调试口子：便于排查播放状态
  window.__tide = { player, Store, Theme, Api, Views };
}

boot();
