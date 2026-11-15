package it.sapienza.appinterpreter.utils

import android.content.Context
import it.sapienza.appinterpreter.security.ConfigurationApiKeys
import it.sapienza.appinterpreter.storage.AppStore

/** The [AppStore] of one SharedPreferences file: the same files and keys the app always used. */
class SharedPreferencesStore(context: Context, name: String) : AppStore {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)
    override fun contains(key: String): Boolean = preferences.contains(key)
    override fun keys(): Set<String> = preferences.all.keys
    override fun edit(changes: Map<String, String?>) {
        val editor = preferences.edit()
        changes.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
        editor.apply()
    }
}

/** The library of saved apps, in the "AppLibrary" preferences as before. */
fun AppLibrary.Companion.of(context: Context): AppLibrary =
    AppLibrary(SharedPreferencesStore(context, "AppLibrary"), onRemoved = ConfigurationApiKeys::release)
