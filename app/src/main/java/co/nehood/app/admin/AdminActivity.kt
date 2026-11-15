package co.nehood.app.admin

import androidx.fragment.app.Fragment
import co.nehood.app.R
import co.nehood.app.core.RoleHostActivity
import co.nehood.app.data.Role
import co.nehood.app.seller.ManageProductsFragment

/** Rol Administrador: Panel · Usuarios · Productos · Ventas (el perfil está en el drawer) */
class AdminActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.menu_bottom_admin
    override val expectedRole = Role.ADMIN

    override fun fragmentFor(itemId: Int): Fragment = when (itemId) {
        R.id.nav_users -> AdminUsersFragment()
        R.id.nav_admin_products -> ManageProductsFragment.newInstance(admin = true)
        R.id.nav_report -> SalesReportFragment()
        else -> AdminDashboardFragment()
    }
}
