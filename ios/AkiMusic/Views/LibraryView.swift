import SwiftUI
import UIKit

struct LibraryView: View {

    var onScroll: (Bool) -> Void = { _ in }

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    header

                    entryCards

                    if library.likedSongs.isEmpty && library.recentSongs.isEmpty {
                        EmptyStateView(
                            icon: "heart.text.square",
                            title: "资料库还是空的",
                            message: "在歌曲右侧点一下爱心，或者随便播一首歌，这里就会记下来。"
                        )
                    }

                    if !library.recentSongs.isEmpty {
                        recentSection
                    }

                    if !library.likedSongs.isEmpty {
                        likedSection
                    }
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
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("收藏与足迹")
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.1)
                .textCase(.uppercase)
                .foregroundStyle(Theme.secondaryText)

            Text("资料库")
                .font(Theme.largeTitle)
                .foregroundStyle(Theme.text)
        }
    }

    private var entryCards: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: SongListView(title: "我喜欢的音乐",
                                                     subtitle: "共 \(library.likedSongs.count) 首",
                                                     songs: library.likedSongs)) {
                LibraryEntryCard(
                    icon: "heart.fill",
                    title: "我喜欢的音乐",
                    subtitle: "\(library.likedSongs.count) 首",
                    tint: Theme.accent
                )
            }
            .buttonStyle(PressableButtonStyle(scale: 0.97))

            NavigationLink(destination: SongListView(title: "最近播放",
                                                     subtitle: "共 \(library.recentSongs.count) 首",
                                                     songs: library.recentSongs)) {
                LibraryEntryCard(
                    icon: "clock.fill",
                    title: "最近播放",
                    subtitle: "\(library.recentSongs.count) 首",
                    tint: Color(uiColor: .systemBlue)
                )
            }
            .buttonStyle(PressableButtonStyle(scale: 0.97))
        }
    }

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(
                title: "最近播放",
                trailingTitle: "全部",
                destination: AnyView(SongListView(title: "最近播放", songs: library.recentSongs))
            )

            ForEach(Array(library.recentSongs.prefix(4).indices), id: \.self) { index in
                let song = library.recentSongs[index]
                SongRow(
                    song: song,
                    isPlaying: player.currentSong?.id == song.id && player.isPlaying,
                    isLiked: library.isLiked(song),
                    onTap: { player.play(songs: library.recentSongs, startAt: index) },
                    onLike: { library.toggleLike(song) }
                )
            }
        }
    }

    private var likedSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(
                title: "我喜欢的音乐",
                trailingTitle: "全部",
                destination: AnyView(SongListView(title: "我喜欢的音乐", songs: library.likedSongs))
            )

            ForEach(Array(library.likedSongs.prefix(4).indices), id: \.self) { index in
                let song = library.likedSongs[index]
                SongRow(
                    song: song,
                    isPlaying: player.currentSong?.id == song.id && player.isPlaying,
                    isLiked: library.isLiked(song),
                    onTap: { player.play(songs: library.likedSongs, startAt: index) },
                    onLike: { library.toggleLike(song) }
                )
            }
        }
    }
}

// MARK: - 入口卡片

struct LibraryEntryCard: View {

    let icon: String
    let title: String
    let subtitle: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(tint)

            Spacer(minLength: 0)

            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.text)
                .lineLimit(1)

            Text(subtitle)
                .font(.system(size: 12))
                .foregroundStyle(Theme.secondaryText)
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 116, alignment: .leading)
        .glass(cornerRadius: 18)
    }
}
