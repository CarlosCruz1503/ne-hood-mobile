package co.nehood.app

import android.app.Application
import co.nehood.app.core.Session
import co.nehood.app.data.Database

/** Clase de aplicación: abre la base de datos SQLite y la sesión una sola vez al iniciar la app. */
class NeHoodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Database.init(this)
        Session.init(this)
    }
}
