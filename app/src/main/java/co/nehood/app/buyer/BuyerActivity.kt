package co.nehood.app.buyer

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import co.nehood.app.R
import co.nehood.app.common.ProfileFragment
import co.nehood.app.core.RoleHostActivity
import co.nehood.app.core.Session
import co.nehood.app.data.CartRepository
import co.nehood.app.data.Role
import co.nehood.app.data.Registration

/** Rol Comprador: Inicio · Tienda · Carrito · Perfil (y "Mis pedidos" en el menú lateral). */
class BuyerActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.menu_bottom_buyer
    override val expectedRole = Role.BUYER

    private var cartListener: Registration? = null

    override fun fragmentFor(itemId: Int): Fragment = when (itemId) {
        R.id.nav_catalog -> CatalogFragment()
        R.id.nav_cart -> CartFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> HomeFragment()
    }

    /** Escucha el carrito para mostrar el número de prendas sobre la pestaña. */
    override fun onStart() {
        super.onStart()
        val uid = Session.uid ?: return
        if (!isBindingReady()) return
        cartListener = CartRepository.listen(uid, onChange = { items ->
            val count = items.sumOf { it.qty }
            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_cart)
            badge.backgroundColor = ContextCompat.getColor(this, R.color.nh_orange)
            badge.isVisible = count > 0
            badge.number = count
        }, onError = { })
    }

    override fun onStop() {
        cartListener?.remove()
        cartListener = null
        super.onStop()
    }
}
