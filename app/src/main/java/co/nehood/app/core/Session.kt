package co.nehood.app.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import co.nehood.app.admin.AdminActivity
import co.nehood.app.auth.LoginActivity
import co.nehood.app.buyer.BuyerActivity
import co.nehood.app.data.Role
import co.nehood.app.data.User
import co.nehood.app.seller.SellerActivity

/**
 * Sesión del usuario.
 * El usuario actual vive en memoria y su id se guarda en SharedPreferences,
 * así la sesión sigue abierta aunque se cierre la app (SplashActivity la restaura).
 */
object Session {

    private const val PREFS = "nehood_session"
    private const val KEY_USER_ID = "user_id"
    private lateinit var prefs: SharedPreferences

    var user: User? = null

    val uid: String? get() = user?.id

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun start(user: User) {
        this.user = user
        prefs.edit { putString(KEY_USER_ID, user.id) }
    }

    fun savedUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun clear() {
        user = null
        prefs.edit { remove(KEY_USER_ID) }
    }

    /** Pantalla principal según el rol (Figura 1 del documento técnico). */
    fun homeIntent(context: Context, role: Role): Intent {
        val target = when (role) {
            Role.BUYER -> BuyerActivity::class.java
            Role.SELLER -> SellerActivity::class.java
            Role.ADMIN -> AdminActivity::class.java
        }
        return Intent(context, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    /** Cierra sesión y vuelve al login. Las credenciales de la huella se conservan. */
    fun logoutAndGoToLogin(activity: Activity) {
        clear()
        activity.startActivity(
            Intent(activity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
