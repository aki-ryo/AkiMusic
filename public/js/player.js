/* 播放引擎 */

import { Api, parseLrc } from "./api.js";
import { Store } from "./store.js";
import { toast, fmtTime } from "./util.js";
import { Theme } from "./theme.js";

class PlayerEngine extends EventTarget {
  constructor() {
    super();
    this.audio = new Audio();
    this.audio.preload = "auto";
    this.audio.volume = Store.get("volume") ?? 0.8;

    this.queue = [];
    this.index = -1;
    this.current = null;
    this.playing = false;
    this.loading = false;
    this.shuffle = !!Store.get("shuffle");
    this.repeat = Store.get("repeat") || "off";
    this.muted = false;

    this.urlCache = new Map();
    this.lyric = [];
    this.token = 0;
    this.skipGuard = 0;
    this.lyricSeq = 0;

    this.audio.addEventListener("play", () => {
      this.playing = true;
      this.emit("state");
    });
    this.audio.addEventListener("pause", () => {
      this.playing = false;
      this.emit("state");
    });
    this.audio.addEventListener("timeupdate", () => this.emit("time"));
    this.audio.addEventListener("durationchange", () => this.emit("duration"));
    this.audio.addEventListener("ended", () => this.handleEnded());
    this.audio.addEventListener("error", () => {
      if (!this.audio.src || !this.current) return;
      this.onPlaybackError();
    });
    this.audio.addEventListener("waiting", () => {
      this.loading = true;
      this.emit("state");
    });
    this.audio.addEventListener("playing", () => {
      this.loading = false;
      this.emit("state");
    });
  }

  emit(name, detail = {}) {
    this.dispatchEvent(new CustomEvent(name, { detail }));
  }

  get duration() {
    const d = this.audio.duration;
    return Number.isFinite(d) && d > 0 ? d * 1000 : this.current?.duration || 0;
  }

  get position() {
    return (this.audio.currentTime || 0) * 1000;
  }

  /* ---------------- 队列 ---------------- */

  setQueue(tracks, startIndex = 0, { autoplay = true, label = "" } = {}) {
    this.queue = tracks.slice();
    this.label = label;
    this.emit("queue");
    return this.playIndex(startIndex, { autoplay });
  }

  async playIndex(i, { autoplay = true } = {}) {
    const track = this.queue[i];
    if (!track) return;
    const token = ++this.token;

    this.index = i;
    this.current = track;
    this.lyric = [];
    this.emit("track");
    this.emit("lyric", { lines: [] });
    Theme.fromCover(track.cover);

    this.updateMediaSession(track);
    this.loadLyric(track.id, token);

    this.loading = true;
    this.emit("state");

    let res = null;
    try {
      res = await this.resolveUrl(track);
    } catch {
      res = null;
    }
    if (token !== this.token) return;

    if (!res?.url) {
      this.loading = false;
      this.emit("state");
      this.skipGuard += 1;
      const guard = Math.max(1, this.queue.length);
      if (this.skipGuard >= guard) {
        this.skipGuard = 0;
        toast("这个队列里的曲目暂时都无法播放（VIP 或版权限制）", { icon: "sparkle" });
        return;
      }
      toast(`「${track.name}」${track.vip ? "是 VIP 曲目" : "暂无音源"}，已跳过`, { icon: "sparkle", duration: 1900 });
      setTimeout(() => {
        if (token === this.token) this.next(true);
      }, 760);
      return;
    }

    this.skipGuard = 0;
    Store.addRecent(track);
    this.audio.src = res.url;
    this.trial = res.trial;
    if (res.trial) {
      toast(`「${track.name}」是 VIP 曲目，先听 30 秒试听`, { icon: "sparkle", duration: 2800 });
    }
    if (autoplay) {
      try {
        await this.audio.play();
      } catch {
        this.playing = false;
        this.emit("state");
      }
    } else {
      this.emit("state");
    }
  }

  async resolveUrl(track) {
    if (this.urlCache.has(track.id)) return this.urlCache.get(track.id);
    const res = await Api.songUrl(track.id);
    this.urlCache.set(track.id, res);
    return res;
  }

  async toggle() {
    if (!this.current) {
      if (this.queue.length) return this.playIndex(0);
      return;
    }
    if (this.audio.paused) {
      if (!this.audio.src) return this.playIndex(this.index);
      try {
        await this.audio.play();
      } catch {
        toast("播放被浏览器拦住了，再点一次试试", { icon: "sparkle" });
      }
    } else {
      this.audio.pause();
    }
  }

  next(auto = false) {
    if (!this.queue.length) return;
    if (auto && this.repeat === "one") {
      this.audio.currentTime = 0;
      this.audio.play().catch(() => {});
      return;
    }
    let nextIdx = this.index + 1;
    if (this.shuffle && this.queue.length > 1) {
      const pool = this.queue.map((_, i) => i).filter((i) => i !== this.index);
      nextIdx = pool[Math.floor(Math.random() * pool.length)];
    } else if (nextIdx >= this.queue.length) {
      if (this.repeat === "all") nextIdx = 0;
      else {
        this.audio.pause();
        this.audio.currentTime = 0;
        this.playing = false;
        this.emit("state");
        return;
      }
    }
    this.playIndex(nextIdx);
  }

  prev() {
    if (!this.queue.length) return;
    if (this.audio.currentTime > 3) {
      this.audio.currentTime = 0;
      return;
    }
    let prevIdx = this.index - 1;
    if (this.shuffle && this.queue.length > 1) {
      const pool = this.queue.map((_, i) => i).filter((i) => i !== this.index);
      prevIdx = pool[Math.floor(Math.random() * pool.length)];
    } else if (prevIdx < 0) {
      prevIdx = this.queue.length - 1;
    }
    this.playIndex(prevIdx);
  }

  handleEnded() {
    if (this.repeat === "one") {
      this.audio.currentTime = 0;
      this.audio.play().catch(() => {});
      return;
    }
    this.next(true);
  }

  onPlaybackError() {
    // 直链过期：清掉缓存再试一次
    if (this.current && this.urlCache.has(this.current.id)) {
      this.urlCache.delete(this.current.id);
    }
    toast("音源加载失败，试试下一首", { icon: "sparkle" });
    this.playing = false;
    this.emit("state");
  }

  seekMs(ms) {
    if (!Number.isFinite(ms)) return;
    const dur = this.audio.duration;
    if (!Number.isFinite(dur) || dur <= 0) return;
    this.audio.currentTime = Math.min(dur - 0.05, Math.max(0, ms / 1000));
    this.emit("time");
  }

  setVolume(v) {
    const vol = Math.min(1, Math.max(0, v));
    this.audio.volume = vol;
    this.audio.muted = vol === 0;
    this.muted = vol === 0;
    Store.set("volume", vol);
    this.emit("volume");
  }

  get volume() {
    return this.audio.muted ? 0 : this.audio.volume;
  }

  toggleMute() {
    this.audio.muted = !this.audio.muted;
    this.muted = this.audio.muted;
    this.emit("volume");
  }

  setShuffle(on) {
    this.shuffle = on;
    Store.set("shuffle", on);
    this.emit("mode");
  }

  cycleRepeat() {
    this.repeat = this.repeat === "off" ? "all" : this.repeat === "all" ? "one" : "off";
    Store.set("repeat", this.repeat);
    this.emit("mode");
    return this.repeat;
  }

  /* ---------------- 歌词 ---------------- */

  async loadLyric(id, token) {
    const seq = ++this.lyricSeq;
    try {
      const { lrc } = await Api.lyric(id);
      if (seq !== this.lyricSeq || token !== this.token) return;
      this.lyric = parseLrc(lrc);
      this.emit("lyric", { lines: this.lyric });
    } catch {
      this.lyric = [];
      this.emit("lyric", { lines: [] });
    }
  }

  /* ---------------- 系统媒体控制 ---------------- */

  updateMediaSession(track) {
    if (!("mediaSession" in navigator)) return;
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: track.name,
        artist: track.artistText,
        album: track.album || "网易云音乐",
        artwork: [{ src: track.cover, sizes: "400x400", type: "image/jpeg" }],
      });
      navigator.mediaSession.setActionHandler("play", () => this.toggle());
      navigator.mediaSession.setActionHandler("pause", () => this.toggle());
      navigator.mediaSession.setActionHandler("previoustrack", () => this.prev());
      navigator.mediaSession.setActionHandler("nexttrack", () => this.next());
    } catch {
      /* 忽略 */
    }
  }
}

export const player = new PlayerEngine();
export { fmtTime };
