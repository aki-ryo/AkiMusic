import Foundation

// MARK: - Song

/// 一首歌。
///
/// 网易云不同接口返回的字段名不一致：搜索接口给 `artists` / `album` / `duration`，
/// 歌单与榜单接口给 `ar` / `al` / `dt`。这里用自定义 `init(from:)` 同时兼容两种形态，
/// 并且所有字段都做了缺失兜底，避免某一条脏数据让整个列表解析失败。
struct Song: Identifiable, Hashable {

    var id: Int
    var name: String
    var artist: String
    var album: String
    var coverURL: String?
    /// 毫秒
    var duration: Double
    /// 0 免费 / 1 VIP / 4 付费专辑 / 8 低音质免费
    var fee: Int

    init(id: Int,
         name: String,
         artist: String = "未知歌手",
         album: String = "",
         coverURL: String? = nil,
         duration: Double = 0,
         fee: Int = 0) {
        self.id = id
        self.name = name
        self.artist = artist
        self.album = album
        self.coverURL = coverURL
        self.duration = duration
        self.fee = fee
    }

    var isVIP: Bool { fee == 1 || fee == 4 }

    var durationText: String { TimeFormat.duration(ms: duration) }
}

extension Song: Codable {

    enum CodingKeys: String, CodingKey {
        case id, name, artist, album, coverURL, duration, fee
        case ar, al, dt, artists, picUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)

        id = c.int(.id) ?? 0
        let decodedName = c.string(.name) ?? ""
        name = decodedName.isEmpty ? "未知歌曲" : decodedName
        fee = c.int(.fee) ?? 0

        var milliseconds = c.double(.duration) ?? 0
        if milliseconds <= 0 {
            milliseconds = Double(c.int(.dt) ?? 0)
        }
        duration = milliseconds

        if let value = c.string(.artist), !value.isEmpty {
            artist = value
        } else {
            let arRefs: [NameRef]? = c.value(.ar)
            let legacyRefs: [NameRef]? = c.value(.artists)
            let names = (arRefs ?? legacyRefs ?? []).compactMap { $0.name }.filter { !$0.isEmpty }
            artist = names.isEmpty ? "未知歌手" : names.joined(separator: " / ")
        }

        var albumName = ""
        var cover = c.string(.coverURL)

        let alRef: AlbumRef? = c.value(.al)
        let legacyAlbumRef: AlbumRef? = c.value(.album)
        if let albumRef = alRef ?? legacyAlbumRef {
            albumName = albumRef.name ?? ""
            if cover == nil { cover = albumRef.picUrl }
        }
        if albumName.isEmpty, let storedAlbum = c.string(.album) {
            albumName = storedAlbum
        }
        if cover == nil, let pic = c.string(.picUrl) {
            cover = pic
        }

        album = albumName
        coverURL = cover
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(artist, forKey: .artist)
        try c.encode(album, forKey: .album)
        try c.encodeIfPresent(coverURL, forKey: .coverURL)
        try c.encode(duration, forKey: .duration)
        try c.encode(fee, forKey: .fee)
    }
}

// MARK: - 榜单项

struct ToplistItem: Identifiable, Hashable, Decodable {

    var id: Int
    var name: String
    var coverURL: String?
    var updateFrequency: String
    var trackCount: Int
    var tracks: [Song]

    enum CodingKeys: String, CodingKey {
        case id, name, coverURL, updateFrequency, trackCount, tracks, coverImgUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.int(.id) ?? 0
        name = c.string(.name) ?? "榜单"
        coverURL = c.string(.coverImgUrl) ?? c.string(.coverURL)
        updateFrequency = c.string(.updateFrequency) ?? "每日更新"
        trackCount = c.int(.trackCount) ?? 0
        tracks = c.value(.tracks) ?? []
    }
}

// MARK: - 歌单摘要

struct PlaylistSummary: Identifiable, Hashable, Codable {

    var id: Int
    var name: String
    var coverURL: String?
    var playCount: Int
    var trackCount: Int
    var creatorName: String

    init(id: Int,
         name: String,
         coverURL: String? = nil,
         playCount: Int = 0,
         trackCount: Int = 0,
         creatorName: String = "") {
        self.id = id
        self.name = name
        self.coverURL = coverURL
        self.playCount = playCount
        self.trackCount = trackCount
        self.creatorName = creatorName
    }

    enum CodingKeys: String, CodingKey {
        case id, name, coverURL, playCount, playcount, trackCount, creatorName
        case coverImgUrl, picUrl, copywriter
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.int(.id) ?? 0
        name = c.string(.name) ?? "未命名歌单"
        coverURL = c.string(.coverURL) ?? c.string(.coverImgUrl) ?? c.string(.picUrl)
        playCount = c.int(.playCount) ?? c.int(.playcount) ?? 0
        trackCount = c.int(.trackCount) ?? 0
        creatorName = c.string(.creatorName) ?? c.string(.copywriter) ?? ""
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encodeIfPresent(coverURL, forKey: .coverURL)
        try c.encode(playCount, forKey: .playCount)
        try c.encode(trackCount, forKey: .trackCount)
        try c.encode(creatorName, forKey: .creatorName)
    }
}

// MARK: - 歌单详情

struct PlaylistDetail: Identifiable, Decodable {

    var id: Int
    var name: String
    var coverURL: String?
    var summary: String
    var creatorName: String
    var trackCount: Int
    var tracks: [Song]

    enum CodingKeys: String, CodingKey {
        case id, name, coverURL, summary, desc, description, creator, trackCount, tracks, coverImgUrl
    }

    struct Creator: Decodable {
        let nickname: String?
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.int(.id) ?? 0
        name = c.string(.name) ?? "歌单"
        coverURL = c.string(.coverImgUrl) ?? c.string(.coverURL)
        summary = c.string(.description) ?? c.string(.desc) ?? ""
        let creator: Creator? = c.value(.creator)
        creatorName = creator?.nickname ?? "网易云音乐"
        trackCount = c.int(.trackCount) ?? 0
        tracks = c.value(.tracks) ?? []
    }
}

// MARK: - 专辑

struct AlbumSummary: Identifiable, Hashable, Decodable {

    var id: Int
    var name: String
    var coverURL: String?
    var artist: String
    var size: Int

    enum CodingKeys: String, CodingKey {
        case id, name, coverURL, artist, size, picUrl, albumSize
    }

    struct ArtistRef: Decodable {
        let name: String?
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.int(.id) ?? 0
        name = c.string(.name) ?? "未命名专辑"
        coverURL = c.string(.picUrl) ?? c.string(.coverURL)
        let ref: ArtistRef? = c.value(.artist)
        artist = ref?.name ?? "未知歌手"
        size = c.int(.size) ?? c.int(.albumSize) ?? 0
    }
}

// MARK: - 歌手

struct ArtistSummary: Identifiable, Hashable, Decodable {

    var id: Int
    var name: String
    var coverURL: String?
    var albumSize: Int
    var musicSize: Int

    enum CodingKeys: String, CodingKey {
        case id, name, coverURL, albumSize, musicSize, picUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.int(.id) ?? 0
        name = c.string(.name) ?? "未知歌手"
        coverURL = c.string(.picUrl) ?? c.string(.coverURL)
        albumSize = c.int(.albumSize) ?? 0
        musicSize = c.int(.musicSize) ?? 0
    }
}

// MARK: - 内部引用类型

struct NameRef: Decodable {
    let name: String?
}

struct AlbumRef: Decodable {
    let name: String?
    let picUrl: String?
}

// MARK: - 容错解码

extension KeyedDecodingContainer {

    func string(_ key: Key) -> String? {
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            return value
        }
        return nil
    }

    func int(_ key: Key) -> Int? {
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return value
        }
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            return Int(value)
        }
        return nil
    }

    func double(_ key: Key) -> Double? {
        if let value = try? decodeIfPresent(Double.self, forKey: key) {
            return value
        }
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return Double(value)
        }
        return nil
    }

    func value<T: Decodable>(_ key: Key) -> T? {
        if let value = try? decodeIfPresent(T.self, forKey: key) {
            return value
        }
        return nil
    }
}

// MARK: - 导航路由

enum Route: Hashable {
    case playlist(Int)
    case album(name: String, artist: String, cover: String?)
    case artist(id: Int, name: String)
}
