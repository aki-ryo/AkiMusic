import Foundation

// MARK: - 错误

enum APIError: LocalizedError {

    case invalidURL
    case http(Int)
    case decoding
    case network(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "请求地址不合法"
        case .http(let code):
            return "网络请求失败（HTTP \(code)）"
        case .decoding:
            return "数据解析失败，请稍后再试"
        case .network(let message):
            return "网络异常：\(message)"
        }
    }
}

// MARK: - 网易云接口

/// 直接请求 music.163.com 的公开 web 接口。
/// iOS 没有 CORS 限制，所以不需要像网页版那样走本地代理。
final class NetEaseAPI {

    static let shared = NetEaseAPI()

    private let session: URLSession
    private let baseURL = "https://music.163.com"
    private let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 25
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.httpAdditionalHeaders = ["Accept": "application/json"]
        session = URLSession(configuration: configuration)
    }

    // MARK: - 榜单

    func topLists() async throws -> [ToplistItem] {
        let data = try await get("/api/toplist/detail")
        let response = try decode(ToplistResponse.self, from: data)
        return response.list ?? []
    }

    // MARK: - 新歌

    func newSongs(limit: Int = 12) async throws -> [Song] {
        let data = try await get("/api/personalized/newsong", query: [
            URLQueryItem(name: "limit", value: "\(limit)")
        ])
        let response = try decode(NewSongResponse.self, from: data)
        let items = response.result ?? []

        return items.compactMap { item -> Song? in
            if var song = item.song {
                if song.id == 0, let id = item.id { song.id = id }
                if song.id == 0 { return nil }
                if song.coverURL == nil { song.coverURL = item.picUrl }
                if song.name.isEmpty || song.name == "未知歌曲" {
                    song.name = item.name ?? song.name
                }
                return song
            }
            guard let id = item.id, id > 0 else { return nil }
            return Song(id: id, name: item.name ?? "未知歌曲", coverURL: item.picUrl)
        }
    }

    // MARK: - 推荐歌单

    func recommendedPlaylists(limit: Int = 12) async throws -> [PlaylistSummary] {
        let data = try await get("/api/personalized/playlist", query: [
            URLQueryItem(name: "limit", value: "\(limit)")
        ])
        let response = try decode(PlaylistListResponse.self, from: data)
        return response.result ?? []
    }

    // MARK: - 歌单 / 榜单详情

    func playlist(id: Int) async throws -> PlaylistDetail {
        let data = try await get("/api/v6/playlist/detail", query: [
            URLQueryItem(name: "id", value: "\(id)"),
            URLQueryItem(name: "n", value: "1000")
        ])
        let response = try decode(PlaylistDetailResponse.self, from: data)
        guard let detail = response.playlist else { throw APIError.decoding }
        return detail
    }

    // MARK: - 搜索

    func searchSongs(keyword: String, limit: Int = 50, offset: Int = 0) async throws -> [Song] {
        let data = try await get("/api/search/get/web", query: searchQuery(keyword: keyword,
                                                                          type: 1,
                                                                          limit: limit,
                                                                          offset: offset))
        let response = try decode(SearchResponse.self, from: data)
        return response.result?.songs ?? []
    }

    func searchPlaylists(keyword: String, limit: Int = 30, offset: Int = 0) async throws -> [PlaylistSummary] {
        let data = try await get("/api/search/get/web", query: searchQuery(keyword: keyword,
                                                                          type: 1000,
                                                                          limit: limit,
                                                                          offset: offset))
        let response = try decode(SearchResponse.self, from: data)
        return response.result?.playlists ?? []
    }

    func searchAlbums(keyword: String, limit: Int = 30, offset: Int = 0) async throws -> [AlbumSummary] {
        let data = try await get("/api/search/get/web", query: searchQuery(keyword: keyword,
                                                                          type: 10,
                                                                          limit: limit,
                                                                          offset: offset))
        let response = try decode(SearchResponse.self, from: data)
        return response.result?.albums ?? []
    }

    func searchArtists(keyword: String, limit: Int = 20, offset: Int = 0) async throws -> [ArtistSummary] {
        let data = try await get("/api/search/get/web", query: searchQuery(keyword: keyword,
                                                                          type: 100,
                                                                          limit: limit,
                                                                          offset: offset))
        let response = try decode(SearchResponse.self, from: data)
        return response.result?.artists ?? []
    }

    // MARK: - 播放地址

    /// 返回可直接交给 AVPlayer 的地址。`nil` 表示该曲目没有可用音源（VIP / 下架）。
    func streamURL(for songID: Int) async throws -> URL? {
        let data = try await get("/api/song/enhance/player/url", query: [
            URLQueryItem(name: "ids", value: "[\(songID)]"),
            URLQueryItem(name: "id", value: "\(songID)"),
            URLQueryItem(name: "br", value: "320000")
        ])
        let response = try decode(StreamResponse.self, from: data)
        guard let first = response.data?.first, let raw = first.url, !raw.isEmpty else { return nil }

        // 音源 CDN 有时返回 http，iOS 默认会拦，统一升级成 https。
        var resolved = raw
        if resolved.hasPrefix("http://") {
            resolved = "https://" + String(resolved.dropFirst(7))
        }
        return URL(string: resolved)
    }

    // MARK: - 歌词

    func lyrics(for songID: Int) async throws -> Lyrics {
        let data = try await get("/api/song/lyric", query: [
            URLQueryItem(name: "id", value: "\(songID)"),
            URLQueryItem(name: "lv", value: "-1"),
            URLQueryItem(name: "kv", value: "-1"),
            URLQueryItem(name: "tv", value: "-1")
        ])
        let response = try decode(LyricResponse.self, from: data)
        let lrc = response.lrc?.lyric ?? ""
        let translation = response.tlyric?.lyric ?? ""
        guard !lrc.isEmpty else { return .empty }
        return Lyrics.parse(lrc: lrc, translation: translation)
    }

    // MARK: - 底层

    private func searchQuery(keyword: String, type: Int, limit: Int, offset: Int) -> [URLQueryItem] {
        [
            URLQueryItem(name: "s", value: keyword),
            URLQueryItem(name: "type", value: "\(type)"),
            URLQueryItem(name: "limit", value: "\(limit)"),
            URLQueryItem(name: "offset", value: "\(offset)")
        ]
    }

    private func get(_ path: String, query: [URLQueryItem] = []) async throws -> Data {
        var components = URLComponents(string: baseURL + path)
        if !query.isEmpty {
            components?.queryItems = query
        }
        guard let url = components?.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("https://music.163.com/", forHTTPHeaderField: "Referer")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("os=ios; appver=8.10.0;", forHTTPHeaderField: "Cookie")

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw APIError.http(-1) }
            guard (200..<300).contains(http.statusCode) else { throw APIError.http(http.statusCode) }
            return data
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.network(error.localizedDescription)
        }
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try JSONDecoder().decode(type, from: data)
        } catch {
            throw APIError.decoding
        }
    }
}

// MARK: - 响应外壳

private struct ToplistResponse: Decodable {
    let list: [ToplistItem]?
}

private struct NewSongResponse: Decodable {
    let result: [NewSongItem]?
}

private struct NewSongItem: Decodable {
    let id: Int?
    let name: String?
    let picUrl: String?
    let song: Song?
}

private struct PlaylistListResponse: Decodable {
    let result: [PlaylistSummary]?
}

private struct PlaylistDetailResponse: Decodable {
    let playlist: PlaylistDetail?
}

private struct SearchResponse: Decodable {

    let result: Payload?

    struct Payload: Decodable {
        let songs: [Song]?
        let playlists: [PlaylistSummary]?
        let albums: [AlbumSummary]?
        let artists: [ArtistSummary]?
    }
}

private struct StreamResponse: Decodable {

    let data: [Item]?

    struct Item: Decodable {
        let url: String?
        let br: Int?
        let code: Int?
    }
}

private struct LyricResponse: Decodable {

    struct Body: Decodable {
        let lyric: String?
    }

    let lrc: Body?
    let tlyric: Body?
}
