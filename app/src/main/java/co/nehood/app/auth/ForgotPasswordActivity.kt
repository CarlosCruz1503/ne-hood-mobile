package co.nehood.app.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.BiometricStore
import co.nehood.app.core.launchSafe
import co.nehood.app.data.AuthRepository
import co.nehood.app.databinding.ActivityForgotPasswordBinding

/**
 * Recuperación de contraseña (RF-07).
 * La persona confirma su identidad con el correo y el celular registrados y define una contraseña nueva.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var b: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnBackLogin.setOnClickListener { finish() }
        b.btnSend.setOnClickListener { reset() }
    }

    private fun reset() {
        listOf(b.tilEmail, b.tilPhone, b.tilPassword, b.tilConfirm).forEach { it.error = null }
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val phone = b.etPhone.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()

        var valid = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = getString(R.string.err_email); valid = false }
        if (phone.length != 10) { b.tilPhone.error = getString(R.string.err_phone); valid = false }
        if (password.length < 8) { b.tilPassword.error = getString(R.string.err_password_length); valid = false }
        if (confirm != password) { b.tilConfirm.error = getString(R.string.err_password_match); valid = false }
        if (!valid) return

        b.btnSend.isEnabled = false
        b.progress.isVisible = true
        launchSafe(b.root) {
            try {
                AuthRepository.resetPassword(email, phone, password)
                // La huella guardaba la contraseña anterior: se desactiva para esa cuenta
                val biometric = BiometricStore(this@ForgotPasswordActivity)
                if (biometric.isEnrolledFor(email)) biometric.clear()
                b.groupForm.isVisible = false
                b.cardSuccess.isVisible = true
                b.tvSuccessBody.text = getString(R.string.forgot_success_body, email)
            } finally {
                b.btnSend.isEnabled = true
                b.progress.isVisible = false
            }
        }
    }
}
