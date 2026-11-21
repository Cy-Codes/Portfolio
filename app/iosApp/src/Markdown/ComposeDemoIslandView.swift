import SwiftUI
import UIKit
import KotlinModules

/// Embeds a shared Compose Multiplatform interactive demo widget inline via `ComposeUIViewController`.
struct ComposeDemoIslandView: UIViewControllerRepresentable {
    let demoId: String
    let darkTheme: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = IosPortfolioBridge.shared.createDemoIslandViewController(
            demoId: demoId,
            darkTheme: darkTheme
        )
        controller.view.backgroundColor = .clear
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        uiViewController.overrideUserInterfaceStyle = darkTheme ? .dark : .light
    }
}

/// Sized, rounded inline container presenting an embedded Compose demo island inside native SwiftUI scroll views.
struct EmbeddedDemoIslandSection: View {
    @Environment(\.colorScheme) private var colorScheme
    let demoId: String

    private var isDark: Bool { colorScheme == .dark }
    private var heightPt: CGFloat {
        CGFloat(IosPortfolioBridge.shared.recommendedDemoHeightPt(demoId: demoId))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles.rectangle.stack")
                    .font(.caption)
                    .foregroundStyle(.tint)
                Text("Interactive Compose Demo Island")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text("demo:\(demoId)")
                    .font(.caption2.monospaced())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(.quaternary, in: Capsule())
            }

            ComposeDemoIslandView(demoId: demoId, darkTheme: isDark)
                .id("\(demoId)-\(isDark)")
                .frame(maxWidth: .infinity)
                .frame(height: heightPt)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.12), lineWidth: 1)
                )
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Interactive demo \(demoId)")
    }
}
