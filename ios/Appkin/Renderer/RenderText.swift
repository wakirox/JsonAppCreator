import AppkinShared
import SwiftUI

/// A text view: its label as a heading, then its text (plain, HTML or Markdown); text with an action ends in an arrow.
struct RenderText: View {
    @EnvironmentObject private var model: ScreenModel
    let view: TextualView
    let data: ItemData
    var padding = EdgeInsets(top: 2, leading: 0, bottom: 2, trailing: 0)
    /// A card's text fills its width and wraps; it is not held to the width the view asks for.
    var compact = false

    var body: some View {
        let label = RenderKit.shared.label(view: view, data: data)
        let text = RenderKit.shared.text(view: view, data: data)
        let size = RenderKit.shared.fontSize(view: view)
        let content = VStack(alignment: .leading, spacing: 2) {
            if !label.isEmpty {
                // The label is a heading: a title's size and weight, unless labelStyle (or textSize) says otherwise.
                Text(linkified(label))
                    .font(.system(size: size > 0 ? size : 17, weight: labelWeight))
                    .italic(view.labelStyle?.name == "italic")
                    .foregroundStyle(.primary)
            }
            if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                FormattedBody(view: view, text: text, size: size, secondary: !label.isEmpty)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)

        Group {
            if view.action == nil {
                content
            } else {
                HStack(spacing: 8) {
                    content
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .padding(padding)
        .frame(height: heightIfAny)
        .actionable(view.action, data)
    }

    private var heightIfAny: CGFloat? {
        let height = RenderKit.shared.textHeight(view: view)
        return height >= 0 ? CGFloat(height) : nil
    }

    private var labelWeight: Font.Weight {
        switch view.labelStyle?.name {
        case nil: .semibold
        case "bold": .bold
        default: .regular
        }
    }
}

/// A text's body in its format.
struct FormattedBody: View {
    let view: TextualView
    let text: String
    let size: Double
    let secondary: Bool

    var body: some View {
        switch view.format?.name {
        case "markdown":
            MarkdownBody(text: text, size: size > 0 ? size : 17)
        case "html":
            styled(Text(htmlToPlain(text)))
        default:
            styled(Text(linkified(text.trimmingCharacters(in: .whitespacesAndNewlines))))
        }
    }

    private func styled(_ text: Text) -> some View {
        text
            .font(size > 0 ? .system(size: size) : .body)
            .fontWeight(view.textStyle?.name == "bold" ? .bold : nil)
            .italic(view.textStyle?.name == "italic")
            .foregroundStyle(secondary ? .secondary : .primary)
    }
}

/// Markdown as the shared parser reads it: headings, paragraphs, lists, quotes and code, with bold, italics and links.
struct MarkdownBody: View {
    let text: String
    let size: Double

    var body: some View {
        let parts = RenderKit.shared.markdown(text: text)
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(parts.enumerated()), id: \.offset) { _, part in
                switch part.kind {
                case "heading":
                    Text(runs(part.runs)).font(.system(size: size + CGFloat(max(0, 4 - Int(part.level))) * 3, weight: .bold))
                case "item":
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(part.number > 0 ? "\(part.number)." : "•")
                        Text(runs(part.runs))
                    }
                    .padding(.leading, CGFloat(part.depth) * 16)
                case "quote":
                    Text(runs(part.runs))
                        .foregroundStyle(.secondary)
                        .padding(.leading, 10)
                        .overlay(alignment: .leading) { Rectangle().fill(.tertiary).frame(width: 3) }
                case "code":
                    Text(part.code)
                        .font(.system(size: size - 2, design: .monospaced))
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8))
                default:
                    Text(runs(part.runs))
                }
            }
        }
        .font(.system(size: size))
    }

    private func runs(_ runs: [MarkdownRun]) -> AttributedString {
        var result = AttributedString()
        for run in runs {
            var piece = AttributedString(run.text)
            if run.code {
                piece.font = .system(size: size - 1, design: .monospaced)
            } else if run.bold && run.italic {
                piece.font = .system(size: size, weight: .bold).italic()
            } else if run.bold {
                piece.font = .system(size: size, weight: .bold)
            } else if run.italic {
                piece.font = .system(size: size).italic()
            }
            if let link = run.link, let url = URL(string: link) { piece.link = url }
            result += piece
        }
        return result
    }
}

/// Web addresses in plain text, made links.
func linkified(_ text: String) -> AttributedString {
    var result = AttributedString(text)
    guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return result }
    let whole = NSRange(text.startIndex..., in: text)
    for match in detector.matches(in: text, range: whole) {
        guard let url = match.url, let range = Range(match.range, in: text),
              let target = Range(range, in: result) else { continue }
        result[target].link = url
    }
    return result
}

/// Simple HTML as plain text: line breaks kept, tags dropped, the common entities read.
func htmlToPlain(_ html: String) -> String {
    var text = html
        .replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: [.regularExpression, .caseInsensitive])
        .replacingOccurrences(of: "</p>", with: "\n\n", options: .caseInsensitive)
        .replacingOccurrences(of: "<[^>]*>", with: "", options: .regularExpression)
    for (entity, character) in [("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'"), ("&nbsp;", " ")] {
        text = text.replacingOccurrences(of: entity, with: character)
    }
    return text.trimmingCharacters(in: .whitespacesAndNewlines)
}
