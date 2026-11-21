import SwiftUI

/// Wraps chips across horizontal lines cleanly while preserving native spacing.
struct FlowChipsLayout: Layout {
    let horizontalSpacing: CGFloat
    let verticalSpacing: CGFloat

    init(horizontalSpacing: CGFloat = 8, verticalSpacing: CGFloat = 8) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = computeRows(maxWidth: proposal.width ?? .infinity, subviews: subviews)
        let totalHeight = rows.enumerated().reduce(CGFloat.zero) { acc, item in
            let rowHeight = item.element.map { $0.size.height }.max() ?? 0
            let gap = item.offset > 0 ? verticalSpacing : 0
            return acc + rowHeight + gap
        }
        let maxRowWidth = rows.map { row in
            row.enumerated().reduce(CGFloat.zero) { acc, el in
                acc + el.element.size.width + (el.offset > 0 ? horizontalSpacing : 0)
            }
        }.max() ?? 0
        return CGSize(width: proposal.width ?? maxRowWidth, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = computeRows(maxWidth: bounds.width, subviews: subviews)
        var cursorY = bounds.minY
        for row in rows {
            var cursorX = bounds.minX
            let rowHeight = row.map { $0.size.height }.max() ?? 0
            for item in row {
                item.subview.place(
                    at: CGPoint(x: cursorX, y: cursorY),
                    proposal: ProposedViewSize(item.size)
                )
                cursorX += item.size.width + horizontalSpacing
            }
            cursorY += rowHeight + verticalSpacing
        }
    }

    private func computeRows(maxWidth: CGFloat, subviews: Subviews) -> [[(subview: LayoutSubview, size: CGSize)]] {
        var rows: [[(subview: LayoutSubview, size: CGSize)]] = [[]]
        var currentWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].isEmpty ? size.width : horizontalSpacing + size.width
            if currentWidth + needed > maxWidth, !rows[rows.count - 1].isEmpty {
                rows.append([(subview, size)])
                currentWidth = size.width
            } else {
                rows[rows.count - 1].append((subview, size))
                currentWidth += needed
            }
        }
        return rows.filter { !$0.isEmpty }
    }
}

struct PortfolioCardContainer<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 1)
        )
    }
}

struct MonoBadgeView: View {
    let text: String
    var foreground: Color = .secondary
    var background: Color = Color(.tertiarySystemFill)

    var body: some View {
        Text(text)
            .font(.caption.monospaced())
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(background, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}

struct PortfolioLoadingView: View {
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(32)
    }
}

struct PortfolioErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Unable to Load", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Retry", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
    }
}
