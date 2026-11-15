import AppkinShared
import SwiftUI

/// A button: its text, an icon, or both; a tonal button across its space, or a round one for an icon alone.
struct RenderButton: View {
    @EnvironmentObject private var model: ScreenModel
    @Environment(\.buttonsInRow) private var inRow
    let view: ButtonView
    let data: ItemData

    var body: some View {
        let title = RenderKit.shared.text(view: view, data: data)
        let symbol = RenderKit.shared.iconName(view: view).map(symbolName)
        let longClick = RenderKit.shared.isLongClick(screen: model.vm, view: view)
        let run = { if !longClick { RenderKit.shared.onAction(screen: model.vm, action: view.action, data: data) } }
        Group {
            if let symbol, RenderKit.shared.isIconOnly(view: view) {
                let spoken = RenderKit.shared.fill(text: view.label, data: data)
                Button(action: run) { Image(systemName: symbol) }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.circle)
                    .accessibilityLabel(spoken.isEmpty ? (RenderKit.shared.iconName(view: view) ?? "") : spoken)
            } else {
                Button(action: run) {
                    Group {
                        if let symbol { Label(title, systemImage: symbol) } else { Text(title) }
                    }
                    .lineLimit(1)
                    .font(inRow ? .subheadline : nil)
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                // Buttons sharing a row are all one smaller size, so their titles fit without shrinking one alone.
                .controlSize(inRow ? .regular : .large)
            }
        }
        .task {
            // `-tapButton Credits`: presses the button with that title once, so a screenshot can show what it opens.
            if UserDefaults.standard.string(forKey: "tapButton") == title { run() }
        }
        .simultaneousGesture(
            LongPressGesture().onEnded { _ in
                if longClick { RenderKit.shared.onAction(screen: model.vm, action: view.action, data: data) }
            }
        )
    }
}

/// The SF Symbol for a JSON button icon.
func symbolName(_ icon: String) -> String {
    switch icon {
    case "add": "plus"
    case "check": "checkmark"
    case "close": "xmark"
    case "copy": "doc.on.doc"
    case "delete": "trash"
    case "edit": "pencil"
    case "email": "envelope"
    case "favorite": "heart"
    case "info": "info.circle"
    case "open": "arrow.up.right.square"
    case "phone": "phone"
    case "place": "mappin.and.ellipse"
    case "refresh": "arrow.clockwise"
    case "search": "magnifyingglass"
    case "settings": "gearshape"
    case "share": "square.and.arrow.up"
    case "star": "star"
    default: "questionmark.circle"
    }
}

/// Whether views are side by side in a row, where buttons take a smaller size.
private struct ButtonsInRowKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var buttonsInRow: Bool {
        get { self[ButtonsInRowKey.self] }
        set { self[ButtonsInRowKey.self] = newValue }
    }
}
