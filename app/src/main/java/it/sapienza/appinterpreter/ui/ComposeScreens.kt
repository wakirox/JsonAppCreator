package it.sapienza.appinterpreter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import it.sapienza.androidratio.appratio.R

/** Material 3 Expressive: the wallpaper's colours where the phone offers them, and springy motion. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive()) {
        // Edge to edge: the surface reaches behind the system bars, and each screen keeps its content clear of them.
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PhotoScreen(url: String?, onBack: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        val (nextScale, nextOffset) = PhotoZoom.next(scale, offset, zoom, pan, size)
        scale = nextScale
        offset = nextOffset
    }
    var loaded by remember { mutableStateOf(false) }
    // The photo fills the whole screen, under the see-through top bar.
    Box(Modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            // The gestures are read outside the zoomed layer, so a drag moves the photo as far as the finger goes.
            modifier = Modifier.fillMaxSize().onSizeChanged { size = it }.transformable(transform).graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }.then(if (loaded) Modifier.testTag("photo-loaded") else Modifier),
            // The tag marks a loaded photo, which tests wait for.
            onSuccess = { loaded = true },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() } },
            error = { Text(stringResource(R.string.image_unavailable), modifier = Modifier.padding(24.dp)) }
        )
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

/** Pinching and dragging a full-screen photo. */
internal object PhotoZoom {
    const val MAX_SCALE = 6f

    /**
     * The scale and offset after a gesture of [zoom] and [pan] (screen pixels) on a photo shown in [size]: the photo
     * moves with the finger, and never so far that the screen shows past its edge; back at full size it is centred.
     */
    fun next(scale: Float, offset: Offset, zoom: Float, pan: Offset, size: IntSize): Pair<Float, Offset> {
        val nextScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val maxX = size.width * (nextScale - 1) / 2
        val maxY = size.height * (nextScale - 1) / 2
        val moved = offset + pan
        return nextScale to Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
    }
}
