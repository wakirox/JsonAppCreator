package it.sapienza.appinterpreter.ai

import it.sapienza.appinterpreter.storage.AppStore

/** How the user last chose to send the instructions, kept for the next time (all of them by default). */
class AiInstructionsChoice(private val store: AppStore) {
    var withLinks: Boolean
        get() = store.get(WITH_LINKS) == "true"
        set(value) = store.put(WITH_LINKS, value.toString())

    private companion object {
        const val WITH_LINKS = "with_links"
    }
}
