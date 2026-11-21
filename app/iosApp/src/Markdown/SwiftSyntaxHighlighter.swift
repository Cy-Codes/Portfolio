import SwiftUI

/// Native Swift syntax highlighter producing color-scheme-aware `AttributedString` tokens.
enum SwiftSyntaxHighlighter {
    private struct Palette {
        let plain: Color
        let keyword: Color
        let string: Color
        let comment: Color
        let number: Color
        let typeName: Color
    }

    private static let lightPalette = Palette(
        plain: Color(red: 0.08, green: 0.09, blue: 0.11),
        keyword: Color(red: 0.11, green: 0.31, blue: 0.85),
        string: Color(red: 0.06, green: 0.46, blue: 0.43),
        comment: Color(red: 0.42, green: 0.45, blue: 0.50),
        number: Color(red: 0.71, green: 0.33, blue: 0.04),
        typeName: Color(red: 0.49, green: 0.23, blue: 0.93)
    )

    private static let darkPalette = Palette(
        plain: Color(red: 0.92, green: 0.94, blue: 0.96),
        keyword: Color(red: 0.38, green: 0.65, blue: 0.98),
        string: Color(red: 0.18, green: 0.83, blue: 0.75),
        comment: Color(red: 0.58, green: 0.63, blue: 0.71),
        number: Color(red: 0.98, green: 0.75, blue: 0.14),
        typeName: Color(red: 0.75, green: 0.58, blue: 1.00)
    )

    private static let commonKeywords: Set<String> = [
        "fun", "val", "var", "class", "interface", "object", "enum", "sealed", "data",
        "suspend", "override", "private", "public", "internal", "return", "if", "else",
        "when", "for", "while", "import", "package", "struct", "func", "let", "guard",
        "select", "from", "where", "insert", "into", "grant", "on", "to", "schema",
        "table", "true", "false", "null", "nil", "in", "is", "as"
    ]

    static func highlight(code: String, language: String?, colorScheme: ColorScheme) -> AttributedString {
        let palette = colorScheme == .dark ? darkPalette : lightPalette
        var result = AttributedString(code)
        result.foregroundColor = palette.plain
        result.font = .system(.footnote, design: .monospaced)

        let nsCode = code as NSString
        let fullRange = NSRange(location: 0, length: nsCode.length)

        // Apply base word & number styling before string and comment passes override them.
        applyRegex(#"\b\d+(?:_\d+)*(?:\.\d+)?\b"#, in: nsCode, range: fullRange, result: &result, color: palette.number)
        applyRegex(#"\b[A-Z][A-Za-z0-9_]*\b"#, in: nsCode, range: fullRange, result: &result, color: palette.typeName)
        applyWordKeywords(in: nsCode, range: fullRange, result: &result, color: palette.keyword)
        applyRegex(#"\"([^\"\\]|\\.)*\"|'([^'\\]|\\.)*'"#, in: nsCode, range: fullRange, result: &result, color: palette.string)

        let normalizedLang = (language ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if normalizedLang == "sql" || normalizedLang == "postgresql" {
            applyRegex(#"--[^\n]*"#, in: nsCode, range: fullRange, result: &result, color: palette.comment)
        } else {
            applyRegex(#"//[^\n]*"#, in: nsCode, range: fullRange, result: &result, color: palette.comment)
        }

        return result
    }

    private static func applyWordKeywords(
        in text: NSString,
        range: NSRange,
        result: inout AttributedString,
        color: Color
    ) {
        guard let regex = try? NSRegularExpression(pattern: #"\b[A-Za-z_][A-Za-z0-9_]*\b"#) else { return }
        for match in regex.matches(in: text as String, range: range) {
            let word = text.substring(with: match.range)
            if commonKeywords.contains(word.lowercased()),
               let swiftRange = Range(match.range, in: text as String),
               let attrStart = AttributedString.Index(swiftRange.lowerBound, within: result),
               let attrEnd = AttributedString.Index(swiftRange.upperBound, within: result) {
                result[attrStart..<attrEnd].foregroundColor = color
            }
        }
    }

    private static func applyRegex(
        _ pattern: String,
        in text: NSString,
        range: NSRange,
        result: inout AttributedString,
        color: Color
    ) {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return }
        for match in regex.matches(in: text as String, range: range) {
            if let swiftRange = Range(match.range, in: text as String),
               let attrStart = AttributedString.Index(swiftRange.lowerBound, within: result),
               let attrEnd = AttributedString.Index(swiftRange.upperBound, within: result) {
                result[attrStart..<attrEnd].foregroundColor = color
            }
        }
    }
}
