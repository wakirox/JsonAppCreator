package it.sapienza.appinterpreter.ai

import android.content.Context
import it.sapienza.appinterpreter.utils.DomainController
import it.sapienza.appinterpreter.utils.TextHandover

/** What the AI app is asked about: a new app, the running app, or the editor's text (a file path, see [TextHandover]). */
sealed interface AskAiSubject {
    data object NewApp : AskAiSubject
    data object CurrentApp : AskAiSubject
    data class EditorText(val path: String) : AskAiSubject
}

/** The app the AI app may be asked to change, or null for a new app. */
class LoadAppToChangeUseCase(private val context: Context) {
    operator fun invoke(subject: AskAiSubject): String? = when (subject) {
        AskAiSubject.NewApp -> null
        AskAiSubject.CurrentApp -> DomainController.jsonFile(context)
        is AskAiSubject.EditorText -> TextHandover.read(context, subject.path)
    }
}

/**
 * The instructions for the AI app: the guide, schema and examples (or, [withLinks], the short version linking to
 * them), the app to change if any, and the request.
 */
class BuildAiPromptUseCase(private val context: Context) {
    operator fun invoke(appToChange: String?, request: String?, withLinks: Boolean): String =
        AskAi.context(context, appToChange, request, withLinks)
}
