import AppkinShared
import Foundation
import UniformTypeIdentifiers

/// Files attached to saved objects: a security-scoped bookmark of the file the user picked (no copy is made), kept in
/// the file's `uri` as "appkin-bookmark:<base64>", so it opens again later, after a restart too, until it is moved or
/// deleted.
enum FileAttachments {
    static let scheme = "appkin-bookmark:"

    /// Keeps the file at `url` (from the document picker) and says what it is; nil when no bookmark can be made.
    static func add(_ url: URL) -> Files.Attached? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let bookmark = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) else { return nil }
        let values = try? url.resourceValues(forKeys: [.nameKey, .fileSizeKey, .contentTypeKey])
        return Files.Attached(
            name: values?.name ?? url.lastPathComponent,
            type: values?.contentType?.preferredMIMEType,
            size: values?.fileSize.map { KotlinLong(value: Int64($0)) },
            uri: scheme + bookmark.base64EncodedString()
        )
    }

    /// The file a `uri` points to, while it still exists; nil once it was moved or deleted.
    static func resolve(_ uri: String) -> URL? {
        guard uri.hasPrefix(scheme), let bookmark = Data(base64Encoded: String(uri.dropFirst(scheme.count))) else { return nil }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) else { return nil }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    /// The bytes of the file at `uri`, to pack it; nil when it can't be read.
    static func read(_ uri: String) -> Data? {
        guard let url = resolve(uri) else { return nil }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        return try? Data(contentsOf: url)
    }
}

/// A file opened for reading: its scope ends when this goes.
final class OpenedFile: Identifiable {
    let url: URL
    private let scoped: Bool
    var id: URL { url }

    init?(_ file: Files.Attached) {
        guard let url = FileAttachments.resolve(file.uri) else { return nil }
        self.url = url
        scoped = url.startAccessingSecurityScopedResource()
    }

    deinit { if scoped { url.stopAccessingSecurityScopedResource() } }
}
