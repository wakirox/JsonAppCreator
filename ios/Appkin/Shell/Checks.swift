import AppkinShared
import Foundation

/// A Kotlin object handed to another thread: the shared engine's checkers don't share state, so this is safe.
struct Unchecked<T>: @unchecked Sendable {
    let value: T
}

/// The editor's checks, off the main thread: the configuration's own, then (when it is right) whether its web
/// images load, asking each address once with a HEAD request as the Android app does.
actor Checks {
    private var statuses: [String: Int] = [:]
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 8
        configuration.httpAdditionalHeaders = ["User-Agent": ImageLoader.userAgent]
        session = URLSession(configuration: configuration)
    }

    func run(kit: Unchecked<ShellKit>, json: String) async -> Unchecked<[EditorIssue]> {
        let found = kit.value.check(json: json)
        if !found.isEmpty { return Unchecked(value: found) }
        let urls = kit.value.imageUrls(json: json)
        if urls.isEmpty { return Unchecked(value: []) }
        var codes: [KotlinInt] = []
        for url in urls { codes.append(KotlinInt(int: Int32(await status(of: url)))) }
        return Unchecked(value: kit.value.imageProblems(json: json, urls: urls, codes: codes))
    }

    /// The HTTP status of an address after redirects; -1 when it didn't answer.
    private func status(of address: String) async -> Int {
        if let known = statuses[address] { return known }
        var code = -1
        if let url = URL(string: address) {
            var request = URLRequest(url: url)
            request.httpMethod = "HEAD"
            if let (_, response) = try? await session.data(for: request), let http = response as? HTTPURLResponse {
                code = http.statusCode
            }
        }
        statuses[address] = code
        return code
    }
}
