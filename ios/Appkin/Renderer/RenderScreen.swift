import AppkinShared
import SwiftUI

private let pageMargin: CGFloat = 16
private let viewSpacing: CGFloat = 12

/// The screen's views, as ComposeRenderer's RenderRoot lays them out: a vertical layout is the page, its views one
/// below the other; horizontal lists run edge to edge.
struct RenderScreen: View {
    @EnvironmentObject private var model: ScreenModel

    var body: some View {
        if let definition = model.vm.screen?.definition {
            let resolved = model.vm.resolve(view: definition)
            let data = model.data
            ScrollView {
                if RenderKit.shared.isVerticalLayout(view: resolved), let layout = resolved as? AppkinShared.Layout {
                    let children = RenderKit.shared.shown(screen: model.vm, views: layout.views, data: data)
                    LazyVStack(alignment: .leading, spacing: viewSpacing) {
                        ForEach(Array(children.enumerated()), id: \.offset) { _, child in
                            RenderNode(view: child, parentData: data)
                                .padding(.horizontal, isEdgeToEdge(child) ? 0 : pageMargin)
                        }
                    }
                    .padding(.vertical, pageMargin)
                } else {
                    RenderNode(view: resolved, parentData: data)
                        .padding(pageMargin)
                }
            }
            .modifier(Refreshing(enabled: model.canRefresh, refresh: model.refresh))
            .onAppear { model.vm.lists.retryFailed() }
        } else {
            ContentUnavailableView("Nothing to show", systemImage: "exclamationmark.triangle")
        }
    }

    private func isEdgeToEdge(_ view: MView) -> Bool {
        (view as? ListView).map { RenderKit.shared.isHorizontalList(view: $0) } ?? false
    }
}

/// Pull to refresh, when the screen has a list that loads from a request.
private struct Refreshing: ViewModifier {
    let enabled: Bool
    let refresh: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content.refreshable { refresh() }
        } else {
            content
        }
    }
}

/// How a view sits among its siblings: its own width when it asks for one, else all the room.
struct ChildFrame: ViewModifier {
    let view: MView

    func body(content: Content) -> some View {
        if RenderKit.shared.isIconOnly(view: view) {
            content
        } else {
            let width = RenderKit.shared.explicitWidth(view: view)
            if width >= 0 {
                content.frame(width: CGFloat(width), alignment: .leading)
            } else {
                content.frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

/// One view of the app: resolved, shown only if its data allows, and drawn as its type says.
struct RenderNode: View {
    @EnvironmentObject private var model: ScreenModel
    let view: MView
    let parentData: ItemData

    var body: some View {
        let resolved = model.vm.resolve(view: view)
        let data = RenderKit.shared.dataFor(view: resolved, parent: parentData)
        if RenderKit.shared.isShown(screen: model.vm, model: view, parentData: parentData) {
            switch RenderKit.shared.kind(view: resolved) {
            case "layout":
                if let layout = resolved as? AppkinShared.Layout { RenderLayout(layout: layout, data: data) }
            case "text":
                if let text = resolved as? TextView { RenderText(view: text, data: data) }
            case "button":
                if let button = resolved as? ButtonView { RenderButton(view: button, data: data) }
            case "image":
                if let image = resolved as? ImageView { RenderImage(view: image, data: data) }
            case "list":
                if let list = resolved as? ListView { RenderList(view: list, data: data, screen: model.vm) }
            case "form":
                if let form = resolved as? FormView { RenderForm(view: form, data: data) }
            case "check":
                if let check = resolved as? CheckView { RenderCheck(view: check, data: data) }
            case "files":
                if let files = resolved as? FilesView { RenderFiles(view: files, data: data) }
            default:
                NotYetOnIOS(kind: RenderKit.shared.kind(view: resolved))
            }
        }
    }
}

private struct NotYetOnIOS: View {
    let kind: String

    var body: some View {
        Label("Not yet on iOS: \(kind)", systemImage: "hourglass")
            .font(.footnote)
            .foregroundStyle(.secondary)
    }
}

struct RenderLayout: View {
    @EnvironmentObject private var model: ScreenModel
    let layout: AppkinShared.Layout
    let data: ItemData
    var spacing = viewSpacing

    var body: some View {
        let children = RenderKit.shared.shown(screen: model.vm, views: layout.views, data: data)
        if RenderKit.shared.isHorizontal(view: layout) {
            // A row with an icon button (such as Delete beside a title) centres its views on it.
            let centred = children.contains { RenderKit.shared.isIconOnly(view: $0) }
            HStack(alignment: centred ? .center : .top, spacing: 10) {
                ForEach(Array(children.enumerated()), id: \.offset) { _, child in
                    RenderNode(view: child, parentData: data).modifier(ChildFrame(view: child))
                }
            }
            .environment(\.buttonsInRow, true)
        } else {
            VStack(alignment: .leading, spacing: spacing) {
                ForEach(Array(children.enumerated()), id: \.offset) { _, child in
                    RenderNode(view: child, parentData: data).modifier(ChildFrame(view: child))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Runs a view's action when it is tapped (or held, for a long-click action); `fallback` when it has none.
struct Actionable: ViewModifier {
    @EnvironmentObject private var model: ScreenModel
    let action: Action?
    let data: ItemData
    var fallback: (() -> Void)?

    func body(content: Content) -> some View {
        let resolved = model.vm.resolve(action: action)
        if resolved != nil || fallback != nil {
            if resolved?.type.name == "longclick" {
                content.contentShape(Rectangle()).onLongPressGesture { RenderKit.shared.onAction(screen: model.vm, action: action, data: data) }
            } else {
                content.contentShape(Rectangle()).onTapGesture {
                    if resolved != nil { RenderKit.shared.onAction(screen: model.vm, action: action, data: data) } else { fallback?() }
                }
                .accessibilityAddTraits(.isButton)
            }
        } else {
            content
        }
    }
}

extension View {
    func actionable(_ action: Action?, _ data: ItemData, fallback: (() -> Void)? = nil) -> some View {
        modifier(Actionable(action: action, data: data, fallback: fallback))
    }
}
