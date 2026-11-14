package it.sapienza.appinterpreter.security

/**
 * Encrypted storage for the API keys a configuration asks for, each kept under its configuration's identity
 * (Android: AES-GCM with a non-exportable Keystore key; iOS would use the Keychain).
 */
interface SecretStore {
    /** The value, or null when there is none or it can no longer be decrypted. */
    fun read(configurationId: String, name: String): String?
    fun write(configurationId: String, name: String, value: String)
    fun remove(configurationId: String, name: String) {}
}

/** A [SecretStore] in memory only, for tests. */
class MemorySecretStore : SecretStore {
    private val values = HashMap<Pair<String, String>, String>()
    override fun read(configurationId: String, name: String) = values[configurationId to name]
    override fun write(configurationId: String, name: String, value: String) {
        values[configurationId to name] = value
    }

    override fun remove(configurationId: String, name: String) {
        values.remove(configurationId to name)
    }
}
