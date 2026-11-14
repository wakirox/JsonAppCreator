package it.sapienza.appinterpreter.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The [SecretStore] on Android: AES-GCM values in private preferences, with a non-exportable Keystore key. */
internal class EncryptedApiKeyStore(context: Context) : SecretStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(configurationId: String, name: String): String? {
        val encoded = preferences.getString(storageKey(configurationId, name), null) ?: return null
        return try {
            val parts = encoded.split('.', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // A restored preference cannot be decrypted if its non-exportable Keystore key is gone.
            null
        }
    }

    override fun write(configurationId: String, name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encoded = listOf(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        ).joinToString(".")
        preferences.edit { putString(storageKey(configurationId, name), encoded) }
    }

    override fun remove(configurationId: String, name: String) {
        preferences.edit { remove(storageKey(configurationId, name)) }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun storageKey(configurationId: String, name: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$configurationId\u0000$name".toByteArray(Charsets.UTF_8))
        .let { digest -> Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "json_app_creator_configuration_keys"
        const val PREFERENCES_NAME = "encrypted_configuration_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
