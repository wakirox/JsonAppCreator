@testable import Appkin
import XCTest

/// The editor keeps JSON quotes straight when the keyboard's smart punctuation curls them.
final class EditorQuotesTests: XCTestCase {
    func testATypedCurlyQuoteBecomesStraight() {
        XCTAssertEqual(straightenTyped(old: "{ ", new: "{ \u{201C}"), "{ \"")
        XCTAssertEqual(straightenTyped(old: "{ \"name", new: "{ \"name\u{201D}"), "{ \"name\"")
    }

    func testPastedTextAndOtherTypingAreLeftAlone() {
        let pasted = "\"text\": \"Bucharest\u{2019}s \u{201C}largest\u{201D} park\""
        XCTAssertNil(straightenTyped(old: "", new: pasted), "a paste is longer than typing")
        XCTAssertNil(straightenTyped(old: "{ ", new: "{ a"))
        XCTAssertNil(straightenTyped(old: "{ \"a\"", new: "{ \"a"), "deleting")
    }
}
