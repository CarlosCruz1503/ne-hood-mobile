package co.nehood.app.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Las contraseñas nunca se guardan en texto plano (RNF-05).
 * Se guarda un hash PBKDF2-HMAC-SHA256 con una sal aleatoria distinta por usuario.
 */
object PasswordHasher {

    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /** Compara en tiempo constante para no dar pistas por el tiempo de respuesta. */
    fun matches(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).toByteArray(), expectedHash.toByteArray())
}
