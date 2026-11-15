package it.sapienza.appinterpreter.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A flow being watched from Swift: [cancel] stops it. */
class Watching internal constructor(private val scope: CoroutineScope) {
    fun cancel() = scope.cancel()
}

/** Lets Swift observe the engine's flows: each value arrives on the main thread. */
object Watch {
    /** Calls [onEach] with every value [flow] emits (a StateFlow's current one first), until the result is cancelled. */
    fun <T> flow(flow: Flow<T>, onEach: (T) -> Unit): Watching {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { flow.collect { onEach(it) } }
        return Watching(scope)
    }
}
