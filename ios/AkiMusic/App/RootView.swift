import SwiftUI
import UIKit

enum AppTab: String, CaseIterable, Identifiable {

    case home
    case charts
    case search
    case library

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "首页"
        case .charts: return "排行榜"
        case .search: return "搜索"
        case .library: return "资料库"
        }
    }

    var icon: String {
        switch self {
        case .home: return "house.fill"
        case .charts: return "chart.bar.xaxis"
        case .search: return "magnifyingglass"
        case .library: return "music.note.house"
        }
    }
}

struct RootView: View {

    @EnvironmentObject private var player: AudioPlayer
    @EnvironmentObject private var library: LibraryStore

    @State private var tab: AppTab = .home
    @State private var compactBar = false
    @State private var showNowPlaying = false
    @State private var toast: String?

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.background.ignoresSafeArea()
            ambientBackground
            content
            bottomBar
        }
        .overlay(alignment: .top) {
            toastView
        }
        .task(id: toast) {
            guard toast != nil else { return }
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            withAnimation(Theme.spring) {
                toast = nil
            }
        }
        .onChange(of: tab) { _ in
            if compactBar {
                compactBar = false
            }
        }
        .onChange(of: player.currentSong?.id) { _ in
            if let song = player.currentSong {
                library.recordPlay(song)
            }
        }
        .onChange(of: player.errorMessage) { message in
            guard let message = message else { return }
            withAnimation(Theme.spring) {
                toast = message
            }
        }
        .fullScreenCover(isPresented: $showNowPlaying) {
            NowPlayingView()
                .environmentObject(player)
        }
        .preferredColorScheme(nil)
    }

    // MARK: - 内容

    @ViewBuilder
    private var content: some View {
        switch tab {
        case .home:
            HomeView(onScroll: updateChrome)
        case .charts:
            ChartsView(onScroll: updateChrome)
        case .search:
            SearchView()
        case .library:
            LibraryView(onScroll: updateChrome)
        }
    }

    private func updateChrome(_ compact: Bool) {
        guard compact != compactBar else { return }
        compactBar = compact
    }

    // MARK: - 环境光

    private var ambientBackground: some View {
        GeometryReader { geo in
            ZStack {
                Circle()
                    .fill(Theme.accent.opacity(0.16))
                    .frame(width: geo.size.width * 0.95)
                    .blur(radius: 90)
                    .offset(x: -geo.size.width * 0.28, y: -geo.size.height * 0.3)

                Circle()
                    .fill(Color(uiColor: .systemBlue).opacity(0.11))
                    .frame(width: geo.size.width * 0.85)
                    .blur(radius: 100)
                    .offset(x: geo.size.width * 0.34, y: geo.size.height * 0.06)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    // MARK: - 底部悬浮层

    private var bottomBar: some View {
        VStack(spacing: 8) {
            if player.currentSong != nil {
                MiniPlayer(onOpen: { showNowPlaying = true })
            }
            TabBar(selection: $tab, compact: compactBar)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .animation(Theme.spring, value: compactBar)
        .animation(Theme.softSpring, value: player.currentSong?.id)
    }

    // MARK: - Toast

    @ViewBuilder
    private var toastView: some View {
        if let toast = toast {
            Text(toast)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Capsule().fill(Color.black.opacity(0.82)))
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
                .padding(.horizontal, 32)
                .padding(.top, 6)
                .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

// MARK: - 悬浮玻璃标签栏

struct TabBar: View {

    @Binding var selection: AppTab
    var compact: Bool

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { item in
                Button {
                    withAnimation(Theme.spring) {
                        selection = item
                    }
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: item.icon)
                            .font(.system(size: compact ? 17 : 20, weight: .semibold))
                            .frame(height: 22)

                        if !compact {
                            Text(item.title)
                                .font(.system(size: 10, weight: .medium))
                                .lineLimit(1)
                        }
                    }
                    .foregroundStyle(selection == item ? Theme.accent : Theme.secondaryText)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PressableButtonStyle(scale: 0.92))
            }
        }
        .padding(.horizontal, 6)
        .frame(height: compact ? 50 : 64)
        .background(GlassBackground(cornerRadius: compact ? 25 : 32))
    }
}
