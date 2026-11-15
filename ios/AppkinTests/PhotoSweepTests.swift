@testable import Appkin
import XCTest

final class PhotoSweepTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_000_000)

    private func file(_ name: String, age: TimeInterval) -> (name: String, modified: Date) {
        (name, now.addingTimeInterval(-age))
    }

    func testDeletesOnlyOldUnreferencedJpegs() {
        let files = [file("old.jpg", age: 7200), file("kept.jpg", age: 7200), file("new.jpg", age: 60), file("note.txt", age: 7200)]
        let result = PhotoStore.sweepable(files, keeping: ["appkin-photo:kept.jpg"], now: now)
        XCTAssertEqual(result, ["old.jpg"])
    }

    func testNothingReferencedDeletesNothing() {
        let files = [file("old.jpg", age: 7200)]
        XCTAssertEqual(PhotoStore.sweepable(files, keeping: [], now: now), [])
    }

    func testGraceBoundaryKeepsFileExactlyAtGrace() {
        let files = [file("edge.jpg", age: PhotoStore.sweepGrace)]
        XCTAssertEqual(PhotoStore.sweepable(files, keeping: ["appkin-photo:other.jpg"], now: now), [])
    }
}
