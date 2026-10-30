/* 视图模板（返回 HTML 字符串） */

import { Api } from "./api.js";
import { Store } from "./store.js";
import { player } from "./player.js";
import { icon, esc, fmtTime, fmtCount, coverUrl, emptyState } from "./util.js";
import { Theme } from "./theme.js";

/* ---------------- 列表注册表 ----------------
   每张列表注册一次，行上只存 key + index，避免超长 data 属性 */
let seq = 0;
const lists = new Map();
const trackIndex = new Map();

export function registerList(tracks) {
  const key = "L" + ++seq;
  lists.set(key, tracks);
  tracks.forEach((t) => {
    if (t && t.id != null) trackIndex.set(t.id, t);
  });
  if (trackIndex.size > 4000) trackIndex.clear();
  if (lists.size > 160) {
    const first = lists.keys().next().value;
    if (first !== key) lists.delete(first);
  }
  return key;
}
export function getList(key) {
  return lists.get(key) || [];
}

export function findTrack(id) {
  return trackIndex.get(Number(id)) || trackIndex.get(id) || null;
}

const likedSet = () => new Set(Store.get("liked").map((t) => t.id));

/* ---------------- 小部件 ---------------- */

export function trackRow(track, i, listKey) {
  const playing = player.current?.id === track.id;
  const liked = likedSet().has(track.id);
  return `<div class="trk ${playing ? "is-playing" : ""} ${liked ? "is-liked" : ""}" data-list="${listKey}" data-idx="${i}" role="button" tabindex="0">
    <div class="trk-idx">
      <span class="num">${String(i + 1).padStart(2, "0")}</span>
      <span class="pl">${icon("play")}</span>
      <span class="eq ${player.playing ? "" : "is-paused"}"><i></i><i></i><i></i></span>
    </div>
    <div class="trk-art"><img src="${track.cover || coverUrl("", 100)}" alt="" loading="lazy"/></div>
    <div class="trk-main">
      <div class="trk-title">${esc(track.name)}</div>
      <div class="trk-sub">${esc(track.artistText)}${track.vip ? '<span class="pill-vip">VIP</span>' : ""}</div>
    </div>
    <div class="trk-album">${esc(track.album)}</div>
    <div class="trk-time">${fmtTime(track.duration)}</div>
    <div class="trk-actions">
      <button class="icon-btn like-btn ripple ${liked ? "is-liked" : ""}" data-like="${track.id}" title="喜欢">${icon(liked ? "heart-fill" : "heart")}</button>
      <button class="icon-btn ripple" data-queue-next="${i}" data-list="${listKey}" title="下一首播放">${icon("plus")}</button>
    </div>
  </div>`;
}

export function trackList(tracks, { empty = "这里还没有歌曲" } = {}) {
  if (!tracks.length) return emptyState(empty, "换个关键词，或者去排行榜逛逛。");
  const key = registerList(tracks);
  return `<div class="tracklist">${tracks.map((t, i) => trackRow(t, i, key)).join("")}</div>`;
}

function songCard(track, i, key) {
  const playing = player.current?.id === track.id;
  return `<div class="card ripple" role="button" tabindex="0" data-list="${key}" data-idx="${i}">
    <div class="card-art">
      <img src="${track.cover}" alt="" loading="lazy"/>
      ${track.vip ? '<span class="card-badge is-vip">VIP</span>' : ""}
      <span class="card-play">${icon(playing ? "pause" : "play")}</span>
    </div>
    <div class="card-title">${esc(track.name)}</div>
    <div class="card-sub">${esc(track.artistText)}</div>
  </div>`;
}

function playlistCard(p) {
  return `<div class="card ripple" role="button" tabindex="0" data-route="#/playlist/${p.id}">
    <div class="card-art">
      <img src="${p.cover}" alt="" loading="lazy"/>
      ${p.playCount ? `<span class="card-badge">${icon("play")}${fmtCount(p.playCount)}</span>` : ""}
      <button class="card-play" data-quick-playlist="${p.id}" title="播放">${icon("play")}</button>
    </div>
    <div class="card-title">${esc(p.name)}</div>
    <div class="card-sub">${esc(p.creator || p.description || "")}</div>
  </div>`;
}

function albumCard(a) {
  return `<div class="card ripple" role="button" tabindex="0" data-route="#/album/${a.id}">
    <div class="card-art">
      <img src="${a.cover}" alt="" loading="lazy"/>
      <button class="card-play" data-quick-album="${a.id}" title="播放">${icon("play")}</button>
    </div>
    <div class="card-title">${esc(a.name)}</div>
    <div class="card-sub">${esc(a.artist || "")}</div>
  </div>`;
}

function artistCard(a) {
  return `<div class="card card--artist ripple" role="button" tabindex="0" data-route="#/artist/${a.id}">
    <div class="card-art"><img src="${a.cover}" alt="" loading="lazy"/></div>
    <div class="card-title">${esc(a.name)}</div>
    <div class="card-sub">${a.alias ? esc(a.alias) : a.musicSize + " 首单曲"}</div>
  </div>`;
}

export function chartCard(c) {
  return `<div class="chart-card ripple" role="button" tabindex="0" data-route="#/playlist/${c.id}">
    <img src="${c.cover}" alt="" loading="lazy"/>
    <div class="chart-card-body">
      <div class="chart-card-name">${esc(c.name)}</div>
      <div class="chart-card-meta">${esc(c.updateFrequency || "")}${c.trackCount ? " · " + c.trackCount + " 首" : ""}</div>
      <span class="chart-card-tag">${icon("chart")} 查看榜单</span>
    </div>
  </div>`;
}

function shelf(title, sub, body, { cardWidth = 186, iconName = "" } = {}) {
  return `<section class="shelf">
    <div class="shelf-head">
      <div class="shelf-titles">
        <h2 class="shelf-title">${iconName ? icon(iconName) : ""}${esc(title)}</h2>
        ${sub ? `<p class="shelf-sub">${esc(sub)}</p>` : ""}
      </div>
      <div class="shelf-nav">
        <button class="icon-btn ripple" data-scroll="-1" title="向左">${icon("chevron-left")}</button>
        <button class="icon-btn ripple" data-scroll="1" title="向右">${icon("chevron-right")}</button>
      </div>
    </div>
    <div class="shelf-scroll" style="--card-w:${cardWidth}px">${body}</div>
  </section>`;
}

/* ---------------- 首页 ---------------- */

export async function HomeView() {
  const [charts, news, recs] = await Promise.all([
    Api.toplists().catch(() => []),
    Api.newSongs(14).catch(() => []),
    Api.recommendPlaylists(14).catch(() => []),
  ]);

  const heroes = charts.length ? charts.slice(0, 3) : [];
  const newsKey = registerList(news);
  const recent = Store.get("recent").slice(0, 12);
  const recentKey = registerList(recent);

  const heroHtml = heroes.length
    ? `<div class="hero" id="hero" style="--cover:url('${heroes[0].cover}')">
        <div class="hero-bg" id="heroBg" style="background-image:url('${heroes[0].cover}')"></div>
        <div class="hero-copy" id="heroCopy"></div>
        <div class="hero-art-wrap">
          <div class="hero-disc"></div>
          <div class="hero-art"><img id="heroArt" src="${heroes[0].cover}" alt=""/></div>
        </div>
      </div>`
    : "";

  return {
    html: `${heroHtml}
      ${recent.length ? shelf("继续聆听", "从上次停下的地方接着走", recent.map((t, i) => songCard(t, i, recentKey)).join(""), { cardWidth: 156, iconName: "clock" }) : ""}
      ${news.length ? shelf("新歌速递", "刚刚上架，趁着新鲜听", news.map((t, i) => songCard(t, i, newsKey)).join(""), { cardWidth: 158, iconName: "sparkle" }) : ""}
      ${recs.length ? shelf("为你推荐", "根据此刻的耳朵挑的", recs.map(playlistCard).join(""), { cardWidth: 182, iconName: "note" }) : ""}
      ${charts.length ? `<section class="shelf">
        <div class="shelf-head">
          <div class="shelf-titles">
            <h2 class="shelf-title">${icon("chart")}排行榜</h2>
            <p class="shelf-sub">大家正在循环的声音</p>
          </div>
          <button class="btn btn--text btn--sm" data-route="#/charts">全部榜单</button>
        </div>
        <div class="grid-charts">${charts.slice(0, 6).map(chartCard).join("")}</div>
      </section>` : ""}`,
    mount() {
      if (!heroes.length) return;
      const hero = document.getElementById("hero");
      const copy = document.getElementById("heroCopy");
      const bg = document.getElementById("heroBg");
      const art = document.getElementById("heroArt");
      let i = 0;
      let timer = null;

      const paint = () => {
        const item = heroes[i];
        copy.innerHTML = `
          <span class="eyebrow">${icon("sparkle")}今日精选</span>
          <h1 class="hero-title">${esc(item.name)}</h1>
          <p class="hero-desc">${esc(item.description || "网易云音乐榜单，每天更新。")}</p>
          <div class="hero-actions">
            <button class="btn btn--filled ripple" data-quick-playlist="${item.id}">${icon("play")}播放</button>
            <button class="btn btn--tonal ripple" data-route="#/playlist/${item.id}">查看榜单</button>
          </div>
          <div class="hero-meta">${esc(item.updateFrequency || "持续更新")} · ${item.trackCount} 首 · 网易云音乐</div>
          <div class="dot-row">${heroes.map((_, d) => `<span class="dot ${d === i ? "is-on" : ""}"></span>`).join("")}</div>`;
        bg.style.backgroundImage = `url('${item.cover}')`;
        Theme.fromCover(item.cover);
        art.style.opacity = "0";
        const img = new Image();
        img.onload = () => {
          art.src = item.cover;
          art.style.transition = "opacity .5s cubic-bezier(.2,0,0,1)";
          art.style.opacity = "1";
        };
        img.src = item.cover;
        copy.animate([{ opacity: 0.2, transform: "translateY(10px)" }, { opacity: 1, transform: "none" }], {
          duration: 520,
          easing: "cubic-bezier(.2,0,0,1)",
        });
      };

      paint();
      const start = () => {
        stop();
        timer = setInterval(() => {
          i = (i + 1) % heroes.length;
          paint();
        }, 9000);
      };
      const stop = () => timer && clearInterval(timer);
      hero.addEventListener("mouseenter", stop);
      hero.addEventListener("mouseleave", start);
      copy.addEventListener("click", (e) => {
        if (e.target.closest(".dot-row")) {
          const dots = [...copy.querySelectorAll(".dot")];
          const idx = dots.indexOf(e.target.closest(".dot"));
          if (idx >= 0) {
            i = idx;
            paint();
            start();
          }
        }
      });
      start();
    },
  };
}

/* ---------------- 排行榜 ---------------- */

export async function ChartsView() {
  const charts = await Api.toplists();
  return {
    html: `<div class="page-head">
        <h1 class="page-title">排行榜</h1>
        <p class="page-sub">网易云音乐官方榜单，每小时都在变</p>
      </div>
      <div class="grid-charts">${charts.map(chartCard).join("")}</div>`,
  };
}

/* ---------------- 歌单 / 榜单详情 ---------------- */

export async function PlaylistView(id) {
  const pl = await Api.playlist(id);
  const tracks = pl.tracks;
  const totalMs = tracks.reduce((s, t) => s + (t.duration || 0), 0);
  const saved = Store.isSaved(pl.id);

  return {
    html: `<div class="detail">
      <div class="detail-glow"></div>
      <div class="detail-head">
        <div class="detail-cover"><img src="${pl.cover}" alt=""/></div>
        <div class="detail-meta">
          <div class="detail-kind">${esc(pl.kind || "歌单")}</div>
          <h1 class="detail-title">${esc(pl.name)}</h1>
          <div class="detail-sub">
            <span>${esc(pl.creator)}</span>
            <i class="dotsep"></i><span>${tracks.length} 首</span>
            ${pl.playCount ? `<i class="dotsep"></i><span>${fmtCount(pl.playCount)} 次播放</span>` : ""}
            ${totalMs ? `<i class="dotsep"></i><span>${Math.round(totalMs / 60000)} 分钟</span>` : ""}
          </div>
          ${pl.description ? `<p class="detail-desc">${esc(pl.description)}</p>` : ""}
          <div class="detail-actions">
            <button class="btn btn--filled ripple" data-quick-playlist="${pl.id}">${icon("play")}播放</button>
            <button class="btn btn--tonal ripple" data-quick-playlist="${pl.id}" data-shuffle="1">${icon("shuffle")}随机播放</button>
            <button class="btn btn--outline ripple" data-save-playlist="${pl.id}" data-saved="${saved}">
              ${icon(saved ? "check" : "plus")}${saved ? "已收藏" : "收藏"}
            </button>
          </div>
        </div>
      </div>
      ${trackList(tracks, { empty: "这个歌单是空的" })}
    </div>`,
  };
}

/* ---------------- 专辑 ---------------- */

export async function AlbumView(id) {
  const al = await Api.album(id);
  const tracks = al.tracks;
  const year = al.publishTime ? new Date(al.publishTime).getFullYear() : "";

  return {
    html: `<div class="detail">
      <div class="detail-glow"></div>
      <div class="detail-head">
        <div class="detail-cover"><img src="${al.cover}" alt=""/></div>
        <div class="detail-meta">
          <div class="detail-kind">专辑</div>
          <h1 class="detail-title">${esc(al.name)}</h1>
          <div class="detail-sub">
            ${al.artistId ? `<a href="#/artist/${al.artistId}">${esc(al.artist)}</a>` : `<span>${esc(al.artist)}</span>`}
            ${year ? `<i class="dotsep"></i><span>${year}</span>` : ""}
            <i class="dotsep"></i><span>${tracks.length} 首</span>
            ${al.company ? `<i class="dotsep"></i><span>${esc(al.company)}</span>` : ""}
          </div>
          ${al.description ? `<p class="detail-desc">${esc(al.description)}</p>` : ""}
          <div class="detail-actions">
            <button class="btn btn--filled ripple" data-quick-album="${al.id}">${icon("play")}播放</button>
            <button class="btn btn--tonal ripple" data-quick-album="${al.id}" data-shuffle="1">${icon("shuffle")}随机播放</button>
          </div>
        </div>
      </div>
      ${trackList(tracks, { empty: "这张专辑还没有曲目" })}
    </div>`,
  };
}

/* ---------------- 歌手 ---------------- */

export async function ArtistView(id) {
  const [artist, top] = await Promise.all([Api.artist(id), Api.artistTop(id)]);
  const fans = artist.musicSize;

  return {
    html: `<div class="detail">
      <div class="detail-glow"></div>
      <div class="detail-head">
        <div class="detail-cover detail-cover--round"><img src="${artist.cover}" alt=""/></div>
        <div class="detail-meta">
          <div class="detail-kind">歌手</div>
          <h1 class="detail-title">${esc(artist.name)}</h1>
          <div class="detail-sub">
            ${artist.alias ? `<span>${esc(artist.alias)}</span><i class="dotsep"></i>` : ""}
            <span>${fans} 首作品</span>
            <i class="dotsep"></i><span>${artist.albumSize} 张专辑</span>
          </div>
          ${artist.briefDesc ? `<p class="detail-desc">${esc(artist.briefDesc)}</p>` : ""}
          <div class="detail-actions">
            <button class="btn btn--filled ripple" data-play-artist="${artist.id}">${icon("play")}播放热门</button>
          </div>
        </div>
      </div>
      <section class="shelf" style="margin-top:8px">
        <div class="shelf-head">
          <div class="shelf-titles"><h2 class="shelf-title">${icon("chart")}热门单曲</h2></div>
        </div>
      </section>
      ${trackList(top.slice(0, 50), { empty: "这位歌手暂时没有可播放的曲目" })}
    </div>`,
  };
}

/* ---------------- 资料库 ---------------- */

export async function LibraryView(kind) {
  const isLiked = kind === "liked";
  const tracks = isLiked ? Store.get("liked") : Store.get("recent");
  const title = isLiked ? "我喜欢的音乐" : "最近播放";
  const sub = isLiked
    ? `${tracks.length} 首 · 只在你这台设备上保存`
    : `${tracks.length} 首 · 按时间倒序`;

  return {
    html: `<div class="page-head">
        <h1 class="page-title">${title}</h1>
        <p class="page-sub">${sub}</p>
      </div>
      ${
        tracks.length
          ? trackList(tracks, { empty: "" })
          : emptyState(
              isLiked ? "还没有喜欢的歌曲" : "最近还没听过什么",
              isLiked ? "点任意歌曲右侧的心形，它就会留在这里。" : "随便点开一首，这里就会自动记录。",
              isLiked ? "heart" : "clock"
            )
      }`,
  };
}

/* ---------------- 搜索 ---------------- */

const HOT_WORDS = ["周杰伦", "告五人", "林俊杰", "陈奕迅", "五月天", "邓紫棋", "薛之谦", "李荣浩", "粤语", "轻音乐", "钢琴", "说唱"];

export function SearchLanding() {
  return `<div class="search-hero">
    <h1 class="page-title">搜索</h1>
    <p class="page-sub">在网易云音乐的曲库里捞一首歌</p>
    <div class="chips" style="margin-top:24px">
      ${HOT_WORDS.map((w) => `<button class="chip ripple" data-search-word="${esc(w)}">${esc(w)}</button>`).join("")}
    </div>
  </div>
  <div class="empty" style="padding-top:56px">
    <div class="empty-icon">${icon("search")}</div>
    <div class="empty-title">想听点什么？</div>
    <div class="empty-sub">搜索歌曲、歌手、专辑，或者直接点上面的热门词。</div>
  </div>`;
}

export async function SearchView(q) {
  if (!q) return { html: SearchLanding() };

  const tabs = [
    ["songs", "单曲"],
    ["playlists", "歌单"],
    ["albums", "专辑"],
    ["artists", "歌手"],
  ];
  const cache = {};
  let active = "songs";

  const resultHtml = {
    songs: (r) => (r.list.length ? trackList(r.list) : emptyState("没有找到相关歌曲", "换个说法试试，或者搜索歌手的名字。", "search")),
    playlists: (r) => (r.list.length ? `<div class="grid-cards">${r.list.map(playlistCard).join("")}</div>` : emptyState("没有找到相关歌单", "", "search")),
    albums: (r) => (r.list.length ? `<div class="grid-cards">${r.list.map(albumCard).join("")}</div>` : emptyState("没有找到相关专辑", "", "search")),
    artists: (r) => (r.list.length ? `<div class="grid-cards">${r.list.map(artistCard).join("")}</div>` : emptyState("没有找到相关歌手", "", "search")),
  };

  async function load(type) {
    if (!cache[type]) {
      if (type === "songs") cache[type] = await Api.songs(q, 50);
      if (type === "playlists") cache[type] = await Api.playlists(q, 30);
      if (type === "albums") cache[type] = await Api.albums(q, 30);
      if (type === "artists") cache[type] = await Api.artists(q, 30);
    }
    return cache[type];
  }

  const first = await load("songs");

  return {
    html: `<div class="search-hero">
        <h1 class="page-title">「${esc(q)}」的结果</h1>
        <div class="tabs" id="searchTabs">
          ${tabs.map(([k, label], i) => `<button class="tab ripple ${i === 0 ? "is-on" : ""}" data-tab="${k}">${label}</button>`).join("")}
        </div>
      </div>
      <div id="searchResults" style="margin-top:12px">${resultHtml.songs(first)}</div>`,
    mount() {
      const box = document.getElementById("searchResults");
      document.getElementById("searchTabs").addEventListener("click", async (e) => {
        const tab = e.target.closest("[data-tab]");
        if (!tab || tab.dataset.tab === active) return;
        active = tab.dataset.tab;
        document.querySelectorAll("#searchTabs .tab").forEach((t) => t.classList.toggle("is-on", t === tab));
        box.innerHTML = `<div class="tracklist">${Array.from({ length: 5 }, () => `<div class="sk" style="height:56px;margin:6px 0"></div>`).join("")}</div>`;
        try {
          const res = await load(active);
          box.innerHTML = resultHtml[active](res);
        } catch {
          box.innerHTML = emptyState("加载失败", "网络或接口开小差了，稍后再试。", "search");
        }
      });
    },
  };
}

/* ---------------- 收藏歌单入口 ---------------- */

export function SavedPlaylistsView() {
  const list = Store.get("savedPlaylists");
  return {
    html: `<div class="page-head">
        <h1 class="page-title">我的歌单</h1>
        <p class="page-sub">${list.length} 个收藏 · 存在本机</p>
      </div>
      ${list.length ? `<div class="grid-cards">${list.map(playlistCard).join("")}</div>` : emptyState("还没有收藏歌单", "打开任意歌单，点「收藏」，它就会出现在这里。", "library")}`,
  };
}
