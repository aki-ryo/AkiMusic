import SwiftUI
import Foundation

struct ArtistView: View {

    let artistID: Int
    let artistName: String

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var info: ArtistSummary?
    @State private var songs: [Song] = []
    @State private var albums: [AlbumSummary] = []
    @State private var isLoading = true
    @State private var errorText: String?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                header

                if isLoading {
                    VStack(spacing: 2) {
                        ForEach(0..<6, id: \.self) { _ in
                            SongRowSkeleton()
                        }
                    }
                } else if let errorText = errorText, songs.isEmpty {
                    ErrorStateView(message: errorText, retry: {
                        Task { await load() }
                    })
                } else if songs.isEmpty {
                    EmptyStateView(icon: "person.wave.2", title: "没有找到相关歌曲", message: "试试搜索歌手名。")
                } else {
                    songSection
                }

                albumSection
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

    // MARK: - 头部

    private var header: some View {
        VStack(spacing: 12) {
            Color.clear
                .frame(width: 132, height: 132)
                .overlay(CoverImage(urlString: info?.coverURL ?? songs.first?.coverURL))
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
                .shadow(color: Color.black.opacity(0.3), radius: 22, x: 0, y: 12)

            Text(info?.name ?? artistName)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(Theme.text)
                .multilineTextAlignment(.center)

            Text(statsText)
                .font(.system(size: 12))
                .foregroundStyle(Theme.secondaryText)

            HStack(spacing: 10) {
                PlayAllButton(title: "播放热门") {
                    player.play(songs: songs, startAt: 0)
                }

                GlassCapsuleButton {
                    if !player.isShuffle {
                        player.toggleShuffle()
                    }
                    player.play(songs: songs, startAt: 0)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 12, weight: .semibold))
                        Text("随机播放")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 4)
    }

    private var statsText: String {
        let singleCount = info?.musicSize ?? songs.count
        let albumCount = info?.albumSize ?? albums.count
        return "\(singleCount) 首单曲 · \(albumCount) 张专辑"
    }

    // MARK: - 热门单曲

    private var songSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "热门单曲")

            ForEach(songs.prefix(20).indices, id: \.self) { index in
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
        }
    }

    // MARK: - 专辑

    @ViewBuilder
    private var albumSection: some View {
        if !albums.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "专辑")

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(albums.indices, id: \.self) { index in
                            NavigationLink(destination: AlbumDetailView(albumName: albums[index].name,
                                                                         artistName: albums[index].artist,
                                                                         coverURL: albums[index].coverURL)) {
                                ShelfCard(
                                    title: albums[index].name,
                                    subtitle: albums[index].artist,
                                    coverURL: albums[index].coverURL
                                )
                            }
                            .buttonStyle(PressableButtonStyle(scale: 0.97))
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    // MARK: - 数据

    private func load() async {
        isLoading = true
        errorText = nil

        let songsTask = Task { try? await NetEaseAPI.shared.searchSongs(keyword: artistName, limit: 50) }
        let albumsTask = Task { try? await NetEaseAPI.shared.searchAlbums(keyword: artistName, limit: 20) }
        let infoTask = Task { try? await NetEaseAPI.shared.searchArtists(keyword: artistName, limit: 10) }

        let loadedSongs = (await songsTask.value) ?? []
        let loadedAlbums = (await albumsTask.value) ?? []
        let loadedArtists = (await infoTask.value) ?? []

        songs = loadedSongs
        albums = loadedAlbums
        info = loadedArtists.first(where: { $0.id == artistID }) ?? loadedArtists.first
        isLoading = false
        if loadedSongs.isEmpty {
            errorText = "没有找到「\(artistName)」的歌曲"
        }
    }
}
