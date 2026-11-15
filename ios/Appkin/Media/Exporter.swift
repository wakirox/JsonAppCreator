import AppkinShared
import EventKit
import EventKitUI
import UIKit
import WebKit

/// What the share, PDF, Export and calendar events do on the iPhone: each ends in the system's own sheet.
@MainActor
enum Exporter {
    /// The share sheet on text and photos (the kept photos as JPEG files, which is what apps take).
    static func share(text: String, photos: [String]) {
        var items: [Any] = []
        if !text.isEmpty { items.append(text) }
        let folder = freshFolder("share")
        for (index, reference) in photos.enumerated() {
            guard let data = PhotoStore.data(reference) else { continue }
            let file = folder.appending(path: "photo-\(index + 1).jpg")
            if (try? data.write(to: file)) != nil { items.append(file) }
        }
        present(items)
    }

    /// An app as its JSON file, `<Name>.appkin.json`, in the share sheet: that is all another iPhone needs to show it.
    static func shareApp(name: String, json: String, fileName: String) {
        let file = freshFolder("app").appending(path: fileName)
        guard (try? Data(json.utf8).write(to: file)) != nil else { return }
        present([file])
    }

    /// The PDF of `page`, in the share sheet.
    static func sharePDF(_ page: PdfPageUseCase.Page) async {
        var html = page.html
        for reference in page.photos {
            guard let jpeg = PhotoStore.data(reference) else { continue }
            html = html.replacingOccurrences(of: "\"\(reference)\"", with: "\"data:image/jpeg;base64,\(jpeg.base64EncodedString())\"")
        }
        let renderer = PageRenderer()
        guard let pdf = try? await renderer.pdf(html: html, pageless: page.pageless) else { return }
        let file = freshFolder("pdf").appending(path: "\(fileName(page.title)).pdf")
        guard (try? pdf.write(to: file)) != nil else { return }
        keepCopy(file)
        present([file])
    }

    /// The TextPack of `pack` (a zip holding "<title>.textbundle/" with info.json, text.markdown and assets/), in the
    /// share sheet. A photo or file no longer there is left out of assets/.
    static func shareTextPack(_ pack: TextPackUseCase.Pack) {
        let name = fileName(pack.title)
        let bundle = "\(name).textbundle/"
        var zip = ZipWriter()
        zip.add(bundle + "info.json", Data(TextPackUseCase.companion.INFO.utf8))
        zip.add(bundle + "text.markdown", Data(pack.markdown.utf8))
        for asset in pack.assets {
            let bytes = asset.isPhoto ? PhotoStore.data(asset.reference) : FileAttachments.read(asset.reference)
            if let bytes { zip.add(bundle + asset.path, bytes) }
        }
        let file = freshFolder("textpack").appending(path: "\(name).zip")
        guard (try? zip.finished().write(to: file)) != nil else { return }
        keepCopy(file)
        present([file])
    }

    /// A title as a file name: without the characters file systems refuse ("Rainy: ideas" is "Rainy ideas").
    static func fileName(_ title: String) -> String {
        let cleaned = title.replacingOccurrences(of: "[\\\\/:*?\"<>|]", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
        return cleaned.isEmpty ? "Appkin" : cleaned
    }

    private static func freshFolder(_ name: String) -> URL {
        let folder = URL.temporaryDirectory.appending(path: "shared", directoryHint: .isDirectory).appending(path: name, directoryHint: .isDirectory)
        try? FileManager.default.removeItem(at: folder)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    /// For checking what was written: `-exportCopyDir /tmp/x` (debug builds) leaves a copy of each file there.
    private static func keepCopy(_ file: URL) {
        #if DEBUG
        guard let dir = UserDefaults.standard.string(forKey: "exportCopyDir") else { return }
        let target = URL(fileURLWithPath: dir, isDirectory: true)
        try? FileManager.default.createDirectory(at: target, withIntermediateDirectories: true)
        let copy = target.appending(path: file.lastPathComponent)
        try? FileManager.default.removeItem(at: copy)
        try? FileManager.default.copyItem(at: file, to: copy)
        #endif
    }

    // MARK: Presenting

    static func present(_ items: [Any]) {
        guard !items.isEmpty, let top = Presenter.top else { return }
        let sheet = UIActivityViewController(activityItems: items, applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = top.view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
        sheet.popoverPresentationController?.permittedArrowDirections = []
        top.present(sheet, animated: true)
    }

    // MARK: Calendar

    /// The calendar's own screen for a new event, filled in; the user reviews and saves it. Nothing is written without them.
    static func addToCalendar(_ entry: CalendarEntry) {
        guard let top = Presenter.top else { return }
        let store = EKEventStore()
        let event = EKEvent(eventStore: store)
        event.title = entry.title
        event.location = entry.location
        event.notes = entry.description_
        event.isAllDay = entry.allDay
        if entry.allDay {
            // An all-day event is the UTC midnights of its days; the calendar wants the same days at local midnight.
            var utc = Calendar(identifier: .gregorian)
            utc.timeZone = TimeZone(identifier: "UTC")!
            let first = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(entry.beginMillis) / 1000))
            let last = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(entry.endMillis - 1) / 1000))
            event.startDate = Calendar.current.date(from: first)
            event.endDate = Calendar.current.date(from: last)
        } else {
            event.startDate = Date(timeIntervalSince1970: Double(entry.beginMillis) / 1000)
            event.endDate = Date(timeIntervalSince1970: Double(entry.endMillis) / 1000)
        }
        event.calendar = store.defaultCalendarForNewEvents
        let editor = EKEventEditViewController()
        editor.eventStore = store
        editor.event = event
        editor.editViewDelegate = CalendarDone.shared
        top.present(editor, animated: true)
    }
}

private final class CalendarDone: NSObject, EKEventEditViewDelegate, @unchecked Sendable {
    static let shared = CalendarDone()

    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        MainActor.assumeIsolated { controller.dismiss(animated: true) }
    }
}

/// The view controller on top, where a sheet can be presented.
@MainActor
enum Presenter {
    static var top: UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var controller = scene?.windows.first(where: \.isKeyWindow)?.rootViewController ?? scene?.windows.first?.rootViewController
        while let next = controller?.presentedViewController, !next.isBeingDismissed { controller = next }
        return controller
    }
}

/// Lays a page out in a web view no one sees and writes it as a PDF. A page is A4 wide with the margins of
/// PdfPageUseCase; a pageless one is a single page as tall as its content, an A4 page otherwise.
@MainActor
private final class PageRenderer: NSObject, WKNavigationDelegate {
    /// A4 in CSS pixels (96 per inch) and in points (72 per inch).
    private static let pageWidth: CGFloat = 794
    private static let paper = CGSize(width: 595.2, height: 841.8)
    private static let sideMargin: CGFloat = 14 / 25.4 * 72
    private static let verticalMargin: CGFloat = 16 / 25.4 * 72

    private let web: WKWebView
    private var loaded: CheckedContinuation<Void, Error>?

    override init() {
        let configuration = WKWebViewConfiguration()
        // The page has no scripts, and saved text in it is escaped: the app's own measuring script still runs.
        configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        web = WKWebView(frame: CGRect(x: 0, y: 0, width: Self.pageWidth, height: 1123), configuration: configuration)
        super.init()
        web.navigationDelegate = self
    }

    func pdf(html: String, pageless: Bool) async throws -> Data {
        #if DEBUG
        let pageless = pageless && !UserDefaults.standard.bool(forKey: "pdfPaged")
        #endif
        // A window shows nothing it holds off screen, but lays it out as the screen does.
        if let window = Presenter.top?.view.window {
            web.alpha = 0.01
            window.insertSubview(web, at: 0)
        }
        defer { web.removeFromSuperview() }
        try await load(pageless ? Self.screen(html) : Self.printed(html))
        if pageless { return try await onePage() }
        return paged()
    }

    private func load(_ html: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            loaded = continuation
            web.loadHTMLString(html, baseURL: nil)
        }
    }

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        MainActor.assumeIsolated {
            loaded?.resume()
            loaded = nil
        }
    }

    nonisolated func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        MainActor.assumeIsolated {
            loaded?.resume(throwing: error)
            loaded = nil
        }
    }

    nonisolated func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        MainActor.assumeIsolated {
            loaded?.resume(throwing: error)
            loaded = nil
        }
    }

    /// The page as on a screen: the margins an A4 page has are the body's padding.
    private static func screen(_ html: String) -> String {
        let margins = "<style>html,body{margin:0;background:#fff}body{padding:\(PdfPageUseCase.companion.MARGIN_MM)mm 14mm}</style>"
        return html.replacingOccurrences(of: "</head>", with: margins + "</head>")
    }

    /// The page for the print formatter, which sets the width itself: the content takes it, margins come from the paper.
    private static func printed(_ html: String) -> String {
        html.replacingOccurrences(of: "</head>", with: "<style>html,body{margin:0}main{width:auto !important}</style></head>")
    }

    /// One page as tall as the content (and as wide as A4), once its pictures are decoded.
    private func onePage() async throws -> Data {
        let height = try await web.callAsyncJavaScript(
            "await Promise.all([...document.images].map(i => i.decode().catch(() => {}))); return document.body.scrollHeight;",
            contentWorld: .defaultClient
        )
        let content = CGFloat((height as? NSNumber)?.doubleValue ?? 1123)
        web.frame = CGRect(x: 0, y: 0, width: Self.pageWidth, height: content)
        try await Task.sleep(for: .milliseconds(150))
        let configuration = WKPDFConfiguration()
        configuration.rect = CGRect(x: 0, y: 0, width: Self.pageWidth, height: content)
        return try await web.pdf(configuration: configuration)
    }

    /// A4 pages with the margins, the web view's print formatter breaking the content between them.
    private func paged() -> Data {
        let page = UIPrintPageRenderer()
        page.addPrintFormatter(web.viewPrintFormatter(), startingAtPageAt: 0)
        let paper = CGRect(origin: .zero, size: Self.paper)
        let printable = paper.insetBy(dx: Self.sideMargin, dy: Self.verticalMargin)
        page.setValue(NSValue(cgRect: paper), forKey: "paperRect")
        page.setValue(NSValue(cgRect: printable), forKey: "printableRect")
        let data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, paper, nil)
        page.prepare(forDrawingPages: NSRange(location: 0, length: page.numberOfPages))
        for index in 0..<page.numberOfPages {
            UIGraphicsBeginPDFPage()
            page.drawPage(at: index, in: UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()
        return data as Data
    }
}
