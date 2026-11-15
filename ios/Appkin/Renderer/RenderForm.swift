import AppkinShared
import SwiftUI

private let fieldSpacing: CGFloat = 16

/// A form's values and error for SwiftUI: they come from the shared `FormsState`, which keeps them.
@MainActor
final class FormModel: ObservableObject {
    nonisolated(unsafe) let screen: ScreenViewModel
    let form: FormView
    let data: ItemData
    private let state: FormsState.Form
    @Published private(set) var values: FormValues
    @Published private(set) var error: String?
    nonisolated(unsafe) private var watchers: [Watching] = []

    init(screen: ScreenViewModel, form: FormView, data: ItemData) {
        self.screen = screen
        self.form = form
        self.data = data
        state = RenderKit.shared.form(screen: screen, form: form, data: data)
        values = RenderKit.shared.currentValues(form: state)
        error = state.error.value as? String
        watchers = [
            Watch.shared.flow(flow: RenderKit.shared.values(form: state)) { [weak self] value in
                if let value = value as? FormValues { self?.values = value }
            },
            Watch.shared.flow(flow: state.error) { [weak self] value in self?.error = value as? String }
        ]
    }

    deinit { watchers.forEach { $0.cancel() } }

    func text(_ mapping: String) -> String { values.text(mapping: mapping) ?? "" }

    func setText(_ mapping: String, _ text: String) {
        RenderKit.shared.setText(screen: screen, form: form, data: data, mapping: mapping, text: text)
        values = RenderKit.shared.currentValues(form: state)
    }

    func setFlag(_ mapping: String, _ flag: Bool) {
        RenderKit.shared.setFlag(screen: screen, form: form, data: data, mapping: mapping, flag: flag)
        values = RenderKit.shared.currentValues(form: state)
    }

    func setTags(_ mapping: String, _ tags: [String]) {
        RenderKit.shared.setTags(screen: screen, form: form, data: data, mapping: mapping, tags: tags)
        values = RenderKit.shared.currentValues(form: state)
    }

    func setPhotos(_ mapping: String, _ photos: [String]) {
        RenderKit.shared.setPhotos(screen: screen, form: form, data: data, mapping: mapping, photos: photos)
        values = RenderKit.shared.currentValues(form: state)
    }

    func setFiles(_ mapping: String, _ files: [Files.Attached]) {
        RenderKit.shared.setFiles(screen: screen, form: form, data: data, mapping: mapping, files: files)
        values = RenderKit.shared.currentValues(form: state)
    }

    func submit() { RenderKit.shared.submit(screen: screen, form: form, data: data) }
}

/// A form: its title, its fields, why it wasn't sent, and the button that sends it.
struct RenderForm: View {
    @EnvironmentObject private var screen: ScreenModel
    let view: FormView
    let data: ItemData

    var body: some View {
        // A form on other data (an item just saved) is another form: it starts again from that data.
        FormContent(model: FormModel(screen: screen.vm, form: view, data: data))
            .id(data.description)
    }
}

private struct FormContent: View {
    @StateObject var model: FormModel

    init(model: @autoclosure @escaping () -> FormModel) {
        _model = StateObject(wrappedValue: model())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: fieldSpacing) {
            if let title = model.form.title {
                Text(title).font(.headline)
            }
            ForEach(Array(model.form.formElements.enumerated()), id: \.offset) { _, element in
                FormField(element: element)
            }
            if let error = model.error {
                Text(error).font(.subheadline).foregroundStyle(.red)
            }
            Button(action: model.submit) {
                // The fill is the tint (.primary: white in dark mode), so the text takes the screen's colour.
                Text(model.form.buttonTitle ?? "Submit").frame(maxWidth: .infinity)
                    .foregroundStyle(Color(.systemBackground))
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .environmentObject(model)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FormField: View {
    let element: FormElementImpl

    var body: some View {
        switch element {
        case let item as TextInput: TextFormField(item: item)
        case let item as AppkinShared.Selector: SelectorField(item: item)
        case let item as CheckBox: CheckBoxField(item: item)
        case let item as DateInput: DateField(item: item)
        case let item as TimeInput: TimeField(item: item)
        case let item as TagsInput: TagsField(item: item)
        case let item as PhotosInput: PhotosFormField(item: item)
        case let item as FilesInput: FilesFormField(item: item)
        case is HiddenInput: EmptyView()
        default: FieldBox(title: element.title, description: element.description_) {
            Label("Not yet on iOS", systemImage: "hourglass").foregroundStyle(.secondary)
        }
        }
    }
}

/// A field's title above its control, and its description below.
struct FieldBox<Content: View>: View {
    let title: String
    let description: String?
    var boxed = true
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.weight(.medium)).foregroundStyle(.secondary)
            if boxed {
                content
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    // Outlined on the screen's own colour: a grey fill with no edge reads as a disabled field.
                    .background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(.systemGray3)))
            } else {
                content
            }
            if let description { Text(description).font(.footnote).foregroundStyle(.secondary) }
        }
    }
}

private struct TextFormField: View {
    @EnvironmentObject private var model: FormModel
    let item: TextInput

    var body: some View {
        let binding = Binding(get: { model.text(item.mapping) }, set: { model.setText(item.mapping, $0) })
        let placeholder = item.placeholder ?? ""
        let plain = item.textType == nil || item.textType == .plain
        FieldBox(title: item.title, description: item.description_) {
            Group {
                if item.textType == .password {
                    SecureField(placeholder, text: binding)
                } else if item.multiline?.boolValue == true {
                    SwiftUI.TextField(placeholder, text: binding, axis: .vertical).lineLimit(1...8)
                } else {
                    SwiftUI.TextField(placeholder, text: binding)
                }
            }
            .keyboardType(keyboard)
            .textInputAutocapitalization(plain ? .sentences : .never)
            .autocorrectionDisabled(!plain)
        }
    }

    private var keyboard: UIKeyboardType {
        switch item.textType {
        case .numeric: .numberPad
        case .email: .emailAddress
        case .url: .URL
        default: .default
        }
    }
}

private struct SelectorField: View {
    @EnvironmentObject private var model: FormModel
    let item: AppkinShared.Selector

    var body: some View {
        let options = item.options
        let selection = Binding<String?>(
            get: { model.values.text(mapping: item.mapping) },
            set: { model.setText(item.mapping, $0 ?? "") }
        )
        // A few short options sit side by side; more, or longer ones, open as a menu.
        let connected = (2...4).contains(options.count) && options.allSatisfy { $0.label.count <= 12 }
        FieldBox(title: item.title, description: item.description_, boxed: false) {
            if connected {
                Picker(item.title, selection: selection) {
                    ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                        Text(option.label).tag(Optional(option.value))
                    }
                }
                .pickerStyle(.segmented)
            } else {
                Picker(item.title, selection: selection) {
                    ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                        Text(option.label).tag(Optional(option.value))
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

private struct CheckBoxField: View {
    @EnvironmentObject private var model: FormModel
    let item: CheckBox

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Toggle(item.title, isOn: Binding(get: { model.values.flag(mapping: item.mapping) }, set: { model.setFlag(item.mapping, $0) }))
                .font(.body)
            if let description = item.description_ { Text(description).font(.footnote).foregroundStyle(.secondary) }
        }
    }
}

/// The day or time a field holds, as the form writes it (dd/MM/yyyy, HH:mm).
private enum FieldDates {
    static func date(_ text: String?) -> Date? {
        guard let text else { return nil }
        let parts = text.prefix(10).split(separator: "/").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return Calendar.current.date(from: DateComponents(year: parts[2], month: parts[1], day: parts[0]))
    }

    static func text(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.day, .month, .year], from: date)
        return String(format: "%02d/%02d/%04d", parts.day ?? 1, parts.month ?? 1, parts.year ?? 1970)
    }

    static func time(_ text: String?) -> Date? {
        guard let text else { return nil }
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return Calendar.current.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: Date())
    }

    static func timeText(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }
}

private struct DateField: View {
    @EnvironmentObject private var model: FormModel
    let item: DateInput

    var body: some View {
        let value = model.values.text(mapping: item.mapping)
        let low = item.min.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000) } ?? .distantPast
        let high = item.max.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000) } ?? .distantFuture
        FieldBox(title: item.title, description: item.description_, boxed: false) {
            HStack {
                if let date = FieldDates.date(value) {
                    DatePicker(item.title, selection: Binding(get: { date }, set: { model.setText(item.mapping, FieldDates.text($0)) }), in: low...high, displayedComponents: .date)
                        .labelsHidden()
                    if !item.isRequired {
                        Button { model.setText(item.mapping, "") } label: { Image(systemName: "xmark.circle.fill") }
                            .buttonStyle(.plain).foregroundStyle(.secondary).accessibilityLabel("Clear \(item.title)")
                    }
                } else {
                    Button("Choose date") { model.setText(item.mapping, FieldDates.text(min(max(Date(), low), high))) }
                        .buttonStyle(.bordered)
                }
                Spacer()
            }
        }
    }
}

private struct TimeField: View {
    @EnvironmentObject private var model: FormModel
    let item: TimeInput

    var body: some View {
        let value = model.values.text(mapping: item.mapping)
        FieldBox(title: item.title, description: item.description_, boxed: false) {
            HStack {
                if let time = FieldDates.time(value) {
                    DatePicker(item.title, selection: Binding(get: { time }, set: { model.setText(item.mapping, FieldDates.timeText($0)) }), displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    if !item.isRequired {
                        Button { model.setText(item.mapping, "") } label: { Image(systemName: "xmark.circle.fill") }
                            .buttonStyle(.plain).foregroundStyle(.secondary).accessibilityLabel("Clear \(item.title)")
                    }
                } else {
                    Button("Choose time") { model.setText(item.mapping, "12:00") }.buttonStyle(.bordered)
                }
                Spacer()
            }
        }
    }
}

/// Tags typed one at a time (Return or + adds one, ignoring a repeat), with tags already used in the collection to
/// tap, and the tags chosen as chips that can be removed.
private struct TagsField: View {
    @EnvironmentObject private var model: FormModel
    let item: TagsInput
    @State private var typed = ""
    @State private var known: [String] = []

    var body: some View {
        let current = model.values.tags(mapping: item.mapping)
        let suggestions = TagSuggestions.shared.matching(known: known, current: current, typed: typed)
        FieldBox(title: item.title, description: item.description_) {
            HStack {
                SwiftUI.TextField(item.placeholder ?? "", text: Binding(get: { typed }, set: { typed = $0.replacingOccurrences(of: ",", with: "") }))
                    .textInputAutocapitalization(.never)
                    .submitLabel(.done)
                    .onSubmit { add(typed) }
                Button { add(typed) } label: { Image(systemName: "plus.circle.fill").font(.title3) }
                    .buttonStyle(.plain)
                    .disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityLabel("Add tag")
            }
        }
        .task { known = model.screen.knownTags(form: model.form, field: item) }
        if !suggestions.isEmpty {
            FlowLayout {
                ForEach(suggestions, id: \.self) { tag in
                    Button { add(tag) } label: { Chip(text: "+ #\(tag)") }.buttonStyle(.plain)
                }
            }
            .padding(.top, -8)
        }
        if !current.isEmpty {
            FlowLayout {
                ForEach(current, id: \.self) { tag in
                    Button { model.setTags(item.mapping, current.filter { $0 != tag }) } label: {
                        Chip(text: "#\(tag)", selected: true, systemImage: "xmark")
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove \(tag)")
                }
            }
            .padding(.top, -8)
        }
    }

    private func add(_ text: String) {
        let current = model.values.tags(mapping: item.mapping)
        let tag = text.trimmingCharacters(in: .whitespaces).drop(while: { $0 == "#" })
        if !tag.isEmpty, !current.contains(where: { $0.caseInsensitiveCompare(String(tag)) == .orderedSame }) {
            model.setTags(item.mapping, current + [String(tag)])
        }
        typed = ""
    }
}
