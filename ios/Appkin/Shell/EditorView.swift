import AppkinShared
import SwiftUI

/// The JSON editor: monospaced text, the shared live check with its problem bar, the web images' check, and Open.
struct EditorView: View {
    @EnvironmentObject private var shell: Shell
    @Environment(\.dismiss) private var dismiss
    let seed: EditorSeed

    @State private var text: String
    @State private var issues: [EditorIssue]?
    @State private var issuesOpen = false
    @State private var selection: TextSelection?
    @State private var confirmDiscard = false
    @State private var pasted: String?
    @State private var pasting = false
    @State private var toast: String?
    @State private var checks = Checks()
    @FocusState private var focused: Bool

    init(seed: EditorSeed) {
        self.seed = seed
        _text = State(initialValue: seed.text)
    }

    var body: some View {
        VStack(spacing: 0) {
            if let source = seed.source { banner(source) }
            TextEditor(text: $text, selection: $selection)
                .font(.system(size: 13, design: .monospaced))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .focused($focused)
                .accessibilityIdentifier("editor-text")
                // The keyboard's smart punctuation turns a typed " into “ or ”, which breaks the JSON: what was just
                // typed gets a straight quote back. Pasted text keeps its own quotes.
                .onChange(of: text) { old, new in
                    if let straight = straightenTyped(old: old, new: new) { text = straight }
                }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                suggestionBar
                problemBar
            }
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.bottom, 120)
                    .transition(.opacity)
            }
        }
        .navigationTitle(seed.title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar { toolbar }
        .task(id: text) {
            issues = nil
            try? await Task.sleep(for: .milliseconds(450))
            if Task.isCancelled { return }
            issues = await checks.run(kit: Unchecked(value: shell.kit), json: text).value
        }
        .sheet(isPresented: $pasting) {
            Form { PasteRow { paste($0) } }
                .presentationDetents([.height(220)])
        }
        .alert("Use the answer from your AI app?", isPresented: Binding(get: { pasted != nil }, set: { if !$0 { pasted = nil } })) {
            Button("Replace") {
                if let pasted { text = shell.kit.prettyPrinted(json: pasted) }
                pasted = nil
            }
            Button("Cancel", role: .cancel) { pasted = nil }
        } message: {
            Text("It replaces the text in the editor. The check then shows anything to fix.")
        }
        .alert("Discard changes?", isPresented: $confirmDiscard) {
            Button("Discard", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text(seed.source != nil ? "This app hasn't been opened or saved." : "Your edits haven't been opened or saved.")
        }
    }

    // MARK: Toolbar

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button { leave() } label: { Image(systemName: "chevron.backward") }
                .accessibilityLabel("Back")
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            Menu {
                insertMenu
            } label: {
                Image(systemName: "plus").accessibilityLabel("Insert")
            }
            Button { Task { await open() } } label: {
                Image(systemName: "play.fill").accessibilityLabel("Open")
            }
            Menu {
                Button("Format", systemImage: "text.alignleft") { format() }
                Button("Save as a file…", systemImage: "square.and.arrow.down") { saveAsFile() }
                Divider()
                Button("Send to your AI app…", systemImage: "sparkles") { shell.askAI(currentApp: text, changeCurrent: true) }
                Button("Paste answer from your AI app", systemImage: "doc.on.clipboard") { pasting = true }
            } label: {
                Image(systemName: "ellipsis").accessibilityLabel("More")
            }
        }
    }

    @ViewBuilder
    private var insertMenu: some View {
        let all = Snippets.shared.all
        Section("Views") {
            ForEach(all.filter { !$0.formField }, id: \.label) { snippet in
                Button(snippet.label) { insert(snippet) }
            }
        }
        Section("Form fields (inside formElements)") {
            ForEach(all.filter { $0.formField }, id: \.label) { snippet in
                Button(snippet.label) { insert(snippet) }
            }
        }
    }

    // MARK: Problem bar

    private func banner(_ source: String) -> some View {
        Text(seed.fromAIApp
             ? "\(source), not saved yet. ▶ opens it; problems can go back to your AI app from the bar below."
             : "\(source), not saved yet. ▶ opens it.")
            .font(.footnote)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .background(.secondary.opacity(0.15))
    }

    @ViewBuilder
    private var suggestionBar: some View {
        if focused, let cursor = cursorOffset {
            let found = shell.kit.suggestions(json: text, cursor: Int32(cursor))
            if !found.items.isEmpty {
                VStack(spacing: 0) {
                    Divider()
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            if let heading = found.heading {
                                Text("\(heading):")
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(.secondary)
                            }
                            ForEach(Array(found.items.enumerated()), id: \.offset) { _, item in
                                Button {
                                    apply(item)
                                } label: {
                                    Text(item.required ? "\(item.label)*" : item.label)
                                        .font(.system(size: 12, weight: item.required ? .semibold : .regular, design: .monospaced))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color(.secondarySystemFill), in: Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                    }
                    .background(.bar)
                }
            }
        }
    }

    @ViewBuilder
    private var problemBar: some View {
        VStack(spacing: 0) {
            Divider()
            if let issues {
                if issues.isEmpty {
                    Label("No problems: ready to open", systemImage: "checkmark.circle")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                } else {
                    problems(issues)
                }
            } else {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Checking…")
                    Spacer()
                }
                .padding(12)
            }
        }
        .background(.bar)
    }

    private func problems(_ issues: [EditorIssue]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation { issuesOpen.toggle() } } label: {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(issues.count) problems").font(.headline)
                        if !issuesOpen, let first = issues.first {
                            Text(describe(first)).font(.footnote).lineLimit(2).multilineTextAlignment(.leading)
                        }
                    }
                    Spacer()
                    Image(systemName: issuesOpen ? "chevron.down" : "chevron.up")
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("problems-toggle")
            if issuesOpen {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(issues.enumerated()), id: \.offset) { _, issue in
                            Button { jump(to: issue) } label: {
                                Text(describe(issue)).font(.footnote).multilineTextAlignment(.leading)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .frame(maxHeight: 180)
            }
            if seed.fromAIApp {
                Button { sendToAI(issues) } label: {
                    Label("Send problems to your AI app…", systemImage: "square.and.arrow.up")
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .controlSize(.large)
            } else {
                Button { sendToAI(issues) } label: {
                    Label("Ask an AI app to fix them…", systemImage: "sparkles").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
        }
        .foregroundStyle(.red)
        .padding(12)
        .background(.red.opacity(0.1))
    }

    private func describe(_ issue: EditorIssue) -> String {
        guard let offset = issue.offset else { return issue.message }
        let line = shell.kit.line(json: text, offset: offset.int32Value)
        return String(localized: "Line \(Int(line)): \(issue.message)")
    }

    // MARK: Actions

    private func sendToAI(_ issues: [EditorIssue]) {
        Exporter.present([shell.kit.fixRequest(json: text, issues: issues)])
    }

    private func leave() {
        if seed.source != nil || text != seed.text { confirmDiscard = true } else { dismiss() }
    }

    /// Checks the text as it is now, then opens it, or shows what to fix first.
    private func open() async {
        let current = text
        let found = await checks.run(kit: Unchecked(value: shell.kit), json: current).value
        issues = found
        if found.isEmpty {
            shell.run(current, from: seed)
        } else {
            issuesOpen = true
            if let first = found.first(where: { $0.offset != nil }) { jump(to: first) }
            show(String(localized: "\(found.count) problems to fix first"))
        }
    }

    private func show(_ message: String) {
        withAnimation { toast = message }
        Task {
            try? await Task.sleep(for: .seconds(2))
            withAnimation { toast = nil }
        }
    }

    private func format() {
        if shell.kit.canParse(json: text) {
            text = shell.kit.prettyPrinted(json: text)
            selection = nil
        } else {
            show(String(localized: "Fix the JSON syntax first"))
        }
    }

    private func saveAsFile() {
        let name = AppLibrary.companion.nameOf(json: text) ?? String(localized: "App")
        Exporter.shareApp(name: name, json: text, fileName: shell.kit.fileName(name: name))
    }

    private func paste(_ answer: String?) {
        pasting = false
        guard let answer else {
            show(String(localized: "No JSON in the clipboard: copy the AI's answer first"))
            return
        }
        if let json = shell.kit.extractJson(answer: answer) {
            pasted = json
            return
        }
        if let shareLink = shell.kit.findShareLink(text: answer) {
            Task {
                if let json = await shell.readShareLink(shareLink) {
                    pasted = json
                } else {
                    show(String(localized: "Couldn't read that shared chat: copy the AI's answer first"))
                }
            }
            return
        }
        show(String(localized: "No JSON in the clipboard: copy the AI's answer first"))
    }

    private var cursorOffset: Int? {
        guard let selection, case .selection(let range) = selection.indices, range.isEmpty else { return nil }
        return text.utf16.distance(from: text.startIndex, to: range.lowerBound)
    }

    private func apply(_ suggestion: Suggestion) {
        let from = index(Int(suggestion.start))
        let to = index(Int(suggestion.end))
        guard from <= to else { return }
        text.replaceSubrange(from..<to, with: suggestion.insert)
        selection = TextSelection(insertionPoint: index(Int(suggestion.start) + Int(suggestion.cursor)))
    }

    private func index(_ offset: Int) -> String.Index {
        String.Index(utf16Offset: min(max(offset, 0), text.utf16.count), in: text)
    }

    private func jump(to issue: EditorIssue) {
        guard let offset = issue.offset else { return }
        focused = true
        selection = TextSelection(insertionPoint: index(offset.intValue))
    }

    private func insert(_ snippet: Snippets.Snippet) {
        var range = text.endIndex..<text.endIndex
        if let selection, case .selection(let chosen) = selection.indices { range = chosen }
        let start = text.utf16.distance(from: text.startIndex, to: range.lowerBound)
        let indent = Snippets.shared.indentAt(text: text, offset: Int32(start))
        let addition = Snippets.shared.indented(json: snippet.json, indent: indent)
        text.replaceSubrange(range, with: addition)
        focused = true
        selection = TextSelection(insertionPoint: index(start + addition.utf16.count))
    }
}

/// [new] with the curly double quotes just typed (a short insertion into [old]) made straight; nil when there were none.
func straightenTyped(old: String, new: String) -> String? {
    let added = new.count - old.count
    guard (1...2).contains(added) else { return nil }
    let start = zip(old, new).prefix { $0 == $1 }.count
    let from = new.index(new.startIndex, offsetBy: start)
    let to = new.index(from, offsetBy: added)
    let typed = new[from..<to]
    guard typed.contains(where: { $0 == "\u{201C}" || $0 == "\u{201D}" }) else { return nil }
    let fixed = typed.replacingOccurrences(of: "\u{201C}", with: "\"").replacingOccurrences(of: "\u{201D}", with: "\"")
    return String(new[..<from]) + fixed + String(new[to...])
}
