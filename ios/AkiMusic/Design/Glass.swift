import SwiftUI

/// Liquid Glass 的基础材质层。
///
/// 组成（从下到上）：
/// 1. `.ultraThinMaterial` 底座，透出背景并做模糊；
/// 2. 可选的色彩倾向（tint）；
/// 3. 斜向白色高光渐变，模拟玻璃表面的反射；
/// 4. 外侧 1pt 暗边，模拟 iOS 27 加深的玻璃边缘；
/// 5. 内侧 1pt 顶部亮边，模拟 specular highlight；
/// 6. 极淡投影，让玻璃浮在内容之上。
///
/// 注意：这里**没有**使用 iOS 26 的 `.glassEffect` / `GlassEffectContainer`，
/// 全部用 iOS 15 起可用的 Material 手写，保证 iOS 16 部署目标可编译。
struct GlassSurface<S: InsettableShape>: View {

    var shape: S
    var tint: Color?

    init(shape: S, tint: Color? = nil) {
        self.shape = shape
        self.tint = tint
    }

    var body: some View {
        ZStack {
            shape.fill(.ultraThinMaterial)

            if let tint = tint {
                shape.fill(tint.opacity(0.18))
            }

            shape.fill(
                LinearGradient(
                    colors: [Color.white.opacity(0.16), Color.white.opacity(0.02)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )

            // 暗边（darkened edge）
            shape.strokeBorder(Color.black.opacity(0.28), lineWidth: 1)

            // 高光（specular highlight）
            shape.strokeBorder(
                LinearGradient(
                    colors: [Color.white.opacity(0.45), Color.white.opacity(0.06), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                ),
                lineWidth: 1
            )
        }
        .compositingGroup()
        .shadow(color: Color.black.opacity(0.18), radius: 24, x: 0, y: 10)
    }
}

/// 圆角矩形玻璃，最常用的形态。
struct GlassBackground: View {

    var cornerRadius: CGFloat
    var tint: Color?

    init(cornerRadius: CGFloat = 20, tint: Color? = nil) {
        self.cornerRadius = cornerRadius
        self.tint = tint
    }

    var body: some View {
        GlassSurface(shape: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous), tint: tint)
    }
}

// MARK: - Modifier

private struct GlassModifier: ViewModifier {

    let cornerRadius: CGFloat
    let tint: Color?

    func body(content: Content) -> some View {
        content.background(GlassBackground(cornerRadius: cornerRadius, tint: tint))
    }
}

extension View {

    /// 给任意视图铺一层玻璃底。
    func glass(cornerRadius: CGFloat = Theme.Radius.card, tint: Color? = nil) -> some View {
        modifier(GlassModifier(cornerRadius: cornerRadius, tint: tint))
    }
}

// MARK: - 按压样式

struct PressableButtonStyle: ButtonStyle {

    var scale: CGFloat = 0.96

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.32, dampingFraction: 0.72), value: configuration.isPressed)
    }
}

// MARK: - 玻璃按钮

/// 玻璃胶囊按钮：材质 + 描边 + 按下回弹。
struct GlassCapsuleButton<Label: View>: View {

    private let action: () -> Void
    private let label: Label

    init(action: @escaping () -> Void, @ViewBuilder label: () -> Label) {
        self.action = action
        self.label = label()
    }

    var body: some View {
        Button(action: action) {
            label
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(GlassSurface(shape: Capsule()))
                .contentShape(Capsule())
        }
        .buttonStyle(PressableButtonStyle())
    }
}

/// 圆形玻璃图标按钮，用于返回、队列、更多等场景。
struct GlassIconButton: View {

    var systemName: String
    var size: CGFloat = 36
    var tint: Color = .primary
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: size, height: size)
                .background(GlassSurface(shape: Circle()))
                .contentShape(Circle())
        }
        .buttonStyle(PressableButtonStyle())
    }
}

/// 玻璃搜索框容器（不带输入逻辑，只负责外观）。
struct GlassField<Content: View>: View {

    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(GlassBackground(cornerRadius: 14))
    }
}
