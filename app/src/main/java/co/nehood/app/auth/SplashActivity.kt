package co.nehood.app.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.nehood.app.R
import co.nehood.app.core.Session
import co.nehood.app.data.AuthRepository
import co.nehood.app.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen. Mientras se ve la animación revisa si quedó una sesión abierta:
 * si la hay (y la cuenta sigue activa) entra directo a la pantalla del rol; si no, va al login.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.logoGroup.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_slide_up))

        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val user = AuthRepository.restoreSession()
            // La splash se muestra al menos 1,2 s para que se vea la marca
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_SPLASH_MS) delay(MIN_SPLASH_MS - elapsed)

            if (user != null) startActivity(Session.homeIntent(this@SplashActivity, user.role))
            else startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish()
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 1200L
    }
}
