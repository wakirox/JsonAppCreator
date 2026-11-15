import AppkinShared
import SwiftUI

/// An image view: the picture at its address, cropped to fill its space or (scale "fit") whole inside it.
struct RenderImage: View {
    @EnvironmentObject private var model: ScreenModel
    let view: ImageView
    let data: ItemData
    /// In a list card, a picture runs to the card's edges and its corners are the card's.
    var edgeToEdge = false
    /// Height from the parent instead of the view's size.
    var fillHeight = false
    @State private var viewing: PhotoSelection?

    var body: some View {
        let height = RenderKit.shared.imageHeight(view: view)
        if let urls = RenderKit.shared.imageUrls(view: view, data: data) {
            // A mapping to a list (a photos field's) shows every picture in it, in a row.
            let side = CGFloat(height >= 0 ? height : 96)
            PhotoStrip(references: urls, side: side)
        } else {
            let photo = RenderKit.shared.imageUrl(view: view, data: data).flatMap { PhotoStore.isPhoto($0) ? $0 : nil }
            let picture = Group {
                if let address = RenderKit.shared.imageUrl(view: view, data: data) {
                    RemoteImage(url: imageURL(address), fit: view.scale?.name == "fit")
                } else {
                    Label("Image unavailable", systemImage: "photo")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color(.tertiarySystemFill))
                }
            }
            picture
                .frame(maxWidth: .infinity)
                .frame(height: fillHeight ? nil : CGFloat(height >= 0 ? height : 220))
                .clipShape(RoundedRectangle(cornerRadius: edgeToEdge ? 0 : 16))
                .actionable(view.action, data, fallback: photo.map { reference in { viewing = PhotoSelection(references: [reference], index: 0) } })
                .photoViewer($viewing)
        }
    }
}
