package it.sapienza.appinterpreter.runtime

import androidx.lifecycle.SavedStateHandle
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.formValueToJson
import it.sapienza.appinterpreter.json.toFormValue
import it.sapienza.appinterpreter.model.view_model.FormView
import it.sapienza.appinterpreter.runtime.usecase.InitialFormValuesUseCase
import it.sapienza.appinterpreter.runtime.usecase.SubmitFormUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The values and errors of a screen's forms. Values are also written to [saved], so a half-filled form survives
 * the system closing the app in the background. A form's definition is shared (by list rows, and by screens opened
 * again with other data), so it is only read, never written. [addsNew] says which forms save a new object: those
 * start empty rather than from the data they show.
 */
class FormsState(
    private val saved: SavedStateHandle,
    private val initialValues: InitialFormValuesUseCase = InitialFormValuesUseCase(),
    private val submit: SubmitFormUseCase = SubmitFormUseCase(),
    private val addsNew: (FormView) -> Boolean = { false },
    private val strings: RuntimeStrings = EnglishRuntimeStrings
) {
    /** One form on one piece of data: its values by mapping (text, true/false or null) and why it wasn't sent. */
    class Form internal constructor(values: Map<String, Any?>) {
        internal val valuesFlow = MutableStateFlow(values)
        internal val errorFlow = MutableStateFlow<String?>(null)
        val values: StateFlow<Map<String, Any?>> = valuesFlow.asStateFlow()
        val error: StateFlow<String?> = errorFlow.asStateFlow()
    }

    private val forms = mutableMapOf<String, Form>()

    /** [form] when it shows [data], to observe: the saved values, else its starting values. */
    fun state(form: FormView, data: JsonObject?): Form = forms.getOrPut(key(form, data)) {
        val restored = saved.get<String>(key(form, data))?.let {
            runCatching { JsonText.parse(it) as? JsonObject }.getOrNull()
        }
        Form(
            initialValues(form, data, addsNew(form)).mapValues { (mapping, initial) ->
                val kept = restored?.get(mapping)
                if (kept == null || kept is JsonNull) initial else kept.toFormValue()
            }
        )
    }

    /** [form]'s values when it shows [data] now. */
    fun values(form: FormView, data: JsonObject?): Map<String, Any?> = state(form, data).values.value

    fun set(form: FormView, data: JsonObject?, mapping: String, value: Any?) {
        val state = state(form, data)
        state.valuesFlow.value = state.valuesFlow.value + (mapping to value)
        saved[key(form, data)] = JsonObject(state.valuesFlow.value.mapValues { formValueToJson(it.value) }).toString()
    }

    /** Back to [form]'s starting values, as after its values were saved. */
    fun reset(form: FormView, data: JsonObject?) {
        val state = state(form, data)
        state.valuesFlow.value = initialValues(form, data, addsNew(form))
        state.errorFlow.value = null
        saved.remove<String>(key(form, data))
    }

    /** Why [form] wasn't sent, if it wasn't. */
    fun error(form: FormView, data: JsonObject?): String? = state(form, data).error.value

    /** The JSON to send when every required field is filled in; else null, and [error] says which aren't. */
    fun submit(form: FormView, data: JsonObject?): JsonObject? {
        val state = state(form, data)
        return when (val result = submit(form, state.values.value)) {
            is SubmitFormUseCase.Result.Missing -> null.also {
                state.errorFlow.value = strings.formIncomplete(result.fields.joinToString())
            }

            is SubmitFormUseCase.Result.Ready -> result.json.also { state.errorFlow.value = null }
        }
    }

    /** Stable across recreation, so saved values find their form again; identical forms on identical data share it. */
    private fun key(form: FormView, data: JsonObject?) =
        "form:${form.id.orEmpty()}:${form.formElements.joinToString(",") { it.mapping }}:${data?.toString().hashCode()}"
}
