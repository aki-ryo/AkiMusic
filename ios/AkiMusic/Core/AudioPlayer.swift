import Foundation
import AVFoundation
import Combine

// MARK: - 循环模式

enum RepeatMode: String, CaseIterable, Identifiable {

    case off
    case all
    case one

    var id: String { rawValue }

    var iconName: String {
        switch self {
        case .off, .all: return "repeat"
        case .one: return "repeat.1"
        }
    }

    var title: String {
        switch self {
        case .off: return "顺序播放"
        case .all: return "列表循环"
        case .one: return "单曲循环"
        }
    }

    var next: RepeatMode {
        switch self {
        case .off: return .all
        case .all: return .one
        case .one: return .off
        }
    }
}

// MARK: - 播放器

/// 全局播放器。AVPlayer + AVAudioSession(.playback)。
/// 负责队列、随机、循环、进度、歌词同步、无音源自动跳过。
@MainActor
final class AudioPlayer: ObservableObject {

    static let shared = AudioPlayer()

    // MARK: 对外状态

    @Published private(set) var queue: [Song] = []
    @Published private(set) var currentIndex: Int = 0
    @Published private(set) var isPlaying: Bool = false
    @Published private(set) var isLoading: Bool = false
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var isShuffle: Bool = false
    @Published private(set) var repeatMode: RepeatMode = .all
    @Published private(set) var lyrics: Lyrics = .empty
    @Published private(set) var currentLyricIndex: Int = 0
    @Published private(set) var errorMessage: String?

    var currentSong: Song? {
        guard queue.indices.contains(currentIndex) else { return nil }
        return queue[currentIndex]
    }

    var progress: Double {
        guard duration > 0 else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    // MARK: 私有

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var loadTask: Task<Void, Never>?
    private var lyricTask: Task<Void, Never>?
    private var didConfigureSession = false

    /// 随机播放时真实播放顺序（存 queue 的下标）。
    private var order: [Int] = []
    private var orderPosition: Int = 0

    /// 连续跳过上限，防止整张专辑都没有音源时无限循环。
    private let maxConsecutiveSkips = 5

    // MARK: 生命周期

    private init() {
        player.actionAtItemEnd = .pause
        registerObservers()
    }

    private func registerObservers() {
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            Task { @MainActor in
                guard let self = self else { return }
                let seconds = time.seconds
                if seconds.isFinite, seconds >= 0 {
                    self.currentTime = seconds
                }
                if let itemDuration = self.player.currentItem?.duration.seconds,
                   itemDuration.isFinite,
                   itemDuration > 0 {
                    self.duration = itemDuration
                }
                self.updateLyricIndex()
            }
        }

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: nil,
            queue: .main
        ) { [weak self] note in
            Task { @MainActor in
                guard let self = self else { return }
                guard let item = note.object as? AVPlayerItem, item === self.player.currentItem else { return }
                self.handleTrackEnd()
            }
        }
    }

    // MARK: - 播放控制

    func play(songs: [Song], startAt index: Int = 0) {
        guard !songs.isEmpty else { return }
        queue = songs
        let safeIndex = min(max(index, 0), songs.count - 1)
        setupOrder(startingAt: safeIndex)
        startLoading()
    }

    func play(song: Song) {
        play(songs: [song], startAt: 0)
    }

    /// 拉取歌单详情后整张播放。
    func playPlaylist(id: Int) async {
        isLoading = true
        do {
            let detail = try await NetEaseAPI.shared.playlist(id: id)
            isLoading = false
            guard !detail.tracks.isEmpty else {
                errorMessage = "这个歌单里没有可播放的歌曲"
                return
            }
            play(songs: detail.tracks, startAt: 0)
        } catch {
            isLoading = false
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "加载歌单失败"
        }
    }

    func togglePlayPause() {
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            if player.currentItem == nil {
                startLoading()
            } else {
                player.play()
                isPlaying = true
            }
        }
    }

    func next() {
        guard stepForward(1) else {
            errorMessage = "已经是最后一首了"
            return
        }
        startLoading()
    }

    func previous() {
        if currentTime > 3 {
            seek(to: 0)
            return
        }
        guard stepForward(-1) else {
            seek(to: 0)
            return
        }
        startLoading()
    }

    func jump(to index: Int) {
        guard queue.indices.contains(index) else { return }
        currentIndex = index
        if let position = order.firstIndex(of: index) {
            orderPosition = position
        }
        startLoading()
    }

    func seek(to time: Double) {
        let upperBound = duration > 0 ? duration : time
        let target = min(max(time, 0), upperBound)
        currentTime = target
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600),
                    toleranceBefore: .zero,
                    toleranceAfter: .zero)
        updateLyricIndex()
    }

    func toggleShuffle() {
        isShuffle.toggle()
        setupOrder(startingAt: currentIndex)
    }

    func cycleRepeatMode() {
        repeatMode = repeatMode.next
    }

    func clearError() {
        errorMessage = nil
    }

    // MARK: - 加载

    private func startLoading() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            guard let self = self else { return }
            await self.loadCurrentTrack()
        }
    }

    private func loadCurrentTrack() async {
        guard !queue.isEmpty else { return }

        isLoading = true
        errorMessage = nil
        lyrics = .empty
        currentLyricIndex = 0
        currentTime = 0

        var skipped = 0

        while true {
            if Task.isCancelled {
                isLoading = false
                return
            }
            guard queue.indices.contains(currentIndex) else {
                isLoading = false
                return
            }

            let song = queue[currentIndex]
            duration = song.duration / 1000

            do {
                let url = try await NetEaseAPI.shared.streamURL(for: song.id)
                if Task.isCancelled {
                    isLoading = false
                    return
                }
                if let url = url {
                    attach(url: url)
                    isLoading = false
                    loadLyrics(for: song)
                    return
                }
            } catch {
                if Task.isCancelled {
                    isLoading = false
                    return
                }
            }

            // 拿不到音源：跳过，最多连续 5 首
            skipped += 1
            errorMessage = "《\(song.name)》暂无可用音源，已跳过"
            if skipped > maxConsecutiveSkips {
                isLoading = false
                isPlaying = false
                errorMessage = "连续多首歌曲无法播放，请稍后再试"
                return
            }
            if !stepForward(1) {
                isLoading = false
                isPlaying = false
                return
            }
        }
    }

    private func attach(url: URL) {
        configureSessionIfNeeded()
        let item = AVPlayerItem(url: url)
        player.replaceCurrentItem(with: item)
        player.play()
        isPlaying = true
    }

    private func configureSessionIfNeeded() {
        guard !didConfigureSession else { return }
        didConfigureSession = true
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [])
        try? session.setActive(true)
    }

    private func loadLyrics(for song: Song) {
        lyricTask?.cancel()
        lyricTask = Task { [weak self] in
            let result = try? await NetEaseAPI.shared.lyrics(for: song.id)
            guard let self = self else { return }
            guard !Task.isCancelled else { return }
            guard let lyrics = result, !lyrics.isEmpty else { return }
            // 只有当这首歌还在播时才应用歌词
            guard self.currentSong?.id == song.id else { return }
            self.lyrics = lyrics
            self.currentLyricIndex = lyrics.index(at: self.currentTime)
        }
    }

    // MARK: - 队列

    private func setupOrder(startingAt index: Int) {
        guard !queue.isEmpty else {
            order = []
            orderPosition = 0
            return
        }
        let safeIndex = min(max(index, 0), queue.count - 1)
        var indices = Array(queue.indices)
        if isShuffle {
            indices.removeAll { $0 == safeIndex }
            indices.shuffle()
            indices.insert(safeIndex, at: 0)
            order = indices
            orderPosition = 0
        } else {
            order = indices
            orderPosition = safeIndex
        }
        currentIndex = safeIndex
    }

    /// 按当前顺序前进/后退。返回 false 表示走到头了。
    private func stepForward(_ delta: Int) -> Bool {
        guard !order.isEmpty else { return false }
        var position = orderPosition + delta

        if position >= order.count {
            guard repeatMode == .all else { return false }
            position = 0
        }
        if position < 0 {
            guard repeatMode == .all else { return false }
            position = order.count - 1
        }

        orderPosition = position
        currentIndex = order[position]
        return true
    }

    private func handleTrackEnd() {
        if repeatMode == .one {
            seek(to: 0)
            player.play()
            isPlaying = true
            return
        }
        if stepForward(1) {
            startLoading()
        } else {
            isPlaying = false
            player.pause()
            seek(to: 0)
        }
    }

    private func updateLyricIndex() {
        guard !lyrics.isEmpty else { return }
        var index = 0
        for (i, line) in lyrics.lines.enumerated() {
            if line.time <= currentTime + 0.12 {
                index = i
            } else {
                break
            }
        }
        if index != currentLyricIndex {
            currentLyricIndex = index
        }
    }
}
