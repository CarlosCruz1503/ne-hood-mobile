package co.nehood.app.data

import android.content.ContentValues
import co.nehood.app.R
import co.nehood.app.core.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Autenticación local con SQLite (RF-04 a RF-07).
 * La contraseña se verifica contra su hash; la sesión abierta se recuerda en SharedPreferences.
 */
object AuthRepository {

    /** Inicia sesión. Rechaza credenciales incorrectas y cuentas suspendidas (RF-10). */
    suspend fun login(email: String, password: String): User = withContext(Dispatchers.IO) {
        val row = Database.db.rawQuery(
            "SELECT id, password_hash, salt FROM users WHERE email = ?", arrayOf(email.trim())
        ).use { c ->
            if (!c.moveToFirst()) null else Triple(c.long("id"), c.str("password_hash"), c.str("salt"))
        }
        if (row == null || !PasswordHasher.matches(password, row.third, row.second)) {
            throw AppException(R.string.err_credentials)
        }
        val user = UserRepository.getSync(row.first.toString()) ?: throw AppException(R.string.err_credentials)
        if (!user.active) throw AppException(R.string.err_suspended)
        Session.start(user)
        user
    }

    /** Crea la cuenta. El rol siempre inicia como Comprador y nivel Cliente (RF-04). */
    suspend fun register(name: String, email: String, phone: String, password: String): User =
        withContext(Dispatchers.IO) {
            if (UserRepository.emailExists(email)) throw AppException(R.string.err_email_taken)
            val user = User(name = name, email = email.trim(), phone = phone)
            val id = UserRepository.insertSync(user, password)
            val created = user.copy(id = id.toString())
            Session.start(created)
            created
        }

    /**
     * Recuperación de contraseña (RF-07): si el correo y el celular coinciden con una cuenta,
     * se guarda la nueva contraseña. Sin servidor de correo, el celular registrado sirve de verificación.
     */
    suspend fun resetPassword(email: String, phone: String, newPassword: String) = withContext(Dispatchers.IO) {
        val salt = PasswordHasher.newSalt()
        val updated = Database.db.update(
            "users",
            ContentValues().apply {
                put("password_hash", PasswordHasher.hash(newPassword, salt))
                put("salt", salt)
            },
            "email = ? AND phone = ?", arrayOf(email.trim(), phone.trim())
        )
        if (updated == 0) throw AppException(R.string.err_reset_mismatch)
    }

    /** Si quedó una sesión abierta, recupera el usuario (si sigue existiendo y activo). */
    suspend fun restoreSession(): User? {
        val id = Session.savedUserId() ?: return null
        val user = withContext(Dispatchers.IO) { UserRepository.getSync(id) }
        if (user == null || !user.active) {
            Session.clear()
            return null
        }
        Session.start(user)
        return user
    }

    /** Recarga el usuario actual (por ejemplo, después de una compra que sube su nivel). */
    suspend fun refreshProfile(): User? {
        val id = Session.uid ?: return null
        return withContext(Dispatchers.IO) { UserRepository.getSync(id) }?.also { Session.user = it }
    }

    fun logout() = Session.clear()
}
