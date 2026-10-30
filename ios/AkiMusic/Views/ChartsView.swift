import SwiftUI
import Foundation

struct ChartsView: View {

    var onScroll: (Bool) -> Void = { _ in }

    @EnvironmentObject private var player: AudioPlayer

    @State private var path = NavigationPath()
    @State private var items: [ToplistItem] = []
    @State private var isLoading = true
    @State private var errorText: String?

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    header
                    if isLoading {
                        loadingView
                    } else if let errorText = errorText {
                        ErrorStateView(message: errorText, retry: {
                            Task { await load() }
                        })
                    } else if items.isEmpty {
                        EmptyStateView(title: "暂无榜单", message: "稍后再来看看吧。")
                    } else {
                        ForEach(items.indices, id: \.self) { index in
                            NavigationLink(value: Route.playlist(items[index].id)) {
                                ToplistRow(item: items[index])
                            }
                            .buttonStyle(PressableButtonStyle(scale: 0.99))

                            if index < items.count - 1 {
                                Rectangle()
                                    .fill(Theme.hairline)
                                    .frame(height: 0.5)
                                    .padding(.leading, 96)
                            }
                        }
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
        .task {
            if items.isEmpty {
                await load()
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("网易云 · 实时更新")
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.1)
                .textCase(.uppercase)
                .foregroundStyle(Theme.secondaryText)

            Text("排行榜")
                .font(Theme.largeTitle)
                .foregroundStyle(Theme.text)
        }
        .padding(.bottom, 18)
    }

    private var loadingView: some View {
        VStack(alignment: .leading, spacing: 18) {
            ForEach(0..<6, id: \.self) { _ in
                HStack(spacing: 14) {
                    SkeletonBlock(width: 76, height: 76, cornerRadius: 14)
                    VStack(alignment: .leading, spacing: 8) {
                        SkeletonBlock(width: 120, height: 14)
                        SkeletonBlock(width: 80, height: 11)
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(.top, 4)
    }

    private func load() async {
        isLoading = true
        errorText = nil
        do {
            items = try await NetEaseAPI.shared.topLists()
            isLoading = false
        } catch {
            isLoading = false
            errorText = (error as? LocalizedError)?.errorDescription ?? "榜单加载失败，请稍后再试"
        }
    }
}

struct ToplistRow: View {

    let item: ToplistItem

    var body: some View {
        HStack(spacing: 14) {
            CoverImage(urlString: item.coverURL)
                .frame(width: 76, height: 76)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.1), lineWidth: 0.8)
                )

            VStack(alignment: .leading, spacing: 5) {
                Text(item.name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.text)
                    .lineLimit(1)

                Text("\(item.updateFrequency) · 共 \(item.trackCount) 首")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.secondaryText)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.tertiaryText)
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}
