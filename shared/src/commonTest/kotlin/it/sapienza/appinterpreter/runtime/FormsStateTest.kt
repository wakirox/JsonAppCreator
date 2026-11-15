package it.sapienza.appinterpreter.runtime

import androidx.lifecycle.SavedStateHandle
import it.sapienza.appinterpreter.json.JsonText
import it.sapienza.appinterpreter.json.getValue
import it.sapienza.appinterpreter.model.view_model.FormView
import it.sapienza.appinterpreter.utils.AppParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject

class FormsStateTest {
    private val form = AppParser(
        """
        {
          "name": "Forms", "version": "1.0", "schemaVersion": 3,
          "mainView": {
            "type": "form", "id": "signup",
            "formElements": [
              { "type": "text", "mapping": "runner.name", "title": "Name", "mandatory": true },
              { "type": "checkbox", "mapping": "rules", "title": "Rules", "mandatory": true },
              { "type": "text", "mapping": "runner.club", "title": "Club" }
            ]
          }
        }
        """
    ).parseApplication2().mainView as FormView
    private val data = JsonText.parse("""{ "runner": { "name": "Alex" } }""") as JsonObject
    private val saved = SavedStateHandle()

    @Test
    fun startsFromTheScreensData() {
        val values = FormsState(saved).values(form, data)

        assertEquals("Alex", values["runner.name"])
        assertEquals(false, values["rules"])
        assertNull(values["runner.club"])
    }

    @Test
    fun aFormThatAddsANewObjectStartsEmptyAndResetsEmpty() {
        val forms = FormsState(saved, addsNew = { true })
        assertNull(forms.values(form, data)["runner.name"])

        forms.set(form, data, "runner.name", "Giulia")
        forms.reset(form, data)
        assertNull(forms.values(form, data)["runner.name"])
    }

    @Test
    fun theMissingFieldsMessageComesInTheAppsLanguage() {
        val italian = object : RuntimeStrings by EnglishRuntimeStrings {
            override fun formIncomplete(fields: String) = "Completa: $fields"
        }
        val forms = FormsState(saved, strings = italian)
        forms.submit(form, data)
        assertEquals("Completa: Rules", forms.error(form, data))
    }

    @Test
    fun aRecreatedScreenGetsTheTypedValuesBack() {
        FormsState(saved).set(form, data, "runner.name", "Giulia")
        FormsState(saved).set(form, data, "rules", true)

        // A new FormsState on the same SavedStateHandle: the screen after the system closed the app in the background.
        val restored = FormsState(saved).values(form, data)
        assertEquals("Giulia", restored["runner.name"])
        assertEquals(true, restored["rules"])
    }

    @Test
    fun submittingSaysWhatIsMissingThenSendsNestedJsonWithoutEmptyFields() {
        val forms = FormsState(saved)
        val state = forms.state(form, data)

        assertNull(forms.submit(form, data))
        assertEquals("Please complete: Rules", state.error.value)

        forms.set(form, data, "rules", true)
        assertEquals(true, state.values.value["rules"], "the observed values change")
        val json = assertNotNull(forms.submit(form, data))
        assertEquals("""{"runner":{"name":"Alex"},"rules":true}""", json.toString())
        assertEquals("Alex", json.getValue("runner.name"))
        assertNull(forms.error(form, data))
    }
}
