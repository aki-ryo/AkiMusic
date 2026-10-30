#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
潮汐 TIDE · 本地服务
------------------------------------------------------------------
一个零依赖的小型服务器，做三件事：
  1. 托管 public/ 下的前端静态资源
  2. 把 /api/* 请求转发到网易云音乐 Web API（补上 Referer / UA / Cookie）
  3. /img?u=... 图片代理，让浏览器 canvas 能取到封面主色（动态配色）

运行：  python server.py [端口]
"""

import http.server
import json
import os
import re
import socketserver
import ssl
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PUBLIC_DIR = os.path.join(BASE_DIR, "public")

UPSTREAM = "https://music.163.com/api/"
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)
COOKIE = "NMTID=00O4u1VLmzT9Fq5Q7oWm4p3Jm8Hq0kY6c8g; os=pc; appver=8.10.35"

SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE

# 允许转发的接口形状（正则），避免被当成任意代理使用
ALLOW_PATTERNS = [
    r"^search/get(/web)?$",
    r"^search/suggest(/web)?$",
    r"^song/enhance/player/url$",
    r"^song/enhance/download/url$",
    r"^song/detail$",
    r"^song/lyric$",
    r"^song/lyrics$",
    r"^v1/album/\d+$",
    r"^album/\d+$",
    r"^album/new$",
    r"^album/sublist$",
    r"^artist/\d+$",
    r"^artist/top/song$",
    r"^artist/songs$",
    r"^artists$",
    r"^v6/playlist/detail$",
    r"^playlist/detail$",
    r"^playlist/track/all$",
    r"^personalized/newsong$",
    r"^personalized/playlist$",
    r"^personalized/djprogram$",
    r"^toplist/detail$",
    r"^toplist/artist$",
    r"^top/playlist$",
    r"^dj/program/detail$",
    r"^mv/detail$",
    r"^comment/music$",
    r"^comment/playlist$",
]
ALLOW_RE = [re.compile(p) for p in ALLOW_PATTERNS]

_json_cache = {}
_cache_lock = threading.Lock()
CACHE_TTL = 300  # 秒


def upstream_get(path_with_query: str, timeout: int = 18):
    url = UPSTREAM + path_with_query
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Referer": "https://music.163.com/",
            "Cookie": COOKIE,
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "zh-CN,zh;q=0.9",
            "Origin": "https://music.163.com",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
        return resp.read()


def fix_urls(text: str) -> str:
    """把 JSON 里的 http 图片/音频地址升级为 https，避免混合内容被拦截。"""
    text = re.sub(r"http://([a-z0-9\-]+)\.music\.126\.net", r"https://\1.music.126.net", text)
    text = re.sub(r"http://([a-z0-9\-]+)\.126\.net", r"https://\1.126.net", text)
    return text


MIME = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".ico": "image/x-icon",
    ".woff2": "font/woff2",
    ".mp3": "audio/mpeg",
}


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "TideServer/1.0"

    # ---------- helpers ----------
    def log_message(self, fmt, *args):
        try:
            line = fmt % args
        except Exception:
            line = str(fmt) + " " + " ".join(str(a) for a in args)
        sys.stdout.write("  · %s\n" % line)
        sys.stdout.flush()

    def _send(self, code, body, content_type="application/json; charset=utf-8", extra=None):
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def _json_error(self, code, message):
        self._send(code, json.dumps({"code": code, "error": message}, ensure_ascii=False))

    # ---------- routes ----------
    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)

        if path.startswith("/api/"):
            return self.handle_api(path[5:], parsed.query)
        if path == "/img":
            return self.handle_img(parsed.query)
        if path == "/health":
            return self._send(200, json.dumps({"ok": True, "time": int(time.time())}))
        return self.handle_static(path)

    # ---------- API proxy ----------
    def handle_api(self, api_path, query):
        if api_path == "batch":
            # /api/batch?paths=/search/get/web?s=x|/personalized/newsong
            qs = urllib.parse.parse_qs(query)
            raw = (qs.get("paths") or [""])[0]
            results = {}
            for item in [p for p in raw.split("|") if p]:
                try:
                    results[item] = json.loads(fix_urls(upstream_get(item).decode("utf-8", "replace")))
                except Exception as exc:  # noqa: BLE001
                    results[item] = {"error": str(exc)}
            return self._send(200, json.dumps(results, ensure_ascii=False))

        if not any(p.match(api_path) for p in ALLOW_RE):
            return self._json_error(403, "endpoint not allowed: " + api_path)

        full = api_path + (("?" + query) if query else "")
        now = time.time()
        with _cache_lock:
            hit = _json_cache.get(full)
            if hit and hit[0] > now:
                return self._send(200, hit[1], extra={"X-Cache": "HIT"})

        try:
            raw = upstream_get(full)
        except urllib.error.HTTPError as exc:
            return self._json_error(502, "upstream HTTP %s" % exc.code)
        except Exception as exc:  # noqa: BLE001
            return self._json_error(502, "upstream error: %s" % exc)

        text = fix_urls(raw.decode("utf-8", "replace"))
        with _cache_lock:
            if len(_json_cache) > 600:
                _json_cache.clear()
            _json_cache[full] = (now + CACHE_TTL, text)
        return self._send(200, text, extra={"X-Cache": "MISS"})

    # ---------- image proxy (for canvas colour extraction) ----------
    def handle_img(self, query):
        qs = urllib.parse.parse_qs(query)
        url = (qs.get("u") or [""])[0]
        if not url:
            return self._json_error(400, "missing u")
        host = urllib.parse.urlparse(url).hostname or ""
        if not (host.endswith("music.126.net") or host.endswith("126.net") or host.endswith("163.com")):
            return self._json_error(403, "host not allowed")

        key = "img:" + url
        now = time.time()
        with _cache_lock:
            hit = _json_cache.get(key)
            if hit and hit[0] > now:
                return self._send(200, hit[1], content_type=hit[2], extra={"X-Cache": "HIT"})

        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": UA, "Referer": "https://music.163.com/"},
            )
            with urllib.request.urlopen(req, timeout=18, context=SSL_CTX) as resp:
                body = resp.read()
                ctype = resp.headers.get("Content-Type", "image/jpeg")
        except Exception as exc:  # noqa: BLE001
            return self._json_error(502, "image error: %s" % exc)

        with _cache_lock:
            if len(_json_cache) > 600:
                _json_cache.clear()
            _json_cache[key] = (now + 86400, body, ctype)
        return self._send(200, body, content_type=ctype, extra={"Cache-Control": "public, max-age=86400"})

    # ---------- static ----------
    def handle_static(self, path):
        if path in ("/", ""):
            path = "/index.html"
        safe = os.path.normpath(path).lstrip("\\/")
        file_path = os.path.join(PUBLIC_DIR, safe)
        if not os.path.abspath(file_path).startswith(os.path.abspath(PUBLIC_DIR)):
            return self._json_error(403, "forbidden")
        if not os.path.isfile(file_path):
            # SPA 回退
            file_path = os.path.join(PUBLIC_DIR, "index.html")
            if not os.path.isfile(file_path):
                return self._json_error(404, "not found")
        ext = os.path.splitext(file_path)[1].lower()
        ctype = MIME.get(ext, "application/octet-stream")
        try:
            with open(file_path, "rb") as fh:
                body = fh.read()
        except OSError as exc:
            return self._json_error(500, str(exc))
        extra = {"Cache-Control": "no-store" if ext in (".html", ".css", ".js") else "public, max-age=3600"}
        return self._send(200, body, content_type=ctype, extra=extra)


class ThreadingServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    port = 8777
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            pass

    for attempt in range(12):
        try:
            httpd = ThreadingServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            print("端口 %d 被占用，尝试 %d ..." % (port, port + 1))
            port += 1
    else:
        print("找不到可用端口，退出。")
        return

    banner = r"""
   ┌─────────────────────────────────────────────┐
   │   潮 汐   T I D E                           │
   │   Material 3 Expressive Music Player        │
   └─────────────────────────────────────────────┘
     本地地址   http://localhost:%d
     数据来源   网易云音乐 Web API
     停止运行   Ctrl + C
""" % port
    print(banner)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。下首歌见 👋")
        httpd.server_close()


if __name__ == "__main__":
    main()
