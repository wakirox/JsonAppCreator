import SwiftUI

/// About Appkin: version, what it is, the terms, where to report a problem, and credits.
struct AboutView: View {
    @State private var noMail = false

    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0" }

    var body: some View {
        Form {
            Section {
                LabeledContent("Version", value: version)
                Text("No ads, no account and no data collected. The format reference and more examples are on the website.")
                Text("Questions, or an app or its content to report: \(Links.contact)")
                Link("wakirox.app", destination: Links.site)
                Link("JSON format guide", destination: Links.formatGuide)
            }
            Section {
                Button("Report a problem") { reportProblem() }
                NavigationLink("Terms of use") { TermsView() }
            }
            Section("Credits") {
                Text("Masterpieces shows public-domain works; the images come from Wikimedia Commons.")
                Image("TMDBLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96)
                    .accessibilityLabel("TMDB logo")
                Text("The Movies example uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.")
            }
        }
        .navigationTitle("About Appkin")
        .navigationBarTitleDisplayMode(.inline)
        .alert("No email app on this iPhone", isPresented: $noMail) {
            Button("OK") {}
        } message: {
            Text("Write to \(Links.contact)")
        }
    }

    /// An email to the contact address, with Appkin's version and the iOS version filled in.
    private func reportProblem() {
        let subject = String(localized: "Appkin \(version) on iOS \(UIDevice.current.systemVersion): a problem")
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = Links.contact
        components.queryItems = [URLQueryItem(name: "subject", value: subject)]
        guard let url = components.url else { return }
        UIApplication.shared.open(url) { opened in
            if !opened { noMail = true }
        }
    }
}

struct TermsView: View {
    var body: some View {
        ScrollView {
            Text(String(localized: "terms.body"))
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle("Terms of use")
        .navigationBarTitleDisplayMode(.inline)
    }
}
