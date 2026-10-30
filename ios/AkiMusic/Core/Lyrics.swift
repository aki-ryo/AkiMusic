import Foundation

// MARK: - 歌词行

struct LyricLine: Identifiable, Hashable {

    let id: Int
    let time: Double
    let text: String
    var translation: String?

    var isEmpty: Bool { text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}

// MARK: - 歌词

struct Lyrics: Equatable {

    var lines: [LyricLine]

    static let empty = Lyrics(lines: [])

    var isEmpty: Bool { lines.isEmpty }

    /// 返回当前时间对应的歌词下标（最后一句 time <= 当前时间）。
    func index(at time: Double) -> Int {
        guard !lines.isEmpty else { return 0 }
        var result = 0
        for (i, line) in lines.enumerated() {
            if line.time <= time + 0.12 {
                result = i
            } else {
                break
            }
        }
        return result
    }

    /// 解析 LRC。`translation` 是可选的逐行翻译（tlyric），按时间戳对齐。
    static func parse(lrc: String, translation: String = "") -> Lyrics {
        var main = rawLines(from: lrc)
        guard !main.isEmpty else { return .empty }

        if !translation.isEmpty {
            let translated = rawLines(from: translation)
            var table: [Int: String] = [:]
            for line in translated {
                table[key(for: line.time)] = line.text
            }
            for i in main.indices {
                if let text = table[key(for: main[i].time)] {
                    main[i].translation = text
                }
            }
        }

        let lines = main.enumerated().map { index, raw in
            LyricLine(id: index, time: raw.time, text: raw.text, translation: raw.translation)
        }
        return Lyrics(lines: lines)
    }

    // MARK: - 私有

    private struct RawLine {
        var time: Double
        var text: String
        var translation: String?
    }

    private static func key(for time: Double) -> Int {
        Int((time * 100).rounded())
    }

    private static let timeTagRegex = try! NSRegularExpression(
        pattern: "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]",
        options: []
    )

    private static func rawLines(from raw: String) -> [RawLine] {
        var result: [RawLine] = []

        for line in raw.components(separatedBy: .newlines) {
            let full = line as NSString
            guard full.length > 0 else { continue }

            let matches = timeTagRegex.matches(in: line, options: [], range: NSRange(location: 0, length: full.length))
            guard let last = matches.last else { continue }

            let textRange = NSRange(location: last.range.location + last.range.length,
                                    length: full.length - (last.range.location + last.range.length))
            let text = full.substring(with: textRange).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty else { continue }

            for match in matches {
                guard let time = parseTime(match: match, in: full) else { continue }
                result.append(RawLine(time: time, text: text, translation: nil))
            }
        }

        result.sort { $0.time < $1.time }
        return result
    }

    private static func parseTime(match: NSTextCheckingResult, in source: NSString) -> Double? {
        guard match.numberOfRanges >= 3 else { return nil }
        guard let minutes = Int(source.substring(with: match.range(at: 1))) else { return nil }
        guard let seconds = Int(source.substring(with: match.range(at: 2))) else { return nil }

        var fraction = 0.0
        if match.numberOfRanges >= 4, match.range(at: 3).location != NSNotFound {
            let raw = source.substring(with: match.range(at: 3))
            if let value = Double(raw) {
                fraction = value / pow(10, Double(raw.count))
            }
        }

        return Double(minutes * 60 + seconds) + fraction
    }
}
