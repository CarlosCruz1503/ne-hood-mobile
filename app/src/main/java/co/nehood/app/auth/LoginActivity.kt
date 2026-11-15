package co.nehood.app.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.BiometricStore
import co.nehood.app.core.Session
import co.nehood.app.core.launchSafe
import co.nehood.app.data.AuthRepository
import co.nehood.app.data.User
import co.nehood.app.databinding.ActivityLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import co.nehood.app.data.AppException
import javax.crypto.Cipher

/**
 * Inicio de sesión (RF-05) con usuario y contraseña de la base de datos y con huella (RF-06).
 *
 * Flujo de la huella:
 * 1. Después de entrar con contraseña, se ofrece activar la huella.
 * 2. Si acepta, BiometricPrompt desbloquea una llave del Keystore y se guarda la contraseña cifrada.
 * 3. La próxima vez, la huella desbloquea la llave, se descifra la contraseña y se inicia sesión.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private lateinit var biometric: BiometricStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        biometric = BiometricStore(this)

        b.etEmail.setText(biometric.email.orEmpty())
        b.btnLogin.setOnClickListener { attemptLogin() }
        b.btnForgot.setOnClickListener { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
        b.btnRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.btnGoogle.setOnClickListener {
            Snackbar.make(b.root, R.string.msg_google_pending, Snackbar.LENGTH_LONG).show()
        }
        b.btnBiometric.setOnClickListener { loginWithBiometric() }
    }

    override fun onResume() {
        super.onResume()
        refreshBiometricState()
    }

    /** Valida el formulario y entra con correo y contraseña. */
    private fun attemptLogin() {
        b.tilEmail.error = null
        b.tilPassword.error = null
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()

        var valid = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.error = getString(R.string.err_email)
            valid = false
        }
        if (password.length < 8) {
            b.tilPassword.error = getString(R.string.err_password_length)
            valid = false
        }
        if (!valid) return

        setLoading(true)
        launchSafe(b.root) {
            try {
                val user = AuthRepository.login(email, password)
                if (BiometricStore.isAvailable(this@LoginActivity) && !biometric.isEnrolledFor(email)) {
                    offerBiometric(user, password)
                } else {
                    goHome(user)
                }
            } finally {
                setLoading(false)
            }
        }
    }

    /** Después del primer ingreso con contraseña se ofrece activar la huella. */
    private fun offerBiometric(user: User, password: String) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_fingerprint)
            .setTitle(R.string.bio_offer_title)
            .setMessage(R.string.bio_offer_body)
            .setCancelable(false)
            .setNegativeButton(R.string.action_not_now) { _, _ -> goHome(user) }
            .setPositiveButton(R.string.action_activate) { _, _ ->
                val cipher = runCatching { biometric.encryptCipher() }.getOrNull()
                if (cipher == null) {
                    goHome(user)
                    return@setPositiveButton
                }
                authenticate(cipher, getString(R.string.bio_enroll_subtitle),
                    onSuccess = { unlocked ->
                        biometric.save(user.email, password, unlocked)
                        goHome(user)
                    },
                    onCancel = { goHome(user) }
                )
            }
            .show()
    }

    /** Entra con la huella: descifra la contraseña guardada e inicia sesión en Firebase. */
    private fun loginWithBiometric() {
        val email = biometric.email
        val cipher = biometric.decryptCipher()
        if (email == null || cipher == null) {
            Snackbar.make(b.root, R.string.bio_invalidated, Snackbar.LENGTH_LONG).show()
            refreshBiometricState()
            return
        }
        authenticate(cipher, getString(R.string.bio_subtitle), onSuccess = { unlocked ->
            val password = biometric.readPassword(unlocked)
            setLoading(true)
            launchSafe(b.root) {
                try {
                    goHome(AuthRepository.login(email, password))
                } catch (e: AppException) {
                    if (e.messageRes != R.string.err_credentials) throw e
                    // La contraseña cambió (por ejemplo, después de recuperarla): hay que activarla otra vez
                    biometric.clear()
                    refreshBiometricState()
                    Snackbar.make(b.root, R.string.bio_password_changed, Snackbar.LENGTH_LONG).show()
                } finally {
                    setLoading(false)
                }
            }
        })
    }

    /** Muestra el diálogo de huella del sistema con un CryptoObject (biometría fuerte). */
    private fun authenticate(
        cipher: Cipher,
        subtitle: String,
        onSuccess: (Cipher) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val unlocked = result.cryptoObject?.cipher
                if (unlocked != null) onSuccess(unlocked) else onCancel()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    Snackbar.make(b.root, errString, Snackbar.LENGTH_LONG).show()
                }
                onCancel()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.bio_title))
            .setSubtitle(subtitle)
            .setNegativeButtonText(getString(R.string.bio_use_password))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun refreshBiometricState() {
        val available = BiometricStore.isAvailable(this)
        val enrolled = biometric.hasCredentials
        b.btnBiometric.isEnabled = available && enrolled
        b.tvBiometricHint.text = when {
            !available -> getString(R.string.bio_unavailable)
            !enrolled -> getString(R.string.bio_not_enrolled)
            else -> getString(R.string.bio_ready, biometric.email)
        }
    }

    private fun setLoading(loading: Boolean) {
        b.progress.isVisible = loading
        b.btnLogin.isEnabled = !loading
        b.btnBiometric.isEnabled = !loading && biometric.hasCredentials && BiometricStore.isAvailable(this)
    }

    private fun goHome(user: User) {
        startActivity(Session.homeIntent(this, user.role))
        finish()
    }
}
