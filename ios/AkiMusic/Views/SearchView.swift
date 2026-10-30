import SwiftUI

enum SearchScope: String, CaseIterable, Identifiable {

    case songs
    case playlists
    case albums
    case artists

    var id: String { rawValue }

    var title: String {
        switch self {
        case .songs: return "单曲"
        case .playlists: return "歌单"
        case .albums: return "专辑"
        case .artists: return "歌手"
        }
    }
}

struct SearchView: View {

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var path = NavigationPath()
    @State private var query = ""
    @State private var scope: SearchScope = .songs

    @State private var songs: [Song] = []
    @State private var playlists: [PlaylistSummary] = []
    @State private var albums: [AlbumSummary] = []
    @State private var artists: [ArtistSummary] = []

    @State private var isLoading = false
    @State private var hasSearched = false
    @State private var errorText: String?
    @State private var searchTask: Task<Void, Never>?

    private let hotKeywords = ["周杰伦", "告五人", "林俊杰", "陈奕迅", "五月天", "邓紫棋",
                               "薛之谦", "李荣浩", "粤语", "轻音乐", "钢琴", "说唱"]

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                topBar

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 16) {
                        if !hasSearched {
                            hotSection
                        } else if isLoading {
                            loadingView
                        } else if let errorText = errorText {
                            EmptyStateView(icon: "magnifyingglass", title: "没有找到结果", message: errorText)
                        } else {
                            results
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 150)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
            }
            .background(Theme.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .akiDestinations()
        }
        .onChange(of: query) { newValue in
            scheduleSearch(newValue)
        }
    }

    // MARK: - 顶部

    private var topBar: some View {
        VStack(spacing: 12) {
            GlassField {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.secondaryText)

                    TextField("搜索歌曲、歌单、歌手", text: $query)
                        .textFieldStyle(.plain)
                        .font(.system(size: 16))
                        .submitLabel(.search)
                        .autocorrectionDisabled(true)
                        .textInputAutocapitalization(.never)

                    if !query.isEmpty {
                        Button {
                            query = ""
                            clearResults()
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 15))
                                .foregroundStyle(Theme.secondaryText)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if hasSearched {
                scopeBar
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 6)
    }

    private var scopeBar: some View {
        HStack(spacing: 2) {
            ForEach(SearchScope.allCases) { item in
                Button {
                    withAnimation(Theme.quickSpring) {
                        scope = item
                    }
                } label: {
                    Text(item.title)
                        .font(.system(size: 13, weight: scope == item ? .semibold : .regular))
                        .foregroundStyle(scope == item ? Color.white : Theme.text)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 7)
                        .background {
                            if scope == item {
                                Capsule().fill(Theme.accent)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(PressableButtonStyle(scale: 0.95))
            }
        }
        .padding(4)
        .background(GlassSurface(shape: Capsule()))
        .animation(Theme.quickSpring, value: scope)
    }

    // MARK: - 热门

    private var hotSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("热门搜索")
                .font(Theme.sectionTitle)
                .foregroundStyle(Theme.text)
                .padding(.top, 6)

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 82), spacing: 10)],
                      alignment: .leading,
                      spacing: 10) {
                ForEach(hotKeywords, id: \.self) { keyword in
                    GlassChip(title: keyword) {
                        query = keyword
                        performSearchSoon(keyword)
                    }
                }
            }

            Text("搜索由网易云音乐公开接口提供，支持单曲、歌单、专辑与歌手。")
                .font(.system(size: 12))
                .foregroundStyle(Theme.tertiaryText)
                .padding(.top, 4)
        }
    }

    // MARK: - 结果

    @ViewBuilder
    private var results: some View {
        switch scope {
        case .songs:
            if songs.isEmpty {
                EmptyStateView(title: "没有找到单曲", message: "换一个关键词试试。")
            } else {
                ForEach(songs.indices, id: \.self) { index in
                    let song = songs[index]
                    SongRow(
                        song: song,
                        isPlaying: player.currentSong?.id == song.id && player.isPlaying,
                        isLiked: library.isLiked(song),
                        onTap: { player.play(songs: songs, startAt: index) },
                        onLike: { library.toggleLike(song) }
                    )
                }
            }

        case .playlists:
            if playlists.isEmpty {
                EmptyStateView(title: "没有找到歌单", message: "换一个关键词试试。")
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)],
                          alignment: .leading,
                          spacing: 18) {
                    ForEach(playlists.indices, id: \.self) { index in
                        NavigationLink(value: Route.playlist(playlists[index].id)) {
                            PlaylistCard(playlist: playlists[index])
                        }
                        .buttonStyle(PressableButtonStyle(scale: 0.97))
                    }
                }
            }

        case .albums:
            if albums.isEmpty {
                EmptyStateView(title: "没有找到专辑", message: "换一个关键词试试。")
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)],
                          alignment: .leading,
                          spacing: 18) {
                    ForEach(albums.indices, id: \.self) { index in
                        NavigationLink(value: Route.album(name: albums[index].name,
                                                          artist: albums[index].artist,
                                                          cover: albums[index].coverURL)) {
                            AlbumGridCard(album: albums[index])
                        }
                        .buttonStyle(PressableButtonStyle(scale: 0.97))
                    }
                }
            }

        case .artists:
            if artists.isEmpty {
                EmptyStateView(title: "没有找到歌手", message: "换一个关键词试试。")
            } else {
                ForEach(artists.indices, id: \.self) { index in
                    NavigationLink(value: Route.artist(id: artists[index].id, name: artists[index].name)) {
                        ArtistRow(artist: artists[index])
                    }
                    .buttonStyle(PressableButtonStyle(scale: 0.99))
                }
            }
        }
    }

    private var loadingView: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(0..<7, id: \.self) { _ in
                SongRowSkeleton()
            }
        }
        .padding(.top, 6)
    }

    // MARK: - 搜索逻辑

    private func scheduleSearch(_ raw: String) {
        searchTask?.cancel()
        let keyword = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty else {
            clearResults()
            return
        }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            if Task.isCancelled { return }
            await performSearch(keyword)
        }
    }

    private func performSearchSoon(_ keyword: String) {
        searchTask?.cancel()
        searchTask = Task {
            await performSearch(keyword)
        }
    }

    private func performSearch(_ keyword: String) async {
        isLoading = true
        hasSearched = true
        errorText = nil

        let songsTask = Task { try? await NetEaseAPI.shared.searchSongs(keyword: keyword, limit: 50) }
        let playlistsTask = Task { try? await NetEaseAPI.shared.searchPlaylists(keyword: keyword, limit: 30) }
        let albumsTask = Task { try? await NetEaseAPI.shared.searchAlbums(keyword: keyword, limit: 30) }
        let artistsTask = Task { try? await NetEaseAPI.shared.searchArtists(keyword: keyword, limit: 20) }

        let loadedSongs = await songsTask.value ?? []
        let loadedPlaylists = await playlistsTask.value ?? []
        let loadedAlbums = await albumsTask.value ?? []
        let loadedArtists = await artistsTask.value ?? []

        if Task.isCancelled { return }

        songs = loadedSongs
        playlists = loadedPlaylists
        albums = loadedAlbums
        artists = loadedArtists
        isLoading = false

        if loadedSongs.isEmpty && loadedPlaylists.isEmpty && loadedAlbums.isEmpty && loadedArtists.isEmpty {
            errorText = "换个关键词试试，比如「\(keyword)」的完整歌名。"
        }
    }

    private func clearResults() {
        searchTask?.cancel()
        songs = []
        playlists = []
        albums = []
        artists = []
        hasSearched = false
        isLoading = false
        errorText = nil
    }
}

// MARK: - 专辑卡片

struct AlbumGridCard: View {

    let album: AlbumSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            SquareCover(urlString: album.coverURL)
            Text(album.name)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.text)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Text(album.artist)
                .font(.system(size: 11))
                .foregroundStyle(Theme.secondaryText)
                .lineLimit(1)
        }
    }
}

// MARK: - 歌手行

struct ArtistRow: View {

    let artist: ArtistSummary

    var body: some View {
        HStack(spacing: 14) {
            Color.clear
                .frame(width: 56, height: 56)
                .overlay(CoverImage(urlString: artist.coverURL))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(artist.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.text)
                    .lineLimit(1)

                Text("\(artist.musicSize) 首单曲 · \(artist.albumSize) 张专辑")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.tertiaryText)
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }
}
