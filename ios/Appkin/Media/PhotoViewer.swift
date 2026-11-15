import SwiftUI
import UIKit

/// The photos a tap opens full screen, and the one shown first.
struct PhotoSelection: Identifiable {
    let id = UUID()
    let references: [String]
    let index: Int
}

extension View {
    /// Opens `selection` full screen: swipe between photos, pinch or double-tap to zoom, drag to look around.
    func photoViewer(_ selection: Binding<PhotoSelection?>) -> some View {
        fullScreenCover(item: selection) { PhotoViewer(selection: $0) }
    }
}

private struct PhotoViewer: View {
    let selection: PhotoSelection
    @Environment(\.dismiss) private var dismiss
    @State private var page: Int

    init(selection: PhotoSelection) {
        self.selection = selection
        _page = State(initialValue: selection.index)
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            TabView(selection: $page) {
                ForEach(Array(selection.references.enumerated()), id: \.offset) { index, reference in
                    ZoomablePhoto(reference: reference).tag(index).ignoresSafeArea()
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea()
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.body.weight(.semibold))
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.circle)
            .padding()
            .accessibilityLabel("Close")
        }
        .preferredColorScheme(.dark)
    }
}

/// One photo, loaded at its largest, in a zoomable scroll view.
private struct ZoomablePhoto: View {
    let reference: String
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                ZoomScroll(image: image)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task(id: reference) {
            if PhotoStore.isPhoto(reference) {
                image = PhotoStore.image(reference, full: true)
            } else if let url = imageURL(reference) {
                image = try? await ImageLoader.shared.image(at: url)
            }
        }
        .accessibilityLabel("Photo")
        .accessibilityIdentifier("photo-viewer")
    }
}

private struct ZoomScroll: UIViewRepresentable {
    let image: UIImage

    func makeUIView(context: Context) -> ZoomScrollView { ZoomScrollView() }

    func updateUIView(_ view: ZoomScrollView, context: Context) { view.show(image) }
}

/// A scroll view that zooms its picture between "whole" and 5 times that: the pan follows the finger and stops at the
/// picture's edges, and a picture smaller than the screen sits in the middle.
private final class ZoomScrollView: UIScrollView, UIScrollViewDelegate {
    private let pictureView = UIImageView()
    private var fitted = CGSize.zero

    init() {
        super.init(frame: .zero)
        delegate = self
        minimumZoomScale = 1
        maximumZoomScale = 5
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        bouncesZoom = true
        pictureView.contentMode = .scaleAspectFit
        addSubview(pictureView)
        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(toggleZoom(_:)))
        doubleTap.numberOfTapsRequired = 2
        addGestureRecognizer(doubleTap)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    func show(_ image: UIImage) {
        guard pictureView.image !== image else { return }
        pictureView.image = image
        zoomScale = 1
        fitted = .zero
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard let image = pictureView.image, bounds.width > 0, bounds.height > 0 else { return }
        let scale = min(bounds.width / image.size.width, bounds.height / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        if size != fitted {
            fitted = size
            zoomScale = 1
            pictureView.frame = CGRect(origin: .zero, size: size)
            contentSize = size
        }
        centre()
    }

    private func centre() {
        var frame = pictureView.frame
        frame.origin.x = frame.width < bounds.width ? (bounds.width - frame.width) / 2 : 0
        frame.origin.y = frame.height < bounds.height ? (bounds.height - frame.height) / 2 : 0
        pictureView.frame = frame
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { pictureView }

    func scrollViewDidZoom(_ scrollView: UIScrollView) { centre() }

    @objc private func toggleZoom(_ tap: UITapGestureRecognizer) {
        if zoomScale > 1 {
            setZoomScale(1, animated: true)
        } else {
            let point = tap.location(in: pictureView)
            let size = CGSize(width: bounds.width / 3, height: bounds.height / 3)
            zoom(to: CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2, width: size.width, height: size.height), animated: true)
        }
    }
}
