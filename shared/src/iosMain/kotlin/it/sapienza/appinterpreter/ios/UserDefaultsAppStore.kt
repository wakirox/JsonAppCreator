package it.sapienza.appinterpreter.ios

import it.sapienza.appinterpreter.storage.AppStore
import platform.Foundation.NSUserDefaults

/**
 * An [AppStore] on UserDefaults: what the JSON apps keep on the phone. Its keys carry [prefix], so the app's other
 * defaults are never listed as the apps' data.
 */
class UserDefaultsAppStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val prefix: String = "appkin.appdata."
) : AppStore {
    override fun get(key: String): String? = defaults.stringForKey(prefix + key)

    override fun keys(): Set<String> = defaults.dictionaryRepresentation().keys
        .mapNotNull { (it as? String)?.takeIf { name -> name.startsWith(prefix) }?.removePrefix(prefix) }
        .toSet()

    override fun edit(changes: Map<String, String?>) {
        changes.forEach { (key, value) ->
            if (value == null) defaults.removeObjectForKey(prefix + key) else defaults.setObject(value, prefix + key)
        }
    }
}
