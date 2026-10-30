/* 本地状态：喜欢、最近播放、收藏歌单、音量、主题 */

const KEY = "tide.state.v2";

const blank = {
  liked: [],
  recent: [],
  savedPlaylists: [],
  volume: 0.8,
  theme: "dark",
  shuffle: false,
  repeat: "off",
};

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...blank };
    return { ...blank, ...JSON.parse(raw) };
  } catch {
    return { ...blank };
  }
}

let state = load();
let timer = null;

function persist() {
  clearTimeout(timer);
  timer = setTimeout(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      /* 存储满了就算了 */
    }
  }, 120);
}

export const Store = {
  get(key) {
    return state[key];
  },
  set(key, value) {
    state[key] = value;
    clearTimeout(timer);
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      persist();
    }
  },
  all() {
    return state;
  },

  /* 喜欢 */
  isLiked(id) {
    return state.liked.some((t) => t.id === id);
  },
  toggleLike(track) {
    if (!track) return false;
    const i = state.liked.findIndex((t) => t.id === track.id);
    if (i >= 0) {
      state.liked.splice(i, 1);
      persist();
      return false;
    }
    state.liked.unshift(track);
    persist();
    return true;
  },

  /* 最近播放 */
  addRecent(track) {
    if (!track) return;
    state.recent = [track, ...state.recent.filter((t) => t.id !== track.id)].slice(0, 120);
    persist();
  },
  clearRecent() {
    state.recent = [];
    persist();
  },

  /* 收藏歌单 */
  isSaved(id) {
    return state.savedPlaylists.some((p) => p.id === id);
  },
  toggleSaved(playlist) {
    const i = state.savedPlaylists.findIndex((p) => p.id === playlist.id);
    if (i >= 0) {
      state.savedPlaylists.splice(i, 1);
      persist();
      return false;
    }
    state.savedPlaylists.unshift({
      id: playlist.id,
      name: playlist.name,
      cover: playlist.cover,
      trackCount: playlist.trackCount,
    });
    persist();
    return true;
  },
};

export function bootTheme() {
  const saved = state.theme === "light" ? "light" : "dark";
  document.documentElement.dataset.theme = saved;
  return saved;
}
