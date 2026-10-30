import Foundation
import Combine

/// 资料库：我喜欢的音乐 + 最近播放，全部落在 UserDefaults（JSON）。
final class LibraryStore: ObservableObject {

    static let shared = LibraryStore()

    @Published private(set) var likedSongs: [Song] = []
    @Published private(set) var recentSongs: [Song] = []

    private let defaults: UserDefaults
    private let likedKey = "aki.library.liked.v1"
    private let recentKey = "aki.library.recent.v1"
    private let maxRecent = 40

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        likedSongs = Self.load(key: likedKey, defaults: defaults)
        recentSongs = Self.load(key: recentKey, defaults: defaults)
    }

    // MARK: - 喜欢

    func isLiked(_ song: Song) -> Bool {
        likedSongs.contains { $0.id == song.id }
    }

    func toggleLike(_ song: Song) {
        if let index = likedSongs.firstIndex(where: { $0.id == song.id }) {
            likedSongs.remove(at: index)
        } else {
            likedSongs.insert(song, at: 0)
        }
        save(likedSongs, key: likedKey)
    }

    // MARK: - 最近播放

    func recordPlay(_ song: Song) {
        recentSongs.removeAll { $0.id == song.id }
        recentSongs.insert(song, at: 0)
        if recentSongs.count > maxRecent {
            recentSongs = Array(recentSongs.prefix(maxRecent))
        }
        save(recentSongs, key: recentKey)
    }

    func clearRecent() {
        recentSongs = []
        save(recentSongs, key: recentKey)
    }

    // MARK: - 持久化

    private func save(_ songs: [Song], key: String) {
        guard let data = try? JSONEncoder().encode(songs) else { return }
        defaults.set(data, forKey: key)
    }

    private static func load(key: String, defaults: UserDefaults) -> [Song] {
        guard let data = defaults.data(forKey: key) else { return [] }
        guard let songs = try? JSONDecoder().decode([Song].self, from: data) else { return [] }
        return songs
    }
}
