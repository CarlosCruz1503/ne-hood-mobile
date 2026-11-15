package co.nehood.app.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** CRUD de usuarios en SQLite (RF-08 a RF-10, RF-13 a RF-15). */
object UserRepository {

    private const val COLUMNS = "id, name, email, phone, role, level, active, purchases, last_change"

    private fun fromCursor(c: Cursor) = User(
        id = c.long("id").toString(),
        name = c.str("name"),
        email = c.str("email"),
        phone = c.str("phone"),
        role = enumOr(c.str("role"), Role.BUYER),
        level = enumOr(c.str("level"), Level.CLIENTE),
        active = c.bool("active"),
        purchases = c.int("purchases"),
        lastChange = c.strOrNull("last_change")
    )

    fun getSync(id: String): User? =
        Database.db.rawQuery("SELECT $COLUMNS FROM users WHERE id = ?", arrayOf(id)).mapAll(::fromCursor).firstOrNull()

    suspend fun get(id: String): User? = withContext(Dispatchers.IO) { getSync(id) }

    fun emailExists(email: String): Boolean =
        Database.db.rawQuery("SELECT 1 FROM users WHERE email = ?", arrayOf(email.trim())).use { it.moveToFirst() }

    private fun allSync(): List<User> =
        Database.db.rawQuery("SELECT $COLUMNS FROM users ORDER BY name COLLATE NOCASE", null).mapAll(::fromCursor)

    /** Lista de usuarios que se actualiza sola cuando cambia la tabla. */
    fun listenAll(onChange: (List<User>) -> Unit, onError: (Exception) -> Unit): Registration =
        observe(setOf(Table.USERS), ::allSync, onChange, onError)

    suspend fun count(): Int = withContext(Dispatchers.IO) { allSync().size }

    /** Inserta un usuario con su contraseña convertida en hash. Devuelve el id nuevo. */
    fun insertSync(user: User, password: String): Long {
        val salt = PasswordHasher.newSalt()
        val id = Database.db.insertOrThrow("users", null, ContentValues().apply {
            put("name", user.name); put("email", user.email); put("phone", user.phone)
            put("password_hash", PasswordHasher.hash(password, salt)); put("salt", salt)
            put("role", user.role.name); put("level", user.level.name)
            put("active", if (user.active) 1 else 0); put("purchases", user.purchases)
            put("last_change", user.lastChange); put("created_at", System.currentTimeMillis())
        })
        ChangeBus.notify(Table.USERS)
        return id
    }

    /** El administrador crea un usuario con cualquier rol y una contraseña inicial. */
    suspend fun createByAdmin(user: User, password: String): User = withContext(Dispatchers.IO) {
        if (emailExists(user.email)) throw AppException(co.nehood.app.R.string.err_email_taken)
        user.copy(id = insertSync(user, password).toString())
    }

    /** Actualización completa desde el administrador (rol, nivel, estado, datos). No toca la contraseña. */
    suspend fun update(user: User) = withContext(Dispatchers.IO) {
        Database.db.update("users", ContentValues().apply {
            put("name", user.name); put("phone", user.phone)
            put("role", user.role.name); put("level", user.level.name)
            put("active", if (user.active) 1 else 0); put("last_change", user.lastChange)
        }, "id = ?", arrayOf(user.id))
        ChangeBus.notify(Table.USERS)
    }

    /** El propio usuario solo cambia su nombre y su celular (RF-14). */
    suspend fun updateOwnProfile(id: String, name: String, phone: String) = withContext(Dispatchers.IO) {
        Database.db.update("users", ContentValues().apply {
            put("name", name); put("phone", phone)
        }, "id = ?", arrayOf(id))
        ChangeBus.notify(Table.USERS)
    }

    /** Elimina la cuenta y su carrito (ON DELETE CASCADE). Sus órdenes quedan en el historial. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        Database.db.delete("users", "id = ?", arrayOf(id))
        ChangeBus.notify(Table.USERS, Table.CART)
    }
}
