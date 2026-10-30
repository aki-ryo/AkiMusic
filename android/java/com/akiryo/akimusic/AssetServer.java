package com.akiryo.akimusic;

import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 跑在 127.0.0.1 上的迷你 HTTP 服务，行为对齐桌面版 server.py：
 *   1. 以 APK 内的 assets/public 作为网站根目录
 *   2. /api/* 转发到网易云音乐 Web API（补 Referer / UA / Cookie）
 *   3. /img?u= 代理封面图，让 WebView 的 canvas 能取到像素做动态配色
 *
 * 只监听回环地址，不对外暴露。
 */
public class AssetServer {

    private static final String UPSTREAM = "https://music.163.com/api/";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String COOKIE = "NMTID=00O4u1VLmzT9Fq5Q7oWm4p3Jm8Hq0kY6c8g; os=pc; appver=8.10.35";
    private static final int MAX_BODY = 16 * 1024 * 1024;

    private final AssetManager assets;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public AssetServer(AssetManager assets) {
        this.assets = assets;
    }

    public int start() throws IOException {
        serverSocket = new ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"));
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "akimusic-asset-server");
        t.setDaemon(true);
        t.start();
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket socket = serverSocket.accept();
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(socket);
                    }
                });
            } catch (IOException e) {
                if (!running) return;
            } catch (Throwable ignored) {
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase(Locale.US);
            String target = parts[1];

            // 丢弃请求头
            String line;
            int guard = 0;
            while ((line = readLine(in)) != null && line.length() > 0 && guard++ < 200) {
                // no-op
            }

            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                respond(socket, 405, "text/plain; charset=utf-8", "method not allowed".getBytes("UTF-8"), false, false);
                return;
            }

            int q = target.indexOf('?');
            String rawPath = q >= 0 ? target.substring(0, q) : target;
            String query = q >= 0 ? target.substring(q + 1) : "";
            String path = URLDecoder.decode(rawPath, "UTF-8");

            if (path.startsWith("/api/")) {
                proxyApi(socket, path.substring(5), query, !"HEAD".equals(method));
            } else if (path.equals("/img")) {
                proxyImage(socket, query, !"HEAD".equals(method));
            } else if (path.equals("/health")) {
                respond(socket, 200, "application/json; charset=utf-8", "{\"ok\":true}".getBytes("UTF-8"), false, !"HEAD".equals(method));
            } else {
                serveAsset(socket, path, !"HEAD".equals(method));
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /* ---------------- 静态资源 ---------------- */

    private void serveAsset(Socket socket, String path, boolean withBody) throws IOException {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        if (rel.length() == 0) rel = "index.html";
        if (rel.contains("..")) {
            respond(socket, 403, "text/plain; charset=utf-8", "forbidden".getBytes("UTF-8"), false, withBody);
            return;
        }

        InputStream is = null;
        try {
            is = assets.open("public/" + rel);
        } catch (IOException missing) {
            try {
                is = assets.open("public/index.html");
                rel = "index.html";
            } catch (IOException fatal) {
                respond(socket, 404, "text/plain; charset=utf-8", "not found".getBytes("UTF-8"), false, withBody);
                return;
            }
        }

        byte[] body = readAll(is);
        boolean cacheable = !(rel.endsWith(".html") || rel.endsWith(".js") || rel.endsWith(".css"));
        respond(socket, 200, mimeOf(rel), body, cacheable, withBody);
    }

    private static String mimeOf(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    /* ---------------- 接口代理 ---------------- */

    private void proxyApi(Socket socket, String apiPath, String query, boolean withBody) throws IOException {
        if (!apiPath.matches("[A-Za-z0-9/_.\\-]+")) {
            respond(socket, 403, "application/json; charset=utf-8", "{\"error\":\"bad path\"}".getBytes("UTF-8"), false, withBody);
            return;
        }
        String url = UPSTREAM + apiPath + (query.length() > 0 ? "?" + query : "");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", "https://music.163.com/");
            conn.setRequestProperty("Cookie", COOKIE);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = is == null ? "{}" : new String(readAll(is), "UTF-8");
            text = upgradeUrls(text);
            respond(socket, 200, "application/json; charset=utf-8", text.getBytes("UTF-8"), false, withBody);
        } catch (Throwable e) {
            String msg = "{\"code\":502,\"error\":\"" + e.getClass().getSimpleName() + "\"}";
            respond(socket, 502, "application/json; charset=utf-8", msg.getBytes("UTF-8"), false, withBody);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String upgradeUrls(String text) {
        return text.replaceAll("http://([a-z0-9\\-]+)\\.music\\.126\\.net", "https://$1.music.126.net")
                .replaceAll("http://([a-z0-9\\-]+)\\.126\\.net", "https://$1.126.net");
    }

    private void proxyImage(Socket socket, String query, boolean withBody) throws IOException {
        String url = param(query, "u");
        if (url == null || url.length() == 0) {
            respond(socket, 400, "text/plain; charset=utf-8", "missing u".getBytes("UTF-8"), false, withBody);
            return;
        }
        String host;
        try {
            host = new URL(url).getHost();
        } catch (Exception e) {
            respond(socket, 400, "text/plain; charset=utf-8", "bad url".getBytes("UTF-8"), false, withBody);
            return;
        }
        if (host == null || !(host.endsWith("music.126.net") || host.endsWith("126.net") || host.endsWith("163.com"))) {
            respond(socket, 403, "text/plain; charset=utf-8", "host not allowed".getBytes("UTF-8"), false, withBody);
            return;
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", "https://music.163.com/");
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] body = is == null ? new byte[0] : readAll(is);
            String type = conn.getContentType();
            if (type == null) type = "image/jpeg";
            respond(socket, 200, type, body, true, withBody);
        } catch (Throwable e) {
            respond(socket, 502, "text/plain; charset=utf-8", "image error".getBytes("UTF-8"), false, withBody);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /* ---------------- 工具 ---------------- */

    private static String param(String query, String key) {
        if (query == null || query.length() == 0) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            try {
                if (URLDecoder.decode(pair.substring(0, eq), "UTF-8").equals(key)) {
                    return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') buf.write(c);
            if (buf.size() > 8192) break;
        }
        if (c == -1 && buf.size() == 0) return null;
        return new String(buf.toByteArray(), "UTF-8");
    }

    private static byte[] readAll(InputStream is) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                out.write(chunk, 0, n);
                if (out.size() > MAX_BODY) break;
            }
            return out.toByteArray();
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void respond(Socket socket, int code, String mime, byte[] body, boolean cacheable, boolean withBody)
            throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n");
        head.append("Content-Type: ").append(mime).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Access-Control-Allow-Origin: *\r\n");
        head.append("Connection: close\r\n");
        head.append(cacheable ? "Cache-Control: public, max-age=86400\r\n" : "Cache-Control: no-store\r\n");
        head.append("\r\n");

        OutputStream out = socket.getOutputStream();
        out.write(head.toString().getBytes("UTF-8"));
        if (withBody) out.write(body);
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 502: return "Bad Gateway";
            default: return "OK";
        }
    }
}
