import AppkinShared
import PhotosUI
import QuickLook
import SwiftUI

/// The thumbnails of the photos a field holds (or an object has), tapped to open one full screen.
struct PhotoStrip: View {
    let references: [String]
    var side: CGFloat = 96
    /// The photo a remove button takes out of the field; none: no button.
    var onRemove: ((String) -> Void)?
    @State private var viewing: PhotoSelection?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(references.enumerated()), id: \.offset) { index, reference in
                    RemoteImage(url: imageURL(reference))
                        .frame(width: side, height: side)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .contentShape(RoundedRectangle(cornerRadius: 12))
                        .onTapGesture { viewing = PhotoSelection(references: references, index: index) }
                        .overlay(alignment: .topTrailing) {
                            if let onRemove {
                                Button { onRemove(reference) } label: { Image(systemName: "xmark").font(.caption.weight(.bold)) }
                                    .buttonStyle(.bordered)
                                    .buttonBorderShape(.circle)
                                    .controlSize(.mini)
                                    .padding(4)
                                    .accessibilityLabel("Remove photo")
                            }
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("Photo \(index + 1)")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityIdentifier("photo-\(index + 1)")
                }
            }
        }
        .photoViewer($viewing)
    }
}

/// A photos field: the photos it holds, and the system's photo picker (which needs no permission) to add more.
struct PhotosFormField: View {
    @EnvironmentObject private var model: FormModel
    let item: PhotosInput
    @State private var picked: [PhotosPickerItem] = []
    @State private var failed = false

    var body: some View {
        let current = model.values.photos(mapping: item.mapping)
        let limit = Int(RenderKit.shared.photoLimit(field: item))
        let room = limit - current.count
        FieldBox(title: item.title, description: item.description_, boxed: false) {
            VStack(alignment: .leading, spacing: 8) {
                if !current.isEmpty {
                    PhotoStrip(references: current, side: 88) { removed in
                        model.setPhotos(item.mapping, current.filter { $0 != removed })
                    }
                }
                PhotosPicker(selection: $picked, maxSelectionCount: max(room, 1), matching: .images) {
                    Label(current.isEmpty ? "Add photos" : "Add more", systemImage: "photo.badge.plus")
                }
                .buttonStyle(.bordered)
                .disabled(room <= 0)
                if failed { Text("A photo couldn't be added.").font(.footnote).foregroundStyle(.red) }
            }
        }
        .onChange(of: picked) { _, items in
            guard !items.isEmpty else { return }
            picked = []
            Task { await keep(items, limit: limit) }
        }
    }

    private func keep(_ items: [PhotosPickerItem], limit: Int) async {
        var added: [String] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self),
               let reference = await Task.detached(operation: { PhotoStore.add(data) }).value {
                added.append(reference)
            }
        }
        failed = added.count < items.count
        let current = model.values.photos(mapping: item.mapping)
        if !added.isEmpty { model.setPhotos(item.mapping, Array((current + added).prefix(limit))) }
    }
}

/// A files field: the files it holds as chips, and the system's document picker to attach more.
struct FilesFormField: View {
    @EnvironmentObject private var model: FormModel
    let item: FilesInput
    @State private var picking = false

    var body: some View {
        let current = model.values.files(mapping: item.mapping)
        let limit = Int(RenderKit.shared.fileLimit(field: item))
        FieldBox(title: item.title, description: item.description_, boxed: false) {
            VStack(alignment: .leading, spacing: 8) {
                if !current.isEmpty {
                    FileChips(files: current) { removed in
                        model.setFiles(item.mapping, current.filter { $0.uri != removed.uri })
                    }
                }
                Button { picking = true } label: {
                    Label(current.isEmpty ? "Attach files" : "Attach more", systemImage: "paperclip")
                }
                .buttonStyle(.bordered)
                .disabled(current.count >= limit)
            }
        }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            guard case .success(let urls) = result else { return }
            let latest = model.values.files(mapping: item.mapping)
            let known = Set(latest.map(\.uri))
            let added = urls.compactMap(FileAttachments.add).filter { !known.contains($0.uri) }
            if !added.isEmpty { model.setFiles(item.mapping, Array((latest + added).prefix(limit))) }
        }
    }
}

/// An object's attached files, a chip each.
struct RenderFiles: View {
    @EnvironmentObject private var model: ScreenModel
    let view: FilesView
    let data: ItemData

    var body: some View {
        let files = RenderKit.shared.files(view: view, data: data)
        if !files.isEmpty { FileChips(files: files) }
    }
}

/// "📎 name · size" chips: a tap previews the file with Quick Look, or says it is gone.
struct FileChips: View {
    let files: [Files.Attached]
    var onRemove: ((Files.Attached) -> Void)?
    @State private var opened: OpenedFile?
    @State private var missing: Files.Attached?

    var body: some View {
        FlowLayout {
            ForEach(Array(files.enumerated()), id: \.offset) { _, file in
                HStack(spacing: 2) {
                    Button { open(file) } label: { Chip(text: label(file)) }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("file-\(file.name)")
                    if let onRemove {
                        Button { onRemove(file) } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Remove \(file.name)")
                    }
                }
            }
        }
        .quickLookPreview(Binding(get: { opened?.url }, set: { if $0 == nil { opened = nil } }))
        .alert(
            "\(missing?.name ?? "") is no longer on this phone",
            isPresented: Binding(get: { missing != nil }, set: { if !$0 { missing = nil } })
        ) {
            Button("OK") { missing = nil }
        }
    }

    private func label(_ file: Files.Attached) -> String {
        "📎 " + file.name + (file.size.map { " · " + Files.shared.sizeText(bytes: $0.int64Value) } ?? "")
    }

    private func open(_ file: Files.Attached) {
        if let found = OpenedFile(file) { opened = found } else { missing = file }
    }
}
