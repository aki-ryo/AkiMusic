import SwiftUI
import Foundation

/// 悬浮在标签栏上方的迷你播放条。
struct MiniPlayer: View {

    @EnvironmentObject private var player: AudioPlayer

    var onOpen: () -> Void

    var body: some View {
        if let song = player.currentSong {
            HStack(spacing: 4) {
                Button(action: onOpen) {
                    HStack(spacing: 11) {
                        CoverImage(urlString: song.coverURL)
                            .frame(width: 44, height: 44)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(song.name)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Theme.text)
                                .lineLimit(1)

                            Text(song.artist)
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.secondaryText)
                                .lineLimit(1)
                        }

                        Spacer(minLength: 4)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle(scale: 0.99))

                Button {
                    player.togglePlayPause()
                } label: {
                    Group {
                        if player.isLoading {
                            ProgressView()
                                .scaleEffect(0.8)
                        } else {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(Theme.text)
                        }
                    }
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle(scale: 0.9))

                Button {
                    player.next()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.text)
                        .frame(width: 32, height: 34)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle(scale: 0.9))
            }
            .padding(.horizontal, 9)
            .padding(.vertical, 7)
            .background(GlassBackground(cornerRadius: 18))
            .overlay(alignment: .bottomLeading) {
                GeometryReader { geo in
                    Capsule()
                        .fill(Theme.accent)
                        .frame(width: geo.size.width * CGFloat(player.progress), height: 2)
                        .frame(maxHeight: .infinity, alignment: .bottom)
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 3)
                .allowsHitTesting(false)
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
