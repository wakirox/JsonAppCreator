import AppkinShared
import SwiftUI

/// The navigation stack from Home, and the sheets any screen can raise: API keys, a received file, messages.
struct ShellRoot: View {
    @StateObject private var shell = Shell()

    var body: some View {
        NavigationStack(path: $shell.path) {
            HomeView()
                .navigationDestination(for: Destination.self) { destination in
                    switch destination {
                    case .app(let session): AppRootView(session: session)
                    case .screen(let session, let route): ScreenHost(session: session, route: route, isRoot: false)
                    case .editor(let seed): EditorView(seed: seed)
                    case .ask(let seed): AskAIView(seed: seed)
                    case .about: AboutView()
                    }
                }
        }
        .onOpenURL { shell.receive(file: $0) }
        .sheet(item: $shell.keyRequest) { request in
            KeyEntryView(request: request)
        }
        .sheet(item: $shell.incoming) { incoming in
            ReceivedSheet(info: incoming.info)
        }
        .alert(
            shell.notice.map { Text($0.title) } ?? Text(""),
            isPresented: Binding(get: { shell.notice != nil }, set: { if !$0 { shell.notice = nil } }),
            presenting: shell.notice
        ) { _ in
            Button("OK") {}
        } message: { notice in
            Text(notice.message)
        }
        .task {
            let kept = shell.kit.keptPhotoReferences()
            Task.detached(priority: .utility) { PhotoStore.deleteUnused(keeping: kept) }
            launchHooks()
        }
        .environmentObject(shell)
    }

    /// Launch arguments that open something straight away, for tests and screenshots.
    private func launchHooks() {
        #if DEBUG
        let defaults = UserDefaults.standard
        if defaults.string(forKey: "screen") == "run",
           let example = Example.all.first(where: { $0.id == defaults.string(forKey: "example") }) {
            shell.open(example: example)
        }
        if let file = defaults.string(forKey: "openFile") {
            shell.receive(file: URL(fileURLWithPath: file))
        }
        switch defaults.string(forKey: "screen") {
        case "about": shell.path.append(.about)
        case "ask": shell.askAI()
        case "editor": shell.newApp()
        default: break
        }
        #endif
    }
}

/// An app's first screen with the menu every app has.
struct AppRootView: View {
    @EnvironmentObject private var shell: Shell
    let session: AppSession

    var body: some View {
        ScreenHost(session: session, route: session.root, isRoot: true)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Edit this app", systemImage: "pencil") { shell.edit(session) }
                        Button("Ask your AI app to change it…", systemImage: "sparkles") {
                            shell.askAI(currentApp: session.json, changeCurrent: true)
                        }
                        Button("Share this app", systemImage: "square.and.arrow.up") {
                            Exporter.shareApp(name: session.name, json: session.json, fileName: shell.kit.fileName(name: session.name))
                        }
                        Divider()
                        Button("Home", systemImage: "house") { shell.goHome() }
                    } label: {
                        Image(systemName: "ellipsis").accessibilityLabel("Menu")
                    }
                }
            }
    }
}

/// The API keys an app needs, asked for before it opens and kept in the Keychain, never in the JSON.
struct KeyEntryView: View {
    @EnvironmentObject private var shell: Shell
    @Environment(\.dismiss) private var dismiss
    let request: KeyRequest
    @State private var values: [String: String] = [:]

    private var canLoad: Bool {
        request.missing.names.allSatisfy { request.missing.saved.contains($0) || !(values[$0] ?? "").isEmpty }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(request.missing.names, id: \.self) { name in
                        VStack(alignment: .leading) {
                            Text(name).font(.footnote).foregroundStyle(.secondary)
                            SecureField(request.missing.saved.contains(name) ? "Leave blank to keep the saved value" : "Key", text: binding(name))
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                        }
                    }
                } footer: {
                    Text("Enter the keys this app needs. They are kept in your device's Keychain and are not added to the JSON.")
                }
            }
            .navigationTitle("API keys")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { shell.keyRequest = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Load app") { shell.saveKeys(request, values: values.filter { !$0.value.isEmpty }) }
                        .disabled(!canLoad)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func binding(_ name: String) -> Binding<String> {
        Binding(get: { values[name] ?? "" }, set: { values[name] = $0 })
    }
}

/// A file that opened as an app: who made it, which websites it talks to, which keys it needs, then what to do.
struct ReceivedSheet: View {
    @EnvironmentObject private var shell: Shell
    let info: ReceivedInfo

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if let author = info.author, !author.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("By \(author) · version \(info.version)")
                    } else {
                        Text("Version \(info.version)")
                    }
                    if info.webAddresses.isEmpty {
                        Text("It doesn't connect to any website.")
                    } else {
                        Text("It connects to: \(info.webAddresses.joined(separator: ", "))")
                    }
                    if !info.apiKeys.isEmpty {
                        Text("It needs your own API key for \(info.apiKeys.joined(separator: ", ")): keys never travel with a shared app.")
                    }
                    if info.sameNameAsSaved {
                        Text("You already have an app called \(info.name). Replace it, or keep both.")
                    }
                }
                Section {
                    Button(info.sameNameAsSaved ? "Replace" : "Open") { shell.openIncoming(info, keepBoth: false) }
                    if info.sameNameAsSaved {
                        Button("Keep both") { shell.openIncoming(info, keepBoth: true) }
                    }
                    Button("Look at it in the editor") {
                        shell.incoming = nil
                        shell.openFileInEditor(info.json)
                    }
                }
            }
            .navigationTitle(info.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { shell.incoming = nil }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
