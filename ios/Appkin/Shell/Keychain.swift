import AppkinShared
import Foundation
import Security

/// The API keys a user types, kept in the Keychain (this device only) under the app's identity, never in its JSON.
final class KeychainSecretStore: NSObject, SecretStore, @unchecked Sendable {
    private let service = "app.wakirox.appkin.apikeys"

    private func query(_ configurationId: String, _ name: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "\(configurationId)|\(name)"
        ]
    }

    func read(configurationId: String, name: String) -> String? {
        var request = query(configurationId, name)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(request as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func write(configurationId: String, name: String, value: String) {
        let data = Data(value.utf8)
        let found = query(configurationId, name)
        let update = [kSecValueData as String: data]
        if SecItemUpdate(found as CFDictionary, update as CFDictionary) == errSecItemNotFound {
            var add = found
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }
}
