package it.sapienza.appinterpreter.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.activity.HomeActivity

/** Home-screen shortcuts that open Appkin straight into one saved app: pinned launcher shortcuts. */
class HomeShortcuts(private val context: Context) : HomeScreenShortcuts {

    /** Asks the launcher to pin a shortcut for [app]; false if the launcher can't pin shortcuts. */
    override fun pin(app: AppLibrary.SavedApp): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = shortcut(app)
        // Re-enable it in case this app was deleted and saved again, and give an already pinned one today's icon.
        ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * Gives every pinned shortcut its app's current name and icon: launchers keep a shortcut's icon until the app
     * updates it, so shortcuts pinned before an app (or Appkin) changed would otherwise look old forever.
     */
    fun refreshPinned() {
        val library = AppLibrary.of(context)
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        val current = pinned.filter { it.isEnabled }.mapNotNull { library.get(it.id) }.map(::shortcut)
        if (current.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, current)
    }

    private fun shortcut(app: AppLibrary.SavedApp): ShortcutInfoCompat {
        val intent = Intent(context, HomeActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_APP_ID, app.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return ShortcutInfoCompat.Builder(context, app.id)
            .setShortLabel(app.name.take(25))
            .setLongLabel(app.name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(AppIcon.bitmap(app.name, app.icon)))
            .setIntent(intent)
            .build()
    }

    /** A deleted app's pinned shortcut stays on the home screen, greyed out, and says so when tapped. */
    override fun disable(id: String) =
        ShortcutManagerCompat.disableShortcuts(context, listOf(id), context.getString(R.string.shortcut_deleted))

    companion object {
        const val EXTRA_APP_ID = "app_id"
    }
}
