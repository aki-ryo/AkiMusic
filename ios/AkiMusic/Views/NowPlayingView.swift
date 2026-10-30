import SwiftUI
import UIKit

struct NowPlayingView: View {

    @EnvironmentObject private var player: AudioPlayer
    @Environment(\.dismiss) private var dismiss

    @State private var dragValue: Double?
    @State private var showQueue = false

    var body: some View {
        ZStack {
            background

            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 20)
                    .padding(.top, 6)

                Spacer(minLength: 6)

                cover

                titleBlock
                    .padding(.top, 20)
                    .padding(.horizontal, 28)

                lyricsArea
                    .padding(.top, 8)

                progressBlock
                    .padding(.horizontal, 28)
                    .padding(.top, 6)

                controls
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, 10)
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showQueue) {
            QueueSheet()
                .environmentObject(player)
        }
    }

    // MARK: - 背景

    private var background: some View {
        ZStack {
            Color.black

            if let cover = player.currentSong?.coverURL, !cover.isEmpty {
                FillImage(urlString: cover)
                    .blur(radius: 60)
                    .scaleEffect(1.25)
                    .opacity(0.9)
            } else {
                RadialGradient(colors: [Theme.accent.opacity(0.45), Color.black],
                               center: .top,
                               startRadius: 40,
                               endRadius: 420)
            }

            Color.black.opacity(0.55)

            LinearGradient(
                colors: [Color.black.opacity(0.1), Color.black.opacity(0.25), Color.black.opacity(0.8)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .ignoresSafeArea()
    }

    // MARK: - 顶部

    private var topBar: some View {
        HStack(spacing: 8) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())

            Spacer(minLength: 0)

            VStack(spacing: 2) {
                Text("正在播放")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(1.6)
                    .foregroundStyle(Color.white.opacity(0.55))

                Text(albumLine)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.85))
                    .lineLimit(1)
                    .frame(maxWidth: 190)
            }

            Spacer(minLength: 0)

            Button {
                showQueue = true
            } label: {
                Image(systemName: "list.bullet")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())
        }
    }

    private var albumLine: String {
        guard let song = player.currentSong else { return "AkiMusic" }
        return song.album.isEmpty ? song.artist : song.album
    }

    // MARK: - 封面

    private var cover: some View {
        CoverImage(urlString: player.currentSong?.coverURL)
            .frame(width: coverSize, height: coverSize)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.12), lineWidth: 0.8)
            )
            .shadow(color: Color.black.opacity(0.35), radius: 28, x: 0, y: 14)
            .scaleEffect(player.isPlaying ? 1 : 0.94)
            .animation(Theme.softSpring, value: player.isPlaying)
    }

    private var coverSize: CGFloat {
        let width = UIScreen.main.bounds.width
        let height = UIScreen.main.bounds.height
        let limit: CGFloat = height > 780 ? 300 : 220
        return min(width - 96, limit)
    }

    // MARK: - 标题

    private var titleBlock: some View {
        VStack(spacing: 5) {
            Text(player.currentSong?.name ?? "未在播放")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)

            HStack(spacing: 8) {
                Text(player.currentSong?.artist ?? "—")
                    .font(.system(size: 17))
                    .foregroundStyle(Color.white.opacity(0.6))
                    .lineLimit(1)

                if player.currentSong?.isVIP == true {
                    Text("VIP")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(Theme.accent)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .overlay(
                            RoundedRectangle(cornerRadius: 3, style: .continuous)
                                .strokeBorder(Theme.accent.opacity(0.75), lineWidth: 0.8)
                        )
                }
            }
        }
    }

    // MARK: - 歌词

    private var lyricsArea: some View {
        Group {
            if player.lyrics.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "text.quote")
                        .font(.system(size: 20, weight: .light))
                        .foregroundStyle(Color.white.opacity(0.3))
                    Text(player.isLoading ? "正在加载歌词…" : "暂无歌词")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.45))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { proxy in
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(spacing: 16) {
                            ForEach(player.lyrics.lines) { line in
                                LyricRow(line: line, isCurrent: line.id == player.currentLyricIndex)
                                    .id(line.id)
                                    .onTapGesture {
                                        player.seek(to: line.time)
                                    }
                            }
                        }
                        .padding(.vertical, 58)
                        .frame(maxWidth: .infinity)
                    }
                    .onChange(of: player.currentLyricIndex) { index in
                        withAnimation(.easeInOut(duration: 0.45)) {
                            proxy.scrollTo(index, anchor: .center)
                        }
                    }
                }
            }
        }
        .frame(maxHeight: .infinity)
    }

    // MARK: - 进度

    private var progressBlock: some View {
        VStack(spacing: 5) {
            GeometryReader { geo in
                let width = geo.size.width
                let total = max(player.duration, 0.001)
                let current = dragValue ?? player.currentTime
                let ratio = CGFloat(min(max(current / total, 0), 1))

                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.white.opacity(0.22))
                        .frame(height: 4)

                    Capsule()
                        .fill(Color.white)
                        .frame(width: max(width * ratio, 0), height: 4)

                    Circle()
                        .fill(Color.white)
                        .frame(width: 11, height: 11)
                        .offset(x: max(width * ratio - 5.5, 0))
                        .opacity(dragValue == nil ? 0 : 1)
                }
                .frame(height: 18)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let progress = min(max(value.location.x / width, 0), 1)
                            dragValue = Double(progress) * total
                        }
                        .onEnded { _ in
                            if let target = dragValue {
                                player.seek(to: target)
                            }
                            dragValue = nil
                        }
                )
            }
            .frame(height: 18)

            HStack {
                Text(TimeFormat.clock(dragValue ?? player.currentTime))
                Spacer()
                Text(TimeFormat.clock(player.duration))
            }
            .font(.system(size: 11, weight: .medium))
            .foregroundStyle(Color.white.opacity(0.45))
        }
    }

    // MARK: - 控制

    private var controls: some View {
        HStack(spacing: 0) {
            Button {
                player.toggleShuffle()
            } label: {
                Image(systemName: "shuffle")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(player.isShuffle ? Theme.accent : Color.white.opacity(0.85))
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())

            Button {
                player.previous()
            } label: {
                Image(systemName: "backward.fill")
                    .font(.system(size: 24, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())

            Button {
                player.togglePlayPause()
            } label: {
                ZStack {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 56, height: 56)

                    if player.isLoading {
                        ProgressView()
                            .tint(.black)
                    } else {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 21, weight: .bold))
                            .foregroundStyle(.black)
                            .offset(x: player.isPlaying ? 0 : 1.5)
                    }
                }
            }
            .buttonStyle(PressableButtonStyle(scale: 0.93))
            .padding(.horizontal, 12)

            Button {
                player.next()
            } label: {
                Image(systemName: "forward.fill")
                    .font(.system(size: 24, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())

            Button {
                player.cycleRepeatMode()
            } label: {
                Image(systemName: player.repeatMode.iconName)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(player.repeatMode == .off ? Color.white.opacity(0.45) : Theme.accent)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressableButtonStyle())
        }
    }
}

// MARK: - 歌词行

struct LyricRow: View {

    let line: LyricLine
    let isCurrent: Bool

    var body: some View {
        VStack(spacing: 5) {
            Text(line.text)
                .font(.system(size: isCurrent ? 22 : 20, weight: isCurrent ? .semibold : .regular))
                .foregroundStyle(isCurrent ? Color.white : Color.white.opacity(0.35))
                .blur(radius: isCurrent ? 0 : 2.5)
                .scaleEffect(isCurrent ? 1 : 0.96)
                .multilineTextAlignment(.center)

            if let translation = line.translation, !translation.isEmpty {
                Text(translation)
                    .font(.system(size: isCurrent ? 14 : 13, weight: .regular))
                    .foregroundStyle(Color.white.opacity(isCurrent ? 0.7 : 0.22))
                    .blur(radius: isCurrent ? 0 : 1.6)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .animation(.easeInOut(duration: 0.35), value: isCurrent)
        .contentShape(Rectangle())
    }
}

// MARK: - 播放队列

struct QueueSheet: View {

    @EnvironmentObject private var player: AudioPlayer
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(player.queue.indices, id: \.self) { index in
                        let song = player.queue[index]
                        Button {
                            player.jump(to: index)
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                CoverImage(urlString: song.coverURL)
                                    .frame(width: 42, height: 42)
                                    .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(song.name)
                                        .font(.system(size: 15, weight: .medium))
                                        .foregroundStyle(index == player.currentIndex ? Theme.accent : Theme.text)
                                        .lineLimit(1)
                                    Text(song.artist)
                                        .font(.system(size: 12))
                                        .foregroundStyle(Theme.secondaryText)
                                        .lineLimit(1)
                                }

                                Spacer(minLength: 8)

                                if index == player.currentIndex {
                                    PlayingIndicator()
                                } else {
                                    Text(TimeFormat.duration(ms: song.duration))
                                        .font(.system(size: 11))
                                        .foregroundStyle(Theme.tertiaryText)
                                }
                            }
                            .padding(.vertical, 6)
                            .padding(.horizontal, 20)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PressableButtonStyle(scale: 0.99))
                    }
                }
                .padding(.vertical, 8)
            }
            .scrollIndicators(.hidden)
            .navigationTitle("播放队列")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("关闭") {
                        dismiss()
                    }
                    .foregroundStyle(Theme.accent)
                }
            }
        }
        .presentationDragIndicator(.visible)
    }
}
