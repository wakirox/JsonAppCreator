@testable import Appkin
import XCTest

@MainActor
final class SystemURLTests: XCTestCase {
    func testGeoQueryDecodesPlusAndPercentEncoding() {
        let url = ScreenModel.systemURL("geo:0,0?q=Louvre+Museum%2C+Paris")
        XCTAssertNotNil(url)
        let components = url.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }
        XCTAssertEqual(components?.host, "maps.apple.com")
        XCTAssertEqual(components?.queryItems?.first(where: { $0.name == "q" })?.value, "Louvre Museum, Paris")
    }

    func testGeoCoordinatesOpenAppleMapsPinAndIgnoreUncertaintySuffix() {
        let upper = ScreenModel.systemURL("GEO:41.9028,12.4964")
        let upperItems = upper.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems }
        XCTAssertEqual(upperItems?.first(where: { $0.name == "ll" })?.value, "41.9028,12.4964")

        let withUncertainty = ScreenModel.systemURL("geo:41.9028,12.4964;u=35")
        let uncertaintyItems = withUncertainty.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false)?.queryItems }
        XCTAssertEqual(uncertaintyItems?.first(where: { $0.name == "ll" })?.value, "41.9028,12.4964")
    }

    func testInvalidGeoReturnsNilAndWebAddressRemainsUnchanged() {
        XCTAssertNil(ScreenModel.systemURL("geo:not-a-coordinate"))
        XCTAssertNil(ScreenModel.systemURL("geo:999,12.0"))
        XCTAssertEqual(ScreenModel.systemURL("https://example.com/art")?.absoluteString, "https://example.com/art")
    }
}
