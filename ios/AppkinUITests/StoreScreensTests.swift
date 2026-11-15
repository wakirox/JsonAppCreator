import XCTest

/// The App Store screenshots' screens, the same as the Play ones (StoreScreenshots on Android): it stops on each and
/// waits while tools/store/ios-screenshots.sh shoots it from outside, so the Duo's screens and the real status bar are
/// captured. Skipped unless that script has made /tmp/appkin-store/run. Needs a fresh install and the two mockups
/// (tools/store/raw-assets) as the newest photos.
@MainActor
final class StoreScreensTests: XCTestCase {
    let app = XCUIApplication()
    let dir = "/tmp/appkin-store"

    override func setUp() {
        continueAfterFailure = false
    }

    private func text(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    /// Asks the script to shoot the screen as `name`, and waits until it has.
    private func capture(_ name: String, settle: UInt32 = 2) {
        sleep(settle)
        FileManager.default.createFile(atPath: "\(dir)/req-\(name)", contents: nil)
        let done = "\(dir)/done-\(name)"
        for _ in 0..<120 where !FileManager.default.fileExists(atPath: done) { usleep(500_000) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: done), "\(name) wasn't shot")
    }

    private func repo(_ path: String) throws -> String {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        return try String(contentsOf: root.appending(path: path), encoding: .utf8)
    }

    private func type(_ index: Int, _ value: String) {
        let field = app.textFields.element(boundBy: index)
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(value)
    }

    /// Closes the keyboard: its Done (or the one above it), else Return; a tap elsewhere leaves it up.
    private func dismissKeyboard() {
        guard app.keyboards.firstMatch.exists else { return }
        if app.keyboards.buttons["Done"].exists {
            app.keyboards.buttons["Done"].tap()
        } else if app.buttons["Done"].exists {
            app.buttons["Done"].firstMatch.tap()
        } else {
            app.typeText("\n")
        }
        _ = app.keyboards.firstMatch.waitForNonExistence(timeout: 3)
    }

    /// Opens a file as "Open in Appkin" does, so it's kept under Your apps.
    private func keep(_ json: String) throws {
        let file = NSTemporaryDirectory() + "store-\(UUID().uuidString).json"
        try json.write(toFile: file, atomically: true, encoding: .utf8)
        app.launchArguments = ["-openFile", file]
        app.launch()
        XCTAssertTrue(app.buttons["Open"].waitForExistence(timeout: 10))
        app.buttons["Open"].tap()
        sleep(2)
        app.terminate()
    }

    private func addIdea(title: String, description: String = "", priority: String, tags: [String], bug: Bool = false,
                         photos: Bool = false, link: String = "") {
        app.buttons["New idea"].tap()
        if bug { app.buttons["Bug"].tap() }
        type(0, title)
        if !description.isEmpty { type(1, description) }
        app.buttons[priority].tap()
        for tag in tags {
            type(2, tag)
            app.keyboards.buttons["Done"].tap()
        }
        if !link.isEmpty { type(3, link) }
        dismissKeyboard()
        if photos {
            app.swipeUp()
            app.buttons["Add photos"].tap()
            // The two mockups are the newest photos, which the picker lists first.
            let grid = app.images.matching(identifier: "PXGGridLayout-Info")
            XCTAssertTrue(grid.element(boundBy: 1).waitForExistence(timeout: 10))
            sleep(2)
            grid.element(boundBy: 0).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            grid.element(boundBy: 1).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            app.buttons["Done"].tap()
            XCTAssertTrue(app.buttons["photo-2"].waitForExistence(timeout: 10))
        }
        app.buttons["Add idea"].tap()
        XCTAssertTrue(text(title).waitForExistence(timeout: 5))
    }

    func testStoreScreens() throws {
        try XCTSkipUnless(FileManager.default.fileExists(atPath: "\(dir)/run"), "only when ios-screenshots.sh asks for it")

        // Enough of the user's own apps that Home shows a full list, as on Play.
        for path in ["app/src/androidTest/assets/store/reading_list.json", "app/src/androidTest/assets/store/packing_list.json",
                     "app/src/main/res/raw/v3_ai_checklist.json", "app/src/main/res/raw/v3_race_signup.json",
                     "app/src/androidTest/assets/store/book_club.json"] {
            try keep(try repo(path))
        }
        app.launchArguments = []
        app.launch()
        XCTAssertTrue(text("Your apps").waitForExistence(timeout: 10))
        capture("home")

        app.buttons["Ask your AI app"].firstMatch.tap()
        let request = app.textViews.firstMatch.exists ? app.textViews.firstMatch : app.textFields.firstMatch
        XCTAssertTrue(request.waitForExistence(timeout: 5))
        request.tap()
        request.typeText("A sign-up form for a 10 km run in Rome")
        dismissKeyboard()
        capture("ai")
        app.terminate()

        app.launchArguments = ["-screen", "run", "-example", "v3_masterpieces"]
        app.launch()
        XCTAssertTrue(text("Mona Lisa").waitForExistence(timeout: 15))
        capture("gallery", settle: 6)  // the paintings load from the web
        text("Mona Lisa").tap()
        XCTAssertTrue(text("Where to see it").waitForExistence(timeout: 10))
        capture("detail", settle: 4)
        app.terminate()

        // App ideas, as someone keeping notes on their own app: a bug, priorities, tags and mockups.
        app.launchArguments = ["-screen", "run", "-example", "v3_app_ideas"]
        app.launch()
        app.buttons["New app"].tap()
        type(0, "Rainy")
        type(1, "Rain alerts and forecasts, on the phone and the watch")
        app.buttons["Add app"].tap()
        XCTAssertTrue(text("Rainy").waitForExistence(timeout: 5))
        text("Rainy").tap()
        addIdea(title: "Dark mode", priority: "Low", tags: ["ui"])
        addIdea(title: "The widget is blank after a resize", priority: "Medium", tags: ["widgets"], bug: true)
        addIdea(title: "Rain alerts on the watch", description: "A tap on the wrist before it rains",
                priority: "High", tags: ["watch", "alerts"], photos: true, link: "https://wakirox.app")
        capture("ideas")
        app.terminate()

        app.launchArguments = []
        app.launch()
        let race = text("Summer Run 2026")
        for _ in 0..<6 where !(race.exists && race.isHittable) { app.swipeUp() }
        race.tap()
        XCTAssertTrue(app.buttons["Sign me up"].waitForExistence(timeout: 10))
        type(0, "Giulia Rossi")
        type(1, "giulia@example.com")
        dismissKeyboard()
        // Distance has a long option, so it's a menu; short ones sit side by side.
        if !app.buttons["10 km"].exists {
            app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Distance")).firstMatch.tap()
        }
        app.buttons["10 km"].firstMatch.tap()
        app.buttons["M"].tap()
        // The switch is at the row's trailing end; a tap in the middle lands on its label.
        let rules = app.switches.firstMatch
        if rules.exists { rules.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap() }
        capture("form")

        app.buttons["Menu"].firstMatch.tap()
        app.buttons["Edit this app"].tap()
        XCTAssertTrue(app.navigationBars.firstMatch.waitForExistence(timeout: 5))
        capture("editor")
    }
}
