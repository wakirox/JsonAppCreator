package it.sapienza.appinterpreter

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import it.sapienza.androidratio.appratio.BuildConfig
import it.sapienza.appinterpreter.di.appModule
import it.sapienza.appinterpreter.security.ConfigurationApiKeys
import it.sapienza.appinterpreter.storage.Photos
import it.sapienza.appinterpreter.utils.ImageCacheExpiry
import it.sapienza.appinterpreter.utils.PhotoStore
import it.sapienza.appinterpreter.utils.SharedPreferencesStore
import it.sapienza.appinterpreter.utils.httpclient.HttpClient
import java.io.File
import kotlin.concurrent.thread
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class AppRatioApplication :
    Application(),
    SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        ConfigurationApiKeys.initialize(this, BuildConfig.CONFIG_API_KEYS_BASE64)
        // Robolectric creates an Application for every test in one process: start Koin afresh each time.
        stopKoin()
        startKoin {
            androidContext(this@AppRatioApplication)
            modules(appModule)
        }
        // Expired image cache entries and unreferenced photos are swept away from the main thread.
        thread(name = "photo-cleanup") {
            ImageCacheExpiry(SharedPreferencesStore(this, "ImageCache")) {
                SingletonImageLoader.get(this).diskCache?.clear()
            }.check()
            val saved = SharedPreferencesStore(this, "AppData")
            PhotoStore(this).deleteUnused(Photos.referencesIn(saved.keys().joinToString { saved.get(it).orEmpty() }))
        }
    }

    /** Coil loads images through the app's OkHttp client, so they carry its User-Agent. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { HttpClient.client }))
            // A photo a JSON app keeps on the phone ("appkin-photo:<name>") is its file.
            val photos = PhotoStore(this@AppRatioApplication)
            add(Mapper<String, File> { data, _ -> photos.file(data) })
        }
        .build()
}
