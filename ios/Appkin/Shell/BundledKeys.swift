import Foundation

/// The API keys the build put in the app for its bundled examples (such as the Movies example's TMDB key), as the
/// shared engine reads them: Base64 of {"v3_the_movie_db": {"TMDB_API_KEY": "…"}}. Empty when the build had none; the
/// example then asks for its key. Written by a build step from the build machine's key file, never kept in git.
enum BundledKeys {
    static let encoded: String = Bundle.main.url(forResource: "bundled-api-keys", withExtension: "b64")
        .flatMap { try? String(contentsOf: $0, encoding: .utf8) }?
        .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
}
