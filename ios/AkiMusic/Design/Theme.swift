import SwiftUI
import UIKit
import Foundation

/// 全局设计令牌：颜色 / 字体 / 圆角 / 动效。
/// 所有颜色都基于语义色，跟随系统深浅模式。
enum Theme {

    // MARK: - 颜色

    /// 页面底色：深色 #0B0B0F，浅色 #F2F2F7。
    static let background = Color(uiColor: UIColor { trait in
        if trait.userInterfaceStyle == .dark {
            return UIColor(red: 0.043, green: 0.043, blue: 0.059, alpha: 1)
        }
        return UIColor(red: 0.949, green: 0.949, blue: 0.969, alpha: 1)
    })

    /// Apple Music 红，只用于播放按钮、选中态、当前歌词。
    static let accent = Color(hex: 0xFA2D48)

    static let text = Color.primary

    /// 次要文字，systemGray #8E8E93。
    static let secondaryText = Color(uiColor: .systemGray)

    static let tertiaryText = Color(uiColor: .tertiaryLabel)

    /// 列表卡片底色，极淡的一层，避免用 List 破坏玻璃层次。
    static let cardFill = Color.primary.opacity(0.05)

    static let cardFillStrong = Color.primary.opacity(0.08)

    static let hairline = Color.primary.opacity(0.08)

    static let placeholderFill = Color(uiColor: .secondarySystemFill)

    // MARK: - 字体

    static let largeTitle = Font.system(size: 34, weight: .bold)
    static let sectionTitle = Font.system(size: 22, weight: .bold)
    static let bodyText = Font.system(size: 17, weight: .regular)
    static let secondary = Font.system(size: 15, weight: .regular)
    static let caption = Font.system(size: 13, weight: .regular)
    static let mini = Font.system(size: 13, weight: .medium)

    static let songTitle = Font.system(size: 16, weight: .medium)
    static let songSubtitle = Font.system(size: 13, weight: .regular)

    // MARK: - 圆角

    enum Radius {
        static let card: CGFloat = 20
        static let thumb: CGFloat = 12
        static let row: CGFloat = 16
        static let cover: CGFloat = 14
        static let chip: CGFloat = 999
    }

    // MARK: - 动效

    static let spring = Animation.spring(response: 0.34, dampingFraction: 0.82)
    static let softSpring = Animation.spring(response: 0.45, dampingFraction: 0.78)
    static let quickSpring = Animation.spring(response: 0.28, dampingFraction: 0.72)

    // MARK: - 阴影

    static let floatingShadow = Color.black.opacity(0.22)
}

// MARK: - Hex

extension Color {

    /// `Color(hex: 0xFA2D48)`，不带 # 号。
    init(hex: UInt32, alpha: Double = 1) {
        let red = Double((hex >> 16) & 0xFF) / 255
        let green = Double((hex >> 8) & 0xFF) / 255
        let blue = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}

// MARK: - 文本辅助

enum TimeFormat {

    /// 197 -> "3:17"
    static func clock(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let total = Int(seconds.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    /// 12345 -> "1.2万"
    static func playCount(_ count: Int) -> String {
        if count >= 100_000_000 {
            return String(format: "%.1f亿", Double(count) / 100_000_000)
        }
        if count >= 10_000 {
            return String(format: "%.1f万", Double(count) / 10_000)
        }
        return "\(count)"
    }

    /// 毫秒 -> "3:17"
    static func duration(ms: Double) -> String {
        clock(ms / 1000)
    }
}
