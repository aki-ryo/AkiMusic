/* 网易云音乐接口封装（走本地 /api 代理） */

import { coverUrl } from "./util.js";

const cache = new Map();

async function req(path, params = {}, ttl = 5 * 60 * 1000) {
  const qs = new URLSearchParams(params).toString();
  const key = path + "?" + qs;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.t < ttl) return hit.v;

  const res = await fetch(`/api/${path}${qs ? "?" + qs : ""}`);
  if (!res.ok) throw new Error(`接口 ${path} 返回 ${res.status}`);
  const json = await res.json();
  if (json && json.code && json.code >= 400 && json.code !== 404) {
    throw new Error(json.message || `接口错误 ${json.code}`);
  }
  cache.set(key, { t: Date.now(), v: json });
  return json;
}

/* ---------------- 归一化 ---------------- */

export function toTrack(raw) {
  if (!raw) return null;
  const ar = raw.ar || raw.artists || [];
  const al = raw.al || raw.album || {};
  const name = raw.name || raw.songName || "未知曲目";
  const artists = ar.map((a) => a && a.name).filter(Boolean);
  return {
    id: raw.id,
    name,
    artists,
    artistText: artists.join(" / ") || "未知歌手",
    album: al.name || "",
    albumId: al.id || 0,
    cover: coverUrl(al.picUrl || raw.picUrl || "", 400),
    duration: raw.dt || raw.duration || 0,
    fee: raw.fee ?? 0,
    vip: raw.fee === 1 || raw.fee === 4,
  };
}

export function toPlaylist(raw) {
  if (!raw) return null;
  const pl = raw.playlist || raw;
  return {
    id: pl.id,
    name: pl.name,
    cover: coverUrl(pl.coverImgUrl || pl.picUrl || "", 500),
    trackCount: pl.trackCount ?? pl.songCount ?? 0,
    playCount: pl.playCount ?? 0,
    creator: pl.creator?.nickname || pl.creator?.name || "网易云音乐",
    creatorAvatar: coverUrl(pl.creator?.avatarUrl || "", 100),
    description: pl.description || "",
    updateFrequency: pl.updateFrequency || "",
    kind: pl.specialType === 10 || pl.ToplistType ? "榜单" : "歌单",
    tracks: (pl.tracks || []).map(toTrack).filter(Boolean),
  };
}

export function toAlbum(raw) {
  const a = raw.album || raw;
  return {
    id: a.id,
    name: a.name,
    cover: coverUrl(a.picUrl || "", 500),
    artist: a.artist?.name || (a.artists || []).map((x) => x.name).join(" / ") || "",
    artistId: a.artist?.id || 0,
    publishTime: a.publishTime || 0,
    size: a.size || 0,
    description: a.description || "",
    company: a.company || "",
    tracks: (raw.songs || a.songs || []).map(toTrack).filter(Boolean),
  };
}

export function toArtist(raw) {
  const a = raw.artist || raw;
  return {
    id: a.id,
    name: a.name,
    cover: coverUrl(a.picUrl || a.img1v1Url || "", 400),
    briefDesc: a.briefDesc || "",
    musicSize: a.musicSize || 0,
    albumSize: a.albumSize || 0,
    alias: (a.alias || []).join(" / "),
  };
}

/* ---------------- 接口 ---------------- */

export const Api = {
  async search(keyword, type = 1, limit = 30, offset = 0) {
    const data = await req("search/get/web", { s: keyword, type, limit, offset });
    return data.result || {};
  },

  async songs(keyword, limit = 30, offset = 0) {
    const r = await this.search(keyword, 1, limit, offset);
    return { list: (r.songs || []).map(toTrack).filter(Boolean), total: r.songCount || 0 };
  },
  async playlists(keyword, limit = 30) {
    const r = await this.search(keyword, 1000, limit, 0);
    return { list: (r.playlists || []).map(toPlaylist).filter(Boolean), total: r.playlistCount || 0 };
  },
  async albums(keyword, limit = 30) {
    const r = await this.search(keyword, 10, limit, 0);
    return { list: (r.albums || []).map(toAlbum).filter(Boolean), total: r.albumCount || 0 };
  },
  async artists(keyword, limit = 30) {
    const r = await this.search(keyword, 100, limit, 0);
    return { list: (r.artists || []).map(toArtist).filter(Boolean), total: r.artistCount || 0 };
  },

  async songUrl(id) {
    const data = await req("song/enhance/player/url", { ids: `[${id}]`, br: 320000 }, 30 * 60 * 1000);
    const item = data?.data?.[0];
    return {
      url: item?.url ? item.url.replace(/^http:/, "https:") : null,
      trial: Boolean(item?.freeTrialInfo),
      br: item?.br || 0,
    };
  },

  async lyric(id) {
    const data = await req("song/lyric", { id, lv: -1, kv: -1, tv: -1 }, 60 * 60 * 1000);
    return { lrc: data?.lrc?.lyric || "", tlyric: data?.tlyric?.lyric || "" };
  },

  async playlist(id) {
    const data = await req("v6/playlist/detail", { id, n: 1000 }, 6 * 60 * 1000);
    return toPlaylist(data.playlist);
  },

  async album(id) {
    const data = await req(`v1/album/${id}`, {}, 30 * 60 * 1000);
    return toAlbum(data);
  },

  async artist(id) {
    const data = await req(`artist/${id}`, {}, 30 * 60 * 1000);
    return toArtist(data);
  },

  async artistTop(id) {
    const data = await req("artist/top/song", { id }, 30 * 60 * 1000);
    return (data.songs || []).map(toTrack).filter(Boolean);
  },

  async toplists() {
    const data = await req("toplist/detail", {}, 10 * 60 * 1000);
    return (data.list || []).map((x) => ({
      id: x.id,
      name: x.name,
      cover: coverUrl(x.coverImgUrl, 400),
      updateFrequency: x.updateFrequency || "持续更新",
      description: x.description || "",
      trackCount: x.trackCount || 0,
    }));
  },

  async newSongs(limit = 12) {
    const data = await req("personalized/newsong", { limit }, 5 * 60 * 1000);
    return (data.result || [])
      .map((item) => {
        const t = toTrack(item.song || item);
        if (t && !t.cover) t.cover = coverUrl(item.picUrl, 400);
        return t;
      })
      .filter(Boolean);
  },

  async recommendPlaylists(limit = 12) {
    const data = await req("personalized/playlist", { limit }, 10 * 60 * 1000);
    return (data.result || []).map(toPlaylist).filter(Boolean);
  },

  async hotPlaylists(cat = "全部", limit = 12) {
    const data = await req("top/playlist", { cat, limit, order: "hot" }, 10 * 60 * 1000);
    return (data.playlists || []).map(toPlaylist).filter(Boolean);
  },
};

/* ---------------- LRC 解析 ---------------- */
export function parseLrc(text) {
  if (!text) return [];
  const out = [];
  const tag = /\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;
  for (const rawLine of text.split(/\r?\n/)) {
    const stamps = [...rawLine.matchAll(tag)];
    if (!stamps.length) continue;
    const content = rawLine.replace(tag, "").trim();
    for (const m of stamps) {
      const ms =
        Number(m[1]) * 60000 +
        Number(m[2]) * 1000 +
        (m[3] ? Number(m[3].padEnd(3, "0").slice(0, 3)) : 0);
      out.push({ t: ms, text: content });
    }
  }
  out.sort((a, b) => a.t - b.t);
  const dedup = [];
  for (const line of out) {
    const prev = dedup[dedup.length - 1];
    if (prev && prev.t === line.t && prev.text === line.text) continue;
    dedup.push(line);
  }
  return dedup;
}
