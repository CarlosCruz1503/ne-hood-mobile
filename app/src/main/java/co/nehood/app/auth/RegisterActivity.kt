package co.nehood.app.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.Session
import co.nehood.app.core.launchSafe
import co.nehood.app.data.AuthRepository
import co.nehood.app.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar

/** Registro de cuenta nueva (RF-04). Crea el usuario en la base de datos con la contraseña cifrada. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnRegister.setOnClickListener { register() }
    }

    private fun register() {
        listOf(b.tilName, b.tilEmail, b.tilPhone, b.tilPassword, b.tilConfirm).forEach { it.error = null }
        val name = b.etName.text?.toString()?.trim().orEmpty()
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val phone = b.etPhone.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()

        var valid = true
        if (name.length < 3) { b.tilName.error = getString(R.string.err_name); valid = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = getString(R.string.err_email); valid = false }
        if (phone.length != 10) { b.tilPhone.error = getString(R.string.err_phone); valid = false }
        if (password.length < 8) { b.tilPassword.error = getString(R.string.err_password_length); valid = false }
        if (confirm != password) { b.tilConfirm.error = getString(R.string.err_password_match); valid = false }
        if (!valid) return
        // Ley 1581 de 2012: sin autorización de datos no se crea la cuenta
        if (!b.cbData.isChecked) {
            Snackbar.make(b.root, R.string.err_data_consent, Snackbar.LENGTH_LONG).show()
            return
        }

        b.progress.isVisible = true
        b.btnRegister.isEnabled = false
        launchSafe(b.root) {
            try {
                val user = AuthRepository.register(name, email, phone, password)
                startActivity(Session.homeIntent(this@RegisterActivity, user.role))
                finish()
            } finally {
                b.progress.isVisible = false
                b.btnRegister.isEnabled = true
            }
        }
    }
}
