import AppkinShared
import SwiftUI

/// A tick box: ticked when the data's value is true; ticking it runs the action at once. A ticked title is struck
/// through. Only the box ticks, so a tap on the title reaches the list item (to open it, say).
struct RenderCheck: View {
    @EnvironmentObject private var model: ScreenModel
    let view: CheckView
    let data: ItemData

    var body: some View {
        let checked = RenderKit.shared.isChecked(view: view, data: data)
        let title = RenderKit.shared.checkTitle(view: view, data: data)
        HStack(spacing: 12) {
            Button { RenderKit.shared.onCheck(screen: model.vm, view: view, data: data, checked: !checked) } label: {
                Image(systemName: checked ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(checked ? Color.primary : Color.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(title)
            .accessibilityValue(checked ? "Ticked" : "Not ticked")
            Text(title)
                .strikethrough(checked)
                .foregroundStyle(checked ? Color.secondary : Color.primary)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
