import AppkinShared
import SwiftUI

/// Where the shell's navigation stack can go. Home is its root.
enum Destination: Hashable {
    case app(AppSession)
    case screen(AppSession, ScreenRoute)
    case editor(EditorSeed)
    case ask(AskSeed)
    case about
}

/// What the editor opens with. `source` is where unsaved text came from ("Opened from a file"), none for an app
/// that is saved or about to be written.
struct EditorSeed: Hashable {
    let id = UUID()
    var title: String
    var text: String
    var source: String?
    /// The text is an AI app's answer: its problems are offered back to the AI app first.
    var fromAIApp = false
    /// The app being edited, when it is one: unchanged, it keeps its API keys.
    var original: String?
    var originalConfigurationId: String?
}

/// What "Ask your AI app" opens with: the app the AI app may be asked to change, if there is one.
struct AskSeed: Hashable {
    let id = UUID()
    var currentApp: String?
    /// Change the current app from the start (opened from the editor).
    var changeCurrent = false
}

/// An app waiting for its API keys before it opens.
struct KeyRequest: Identifiable {
    let id = UUID()
    let json: String
    let configurationId: String
    let isExample: Bool
    let missing: MissingKeys
}

/// A file or answer read as an app, shown before it opens since it may come from someone else.
struct Incoming: Identifiable {
    let id = UUID()
    let info: ReceivedInfo
}

/// A message for the user.
struct Notice: Identifiable {
    let id = UUID()
    let title: LocalizedStringResource
    let message: LocalizedStringResource
}

/// A bundled example.
struct Example: Identifiable {
    let id: String
    let name: LocalizedStringResource
    let about: LocalizedStringResource
    let icon: String

    static let all = [
        Example(id: "v3_masterpieces", name: "Masterpieces",
                about: "25 famous paintings and prints, with maps, links and your favourites", icon: "🖼️"),
        Example(id: "v3_app_ideas", name: "App ideas",
                about: "Notes on your app ideas, with photos, sorted by priority and kept on the phone", icon: "💡"),
        Example(id: "v3_the_movie_db", name: "Movies",
                about: "Popular films and the people in them, from TMDB", icon: "🎬")
    ]

    var json: String? {
        Bundle.main.url(forResource: id, withExtension: "json").flatMap { try? String(contentsOf: $0, encoding: .utf8) }
    }
}

/// The app's state above its screens: the navigation stack, the library, and the ways an app or a file gets opened.
@MainActor
final class Shell: ObservableObject {
    let secrets = KeychainSecretStore()
    let kit: ShellKit
    @Published var path: [Destination] = []
    @Published var keyRequest: KeyRequest?
    @Published var incoming: Incoming?
    @Published var notice: Notice?

    init() {
        kit = ShellKit(secrets: secrets, bundledKeys: BundledKeys.encoded)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        ImageLoader.userAgent = JsonHttpClient.companion.userAgent(versionName: version, platform: "iOS \(UIDevice.current.systemVersion)")
    }

    var library: AppLibrary { kit.library }

    // MARK: Opening apps

    /// Shows an app, after asking for the API keys it needs that the Keychain lacks. `replacing` makes it the only
    /// screen over Home (an app run from the editor), else it is pushed.
    func open(json: String, configurationId: String? = nil, isExample: Bool = false, replacing: Bool = false) {
        let id = configurationId ?? kit.configurationId(json: json)
        if let missing = kit.missingKeys(json: json, configurationId: id) {
            keyRequest = KeyRequest(json: json, configurationId: id, isExample: isExample, missing: missing)
            pendingReplacing = replacing
            return
        }
        commit(json: json, configurationId: id, isExample: isExample, replacing: replacing)
    }

    private var pendingReplacing = false

    func saveKeys(_ request: KeyRequest, values: [String: String]) {
        kit.saveKeys(json: request.json, configurationId: request.configurationId, values: values)
        keyRequest = nil
        commit(json: request.json, configurationId: request.configurationId, isExample: request.isExample, replacing: pendingReplacing)
    }

    private func commit(json: String, configurationId: String, isExample: Bool, replacing: Bool) {
        // Examples aren't kept in Your apps (Home lists them apart); every other app is, opened now.
        if !isExample { _ = library.save(json: json, configurationId: configurationId) }
        let name = AppLibrary.companion.nameOf(json: json) ?? ""
        let session = AppSession(json: json, name: name, configurationId: configurationId, isExample: isExample, secrets: secrets)
        session.navigator.push = { [weak self, weak session] route in
            guard let session else { return }
            self?.path.append(.screen(session, route))
        }
        session.navigator.popIfTop = { [weak self] id in
            if case .screen(_, let route)? = self?.path.last, route.id == id { self?.path.removeLast() }
        }
        if replacing { path = [.app(session)] } else { path.append(.app(session)) }
    }

    func open(example: Example) {
        guard let json = example.json else { return }
        open(json: json, configurationId: "bundled:\(example.id)", isExample: true)
    }

    func open(saved app: AppLibrary.SavedApp) {
        open(json: app.json, configurationId: app.configurationId)
    }

    func goHome() { path = [] }

    // MARK: Editor and AI

    func newApp() {
        path.append(.editor(EditorSeed(title: String(localized: "New app"), text: kit.templateForNewApp())))
    }

    func edit(_ session: AppSession) {
        path.append(.editor(EditorSeed(
            title: String(localized: "Edit this app"), text: kit.prettyPrinted(json: session.json),
            original: session.json, originalConfigurationId: session.configurationId
        )))
    }

    func askAI(currentApp: String? = nil, changeCurrent: Bool = false) {
        path.append(.ask(AskSeed(currentApp: currentApp, changeCurrent: changeCurrent)))
    }

    /// An AI app's answer, pasted: opens in the editor, or says there is no app in it.
    func receiveAnswer(_ answer: String?) {
        guard let answer = answer?.trimmingCharacters(in: .whitespacesAndNewlines), !answer.isEmpty else {
            notice = Notice(title: "Nothing to paste", message: "The clipboard is empty: copy the answer in your AI app first")
            return
        }
        guard let json = kit.extractJson(answer: answer) else {
            let link = answer.hasPrefix("http") && !answer.contains(" ")
            notice = Notice(
                title: "No app to open",
                message: link
                    ? "That's a link to the chat, not the AI's answer. In your AI app, copy the answer that contains the JSON, then paste it here."
                    : "What you pasted has no JSON app in it. In your AI app, copy the answer that contains the JSON, then paste it here."
            )
            return
        }
        path.append(.editor(EditorSeed(
            title: String(localized: "From your AI app"), text: kit.prettyPrinted(json: json),
            source: String(localized: "Pasted from your AI app"), fromAIApp: true
        )))
    }

    // MARK: Files

    /// A JSON file from Files, "Open in Appkin" or the file picker.
    func receive(file url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            notice = Notice(title: "Couldn't open the file", message: "Unable to read the selected file")
            return
        }
        receive(fileText: text)
    }

    /// A file that is a valid app shows what it is first; one with problems goes to the editor to fix.
    func receive(fileText text: String) {
        if let info = kit.received(json: text) {
            incoming = Incoming(info: info)
        } else if let json = kit.extractJson(answer: text) {
            openFileInEditor(json)
        } else {
            notice = Notice(title: "Couldn't open the file", message: "That file has no JSON app in it.")
        }
    }

    func openFileInEditor(_ json: String) {
        path.append(.editor(EditorSeed(
            title: String(localized: "Opened file"), text: kit.prettyPrinted(json: json), source: String(localized: "Opened from a file")
        )))
    }

    func openIncoming(_ info: ReceivedInfo, keepBoth: Bool) {
        incoming = nil
        if keepBoth {
            let renamed = kit.renamed(json: info.json, name: library.freeName(name: info.name))
            open(json: renamed)
        } else {
            open(json: info.json)
        }
    }

    /// Runs the editor's text: the app opens over Home and is kept in Your apps. Unchanged text keeps its keys.
    func run(_ text: String, from seed: EditorSeed) {
        var id: String?
        if let original = seed.original, kit.sameJson(a: text, b: original) { id = seed.originalConfigurationId }
        open(json: text, configurationId: id, replacing: true)
    }
}
