import SwiftUI

@main
struct AkiMusicApp: App {
    @StateObject private var player = AudioPlayer.shared
    @StateObject private var library = LibraryStore.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(player)
                .environmentObject(library)
                .tint(Theme.accent)
        }
    }
}
