package it.sapienza.appinterpreter.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.sapienza.androidratio.appratio.R
import it.sapienza.appinterpreter.ai.AskAi
import it.sapienza.appinterpreter.ai.AskAiScreen
import it.sapienza.appinterpreter.ai.AskAiViewModel
import it.sapienza.appinterpreter.ui.AppTheme
import it.sapienza.appinterpreter.utils.TextHandover
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Makes or changes an app with the user's AI app: the request, then Share or Copy, after which it closes. */
class AskAiActivity : AppCompatActivity() {

    private val askAi: AskAiViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_Compose)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { askAi.effects.collect(::handle) }
        }
        setContent {
            AppTheme {
                AskAiScreen(
                    request = askAi.request,
                    onRequestChange = askAi::onRequestChange,
                    canChangeCurrent = askAi.canChangeCurrent,
                    changeCurrent = askAi.changeCurrent,
                    onChangeCurrentChange = askAi::onChangeCurrentChange,
                    withLinks = askAi.withLinks,
                    onWithLinksChange = askAi::onWithLinksChange,
                    tokens = askAi.tokens,
                    onShare = askAi::share,
                    onCopy = askAi::copy,
                    onBack = ::finish,
                    preview = askAi.preview,
                    onPreview = askAi::openPreview,
                    onClosePreview = askAi::closePreview
                )
            }
        }
    }

    private fun handle(effect: AskAiViewModel.Effect) {
        when (effect) {
            is AskAiViewModel.Effect.Share -> AskAi.shareToAiApp(this, effect.prompt)
            is AskAiViewModel.Effect.Copy -> AskAi.copy(this, effect.prompt, getString(R.string.ask_ai_copied))
        }
        finish()
    }

    companion object {
        /** To make a new app. */
        fun newApp(context: Context) = context.startActivity(Intent(context, AskAiActivity::class.java))

        /** With the running app, which the user can choose to have changed. */
        fun currentApp(context: Context) = context.startActivity(
            Intent(context, AskAiActivity::class.java)
                .putExtra(AskAiViewModel.EXTRA_SUBJECT, AskAiViewModel.SUBJECT_CURRENT_APP)
        )

        /**
         * With the editor's text, saved or not, to be changed unless the user unticks it. The text can be large, so it
         * goes through a file (see [TextHandover]).
         */
        fun editorText(context: Context, text: String) = context.startActivity(
            Intent(context, AskAiActivity::class.java)
                .putExtra(AskAiViewModel.EXTRA_SUBJECT, AskAiViewModel.SUBJECT_EDITOR_TEXT)
                .putExtra(AskAiViewModel.EXTRA_TEXT_PATH, TextHandover.write(context, text))
        )
    }
}
