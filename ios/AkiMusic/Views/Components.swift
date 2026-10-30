import SwiftUI

// MARK: - 滚动方向 / 收缩

enum ScrollSpace {
    static let name = "aki.scroll"
}

struct ScrollCompactKey: PreferenceKey {

    static var defaultValue: Bool = false

    static func reduce(value: inout Bool, nextValue: () -> Bool) {
        value = value || nextValue()
    }
}

/// 放在 ScrollView 内容上，向下滚动超过阈值时把标签栏切成紧凑态。
struct ScrollOffsetObserver: View {

    var threshold: CGFloat = 34

    var body: some View {
        GeometryReader { geo in
            let minY = geo.frame(in: .named(ScrollSpace.name)).minY
            Color.clear.preference(key: ScrollCompactKey.self, value: (-minY) > threshold)
        }
    }
}

// MARK: - 导航目的地

struct DestinationInstaller: ViewModifier {

    func body(content: Content) -> some View {
        content.navigationDestination(for: Route.self) { route in
            switch route {
            case .playlist(let id):
                PlaylistView(playlistID: id)
            case .album(let name, let artist, let cover):
                AlbumDetailView(albumName: name, artistName: artist, coverURL: cover)
            case .artist(let id, let name):
                ArtistView(artistID: id, artistName: name)
            }
        }
    }
}

extension View {
    func akiDestinations() -> some View {
        modifier(DestinationInstaller())
    }
}

// MARK: - 封面

struct CoverImage: View {

    let urlString: String?

    var body: some View {
        if let urlString = urlString, !urlString.isEmpty, let url = URL(string: urlString) {
            AsyncImage(url: url, transaction: Transaction(animation: .easeInOut(duration: 0.25))) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .failure:
                    placeholder
                case .empty:
                    placeholder.overlay(ProgressView().tint(Color.white.opacity(0.6)))
                default:
                    placeholder
                }
            }
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.placeholderFill, Theme.placeholderFill.opacity(0.55)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Image(systemName: "music.note")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.secondaryText)
        }
    }
}

/// 铺满父容器的封面，用于 hero、播放页背景。
struct FillImage: View {

    let urlString: String?

    var body: some View {
        GeometryReader { geo in
            CoverImage(urlString: urlString)
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
        }
    }
}

/// 1:1 的方形封面。
struct SquareCover: View {

    let urlString: String?
    var cornerRadius: CGFloat = Theme.Radius.thumb

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay(CoverImage(urlString: urlString))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 0.8)
            )
    }
}

// MARK: - 播放中指示器

struct PlayingIndicator: View {

    @State private var animate = false

    private let tall: [CGFloat] = [12, 16, 9]
    private let short: [CGFloat] = [5, 8, 6]

    var body: some View {
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<3, id: \.self) { index in
                Capsule()
                    .fill(Theme.accent)
                    .frame(width: 3, height: animate ? tall[index] : short[index])
                    .animation(
                        .easeInOut(duration: 0.45)
                            .repeatForever(autoreverses: true)
                            .delay(Double(index) * 0.14),
                        value: animate
                    )
            }
        }
        .frame(width: 14, height: 16, alignment: .bottom)
        .onAppear { animate = true }
    }
}

// MARK: - 区块标题

struct SectionHeader: View {

    let title: String
    var trailingTitle: String? = nil
    var trailingAction: (() -> Void)? = nil
    var destination: AnyView? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(Theme.sectionTitle)
                .foregroundStyle(Theme.text)

            Spacer(minLength: 8)

            if let trailingTitle = trailingTitle {
                if let destination = destination {
                    NavigationLink(destination: destination) {
                        trailingLabel(trailingTitle)
                    }
                    .buttonStyle(PressableButtonStyle(scale: 0.94))
                } else if let trailingAction = trailingAction {
                    Button(action: trailingAction) {
                        trailingLabel(trailingTitle)
                    }
                    .buttonStyle(PressableButtonStyle(scale: 0.94))
                }
            }
        }
    }

    private func trailingLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(Theme.accent)
    }
}

// MARK: - 歌曲行

struct SongRow: View {

    let song: Song
    var index: Int? = nil
    var isPlaying: Bool = false
    var isLiked: Bool = false
    var showsLike: Bool = true
    var onTap: () -> Void
    var onLike: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onTap) {
                HStack(spacing: 12) {
                    if let index = index {
                        Text("\(index)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Theme.secondaryText)
                            .frame(width: 22, alignment: .trailing)
                    }

                    CoverImage(urlString: song.coverURL)
                        .frame(width: 48, height: 48)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(song.name)
                            .font(Theme.songTitle)
                            .foregroundStyle(isPlaying ? Theme.accent : Theme.text)
                            .lineLimit(1)

                        HStack(spacing: 5) {
                            if song.isVIP {
                                Text("VIP")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(Theme.accent)
                                    .padding(.horizontal, 4)
                                    .padding(.vertical, 1)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                                            .strokeBorder(Theme.accent.opacity(0.7), lineWidth: 0.8)
                                    )
                            }
                            Text(song.artist)
                                .font(Theme.songSubtitle)
                                .foregroundStyle(Theme.secondaryText)
                                .lineLimit(1)
                        }
                    }

                    Spacer(minLength: 4)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle(scale: 0.99))

            if isPlaying {
                PlayingIndicator()
            }

            if showsLike {
                Button {
                    onLike?()
                } label: {
                    Image(systemName: isLiked ? "heart.fill" : "heart")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(isLiked ? Theme.accent : Theme.secondaryText)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle(scale: 0.9))
            }
        }
        .padding(.vertical, 5)
    }
}

// MARK: - 书架卡片

struct ShelfCard: View {

    let title: String
    var subtitle: String = ""
    let coverURL: String?
    var badge: String? = nil
    var circular: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .bottomLeading) {
                if circular {
                    Color.clear
                        .aspectRatio(1, contentMode: .fit)
                        .overlay(CoverImage(urlString: coverURL))
                        .clipShape(Circle())
                } else {
                    SquareCover(urlString: coverURL)
                }

                if let badge = badge {
                    Text(badge)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color.black.opacity(0.45)))
                        .padding(8)
                }
            }
            .frame(width: 150, height: 150)

            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.text)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(width: 150, alignment: .leading)

            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(1)
                    .frame(width: 150, alignment: .leading)
            }
        }
        .frame(width: 150, alignment: .leading)
    }
}

// MARK: - 歌单网格卡片

struct PlaylistCard: View {

    let playlist: PlaylistSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            SquareCover(urlString: playlist.coverURL)
            Text(playlist.name)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Theme.text)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            if playlist.playCount > 0 {
                Text("\(TimeFormat.playCount(playlist.playCount)) 次播放")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(1)
            }
        }
    }
}

// MARK: - 按钮 / 胶囊

struct PlayAllButton: View {

    var title: String = "播放"
    var icon: String = "play.fill"
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .bold))
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .padding(.vertical, 11)
            .background(Capsule().fill(Theme.accent))
            .shadow(color: Theme.accent.opacity(0.35), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(PressableButtonStyle())
    }
}

struct GlassChip: View {

    let title: String
    var isSelected: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? Color.white : Theme.text)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Group {
                        if isSelected {
                            Capsule().fill(Theme.accent)
                        } else {
                            GlassSurface(shape: Capsule())
                        }
                    }
                )
                .contentShape(Capsule())
        }
        .buttonStyle(PressableButtonStyle())
    }
}

// MARK: - 骨架屏

struct SkeletonBlock: View {

    var width: CGFloat? = nil
    var height: CGFloat = 14
    var cornerRadius: CGFloat = 6

    @State private var pulse = false

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(Color.primary.opacity(pulse ? 0.12 : 0.05))
            .frame(width: width, height: height)
            .animation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true), value: pulse)
            .onAppear { pulse = true }
    }
}

struct SongRowSkeleton: View {

    var body: some View {
        HStack(spacing: 12) {
            SkeletonBlock(width: 48, height: 48, cornerRadius: 8)
            VStack(alignment: .leading, spacing: 7) {
                SkeletonBlock(width: 170, height: 12)
                SkeletonBlock(width: 100, height: 10)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 8)
    }
}

struct ShelfSkeleton: View {

    var body: some View {
        HStack(spacing: 14) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 8) {
                    SkeletonBlock(width: 150, height: 150, cornerRadius: 12)
                    SkeletonBlock(width: 110, height: 12)
                    SkeletonBlock(width: 70, height: 10)
                }
            }
        }
    }
}

// MARK: - 空 / 错误状态

struct EmptyStateView: View {

    var icon: String = "music.note.list"
    var title: String
    var message: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(Theme.secondaryText)
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.text)
            Text(message)
                .font(Theme.caption)
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
        .padding(.horizontal, 24)
    }
}

struct ErrorStateView: View {

    let message: String
    var retry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(Theme.secondaryText)
            Text(message)
                .font(Theme.secondary)
                .foregroundStyle(Theme.secondaryText)
                .multilineTextAlignment(.center)
            if let retry = retry {
                GlassCapsuleButton(action: retry) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 12, weight: .semibold))
                        Text("重试")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 44)
        .padding(.horizontal, 24)
    }
}

// MARK: - 歌曲集合页

/// 我喜欢的音乐 / 最近播放 / 新歌速递这类“一列歌”的页面。
struct SongListView: View {

    let title: String
    var subtitle: String? = nil
    let songs: [Song]

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                header
                    .padding(.bottom, 8)

                if songs.isEmpty {
                    EmptyStateView(title: "还没有歌曲", message: "去首页逛逛，遇到喜欢的歌点一下爱心就会出现在这里。")
                } else {
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
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)
            .padding(.bottom, 150)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        HStack(spacing: 12) {
            if let subtitle = subtitle {
                Text(subtitle)
                    .font(Theme.caption)
                    .foregroundStyle(Theme.secondaryText)
            }
            Spacer(minLength: 8)
            if !songs.isEmpty {
                PlayAllButton(title: "播放全部") {
                    player.play(songs: songs, startAt: 0)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
