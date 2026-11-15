package co.nehood.app.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.Session
import co.nehood.app.core.launchSafe
import co.nehood.app.data.Level
import co.nehood.app.data.Role
import co.nehood.app.data.User
import co.nehood.app.data.UserRepository
import co.nehood.app.databinding.ActivityUserFormBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Administrador · Crear y editar usuario, rol, nivel y estado (RF-08 a RF-10).
 * Al crear se define una contraseña inicial; el correo no se cambia después
 * porque es el usuario de inicio de sesión.
 */
class UserFormActivity : AppCompatActivity() {

    private lateinit var b: ActivityUserFormBinding
    private var user: User? = null
    private val levels by lazy { resources.getStringArray(R.array.levels) }
    private val isNew get() = intent.getStringExtra(EXTRA_ID).isNullOrBlank()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.topBar.toolbar.setTitle(if (isNew) R.string.user_form_new else R.string.user_form_edit)
        b.topBar.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        b.topBar.toolbar.setNavigationContentDescription(R.string.cd_back)
        b.topBar.toolbar.setNavigationOnClickListener { finish() }

        b.actLevel.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, levels))
        b.tilPassword.isVisible = isNew
        b.btnSave.setOnClickListener { save() }
        b.btnDelete.setOnClickListener { confirmDelete() }

        if (isNew) {
            b.actLevel.setText(levels[Level.CLIENTE.ordinal], false)
        } else {
            setLoading(true)
            launchSafe(b.root) {
                try {
                    val loaded = UserRepository.get(intent.getStringExtra(EXTRA_ID).orEmpty())
                    if (loaded == null) finish() else fillForm(loaded)
                } finally {
                    setLoading(false)
                }
            }
        }
    }

    private fun fillForm(u: User) {
        user = u
        b.etName.setText(u.name)
        b.etEmail.setText(u.email)
        b.tilEmail.isEnabled = false
        b.etPhone.setText(u.phone)
        b.actLevel.setText(levels[u.level.ordinal], false)
        b.switchActive.isChecked = u.active
        when (u.role) {
            Role.BUYER -> b.chipRoleBuyer.isChecked = true
            Role.SELLER -> b.chipRoleSeller.isChecked = true
            Role.ADMIN -> b.chipRoleAdmin.isChecked = true
        }
        u.lastChange?.let {
            b.tvAudit.isVisible = true
            b.tvAudit.text = getString(R.string.user_audit, it)
        }
        b.btnDelete.isVisible = u.id != Session.uid
    }

    private fun selectedRole() = when (b.chipsRole.checkedChipId) {
        R.id.chip_role_seller -> Role.SELLER
        R.id.chip_role_admin -> Role.ADMIN
        else -> Role.BUYER
    }

    /** RF-09: texto de auditoría con quién y cuándo cambió el rol o el estado. */
    private fun auditText(): String {
        val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale("es", "CO")).format(Date())
        return getString(R.string.user_audit_value, date, Session.user?.name ?: getString(R.string.role_admin))
    }

    private fun save() {
        listOf(b.tilName, b.tilEmail, b.tilPhone, b.tilPassword).forEach { it.error = null }
        val name = b.etName.text?.toString()?.trim().orEmpty()
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val phone = b.etPhone.text?.toString()?.trim().orEmpty()
        val password = b.etPassword.text?.toString().orEmpty()

        var valid = true
        if (name.length < 3) { b.tilName.error = getString(R.string.err_name); valid = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = getString(R.string.err_email); valid = false }
        if (phone.length != 10) { b.tilPhone.error = getString(R.string.err_phone); valid = false }
        if (isNew && password.length < 8) { b.tilPassword.error = getString(R.string.err_password_length); valid = false }
        if (!valid) return

        val level = Level.values()[levels.indexOf(b.actLevel.text.toString()).coerceAtLeast(0)]
        val role = selectedRole()
        val active = b.switchActive.isChecked

        setLoading(true)
        launchSafe(b.root) {
            try {
                val current = user
                if (current == null) {
                    UserRepository.createByAdmin(
                        User(name = name, email = email, phone = phone, role = role, level = level,
                            active = active, lastChange = auditText()),
                        password
                    )
                    toast(R.string.msg_user_created, name)
                } else {
                    val changedAccess = current.role != role || current.active != active
                    UserRepository.update(
                        current.copy(
                            name = name, phone = phone, role = role, level = level, active = active,
                            lastChange = if (changedAccess) auditText() else current.lastChange
                        )
                    )
                    toast(R.string.msg_user_updated, name)
                }
                finish()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun confirmDelete() {
        val u = user ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_user_title, u.name))
            .setMessage(R.string.delete_user_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                launchSafe(b.root) {
                    UserRepository.delete(u.id)
                    finish()
                }
            }
            .show()
    }

    private fun toast(message: Int, name: String) =
        Toast.makeText(this, getString(message, name), Toast.LENGTH_SHORT).show()

    private fun setLoading(loading: Boolean) {
        b.progressSave.isVisible = loading
        b.btnSave.isEnabled = !loading
        b.btnDelete.isEnabled = !loading
    }

    companion object {
        private const val EXTRA_ID = "user_id"

        /** userId vacío = usuario nuevo. */
        fun intent(context: Context, userId: String) =
            Intent(context, UserFormActivity::class.java).putExtra(EXTRA_ID, userId)
    }
}
