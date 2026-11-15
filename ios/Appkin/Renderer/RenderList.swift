import AppkinShared
import SwiftUI

private let defaultCardWidth: CGFloat = 300
private let cardChrome: CGFloat = 28

/// A list: its items as cards (a horizontal list scrolls, a vertical one is a group of rows, a grid has columns), a
/// wait while it loads, why it couldn't, or its empty text.
struct RenderList: View {
    @EnvironmentObject private var screen: ScreenModel
    @StateObject private var list: ListModel
    let view: ListView
    let data: ItemData

    init(view: ListView, data: ItemData, screen: ScreenViewModel) {
        _list = StateObject(wrappedValue: ListModel(screen: screen, view: view, data: data))
        self.view = view
        self.data = data
    }

    var body: some View {
        let content = list.content
        let items = RenderKit.shared.items(content: content)
        VStack(alignment: .leading, spacing: 10) {
            if RenderKit.shared.hasControls(view: view), !content.waiting, content.errorMessage == nil {
                StoredListControls(view: view, list: list, content: content)
            }
            if content.waiting {
                ProgressView().frame(maxWidth: .infinity)
            } else if let message = content.errorMessage {
                LoadError(message: message, retry: list.retry)
            } else if items.isEmpty {
                Text(emptyText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            } else if RenderKit.shared.isHorizontalList(view: view) {
                horizontal(content)
            } else if view.columnCount > 1 {
                grid(content)
            } else {
                rows(content)
            }
            if let first = items.first, UserDefaults.standard.bool(forKey: "tapFirstItem"), screen.tapOnce(view) {
                // `-tapFirstItem YES`: opens the first item of the first list, so a screenshot can show its screen.
                Color.clear.frame(height: 0).task { RenderKit.shared.onAction(screen: screen.vm, action: view.action, data: first) }
            }
            if !RenderKit.shared.isHorizontalList(view: view), !items.isEmpty {
                MoreItems(content: content, list: list, automatic: false)
            }
        }
    }

    private var emptyText: String {
        let text = RenderKit.shared.fill(text: view.emptyText, data: data)
        return text.trimmingCharacters(in: .whitespaces).isEmpty ? "No data available" : text
    }

    private var cardWidth: CGFloat {
        let width = RenderKit.shared.explicitWidth(view: view.itemView)
        return width >= 0 ? CGFloat(width) + cardChrome : defaultCardWidth
    }

    private func horizontal(_ content: ListsState.Content) -> some View {
        let items = RenderKit.shared.items(content: content)
        return ScrollView(.horizontal, showsIndicators: false) {
            if items.count > 8 || content.canLoadMore {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        card(item).frame(width: cardWidth)
                    }
                    if content.canLoadMore || content.moreErrorMessage != nil {
                        MoreItems(content: content, list: list, automatic: true).frame(width: cardWidth)
                    }
                }
            } else {
                // Every card as tall as the tallest, so a title on two lines doesn't make one card stick out.
                HStack(alignment: .top, spacing: 12) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        card(item).frame(width: cardWidth).frame(maxHeight: .infinity, alignment: .top)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .contentMargins(.horizontal, 16, for: .scrollContent)
    }

    private func grid(_ content: ListsState.Content) -> some View {
        let columns = Int(view.columnCount)
        let items = RenderKit.shared.items(content: content)
        let rows = stride(from: 0, to: items.count, by: columns).map {
            Array(items[$0..<min($0 + columns, items.count)])
        }
        return VStack(spacing: 10) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(alignment: .top, spacing: 10) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, item in
                        card(item).frame(maxHeight: .infinity, alignment: .top)
                    }
                    ForEach(0..<(columns - row.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// One group, as an iOS list: the rows share a background and are told apart by dividers.
    private func rows(_ content: ListsState.Content) -> some View {
        let items = RenderKit.shared.items(content: content)
        return VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                swipeable(item) {
                    ListCardContent(itemView: view.itemView, item: item, preview: view.action == nil)
                        .actionable(view.action, item)
                }
                if index < items.count - 1 { Divider().padding(.leading, 14) }
            }
        }
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    /// A row that swipes when the list has swipe actions.
    @ViewBuilder
    private func swipeable<Row: View>(_ item: ItemData, @ViewBuilder row: () -> Row) -> some View {
        let start = RenderKit.shared.swipeToStart(view: view)
        let end = RenderKit.shared.swipeToEnd(view: view)
        if start == nil, end == nil {
            row()
        } else {
            SwipeRow(
                startIcon: start == nil ? nil : RenderKit.shared.swipeToStartIcon(view: view),
                endIcon: end == nil ? nil : RenderKit.shared.swipeToEndIcon(view: view),
                onStart: { RenderKit.shared.onAction(screen: screen.vm, action: start, data: item) },
                onEnd: { RenderKit.shared.onAction(screen: screen.vm, action: end, data: item) },
                content: row
            )
        }
    }

    private func card(_ item: ItemData) -> some View {
        ListCardContent(itemView: view.itemView, item: item, preview: view.action == nil)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .actionable(view.action, item)
    }
}

/// Above a stored list: its orders as one segmented control, and its tags as filter chips after "All", to show only
/// the items with that tag.
private struct StoredListControls: View {
    let view: ListView
    @ObservedObject var list: ListModel
    let content: ListsState.Content

    var body: some View {
        let titles = RenderKit.shared.sortTitles(view: view)
        let choice = list.choice
        let tags = (content.tags + [choice.tag].compactMap { $0 }).reduce(into: [String]()) { result, tag in
            if !result.contains(where: { $0.caseInsensitiveCompare(tag) == .orderedSame }) { result.append(tag) }
        }
        VStack(alignment: .leading, spacing: 10) {
            if titles.count > 1 {
                Picker("Sort", selection: Binding(
                    get: { Int(choice.sortOption) },
                    set: { list.choose(sortOption: $0, tag: choice.tag) }
                )) {
                    ForEach(Array(titles.enumerated()), id: \.offset) { index, title in Text(title).tag(index) }
                }
                .pickerStyle(.segmented)
            }
            if RenderKit.shared.filtersByTag(view: view), !tags.isEmpty {
                FlowLayout {
                    Button { list.choose(sortOption: Int(choice.sortOption), tag: nil) } label: {
                        Chip(text: "All", selected: choice.tag == nil)
                    }
                    ForEach(tags, id: \.self) { tag in
                        Button { list.choose(sortOption: Int(choice.sortOption), tag: tag) } label: {
                            Chip(text: "#\(tag)", selected: choice.tag?.caseInsensitiveCompare(tag) == .orderedSame)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// The end of a list with more pages: it fetches the next one when it comes into view, or a button does.
private struct MoreItems: View {
    let content: ListsState.Content
    let list: ListModel
    let automatic: Bool

    var body: some View {
        if content.canLoadMore || content.moreErrorMessage != nil {
            VStack {
                if let message = content.moreErrorMessage {
                    LoadError(message: message, retry: list.loadMore)
                } else if content.loadingMore || automatic {
                    ProgressView().onAppear { if automatic { list.loadMore() } }
                } else {
                    Button("Show more") { list.loadMore() }.buttonStyle(.bordered)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
    }
}

private struct LoadError: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Text(message).font(.subheadline).multilineTextAlignment(.center)
            Button("Try again", action: retry).buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

/// What a list item shows, as ComposeRenderer's ListCardContent decides: a picture alone; a picture over its texts; a
/// picture beside its texts; or the item view as it is.
struct ListCardContent: View {
    @EnvironmentObject private var model: ScreenModel
    let itemView: MView
    let item: ItemData
    let preview: Bool

    var body: some View {
        let resolved = model.vm.resolve(view: itemView)
        let layout = resolved as? AppkinShared.Layout
        let children = layout.map { RenderKit.shared.shown(screen: model.vm, views: $0.views, data: item) }
        if let image = resolved as? ImageView {
            RenderImage(view: image, data: item, edgeToEdge: true)
        } else if let layout, let children, let first = children.first as? ImageView,
                  !RenderKit.shared.isHorizontal(view: layout) {
            VStack(alignment: .leading, spacing: 0) {
                RenderImage(view: first, data: item, edgeToEdge: true)
                ForEach(Array(children.dropFirst().enumerated()), id: \.offset) { _, child in
                    if let text = child as? TextualView, !RenderKit.shared.isIconOnly(view: child) {
                        RenderText(view: text, data: item, padding: EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                    } else {
                        RenderNode(view: child, parentData: item).padding(.horizontal, 12).padding(.vertical, 8)
                    }
                }
            }
        } else if let layout, let children, let first = children.first as? ImageView,
                  RenderKit.shared.isHorizontal(view: layout) {
            sideBySide(image: first, others: Array(children.dropFirst()))
        } else {
            RenderNode(view: resolved, parentData: item)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
        }
    }

    /// A picture at the start, the texts in a column beside it, icon buttons (Remove, say) at the end.
    private func sideBySide(image: ImageView, others: [MView]) -> some View {
        let width = RenderKit.shared.imageWidth(view: image)
        let height = RenderKit.shared.imageHeight(view: image)
        let icons = others.filter { RenderKit.shared.isIconOnly(view: $0) }
        let rest = others.filter { !RenderKit.shared.isIconOnly(view: $0) }
        return HStack(spacing: 0) {
            RenderImage(view: image, data: item, edgeToEdge: true)
                .frame(width: CGFloat(width >= 0 ? width : 112), height: CGFloat(height >= 0 ? height : 112))
                .clipped()
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(rest.enumerated()), id: \.offset) { _, child in
                    if let text = child as? TextualView {
                        RenderText(view: text, data: item, padding: EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 8))
                    } else {
                        RenderNode(view: child, parentData: item).padding(.horizontal, 16).padding(.vertical, 8)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(Array(icons.enumerated()), id: \.offset) { _, child in
                RenderNode(view: child, parentData: item).padding(.trailing, 8)
            }
        }
    }
}
