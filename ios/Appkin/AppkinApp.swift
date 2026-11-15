import SwiftUI

@main
struct AppkinApp: App {
    var body: some Scene {
        WindowGroup {
            ShellRoot()
                // Buttons, icons and links in the text's own colour, black in light and white in dark, not system blue.
                .tint(.primary)
        }
    }
}
