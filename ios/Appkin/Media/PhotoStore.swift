import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

/// The photos JSON apps keep, referred to as "appkin-photo:<name>.jpg". iOS gives no lasting link to a picked photo, so
/// the app keeps two JPEGs in Application Support (which the device backup carries): a small thumbnail ([thumbSide] px),
/// which lists show, and a copy of the photo ([fullSide] px), which the full-screen view, share, PDF and Export read.
enum PhotoStore {
    static let scheme = "appkin-photo:"
    static let thumbSide = 640
    static let fullSide = 2000

    private static var folder: URL {
        URL.applicationSupportDirectory.appending(path: "photos", directoryHint: .isDirectory)
    }

    private static var copies: URL { folder.appending(path: "full", directoryHint: .isDirectory) }

    static func isPhoto(_ address: String) -> Bool { address.hasPrefix(scheme) }

    /// The file name in a reference, or nil when it isn't one (or would leave the photos folder).
    static func fileName(_ reference: String) -> String? {
        guard isPhoto(reference) else { return nil }
        let name = String(reference.dropFirst(scheme.count))
        return name.isEmpty || name.contains("/") || name.contains("\\") || name == ".." ? nil : name
    }

    /// Keeps the picture in `data` (any format the system reads): its thumbnail and its copy. Gives the reference.
    static func add(_ data: Data) -> String? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let thumb = jpeg(source, side: thumbSide),
              let full = jpeg(source, side: fullSide) else { return nil }
        let name = "\(UUID().uuidString.lowercased()).jpg"
        do {
            try FileManager.default.createDirectory(at: copies, withIntermediateDirectories: true)
            try thumb.write(to: folder.appending(path: name))
            try full.write(to: copies.appending(path: name))
        } catch {
            return nil
        }
        return scheme + name
    }

    /// The thumbnail a reference points to, or nil when there is none.
    static func thumbnail(_ reference: String) -> URL? {
        guard let name = fileName(reference) else { return nil }
        let url = folder.appending(path: name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    /// The largest version there is: the kept copy, else the thumbnail.
    static func best(_ reference: String) -> URL? {
        guard let name = fileName(reference) else { return nil }
        let url = copies.appending(path: name)
        return FileManager.default.fileExists(atPath: url.path) ? url : thumbnail(reference)
    }

    /// A photo's JPEG bytes for share, PDF, Export and requests that send it.
    static func data(_ reference: String) -> Data? { best(reference).flatMap { try? Data(contentsOf: $0) } }

    /// The image of a photo reference: the thumbnail, or the large copy.
    static func image(_ reference: String, full: Bool) -> UIImage? {
        guard let url = full ? best(reference) : thumbnail(reference) else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    /// Deletes the files of a photo no longer used.
    static func remove(_ reference: String) {
        guard let name = fileName(reference) else { return }
        try? FileManager.default.removeItem(at: folder.appending(path: name))
        try? FileManager.default.removeItem(at: copies.appending(path: name))
    }

    /// Deletes photo files that no saved collection item refers to any more.
    static func deleteUnused(keeping used: Set<String>) {
        let keptNames = Set(used.compactMap(fileName))
        for dir in [folder, copies] {
            guard let names = try? FileManager.default.contentsOfDirectory(atPath: dir.path) else { continue }
            for name in names where name.hasSuffix(".jpg") && !keptNames.contains(name) {
                try? FileManager.default.removeItem(at: dir.appending(path: name))
            }
        }
    }

    /// The picture scaled down to `side` px on its longer edge (never up), upright, as a JPEG without its metadata.
    private static func jpeg(_ source: CGImageSource, side: Int) -> Data? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: side
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let output = NSMutableData()
        guard let target = CGImageDestinationCreateWithData(output, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(target, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
        return CGImageDestinationFinalize(target) ? output as Data : nil
    }
}
