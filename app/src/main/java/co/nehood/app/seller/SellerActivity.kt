package co.nehood.app.seller

import androidx.fragment.app.Fragment
import co.nehood.app.R
import co.nehood.app.common.ProfileFragment
import co.nehood.app.core.RoleHostActivity
import co.nehood.app.data.Role

/** Rol Vendedor: Productos · Pedidos · Perfil */
class SellerActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.menu_bottom_seller
    override val expectedRole = Role.SELLER

    override fun fragmentFor(itemId: Int): Fragment = when (itemId) {
        R.id.nav_seller_orders -> SellerOrdersFragment.newInstance(buyerMode = false)
        R.id.nav_profile -> ProfileFragment()
        else -> ManageProductsFragment.newInstance(admin = false)
    }
}
