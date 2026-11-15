import XCTest

/// Drives photos, an attached file, the PDF and Export of the bundled "App ideas" example by touch. It needs two
/// pictures in the simulator's Photos and a PDF in Files > On My iPhone ("Appkin spec.pdf"); it saves screenshots to
/// /tmp/appkin-shots and, with `-exportCopyDir`, the PDF and zip to /tmp/appkin-export.
@MainActor
final class MediaFlowTests: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
    }

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

    let appName = "Media \(Int(Date().timeIntervalSince1970) % 100000)"

    func testPhotosFileAndExports() throws {
        app.launchArguments = ["-screen", "run", "-example", "v3_app_ideas", "-exportCopyDir", "/tmp/appkin-export"]
        app.launch()
        app.buttons["New app"].tap()
        type(0, appName)
        app.buttons["Add app"].tap()
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", appName)).firstMatch.tap()
        app.buttons["New idea"].tap()
        type(0, "Hill sketches")
        app.navigationBars.firstMatch.tap()
        app.swipeUp()
        app.buttons["Add photos"].tap()
        let grid = app.images.matching(identifier: "PXGGridLayout-Info")
        XCTAssertTrue(grid.element(boundBy: 1).waitForExistence(timeout: 10))
        sleep(2)
        grid.element(boundBy: 0).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        grid.element(boundBy: 1).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["photo-2"].waitForExistence(timeout: 10))
        app.buttons["Attach files"].tap()
        // The picker remembers where it was last: Recents, Browse, or inside On My iPhone.
        let pdf = app.staticTexts["Appkin spec"]
        if !pdf.waitForExistence(timeout: 3) {
            if app.buttons["Browse"].waitForExistence(timeout: 3) { app.buttons["Browse"].tap() }
            let phone = app.staticTexts["On My iPhone"]
            if phone.waitForExistence(timeout: 5) { phone.tap() }
        }
        XCTAssertTrue(pdf.waitForExistence(timeout: 10), app.debugDescription)
        pdf.tap()
        if app.buttons["Open"].waitForExistence(timeout: 3) { app.buttons["Open"].tap() }
        let chip = app.buttons["file-Appkin spec.pdf"]
        XCTAssertTrue(chip.waitForExistence(timeout: 10))
        app.navigationBars.firstMatch.tap()
        app.swipeUp()
        app.buttons["Add idea"].tap()
        XCTAssertTrue(text("Hill sketches").waitForExistence(timeout: 5))

        // The card: thumbnails and the file chip.
        let card = app.buttons["photo-1"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["file-Appkin spec.pdf"].firstMatch.exists)
        shot("media-card")

        // A photo opens full screen and zooms.
        card.tap()
        XCTAssertTrue(app.buttons["Close"].waitForExistence(timeout: 5))
        sleep(1)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).doubleTap()
        sleep(1)
        shot("media-photo-zoom")
        app.buttons["Close"].tap()

        // The chip opens Quick Look.
        app.buttons["file-Appkin spec.pdf"].firstMatch.tap()
        sleep(3)
        shot("media-file-preview")
        XCTAssertTrue(app.buttons["QLOverlayDoneButtonAccessibilityIdentifier"].exists)
        app.buttons["QLOverlayDoneButtonAccessibilityIdentifier"].tap()

        // Restart: the file still opens.
        app.terminate()
        app.launch()
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", appName)).firstMatch.tap()
        let again = app.buttons["file-Appkin spec.pdf"].firstMatch
        XCTAssertTrue(again.waitForExistence(timeout: 10))
        again.tap()
        sleep(3)
        XCTAssertTrue(app.buttons["QLOverlayDoneButtonAccessibilityIdentifier"].exists, "Quick Look did not open after a restart")
        shot("media-file-after-restart")
        app.buttons["QLOverlayDoneButtonAccessibilityIdentifier"].tap()

        // PDF and Export open the share sheet.
        app.buttons["PDF"].tap()
        sleep(4)
        shot("media-pdf-share")
        XCTAssertTrue(exported("\(appName) open ideas.pdf"), "the PDF was not written")
        // Tap above the sheet to dismiss it.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
        sleep(1)
        app.buttons["Export"].tap()
        sleep(3)
        shot("media-export-share")
        XCTAssertTrue(exported("\(appName) open ideas.zip"), "the zip was not written")
    }

    /// The share sheet's file name is shown by the system; the app's debug copy shows what it was called.
    private func exported(_ name: String) -> Bool {
        FileManager.default.fileExists(atPath: "/tmp/appkin-export/\(name)")
    }
}
