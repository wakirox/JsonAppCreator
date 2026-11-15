import SwiftUI

/// Pasting an AI app's answer: our words beside the system's Paste button, which reads the clipboard only when
/// tapped and needs no "Allow Paste" question.
struct PasteRow: View {
    let onPaste: (String?) -> Void

    var body: some View {
        HStack {
            Text("Got your AI app's answer? Copy it there, then")
                .foregroundStyle(.secondary)
            Spacer()
            PasteButton(payloadType: String.self) { strings in
                onPaste(strings.first)
            }
            .buttonBorderShape(.capsule)
        }
    }
}
