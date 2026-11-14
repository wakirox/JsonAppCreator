package it.sapienza.appinterpreter.security

import android.content.Context
import it.sapienza.appinterpreter.utils.DomainController

/** The configurations' API keys on Android: [ConfigurationKeys] on the Keystore-encrypted [EncryptedApiKeyStore]. */
object ConfigurationApiKeys {
    /** The shared logic, once [initialize] has run (the Application does, first). */
    lateinit var keys: ConfigurationKeys
        private set

    fun initialize(context: Context, encodedBundledValues: String) {
        val applicationContext = context.applicationContext
        keys = ConfigurationKeys(
            EncryptedApiKeyStore(applicationContext),
            decodeBundledValues(encodedBundledValues)
        ) { DomainController.configurationId(applicationContext) }
    }

    fun placeholderNames(json: String): Set<String> = ApiKeyPlaceholders.names(json)

    fun hasSavedValue(json: String, name: String): Boolean = keys.hasSavedValue(json, name)

    fun save(json: String, values: Map<String, String>) = keys.save(json, values)

    fun saveForCurrentConfiguration(json: String, values: Map<String, String>) =
        keys.saveForCurrentConfiguration(json, values)

    fun delete(configurationId: String, json: String) = keys.delete(configurationId, json)

    fun availableNamesForCurrentConfiguration(json: String): Set<String> =
        keys.availableNamesForCurrentConfiguration(json)

    fun replaceForCurrentConfiguration(text: String): String {
        check(::keys.isInitialized) { "ConfigurationApiKeys has not been initialized" }
        return keys.replaceForCurrentConfiguration(text)
    }

    fun replaceInHeaderForCurrentConfiguration(text: String): String {
        check(::keys.isInitialized) { "ConfigurationApiKeys has not been initialized" }
        return keys.replaceInHeaderForCurrentConfiguration(text)
    }

    internal fun decodeBundledValues(encoded: String) = ConfigurationKeys.decodeBundledValues(encoded)
}
