package co.nehood.app.core

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ingreso con huella (RF-06).
 *
 * La contraseña se cifra con una llave AES que vive en el Android Keystore y que solo se puede
 * usar después de poner una huella válida (setUserAuthenticationRequired). La huella nunca sale
 * del celular; solo desbloquea la llave para descifrar la contraseña.
 * Si el usuario registra una huella nueva en el celular, la llave se invalida y hay que volver a activarla.
 */
class BiometricStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    val hasCredentials: Boolean get() = email != null && prefs.contains(KEY_CIPHERTEXT)

    fun isEnrolledFor(email: String) = hasCredentials && this.email.equals(email, ignoreCase = true)

    /** Cipher listo para cifrar; se entrega a BiometricPrompt para que lo desbloquee la huella. */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        return cipher
    }

    /**
     * Cipher listo para descifrar con el vector guardado.
     * Devuelve null si no hay credenciales o si la llave se invalidó por una huella nueva.
     */
    fun decryptCipher(): Cipher? {
        val iv = prefs.getString(KEY_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            null
        }
    }

    /** Guarda la contraseña cifrada con el cipher que la huella ya desbloqueó. */
    fun save(email: String, password: String, unlockedCipher: Cipher) {
        val encrypted = unlockedCipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(KEY_EMAIL, email)
            putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            putString(KEY_IV, Base64.encodeToString(unlockedCipher.iv, Base64.NO_WRAP))
        }
    }

    /** Descifra la contraseña con el cipher que la huella ya desbloqueó. */
    fun readPassword(unlockedCipher: Cipher): String {
        val encrypted = Base64.decode(prefs.getString(KEY_CIPHERTEXT, "").orEmpty(), Base64.NO_WRAP)
        return String(unlockedCipher.doFinal(encrypted), Charsets.UTF_8)
    }

    /** Desactiva la huella: borra los datos cifrados y la llave. */
    fun clear() {
        prefs.edit { clear() }
        deleteKey()
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)          // solo se usa después de la huella
            .setInvalidatedByBiometricEnrollment(true)    // huella nueva = llave inválida
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        private const val PREFS = "nehood_biometric"
        private const val KEY_EMAIL = "email"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_IV = "iv"
        private const val KEY_ALIAS = "nehood_biometric_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** true si el celular tiene sensor y al menos una huella registrada (biometría fuerte). */
        fun isAvailable(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
