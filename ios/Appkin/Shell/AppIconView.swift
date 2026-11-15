import AppkinShared
import SwiftUI

/// An app's icon as Android draws it: its emoji on a soft colour, else its first letter on a coloured tile. The
/// colours come from the app's name through the shared `AppIcon`.
struct AppIconView: View {
    let name: String
    let icon: String?
    var size: CGFloat = 40

    private func color(_ argb: Int32) -> Color {
        let value = UInt32(bitPattern: argb)
        return Color(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }

    var body: some View {
        let emoji = AppIcon.shared.emoji(icon: icon)
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.25)
                .fill(color(emoji == nil ? AppIcon.shared.tileColor(name: name) : AppIcon.shared.softColor(name: name)))
            if let emoji {
                Text(emoji).font(.system(size: size * 0.55))
            } else {
                Text(AppIcon.shared.letter(name: name))
                    .font(.system(size: size * 0.5, weight: .bold))
                    .foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
