import SwiftUI
import Foundation

struct HomeView: View {

    var onScroll: (Bool) -> Void = { _ in }

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var path = NavigationPath()
    @State private var topLists: [ToplistItem] = []
    @State private var newSongs: [Song] = []
    @State private var playlists: [PlaylistSummary] = []
    @State private var isLoading = true
    @State private var errorText: String?
    @State private var featuredIndex = 0

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 26) {
                    header

                    if isLoading {
                        loadingView
                    } else if let errorText = errorText {
                        ErrorStateView(message: errorText, retry: {
                            Task { await load() }
                        })
                    } else {
                        content
                    }

                    footer
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 140)
                .background(ScrollOffsetObserver())
            }
            .coordinateSpace(name: ScrollSpace.name)
            .scrollIndicators(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .onPreferenceChange(ScrollCompactKey.self) { value in
                onScroll(value)
            }
            .toolbar(.hidden, for: .navigationBar)
            .akiDestinations()
        }
        .task {
            if topLists.isEmpty {
                await load()
            }
        }
    }

    // MARK: - 数据

    private func load() async {
        isLoading = true
        errorText = nil
        do {
            let toplistsTask = Task { try await NetEaseAPI.shared.topLists() }
            let newsTask = Task { try await NetEaseAPI.shared.newSongs(limit: 12) }
            let playlistsTask = Task { try await NetEaseAPI.shared.recommendedPlaylists(limit: 12) }

            let loadedTopLists = try await toplistsTask.value
            let loadedSongs = try await newsTask.value
            let loadedPlaylists = try await playlistsTask.value

            topLists = loadedTopLists
            newSongs = loadedSongs
            playlists = loadedPlaylists
            if featuredIndex >= max(loadedTopLists.prefix(5).count, 1) {
                featuredIndex = 0
            }
            isLoading = false
        } catch {
            isLoading = false
            errorText = (error as? LocalizedError)?.errorDescription ?? "加载失败，请检查网络后重试"
        }
    }

    // MARK: - 顶部

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(dateLine)
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.1)
                .textCase(.uppercase)
                .foregroundStyle(Theme.secondaryText)

            Text("现在就听")
                .font(Theme.largeTitle)
                .foregroundStyle(Theme.text)
        }
    }

    private var dateLine: String {
        HomeView.dateFormatter.string(from: Date())
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日 EEEE"
        return formatter
    }()

    // MARK: - 内容

    @ViewBuilder
    private var content: some View {
        featuredSection
        recentShelf
        newSongsShelf
        recommendedShelf
    }

    private var featured: [ToplistItem] {
        Array(topLists.prefix(5))
    }

    @ViewBuilder
    private var featuredSection: some View {
        if !featured.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "精选榜单")

                TabView(selection: $featuredIndex) {
                    ForEach(featured.indices, id: \.self) { index in
                        FeaturedCard(
                            item: featured[index],
                            onPlay: { playToplist(featured[index]) },
                            onOpen: { path.append(Route.playlist(featured[index].id)) }
                        )
                        .tag(index)
                    }
                }
                .frame(height: 200)
                .tabViewStyle(.page(indexDisplayMode: .never))

                HStack(spacing: 6) {
                    ForEach(featured.indices, id: \.self) { index in
                        Capsule()
                            .fill(index == featuredIndex ? Theme.text : Theme.text.opacity(0.18))
                            .frame(width: index == featuredIndex ? 16 : 6, height: 6)
                    }
                }
                .frame(maxWidth: .infinity)
                .animation(Theme.spring, value: featuredIndex)
            }
        }
    }

    @ViewBuilder
    private var recentShelf: some View {
        let items = Array(library.recentSongs.prefix(10))
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(
                    title: "继续聆听",
                    trailingTitle: "全部",
                    destination: AnyView(SongListView(title: "最近播放", subtitle: "最近听过 \(library.recentSongs.count) 首", songs: library.recentSongs))
                )

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(items.indices, id: \.self) { index in
                            Button {
                                player.play(songs: items, startAt: index)
                            } label: {
                                ShelfCard(
                                    title: items[index].name,
                                    subtitle: items[index].artist,
                                    coverURL: items[index].coverURL
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

    @ViewBuilder
    private var newSongsShelf: some View {
        if !newSongs.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(
                    title: "新歌速递",
                    trailingTitle: "全部",
                    destination: AnyView(SongListView(title: "新歌速递", subtitle: "每日更新", songs: newSongs))
                )

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(newSongs.indices, id: \.self) { index in
                            Button {
                                player.play(songs: newSongs, startAt: index)
                            } label: {
                                ShelfCard(
                                    title: newSongs[index].name,
                                    subtitle: newSongs[index].artist,
                                    coverURL: newSongs[index].coverURL,
                                    badge: newSongs[index].isVIP ? "VIP" : nil
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

    @ViewBuilder
    private var recommendedShelf: some View {
        if !playlists.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(
                    title: "为你推荐",
                    trailingTitle: "全部",
                    destination: AnyView(AllPlaylistsView(playlists: playlists))
                )

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(playlists.indices, id: \.self) { index in
                            NavigationLink(value: Route.playlist(playlists[index].id)) {
                                ShelfCard(
                                    title: playlists[index].name,
                                    subtitle: "\(TimeFormat.playCount(playlists[index].playCount)) 次播放",
                                    coverURL: playlists[index].coverURL
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

    private var footer: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("AkiMusic")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.tertiaryText)
            Text("音乐数据来自网易云音乐公开接口，仅供学习与个人试听。")
                .font(.system(size: 11))
                .foregroundStyle(Theme.tertiaryText)
        }
        .padding(.top, 8)
    }

    // MARK: - 加载态

    private var loadingView: some View {
        VStack(alignment: .leading, spacing: 22) {
            SkeletonBlock(width: nil, height: 200, cornerRadius: 20)
            ShelfSkeleton()
            ShelfSkeleton()
            VStack(alignment: .leading, spacing: 12) {
                SongRowSkeleton()
                SongRowSkeleton()
                SongRowSkeleton()
            }
        }
    }

    // MARK: - 动作

    private func playToplist(_ item: ToplistItem) {
        if !item.tracks.isEmpty {
            player.play(songs: item.tracks, startAt: 0)
        } else {
            Task { await player.playPlaylist(id: item.id) }
        }
    }
}

// MARK: - 精选榜单卡片

struct FeaturedCard: View {

    let item: ToplistItem
    var onPlay: () -> Void
    var onOpen: () -> Void

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            FillImage(urlString: item.coverURL)

            LinearGradient(
                colors: [Color.black.opacity(0.05), Color.black.opacity(0.35), Color.black.opacity(0.82)],
                startPoint: .top,
                endPoint: .bottom
            )

            // 整卡打开详情（位于播放按钮之下，避免手势冲突）
            Button(action: onOpen) {
                Color.clear.contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 5) {
                Text("精选榜单")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.4)
                    .foregroundStyle(Color.white.opacity(0.75))

                Text(item.name)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)

                Text("\(item.updateFrequency) · \(item.trackCount) 首")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.65))
            }
            .padding(18)
            .allowsHitTesting(false)

            HStack {
                Spacer(minLength: 0)
                Button(action: onPlay) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 46, height: 46)
                        .background(Circle().fill(Color.white.opacity(0.22)))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.35), lineWidth: 1))
                        .contentShape(Circle())
                }
                .buttonStyle(PressableButtonStyle())
                .padding(18)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.card, style: .continuous)
                .strokeBorder(Color.white.opacity(0.14), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.22), radius: 18, x: 0, y: 10)
    }
}

// MARK: - 全部推荐歌单

struct AllPlaylistsView: View {

    let playlists: [PlaylistSummary]

    private let columns = [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 20) {
                ForEach(playlists.indices, id: \.self) { index in
                    NavigationLink(destination: PlaylistView(playlistID: playlists[index].id)) {
                        PlaylistCard(playlist: playlists[index])
                    }
                    .buttonStyle(PressableButtonStyle(scale: 0.97))
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 150)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("为你推荐")
        .navigationBarTitleDisplayMode(.inline)
    }
}
