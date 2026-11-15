package co.nehood.app.common

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.BiometricStore
import co.nehood.app.core.Session
import co.nehood.app.core.fill
import co.nehood.app.core.host
import co.nehood.app.core.initials
import co.nehood.app.core.setBadge
import co.nehood.app.core.toShortCop
import co.nehood.app.data.AuthRepository
import co.nehood.app.data.CartRepository
import co.nehood.app.data.Level
import co.nehood.app.data.OrderRepository
import co.nehood.app.data.OrderStatus
import co.nehood.app.data.ProductRepository
import co.nehood.app.data.Role
import co.nehood.app.data.SalesStats
import co.nehood.app.data.User
import co.nehood.app.data.UserRepository
import co.nehood.app.databinding.FragmentProfileBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Perfil (RF-14, RF-27, RF-28). El mismo fragment sirve como "Perfil de usuario" del comprador
 * y "Perfil de vendedor": cambia los indicadores según el rol.
 */
class ProfileFragment : BindingFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {

    private lateinit var biometric: BiometricStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        biometric = BiometricStore(requireContext())
        val user = Session.user ?: return

        renderHeader(user)
        b.cardLevel.isVisible = user.role == Role.BUYER
        b.etName.setText(user.name)
        b.etPhone.setText(user.phone)
        b.btnSave.setOnClickListener { save(user) }
        b.btnLogout.setOnClickListener { host()?.confirmLogout() }
        setupBiometricSwitch(user)

        // Recarga el perfil para ver compras y nivel actualizados, luego los indicadores
        launchSafe {
            val fresh = AuthRepository.refreshProfile() ?: user
            if (!hasView) return@launchSafe
            renderHeader(fresh)
            when (fresh.role) {
                Role.BUYER -> renderBuyer(fresh)
                Role.SELLER -> renderSeller()
                Role.ADMIN -> renderAdmin()
            }
        }
    }

    private fun renderHeader(user: User) {
        val roleColor = ContextCompat.getColor(requireContext(), user.role.color)
        b.tvAvatar.text = user.name.initials()
        ViewCompat.setBackgroundTintList(b.tvAvatar, ColorStateList.valueOf(roleColor))
        b.tvName.text = user.name
        b.tvEmail.text = user.email
        b.tvRole.setBadge(getString(user.role.label), user.role.color)
    }

    /** Nivel de comunidad: Cliente → Parche (3 compras) → Familia (8 compras). */
    private suspend fun renderBuyer(user: User) {
        b.tvLevel.setText(user.level.label)
        val target = if (user.level == Level.CLIENTE) 3 else 8
        b.progressLevel.max = target
        b.progressLevel.progress = user.purchases.coerceAtMost(target)
        b.tvLevelHint.text = if (user.level == Level.FAMILIA) {
            getString(R.string.level_max)
        } else {
            val next = Level.values()[user.level.ordinal + 1]
            val missing = (target - user.purchases).coerceAtLeast(1)
            resources.getQuantityString(R.plurals.level_next, missing, missing, getString(next.label))
        }
        val orders = OrderRepository.countForUser(user.id)
        val inCart = CartRepository.getAll(user.id).sumOf { it.qty }
        if (!hasView) return
        b.statOne.fill(getString(R.string.stat_purchases), user.purchases.toString())
        b.statTwo.fill(getString(R.string.stat_orders), orders.toString())
        b.statThree.fill(getString(R.string.stat_cart), inCart.toString())
    }

    private suspend fun renderSeller() {
        val products = ProductRepository.count()
        val orders = OrderRepository.getAll()
        if (!hasView) return
        b.statOne.fill(getString(R.string.stat_products), products.toString())
        b.statTwo.fill(getString(R.string.stat_pending), orders.count { it.status == OrderStatus.PENDING }.toString())
        b.statThree.fill(getString(R.string.stat_sales_month), SalesStats.salesThisMonth(orders).toShortCop())
    }

    private suspend fun renderAdmin() {
        val users = UserRepository.count()
        val products = ProductRepository.count()
        val orders = OrderRepository.getAll().size
        if (!hasView) return
        b.statOne.fill(getString(R.string.kpi_users), users.toString())
        b.statTwo.fill(getString(R.string.stat_products), products.toString())
        b.statThree.fill(getString(R.string.kpi_orders), orders.toString())
    }

    /**
     * La huella se activa al iniciar sesión con contraseña (hace falta para cifrarla).
     * Desde aquí solo se puede desactivar.
     */
    private fun setupBiometricSwitch(user: User) {
        b.switchBiometric.isChecked = biometric.isEnrolledFor(user.email)
        b.switchBiometric.setOnCheckedChangeListener { button, checked ->
            if (checked) {
                // Se devuelve a apagado sin volver a disparar este listener
                button.setOnCheckedChangeListener(null)
                button.isChecked = false
                setupBiometricSwitch(user)
                Snackbar.make(b.root, R.string.msg_biometric_how, Snackbar.LENGTH_LONG).show()
            } else {
                biometric.clear()
                Snackbar.make(b.root, R.string.msg_biometric_off, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /** Actualiza nombre y celular (RF-14). */
    private fun save(user: User) {
        b.tilName.error = null
        b.tilPhone.error = null
        val name = b.etName.text?.toString()?.trim().orEmpty()
        val phone = b.etPhone.text?.toString()?.trim().orEmpty()
        var valid = true
        if (name.length < 3) { b.tilName.error = getString(R.string.err_name); valid = false }
        if (phone.length != 10) { b.tilPhone.error = getString(R.string.err_phone); valid = false }
        if (!valid) return

        b.btnSave.isEnabled = false
        launchSafe {
            try {
                UserRepository.updateOwnProfile(user.id, name, phone)
                user.name = name
                user.phone = phone
                Session.user = user
                if (!hasView) return@launchSafe
                renderHeader(user)
                host()?.refreshHeader()
                Snackbar.make(b.root, R.string.msg_profile_saved, Snackbar.LENGTH_SHORT).show()
            } finally {
                if (hasView) b.btnSave.isEnabled = true
            }
        }
    }
}
