import AppkinShared
import SwiftUI
import UniformTypeIdentifiers

/// The first screen: what Appkin does, the apps the user has opened, the examples, and the ways to get a new app.
struct HomeView: View {
    @EnvironmentObject private var shell: Shell
    @AppStorage("how_it_works_open") private var explained = false
    @State private var apps: [AppLibrary.SavedApp] = []
    @State private var deleting: AppLibrary.SavedApp?
    @State private var importing = false

    @ViewBuilder private var startButtons: some View {
        Button { shell.newApp() } label: {
            Label("New app", systemImage: "plus").frame(maxWidth: .infinity)
        }
        Button { shell.askAI() } label: {
            Label("Ask your AI app", systemImage: "sparkles").frame(maxWidth: .infinity)
        }
    }

    var body: some View {
        List {
            HowItWorks(expanded: $explained)
            Section {
                // One above the other: side by side, a phone's width cuts "Ask your AI app".
                VStack(spacing: 8) { startButtons }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .lineLimit(1)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }
            Section("Your apps") {
                if apps.isEmpty {
                    Text("The apps you open or make are kept here. Start with an example below.")
                        .foregroundStyle(.secondary)
                }
                ForEach(apps, id: \.id) { app in
                    Button { shell.open(saved: app) } label: { SavedAppRow(app: app) }
                        .buttonStyle(.plain)
                        .swipeActions(edge: .trailing) {
                            Button("Delete", systemImage: "trash", role: .destructive) { deleting = app }
                            Button("Share", systemImage: "square.and.arrow.up") { share(app) }.tint(.gray)
                        }
                        .contextMenu {
                            Button("Share", systemImage: "square.and.arrow.up") { share(app) }
                            Button("Delete", systemImage: "trash", role: .destructive) { deleting = app }
                        }
                }
            }
            Section {
                ForEach(Example.all) { example in
                    Button { shell.open(example: example) } label: {
                        HStack(spacing: 12) {
                            AppIconView(name: String(localized: example.name), icon: example.icon)
                            VStack(alignment: .leading) {
                                Text(example.name)
                                Text(example.about).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            } header: {
                Text("Examples")
            } footer: {
                Text("Change an example with Edit this app in its ••• menu, and your version is kept under Your apps.")
            }
        }
        .navigationTitle("Appkin")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("New app", systemImage: "square.and.pencil") { shell.newApp() }
                    Button("Ask your AI app…", systemImage: "sparkles") { shell.askAI() }
                    Divider()
                    Button("Open a file…", systemImage: "folder") { importing = true }
                    Link(destination: Links.formatGuide) { Label("JSON format guide", systemImage: "book") }
                    Button("About Appkin", systemImage: "info.circle") { shell.path.append(.about) }
                } label: {
                    Image(systemName: "ellipsis").accessibilityLabel("Menu")
                }
            }
        }
        .onAppear(perform: reload)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText]) { result in
            if case .success(let url) = result { shell.receive(file: url) }
        }
        .confirmationDialog(
            deleting.map { String(localized: "Delete \($0.name)?") } ?? "",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible,
            presenting: deleting
        ) { app in
            Button("Delete", role: .destructive) {
                shell.library.delete(id: app.id)
                reload()
            }
        } message: { _ in
            Text("It's removed from Your apps.")
        }
    }

    private func reload() { apps = shell.library.all() }

    private func share(_ app: AppLibrary.SavedApp) {
        Exporter.shareApp(name: app.name, json: app.json, fileName: shell.kit.fileName(name: app.name))
    }
}

private struct SavedAppRow: View {
    let app: AppLibrary.SavedApp

    var body: some View {
        HStack(spacing: 12) {
            AppIconView(name: app.name, icon: app.icon)
            VStack(alignment: .leading) {
                Text(app.name)
                Text(opened).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .contentShape(Rectangle())
    }

    private var opened: String {
        let date = Date(timeIntervalSince1970: TimeInterval(app.savedAt) / 1000)
        if Date().timeIntervalSince(date) < 60 { return String(localized: "Opened just now") }
        let ago = date.formatted(.relative(presentation: .named))
        return String(localized: "Opened \(ago)")
    }
}

/// What the app does, in plain words. Closed until opened, and it stays as the user left it.
private struct HowItWorks: View {
    @Binding var expanded: Bool

    var body: some View {
        Section {
            DisclosureGroup("How it works", isExpanded: $expanded) {
                VStack(alignment: .leading, spacing: 14) {
                    step(1, "Every app is one file",
                         "A JSON file is plain text that describes an app: its screens, the words and pictures on them, lists, forms and buttons, and what happens when you tap something.")
                    step(2, "Appkin shows it",
                         "Open a file and Appkin draws its screens at once, with its own built-in parts: nothing in the file runs as code. Nothing to install, build or sign up for.")
                    step(3, "Get files your way",
                         "Try an example below, open a file someone sent you, write one in the built-in editor, or ask the AI app you already use (ChatGPT, Gemini, Claude…) to make one for you.")
                    step(4, "Keep the ones you like",
                         "Every app you open is kept under Your apps, so you can come back to it here.")
                    Text("Inside an app, its ••• menu lets you edit it, ask your AI app to change it, share it, or come back here.")
                        .font(.footnote)
                    Link("Write one yourself: read the JSON format guide", destination: Links.formatGuide)
                        .font(.footnote)
                }
                .padding(.vertical, 6)
            }
        }
    }

    private func step(_ number: Int, _ title: LocalizedStringKey, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.footnote.weight(.semibold))
                .frame(width: 24, height: 24)
                .background(.primary.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(text).font(.subheadline)
            }
        }
    }
}

enum Links {
    static let site = URL(string: "https://wakirox.app")!
    /// The format reference written for people (a link target; the repository is the project's home).
    static let formatGuide = URL(string: "https://github.com/wakirox/JsonAppCreator/blob/master/docs/CONFIGURATION_V3.md")!
    static let contact = "info@wakirox.app"
}
