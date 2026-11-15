import XCTest

/// Drives the bundled "App ideas" example by touch: add an app, add ideas, sort, filter, edit, swipe. Run on a fresh
/// install (`xcodebuild test`); it saves screenshots to /tmp/appkin-shots when that folder exists.
@MainActor
final class IdeasFlowTests: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
    }

    /// A text on screen: a row that opens something reads as a button, so any element will do.
    private func text(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func shot(_ name: String) {
        let data = XCUIScreen.main.screenshot().pngRepresentation
        try? data.write(to: URL(fileURLWithPath: "/tmp/appkin-shots/\(name).png"))
    }

    private func type(_ index: Int, _ text: String) {
        let field = app.textFields.element(boundBy: index)
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(text)
    }

    private func addIdea(bug: Bool, title: String, description: String, priority: String, tag: String, link: String) {
        app.buttons["New idea"].tap()
        if bug { app.buttons["Bug"].tap() }
        type(0, title)
        type(1, description)
        app.buttons[priority].tap()
        type(2, tag)
        app.keyboards.buttons["Done"].tap()
        type(3, link)
        app.navigationBars.firstMatch.tap()
        app.buttons["Add idea"].tap()
    }

    func testFlow() throws {
        app.launchArguments = ["-screen", "run", "-example", "v3_app_ideas"]
        app.launch()
        app.buttons["New app"].tap()
        type(0, "Rainy")
        type(1, "A weather app that looks like a painting")
        app.buttons["Add app"].tap()
        XCTAssertTrue(text("Rainy").waitForExistence(timeout: 5))
        shot("ideas-apps")
        text("Rainy").tap()

        app.buttons["New idea"].tap()
        shot("ideas-new")
        app.navigationBars.buttons.firstMatch.tap()

        addIdea(bug: false, title: "Radar tab", description: "Show the rain radar", priority: "Medium", tag: "ui", link: "https://rainy.app")
        XCTAssertTrue(text("Radar tab").waitForExistence(timeout: 5))
        addIdea(bug: true, title: "Crash on rotate", description: "Happens on the watch face", priority: "High", tag: "watch", link: "")
        XCTAssertTrue(text("Crash on rotate").waitForExistence(timeout: 5))
        shot("ideas-list")

        // Filter by tag, then back to all.
        app.buttons["#watch"].tap()
        XCTAssertFalse(text("Radar tab").waitForExistence(timeout: 2))
        app.buttons["All"].tap()
        app.buttons["Newest"].tap()
        XCTAssertTrue(text("Radar tab").waitForExistence(timeout: 2))

        // Edit.
        text("Radar tab").tap()
        XCTAssertTrue(app.buttons["Save changes"].waitForExistence(timeout: 5))
        shot("ideas-edit")
        let title = app.textFields.element(boundBy: 0)
        title.tap()
        title.typeText("v2 ")
        app.buttons["Save changes"].tap()
        XCTAssertTrue(text("v2 Radar tab").waitForExistence(timeout: 5))

        // Swipe right: done.
        swipe(text("v2 Radar tab"), right: true)
        XCTAssertTrue(text("✓ Done").waitForExistence(timeout: 5))

        // Swipe left asks first: Cancel keeps it, OK deletes it.
        addIdea(bug: false, title: "Temp", description: "", priority: "Low", tag: "", link: "")
        XCTAssertTrue(text("Temp").waitForExistence(timeout: 5))
        swipe(text("Temp"), right: false)
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 5))
        app.alerts.buttons["Cancel"].tap()
        XCTAssertTrue(text("Temp").exists)
        swipe(text("Temp"), right: false)
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 5))
        app.alerts.buttons["OK"].tap()
        XCTAssertTrue(text("Temp").waitForNonExistence(timeout: 5))
        shot("ideas-final")
    }

    private func swipe(_ element: XCUIElement, right: Bool) {
        let y = element.frame.midY / app.frame.height
        let from = app.coordinate(withNormalizedOffset: CGVector(dx: right ? 0.15 : 0.85, dy: y))
        let to = app.coordinate(withNormalizedOffset: CGVector(dx: right ? 0.75 : 0.25, dy: y))
        from.press(forDuration: 0.1, thenDragTo: to)
    }
}
