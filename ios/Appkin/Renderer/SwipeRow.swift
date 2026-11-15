import AppkinShared
import SwiftUI

/// A list row that swipes towards the start (a delete, say) or the end (done, say). Past the threshold the action runs
/// and the row slides back, so it only goes when the action removes it, as on Android. Row-level swipeActions need a
/// SwiftUI List, which a list among other views of a scrolling screen is not.
struct SwipeRow<Content: View>: View {
    /// The icon names (see `symbolName`) and whether each direction has an action.
    let startIcon: String?
    let endIcon: String?
    let onStart: () -> Void
    let onEnd: () -> Void
    @ViewBuilder let content: Content

    @State private var offset: CGFloat = 0
    @State private var horizontal: Bool?
    private let threshold: CGFloat = 90

    var body: some View {
        ZStack {
            if offset != 0 { reveal }
            content
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .offset(x: offset)
        }
        .clipped()
        .simultaneousGesture(drag)
        .accessibilityAction(named: Text(startIcon.map(label) ?? "")) { if startIcon != nil { onStart() } }
        .accessibilityAction(named: Text(endIcon.map(label) ?? "")) { if endIcon != nil { onEnd() } }
    }

    private var reveal: some View {
        let towardsEnd = offset > 0
        let icon = towardsEnd ? endIcon : startIcon
        return HStack {
            if towardsEnd { badge(icon) }
            Spacer()
            if !towardsEnd { badge(icon) }
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(towardsEnd ? Color(.systemFill) : Color.red)
    }

    private func badge(_ icon: String?) -> some View {
        Image(systemName: icon.map(symbolName) ?? "questionmark")
            .font(.title3.weight(.semibold))
            .foregroundStyle(offset > 0 ? Color.primary : Color.white)
            .scaleEffect(abs(offset) >= threshold ? 1.2 : 1)
            .animation(.spring(duration: 0.2), value: abs(offset) >= threshold)
    }

    private func label(_ icon: String) -> String { icon == "check" ? "Done" : icon.capitalized }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 16)
            .onChanged { value in
                if horizontal == nil { horizontal = abs(value.translation.width) > abs(value.translation.height) }
                guard horizontal == true else { return }
                let wanted = value.translation.width
                // A direction without an action resists: it doesn't move.
                offset = (wanted > 0 && endIcon == nil) || (wanted < 0 && startIcon == nil) ? 0 : wanted
            }
            .onEnded { _ in
                defer { horizontal = nil }
                guard horizontal == true else { return }
                let moved = offset
                withAnimation(.spring(duration: 0.3)) { offset = 0 }
                if moved >= threshold, endIcon != nil { onEnd() }
                if moved <= -threshold, startIcon != nil { onStart() }
            }
    }
}
