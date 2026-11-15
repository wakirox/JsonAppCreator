import AppkinShared
import SwiftUI

/// "Ask your AI app": describe the app, check the instructions, send them to an AI app through the share sheet (or
/// copy them), then paste its answer back. Appkin has no AI inside and sends nothing anywhere itself.
struct AskAIView: View {
    @EnvironmentObject private var shell: Shell
    let seed: AskSeed

    @State private var request = ""
    @State private var changeCurrent: Bool
    @State private var short: Bool
    @State private var previewing = false
    @State private var copied = false
    @State private var baseTokens = 0
    @FocusState private var typing: Bool

    private struct Idea: Identifiable {
        let id: String
        let label: LocalizedStringResource
        let request: LocalizedStringResource
    }

    private static let ideas = [
        Idea(id: "books", label: "📚 Book list", request: "A list of my favourite books, with a screen for each: cover, author and my notes"),
        Idea(id: "recipes", label: "🍝 Recipes", request: "My family recipes, searchable, each with its ingredients and steps"),
        Idea(id: "race", label: "🏃 Race sign-up", request: "A sign-up form for our 10 km summer race, with name, email, date of birth and T-shirt size"),
        Idea(id: "gallery", label: "🖼️ Art gallery", request: "A gallery of famous paintings from a public museum API, with a page for each")
    ]

    init(seed: AskSeed) {
        self.seed = seed
        _changeCurrent = State(initialValue: seed.changeCurrent && seed.currentApp != nil)
        _short = State(initialValue: false)
    }

    private var changing: Bool { changeCurrent && seed.currentApp != nil }

    var body: some View {
        Form {
            Section {
                Text("Your AI app writes it, Appkin shows it.").foregroundStyle(.secondary)
                TextField(changing ? "Add a screen with the opening hours" : "A list of my favourite books, with a screen for each",
                          text: $request, axis: .vertical)
                    .lineLimit(3...8)
                    .focused($typing)
                    .accessibilityIdentifier("ask-request")
                if !changing {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(Self.ideas) { idea in
                                Button { request = String(localized: idea.request) } label: { Text(idea.label) }
                                    .buttonStyle(.bordered)
                                    .buttonBorderShape(.capsule)
                            }
                        }
                    }
                }
                if seed.currentApp != nil {
                    Toggle(isOn: $changeCurrent) {
                        VStack(alignment: .leading) {
                            Text("Change the current app")
                            Text("Your AI app gets this app too, and changes it instead of starting over.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
            } header: {
                Text(changing ? "What should change?" : "What should your app do?")
            }

            Section {
                Picker("Instructions", selection: $short) {
                    Text("Send all").tag(false)
                    Text("Short + links").tag(true)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("ask-instructions")
                Text(short
                     ? "The rules and a list of every property, with links to the details: shorter, for AI apps that can open links."
                     : "Every rule, property and example in the chat: longer, but works with any AI app.")
                    .font(.footnote).foregroundStyle(.secondary)
                Text("About \(tokens.formatted()) tokens: your AI app reads them before the chat starts.")
                    .font(.footnote).foregroundStyle(.secondary)
                Button("See what's sent") { previewing = true }
            } header: {
                Text("Instructions")
            }

            Section {
                Button { copy() } label: { Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc") }
                Button { Exporter.present([prompt()]) } label: { Label("Share to AI app…", systemImage: "square.and.arrow.up") }
                    .bold()
                Text(copied ? "Copied: paste it into your AI app" : "Share this to your AI app, such as ChatGPT, Claude or Gemini.")
                    .font(.footnote).foregroundStyle(.secondary)
            } footer: {
                Text("Appkin has no AI inside, and sends nothing anywhere else.")
            }

            Section {
                Text("Chat there until you like the result.")
                Text("Bring it back: copy the answer, or open the file it makes. Appkin checks it and opens it.")
                    .foregroundStyle(.secondary)
                PasteRow { shell.receiveAnswer($0) }
            } header: {
                Text("Then")
            }
        }
        .scrollDismissesKeyboard(.immediately)
        .navigationTitle("Ask your AI app")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { short = shell.kit.shortInstructions }
        .onChange(of: short) { _, value in shell.kit.shortInstructions = value }
        .onChange(of: request) { copied = false }
        .task(id: "\(changing)-\(short)") { baseTokens = Int(shell.kit.tokens(text: build(request: nil))) }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { typing = false }
            }
        }
        .sheet(isPresented: $previewing) { preview }
    }

    // MARK: The instructions

    private func resource(_ name: String, _ type: String) -> String {
        Bundle.main.url(forResource: name, withExtension: type).flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
    }

    private func build(request: String?) -> String {
        shell.kit.prompt(
            guide: resource("LLM_GUIDE", "md"),
            checklist: resource("v3_ai_checklist", "json"),
            signUpForm: resource("v3_race_signup", "json"),
            restList: resource("v3_open_access_art", "json"),
            currentApp: changing ? seed.currentApp : nil,
            request: request,
            withLinks: short
        )
    }

    private func prompt() -> String { build(request: request) }

    /// About how many tokens: the instructions are measured once per choice, the request is added as it's typed.
    private var tokens: Int {
        Int(shell.kit.roundedTokens(tokens: Int32(baseTokens + Int(shell.kit.tokens(text: request)))))
    }

    private func copy() {
        UIPasteboard.general.string = prompt()
        copied = true
    }

    private var preview: some View {
        let text = prompt()
        return NavigationStack {
            ScrollView {
                Text(text)
                    .font(.system(size: 12, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .navigationTitle("What's sent")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .top) {
                Text("\(text.count.formatted()) characters, about \(Int(shell.kit.tokens(text: text)).formatted()) tokens. Your request and app are at the end.")
                    .font(.footnote).foregroundStyle(.secondary)
                    .padding(.horizontal).padding(.bottom, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.bar)
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { previewing = false } }
            }
        }
    }
}
