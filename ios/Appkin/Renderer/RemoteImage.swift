import SwiftUI
import UIKit

/// Downloads the pictures of the apps. Sites such as Wikimedia Commons refuse a request without a User-Agent that names
/// the app, which `AsyncImage` can't set, so every picture goes through this session.
final class ImageLoader: @unchecked Sendable {
    static let shared = ImageLoader()
    /// The engine's "Appkin/<version> (iOS; ...)"; set before the first picture loads.
    nonisolated(unsafe) static var userAgent = "Appkin"

    private let cache = NSCache<NSURL, UIImage>()
    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.httpAdditionalHeaders = ["User-Agent": ImageLoader.userAgent]
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        return URLSession(configuration: configuration)
    }()

    func image(at url: URL) async throws -> UIImage {
        if let cached = cache.object(forKey: url as NSURL) { return cached }
        if url.scheme == "appkin-photo" {
            // A kept photo: its thumbnail, from the app's own storage.
            guard let image = PhotoStore.image(url.absoluteString, full: false) else { throw URLError(.fileDoesNotExist) }
            cache.setObject(image, forKey: url as NSURL)
            return image
        }
        let (data, response) = try await session.data(from: url)
        if let status = (response as? HTTPURLResponse)?.statusCode, !(200..<300).contains(status) {
            throw URLError(.badServerResponse)
        }
        guard let image = UIImage(data: data) else { throw URLError(.cannotDecodeContentData) }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: url as NSURL)
        return ready
    }
}

/// A picture from the web: it fills the space it is given (cropped), or fits whole inside it.
struct RemoteImage: View {
    let url: URL?
    var fit = false

    private enum Phase {
        case loading, loaded(UIImage), failed
    }

    @State private var phase = Phase.loading

    var body: some View {
        Color.clear
            .overlay {
                switch phase {
                case .loading:
                    ProgressView()
                case .loaded(let image):
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: fit ? .fit : .fill)
                case .failed:
                    Label("Image unavailable", systemImage: "photo")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .background(Color(.tertiarySystemFill).opacity(isLoaded ? 0 : 1))
            .clipped()
            .task(id: url) { await load() }
    }

    private var isLoaded: Bool {
        if case .loaded = phase { return true }
        return false
    }

    private func load() async {
        guard let url else { phase = .failed; return }
        phase = .loading
        do {
            phase = .loaded(try await ImageLoader.shared.image(at: url))
        } catch {
            if !Task.isCancelled { phase = .failed }
        }
    }
}

/// An address from a JSON app as a URL; the app may write characters (quotes, spaces) a URL has to escape.
func imageURL(_ address: String) -> URL? {
    URL(string: address, encodingInvalidCharacters: true)
}
