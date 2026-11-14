package it.sapienza.appinterpreter.utils

import it.sapienza.appinterpreter.json.JsonSyntaxException
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.model.Layout
import it.sapienza.appinterpreter.model.ModelApplication
import it.sapienza.appinterpreter.model.ModelJson
import it.sapienza.appinterpreter.model.action.Action
import it.sapienza.appinterpreter.model.event.AlertMessage
import it.sapienza.appinterpreter.model.event.CalendarEvent
import it.sapienza.appinterpreter.model.event.CopyText
import it.sapienza.appinterpreter.model.event.DeleteData
import it.sapienza.appinterpreter.model.event.Event
import it.sapienza.appinterpreter.model.event.GoBack
import it.sapienza.appinterpreter.model.event.OpenLink
import it.sapienza.appinterpreter.model.event.PdfItems
import it.sapienza.appinterpreter.model.event.RESTService
import it.sapienza.appinterpreter.model.event.SaveData
import it.sapienza.appinterpreter.model.event.ShareText
import it.sapienza.appinterpreter.model.event.ShowView
import it.sapienza.appinterpreter.model.event.TextPackItems
import it.sapienza.appinterpreter.model.event.UpdateData
import it.sapienza.appinterpreter.model.view_model.ListView
import it.sapienza.appinterpreter.model.view_model.helper.MView
import kotlinx.serialization.json.JsonElement

class AppParser(val obj: String) {

    /** Parses [obj], validates references, and indexes inline view and action definitions into the root registries. */
    fun parseApplication2(): ModelApplication {
        val tree = try {
            JsonText.parse(obj)
        } catch (error: JsonSyntaxException) {
            throw ConfigurationValidationException("$", "invalid JSON: ${error.message}")
        }
        return parse(tree)
    }

    /** The app of an already read configuration [tree]. */
    fun parse(tree: JsonElement?): ModelApplication {
        ReferenceValidator().validate(tree)

        val model = try {
            ModelJson.decodeFromJsonElement(ModelApplication.serializer(), checkNotNull(tree))
        } catch (error: ConfigurationValidationException) {
            throw error
        } catch (error: Exception) {
            throw ConfigurationValidationException("$", "configuration cannot be parsed: ${error.message}")
        }

        val declaredActions = model.actions.toList()
        val declaredViews = model.views.toList()

        declaredActions.forEach { it.event?.let { event -> findView(event, model) } }
        declaredViews.forEach { findGenericView(it, model, addSelf = false) }
        if (!model.mainView.isReference()) findGenericView(model.mainView, model)

        checkModelConsistency(model)

        return model
    }

    private fun findView(ev: Event, model: ModelApplication) {
        when (ev) {
            is ShowView -> if (!ev.view.isReference()) {
                findGenericView(ev.view, model)
            }

            is RESTService -> {
                ev.thenDo?.let { findView(it, model) }
                ev.onError?.let { findView(it, model) }
            }

            is AlertMessage -> {
                ev.thenDoOK?.let { findView(it, model) }
                ev.thenDoKO?.let { findView(it, model) }
            }

            is SaveData -> ev.thenDo?.let { findView(it, model) }

            is UpdateData -> ev.thenDo?.let { findView(it, model) }

            is DeleteData -> ev.thenDo?.let { findView(it, model) }

            is CalendarEvent,
            is CopyText,
            is GoBack,
            is OpenLink,
            is PdfItems,
            is ShareText,
            is TextPackItems -> Unit
        }
    }

    private fun findGenericView(view: MView, model: ModelApplication, addSelf: Boolean = true) {
        when (view) {
            is ListView -> {
                findGenericView(view.itemView, model)
                registerAction(view.swipeToStart, model)
                registerAction(view.swipeToEnd, model)
                view.initData?.let { findView(it, model) }
            }

            is Layout -> {
                view.views.forEach {
                    findGenericView(it, model)
                }
            }
        }

        registerAction(view.action, model)

        if (addSelf && !view.isReference()) {
            model.views.add(view)
        }
    }

    private fun registerAction(action: Action?, model: ModelApplication) {
        action?.event?.let { findView(it, model) }
        action?.takeUnless { it.isReference() }?.let { definition ->
            if (model.actions.none { it.id == definition.id }) model.actions.add(definition)
        }
    }

    private fun checkModelConsistency(model: ModelApplication) {
        model.actions.firstOrNull { it.isReference() }?.let {
            throw ConfigurationValidationException("$.actions", "references cannot be registered as action definitions")
        }
        model.views.firstOrNull { it.isReference() }?.let {
            throw ConfigurationValidationException("$.views", "references cannot be registered as view definitions")
        }
    }
}
