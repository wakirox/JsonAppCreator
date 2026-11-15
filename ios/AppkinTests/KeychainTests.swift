@testable import Appkin
import AppkinShared
import Security
import XCTest

/// The API keys a user types live in the Keychain under the app's identity, not in the app's JSON or the library.
final class KeychainTests: XCTestCase {
    private let configurationId = "test-\(UUID().uuidString)"
    private let keyName = "TEST_API_KEY"

    override func tearDown() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "app.wakirox.appkin.apikeys",
            kSecAttrAccount as String: "\(configurationId)|\(keyName)"
        ]
        SecItemDelete(query as CFDictionary)
    }

    func testAKeyRoundTripsAndIsReplaced() {
        let store = KeychainSecretStore()
        XCTAssertNil(store.read(configurationId: configurationId, name: keyName))
        store.write(configurationId: configurationId, name: keyName, value: "first-value")
        // A new instance (as after a relaunch) sees it.
        XCTAssertEqual(KeychainSecretStore().read(configurationId: configurationId, name: keyName), "first-value")
        store.write(configurationId: configurationId, name: keyName, value: "second-value")
        XCTAssertEqual(store.read(configurationId: configurationId, name: keyName), "second-value")
        XCTAssertNil(store.read(configurationId: configurationId, name: "OTHER"))
    }

    func testTheKeyIsNotInTheSavedJson() {
        let json = """
        {"schemaVersion":3,"name":"Key Test","version":"1","mainView":{"id":"l0","type":"layout","views":[
        {"type":"text","label":"hi"}]}}
        """
        let secrets = KeychainSecretStore()
        let kit = ShellKit(secrets: secrets, bundledKeys: "")
        guard let saved = kit.library.save(json: json, configurationId: configurationId) else { return XCTFail("not saved") }
        defer { kit.library.delete(id: saved.id) }
        secrets.write(configurationId: configurationId, name: keyName, value: "secret-value-123")
        let all = kit.library.all()
        XCTAssertFalse(all.contains { $0.json.contains("secret-value-123") })
        XCTAssertTrue(all.contains { $0.id == saved.id })
    }
}
