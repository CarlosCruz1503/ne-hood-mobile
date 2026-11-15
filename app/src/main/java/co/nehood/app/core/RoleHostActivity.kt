package co.nehood.app.core

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import co.nehood.app.R
import co.nehood.app.auth.SplashActivity
import co.nehood.app.common.ProfileFragment
import co.nehood.app.seller.SellerOrdersFragment
import co.nehood.app.data.Role
import co.nehood.app.data.User
import co.nehood.app.databinding.ActivityRoleHostBinding
import co.nehood.app.databinding.NavHeaderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Actividad base de los tres roles. Arma el menú de navegación transversal:
 * Navigation Drawer (perfil, ayuda, cerrar sesión) + Bottom Navigation (pestañas del rol).
 * Cada subclase solo dice qué menú usa y qué fragment muestra cada pestaña.
 */
abstract class RoleHostActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityRoleHostBinding

    protected abstract val bottomMenu: Int
    protected abstract val expectedRole: Role
    protected abstract fun fragmentFor(itemId: Int): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si Android cerró la app, el usuario en memoria se pierde: la splash lo restaura desde la base de datos.
        val user = Session.user
        if (user == null || user.role != expectedRole) {
            startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        binding = ActivityRoleHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topBar.toolbar)

        // Ícono de hamburguesa que abre el drawer
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.topBar.toolbar,
            R.string.drawer_open, R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupHeader(user)
        // "Mis pedidos" en el menú lateral solo aplica al comprador
        binding.navView.menu.findItem(R.id.drawer_orders)?.isVisible = expectedRole == Role.BUYER
        binding.navView.setNavigationItemSelectedListener { item ->
            onDrawerItem(item.itemId)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.bottomNav.inflateMenu(bottomMenu)
        binding.bottomNav.setOnItemSelectedListener { item ->
            showFragment(fragmentFor(item.itemId), item.title)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            val requested = intent.getIntExtra(EXTRA_TAB, 0)
            if (requested != 0 && binding.bottomNav.menu.findItem(requested) != null) {
                selectTab(requested)
            } else {
                val first = binding.bottomNav.menu.getItem(0)
                showFragment(fragmentFor(first.itemId), first.title)
            }
        } else {
            supportActionBar?.title = binding.bottomNav.menu.findItem(binding.bottomNav.selectedItemId)?.title
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tab = intent.getIntExtra(EXTRA_TAB, 0)
        if (tab != 0 && ::binding.isInitialized) selectTab(tab)
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) refreshBadges()
    }

    /** Cambia de pestaña desde un fragment (por ejemplo, "Ver todo" lleva al catálogo). */
    fun refreshHeader() {
        Session.user?.let { setupHeader(it) }
    }

    fun selectTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    /** Las subclases lo usan para mostrar contadores (ej. productos en el carrito). */
    open fun refreshBadges() {}

    /** true cuando la pantalla terminó de armarse (no se redirigió a la splash). */
    protected fun isBindingReady() = ::binding.isInitialized

    private fun showFragment(fragment: Fragment, title: CharSequence?) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()
        supportActionBar?.title = title
    }

    private fun setupHeader(user: User) {
        val header = NavHeaderBinding.bind(binding.navView.getHeaderView(0))
        header.tvAvatar.text = user.name.initials()
        ViewCompat.setBackgroundTintList(
            header.tvAvatar,
            ColorStateList.valueOf(ContextCompat.getColor(this, user.role.color))
        )
        header.tvName.text = user.name
        header.tvEmail.text = user.email
        header.tvRole.setBadge(getString(user.role.label), R.color.nh_white)
    }

    private fun onDrawerItem(itemId: Int) {
        when (itemId) {
            R.id.drawer_profile -> {
                if (binding.bottomNav.menu.findItem(R.id.nav_profile) != null) {
                    selectTab(R.id.nav_profile)
                } else {
                    showFragment(ProfileFragment(), getString(R.string.tab_profile))
                }
            }
            R.id.drawer_orders ->
                showFragment(SellerOrdersFragment.newInstance(buyerMode = true), getString(R.string.drawer_orders))
            R.id.drawer_help -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_body)
                .setPositiveButton(R.string.action_ok, null)
                .show()
            R.id.drawer_logout -> confirmLogout()
        }
    }

    fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.drawer_logout) { _, _ -> Session.logoutAndGoToLogin(this) }
            .show()
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
    }
}
