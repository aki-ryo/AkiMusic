import SwiftUI
import Foundation

struct PlaylistView: View {

    let playlistID: Int

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var detail: PlaylistDetail?
    @State private var isLoading = true
    @State private var errorText: String?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                if isLoading {
                    loadingView
                } else if let detail = detail {
                    header(detail)
                    trackList(detail)
                } else {
                    ErrorStateView(message: errorText ?? "歌单加载失败", retry: {
                        Task { await load() }
                    })
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)
            .padding(.bottom, 150)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if detail == nil {
                await load()
            }
        }
    }

    // MARK: - 头部

    private func header(_ detail: PlaylistDetail) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom, spacing: 16) {
                CoverImage(urlString: detail.coverURL)
                    .frame(width: 150, height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: Color.black.opacity(0.28), radius: 20, x: 0, y: 10)

                VStack(alignment: .leading, spacing: 6) {
                    Text(detail.name)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Theme.text)
                        .lineLimit(3)

                    Text(detail.creatorName)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Theme.secondaryText)
                        .lineLimit(1)

                    Text("\(detail.tracks.count) 首 · \(totalDurationText(detail))")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.tertiaryText)
                }

                Spacer(minLength: 0)
            }

            if !detail.summary.isEmpty {
                Text(detail.summary)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(3)
            }

            HStack(spacing: 10) {
                PlayAllButton(title: "播放全部") {
                    player.play(songs: detail.tracks, startAt: 0)
                }

                GlassCapsuleButton {
                    if !player.isShuffle {
                        player.toggleShuffle()
                    }
                    player.play(songs: detail.tracks, startAt: 0)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 12, weight: .semibold))
                        Text("随机播放")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
            }
            .padding(.top, 2)
        }
    }

    private func totalDurationText(_ detail: PlaylistDetail) -> String {
        let total = detail.tracks.reduce(0.0) { $0 + $1.duration } / 1000
        let minutes = Int(total / 60)
        if minutes >= 60 {
            return "\(minutes / 60) 小时 \(minutes % 60) 分"
        }
        return "\(max(minutes, 1)) 分钟"
    }

    // MARK: - 列表

    private func trackList(_ detail: PlaylistDetail) -> some View {
        VStack(spacing: 2) {
            ForEach(detail.tracks.indices, id: \.self) { index in
                let song = detail.tracks[index]
                SongRow(
                    song: song,
                    index: index + 1,
                    isPlaying: player.currentSong?.id == song.id && player.isPlaying,
                    isLiked: library.isLiked(song),
                    onTap: { player.play(songs: detail.tracks, startAt: index) },
                    onLike: { library.toggleLike(song) }
                )
            }
        }
        .padding(.top, 6)
    }

    private var loadingView: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .bottom, spacing: 16) {
                SkeletonBlock(width: 150, height: 150, cornerRadius: 16)
                VStack(alignment: .leading, spacing: 10) {
                    SkeletonBlock(width: 140, height: 18)
                    SkeletonBlock(width: 90, height: 12)
                    SkeletonBlock(width: 110, height: 10)
                }
            }
            ForEach(0..<6, id: \.self) { _ in
                SongRowSkeleton()
            }
        }
        .padding(.top, 8)
    }

    // MARK: - 数据

    private func load() async {
        isLoading = true
        errorText = nil
        do {
            detail = try await NetEaseAPI.shared.playlist(id: playlistID)
            isLoading = false
        } catch {
            isLoading = false
            detail = nil
            errorText = (error as? LocalizedError)?.errorDescription ?? "歌单加载失败，请稍后再试"
        }
    }
}

// MARK: - 专辑详情

/// 网易云没有公开的「专辑歌曲」接口，这里用专辑名 + 歌手名做一次搜索，
/// 再按专辑名过滤，效果上等价于专辑页。
struct AlbumDetailView: View {

    let albumName: String
    let artistName: String
    var coverURL: String?

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var songs: [Song] = []
    @State private var isLoading = true
    @State private var errorText: String?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                header

                if isLoading {
                    VStack(spacing: 2) {
                        ForEach(0..<6, id: \.self) { _ in
                            SongRowSkeleton()
                        }
                    }
                } else if !songs.isEmpty {
                    ForEach(songs.indices, id: \.self) { index in
                        let song = songs[index]
                        SongRow(
                            song: song,
                            index: index + 1,
                            isPlaying: player.currentSong?.id == song.id && player.isPlaying,
                            isLiked: library.isLiked(song),
                            onTap: { player.play(songs: songs, startAt: index) },
                            onLike: { library.toggleLike(song) }
                        )
                    }
                } else {
                    EmptyStateView(icon: "opticaldisc", title: "没有找到这张专辑的曲目", message: errorText ?? "网易云公开接口没有提供专辑曲目，可以试试搜索歌曲名。")
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)
            .padding(.bottom, 150)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if songs.isEmpty {
                await load()
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom, spacing: 16) {
                CoverImage(urlString: coverURL ?? songs.first?.coverURL)
                    .frame(width: 150, height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: Color.black.opacity(0.28), radius: 20, x: 0, y: 10)

                VStack(alignment: .leading, spacing: 6) {
                    Text(albumName)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Theme.text)
                        .lineLimit(3)

                    Text(artistName)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Theme.secondaryText)
                        .lineLimit(1)

                    if !songs.isEmpty {
                        Text("\(songs.count) 首")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.tertiaryText)
                    }
                }

                Spacer(minLength: 0)
            }

            if !songs.isEmpty {
                PlayAllButton(title: "播放专辑") {
                    player.play(songs: songs, startAt: 0)
                }
            }
        }
    }

    private func load() async {
        isLoading = true
        errorText = nil
        let keyword = artistName.isEmpty ? albumName : "\(albumName) \(artistName)"

        do {
            let result = try await NetEaseAPI.shared.searchSongs(keyword: keyword, limit: 60)
            let matched = result.filter { $0.album.localizedCaseInsensitiveContains(albumName) }
            songs = matched.isEmpty ? result : matched
            isLoading = false
        } catch {
            isLoading = false
            errorText = (error as? LocalizedError)?.errorDescription ?? "加载失败"
        }
    }
}
