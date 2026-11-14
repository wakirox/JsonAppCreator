import AppkinShared
import SwiftUI

/// A JSON app being shown: its engine and how it is kept. The shell's navigation stack holds its screens.
@MainActor
final class AppSession: Hashable {
    let json: String
    let name: String
    let configurationId: String
    let isExample: Bool
    nonisolated(unsafe) let engine: IosEngine
    let navigator = Navigator()
    let root = ScreenRoute(viewId: nil, payload: nil, title: nil)

    init(json: String, name: String, configurationId: String, isExample: Bool, secrets: SecretStore) {
        self.json = json
        self.name = name
        self.configurationId = configurationId
        self.isExample = isExample
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        engine = IosEngine(
            json: json, versionName: version, platform: "iOS \(UIDevice.current.systemVersion)",
            secrets: secrets, configurationId: configurationId, bundledKeys: BundledKeys.encoded,
            photoData: { PhotoStore.data($0) }
        )
        ImageLoader.userAgent = engine.userAgent
    }

    deinit {
        engine.close()
    }

    nonisolated static func == (a: AppSession, b: AppSession) -> Bool { a === b }
    nonisolated func hash(into hasher: inout Hasher) { hasher.combine(ObjectIdentifier(self)) }
}

/// One screen of an app on the stack, with its title, alert and wait.
struct ScreenHost: View {
    @StateObject private var model: ScreenModel
    let isRoot: Bool

    init(session: AppSession, route: ScreenRoute, isRoot: Bool) {
        _model = StateObject(wrappedValue: ScreenModel(engine: session.engine, navigator: session.navigator, route: route))
        self.isRoot = isRoot
    }

    var body: some View {
        RenderScreen()
            .environmentObject(model)
            .navigationTitle(model.title)
            .navigationBarTitleDisplayMode(isRoot ? .large : .inline)
            .overlay {
                if model.loading {
                    ProgressView()
                        .padding(24)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
                }
            }
            .alert(
                model.title,
                isPresented: Binding(get: { model.alert != nil }, set: { _ in }),
                presenting: model.alert
            ) { alert in
                Button("OK") { model.answerAlert(ok: true) }
                if alert.hasCancel { Button("Cancel", role: .cancel) { model.answerAlert(ok: false) } }
            } message: { alert in
                Text(alert.text)
            }
    }
}
