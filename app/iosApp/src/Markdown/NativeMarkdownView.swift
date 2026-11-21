import SwiftUI
import UIKit
import KotlinModules

/// Native SwiftUI Markdown renderer with code syntax highlighting and inline `demo:<id>` Compose islands.
struct NativeMarkdownView: View {
    let markdown: String

    var body: some View {
        let segments = IosPortfolioBridge.shared.parseMarkdownSegments(markdown: markdown)
        LazyVStack(alignment: .leading, spacing: 14) {
            ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                if let prose = segment as? MarkdownSegmentProse {
                    NativeProseSegmentView(markdown: prose.markdown)
                } else if let demo = segment as? MarkdownSegmentDemoIsland {
                    EmbeddedDemoIslandSection(demoId: demo.demoId)
                }
            }
        }
    }
}

private enum MarkdownBlock {
    case heading(level: Int, text: String)
    case codeFence(language: String, code: String)
    case quote(text: String)
    case bullet(text: String)
    case paragraph(text: String)
}

private struct NativeProseSegmentView: View {
    let markdown: String

    var body: some View {
        let blocks = MarkdownBlockParser.parseBlocks(markdown)
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch block {
                case let .heading(level, text):
                    inlineMarkdownText(text)
                        .font(fontForHeading(level))
                        .padding(.top, level == 1 ? 6 : 4)
                        .textSelection(.enabled)

                case let .codeFence(language, code):
                    NativeCodeBlockView(language: language, code: code)

                case let .quote(text):
                    HStack(alignment: .top, spacing: 10) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Color.accentColor)
                            .frame(width: 3)
                        inlineMarkdownText(text)
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .textSelection(.enabled)
                    }
                    .padding(.vertical, 2)

                case let .bullet(text):
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("•")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                        inlineMarkdownText(text)
                            .font(.body)
                            .textSelection(.enabled)
                    }

                case let .paragraph(text):
                    inlineMarkdownText(text)
                        .font(.body)
                        .lineSpacing(4)
                        .textSelection(.enabled)
                }
            }
        }
    }

    private func inlineMarkdownText(_ raw: String) -> Text {
        if let attributed = try? AttributedString(
            markdown: raw,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            return Text(attributed)
        }
        return Text(raw)
    }

    private func fontForHeading(_ level: Int) -> Font {
        switch level {
        case 1: return .title2.weight(.bold)
        case 2: return .title3.weight(.semibold)
        case 3: return .headline
        default: return .subheadline.weight(.semibold)
        }
    }
}

private struct NativeCodeBlockView: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var copied = false
    let language: String
    let code: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(language.isEmpty ? "code" : language)
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    UIPasteboard.general.string = code
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        copied = false
                    }
                } label: {
                    Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                        .font(.caption)
                }
                .buttonStyle(.borderless)
            }

            ScrollView(.horizontal, showsIndicators: true) {
                Text(SwiftSyntaxHighlighter.highlight(code: code, language: language, colorScheme: colorScheme))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.1), lineWidth: 1)
        )
    }
}

private enum MarkdownBlockParser {
    private static let fenceMarker = "```"
    private static let bulletPrefixes = ["- ", "* ", "+ "]

    static func parseBlocks(_ markdown: String) -> [MarkdownBlock] {
        let lines = markdown.replacingOccurrences(of: "\r\n", with: "\n").split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        var blocks: [MarkdownBlock] = []
        var paragraphBuffer: [String] = []
        var fenceLanguage: String?
        var fenceBuffer: [String] = []

        func flushParagraph() {
            let joined = paragraphBuffer.joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
            if !joined.isEmpty {
                blocks.append(.paragraph(text: joined))
            }
            paragraphBuffer.removeAll()
        }

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix(fenceMarker) {
                if let activeLang = fenceLanguage {
                    blocks.append(.codeFence(language: activeLang, code: fenceBuffer.joined(separator: "\n")))
                    fenceLanguage = nil
                    fenceBuffer.removeAll()
                } else {
                    flushParagraph()
                    fenceLanguage = String(trimmed.dropFirst(fenceMarker.count)).trimmingCharacters(in: .whitespaces)
                }
                continue
            }

            if fenceLanguage != nil {
                fenceBuffer.append(line)
                continue
            }

            if trimmed.isEmpty {
                flushParagraph()
                continue
            }

            if let (level, headingText) = parseHeading(trimmed) {
                flushParagraph()
                blocks.append(.heading(level: level, text: headingText))
            } else if trimmed.hasPrefix("> ") {
                flushParagraph()
                blocks.append(.quote(text: String(trimmed.dropFirst(2))))
            } else if let bulletPrefix = bulletPrefixes.first(where: { trimmed.hasPrefix($0) }) {
                flushParagraph()
                blocks.append(.bullet(text: String(trimmed.dropFirst(bulletPrefix.count))))
            } else {
                paragraphBuffer.append(trimmed)
            }
        }

        if let openLang = fenceLanguage {
            blocks.append(.codeFence(language: openLang, code: fenceBuffer.joined(separator: "\n")))
        }
        flushParagraph()
        return blocks
    }

    private static func parseHeading(_ line: String) -> (Int, String)? {
        let hashCount = line.prefix(while: { $0 == "#" }).count
        guard hashCount >= 1, hashCount <= 4 else { return nil }
        let remainder = line.dropFirst(hashCount)
        guard remainder.first == " " else { return nil }
        let title = remainder.trimmingCharacters(in: .whitespaces)
        return title.isEmpty ? nil : (hashCount, title)
    }
}
