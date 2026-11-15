package it.sapienza.appinterpreter.runtime.usecase

import it.sapienza.appinterpreter.runtime.CurrentConfiguration
import it.sapienza.appinterpreter.security.ConfigurationKeys

/** The current app's API keys this phone lacks, or null when it has them all. */
class FindMissingApiKeysUseCase(private val current: CurrentConfiguration, private val keys: ConfigurationKeys) {

    class Missing(val json: String, val names: Set<String>, val savedNames: Set<String>)

    operator fun invoke(): Missing? {
        val json = current.json()
        val names = keys.placeholderNames(json)
        val saved = keys.availableNamesForCurrentConfiguration(json)
        return if (names.all(saved::contains)) null else Missing(json, names, saved)
    }

    /** All of the current app's API keys, to enter again after a service refused one: none counts as saved. */
    fun all(): Missing? {
        val json = current.json()
        val names = keys.placeholderNames(json)
        return if (names.isEmpty()) null else Missing(json, names, emptySet())
    }
}
