import SwiftUI

@main
struct AkiMusicApp: App {
    @StateObject private var player: AudioPlayer
    @StateObject private var library: LibraryStore

    init() {
        _player = StateObject(wrappedValue: AudioPlayer.shared)
        _library = StateObject(wrappedValue: LibraryStore.shared)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(player)
                .environmentObject(library)
                .tint(Theme.accent)
        }
    }
}
