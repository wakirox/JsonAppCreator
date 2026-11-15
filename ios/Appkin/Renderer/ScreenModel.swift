import AppkinShared
import SwiftUI


/// One screen of the stack: the view named `viewId` (none: the app's main view) shown with a payload and a title.
struct ScreenRoute: Hashable {
    let id = UUID()
    var viewId: String?
    var payload: String?
    var title: String?
}

/// Pushes and pops the screens of an app on the shell's navigation stack.
@MainActor
final class Navigator {
    var push: (ScreenRoute) -> Void = { _ in }
    /// Goes back from the screen with this id, if it is the one on top.
    var popIfTop: (UUID) -> Void = { _ in }
}

/// A screen's state for SwiftUI: it watches the shared `ScreenViewModel`'s flows and carries out its effects.
@MainActor
final class ScreenModel: ObservableObject {
    nonisolated(unsafe) let vm: ScreenViewModel
    nonisolated(unsafe) private let engine: IosEngine
    private let alerts: AlertsViewModel
    private let navigator: Navigator
    private let route: ScreenRoute

    @Published private(set) var data: ItemData
    @Published private(set) var loading = false
    @Published private(set) var alert: AlertInfo?
    @Published private(set) var canRefresh = false

    nonisolated(unsafe) private var watchers: [Watching] = []

    init(engine: IosEngine, navigator: Navigator, route: ScreenRoute) {
        self.engine = engine
        self.navigator = navigator
        self.route = route
        let alerts = AlertsViewModel()
        self.alerts = alerts
        vm = engine.screen(viewId: route.viewId, payload: route.payload, title: route.title, alerts: alerts)
        data = RenderKit.shared.currentData(screen: vm)
        watchers = [
            Watch.shared.flow(flow: RenderKit.shared.data(screen: vm)) { [weak self] value in if let value = value as? ItemData { self?.data = value } },
            Watch.shared.flow(flow: vm.loading) { [weak self] value in self?.loading = (value as? NSNumber)?.boolValue ?? false },
            Watch.shared.flow(flow: vm.lists.canRefresh) { [weak self] value in self?.canRefresh = (value as? NSNumber)?.boolValue ?? false },
            Watch.shared.flow(flow: RenderKit.shared.alert(alerts: alerts)) { [weak self] value in self?.alert = value as? AlertInfo },
            Watch.shared.flow(flow: RenderKit.shared.effects(screen: vm)) { [weak self] value in
                if let effect = value as? ScreenEffect { self?.handle(effect) }
            }
        ]
    }

    deinit {
        watchers.forEach { $0.cancel() }
        engine.dispose(screen: vm)
    }

    var title: String { vm.title ?? vm.app?.name ?? "" }

    func answerAlert(ok: Bool) {
        alert = nil
        alerts.answer(ok: ok)
    }

    private var tapped = false
    /// True once, for the debug launch argument `-tapFirstItem`.
    func tapOnce(_ view: ListView) -> Bool {
        defer { tapped = true }
        return !tapped
    }

    func refresh() { vm.lists.refresh() }

    private func handle(_ effect: ScreenEffect) {
        switch effect.kind {
        case "openScreen":
            navigator.push(ScreenRoute(viewId: effect.view?.id, payload: effect.payload, title: effect.title))
        case "close":
            // Only the screen on top goes back.
            navigator.popIfTop(route.id)
        case "openUrl":
            if let url = effect.url.flatMap(Self.systemURL) { UIApplication.shared.open(url) }
        case "copy":
            UIPasteboard.general.string = effect.text
        case "share":
            Exporter.share(text: effect.text ?? "", photos: effect.photos)
        case "calendar":
            if let entry = effect.calendar { Exporter.addToCalendar(entry) }
        case "pdf":
            if let page = effect.page { Task { await Exporter.sharePDF(page) } }
        case "textPack":
            if let pack = effect.pack { Exporter.shareTextPack(pack) }
        default:
            break
        }
    }

    /// The address the system opens: a `geo:` link (Android's) becomes a search or coordinate pin in Apple Maps.
    static func systemURL(_ address: String) -> URL? {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.lowercased().hasPrefix("geo:") else { return imageURL(trimmed) }
        let body = String(trimmed.dropFirst(4))
        let parts = body.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        let coordinatesPart = parts.first.map(String.init) ?? ""
        let queryPart = parts.count > 1 ? String(parts[1]) : nil

        if let queryPart,
           let place = queryPart.split(separator: "&").first(where: { $0.hasPrefix("q=") })?.dropFirst(2) {
            let plusDecoded = String(place).replacingOccurrences(of: "+", with: " ")
            let decoded = plusDecoded.removingPercentEncoding ?? plusDecoded
            if !decoded.isEmpty {
                var components = URLComponents(string: "https://maps.apple.com/")
                components?.queryItems = [URLQueryItem(name: "q", value: decoded)]
                return components?.url
            }
        }

        let rawCoordinates = coordinatesPart.split(separator: ";", maxSplits: 1).first.map(String.init) ?? ""
        let numbers = rawCoordinates.split(separator: ",")
        guard numbers.count >= 2,
              let lat = Double(numbers[0].trimmingCharacters(in: .whitespaces)),
              let lon = Double(numbers[1].trimmingCharacters(in: .whitespaces)),
              lat.isFinite, lon.isFinite, (-90...90).contains(lat), (-180...180).contains(lon) else { return nil }
        var components = URLComponents(string: "https://maps.apple.com/")
        components?.queryItems = [URLQueryItem(name: "ll", value: "\(lat),\(lon)")]
        return components?.url
    }
}

/// The entries of one list: its content, as the shared `ListsState` loads it.
@MainActor
final class ListModel: ObservableObject {
    let entry: ListsState.Entry
    private let lists: ListsState
    @Published private(set) var content: ListsState.Content
    @Published private(set) var choice: ListChoice
    nonisolated(unsafe) private var watchers: [Watching] = []
    nonisolated(unsafe) private let screen: ScreenViewModel

    init(screen: ScreenViewModel, view: ListView, data: ItemData) {
        lists = screen.lists
        self.screen = screen
        entry = RenderKit.shared.list(screen: screen, view: view, data: data)
        content = RenderKit.shared.currentContent(entry: entry)
        choice = RenderKit.shared.currentChoice(entry: entry)
        watchers = [
            Watch.shared.flow(flow: RenderKit.shared.content(entry: entry)) { [weak self] value in
                if let content = value as? ListsState.Content { self?.content = content }
            },
            Watch.shared.flow(flow: RenderKit.shared.choice(entry: entry)) { [weak self] value in
                if let choice = value as? ListChoice { self?.choice = choice }
            }
        ]
    }

    deinit { watchers.forEach { $0.cancel() } }

    /// The user picked an order (by position) or a tag (none: all) above the list.
    func choose(sortOption: Int, tag: String?) {
        RenderKit.shared.choose(screen: screen, entry: entry, sortOption: Int32(sortOption), tag: tag)
    }

    func retry() { lists.retry(list: entry) }
    func loadMore() { lists.loadMore(list: entry) }
}
