package co.nehood.app.admin

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.Session
import co.nehood.app.core.fill
import co.nehood.app.core.host
import co.nehood.app.core.toShortCop
import co.nehood.app.data.Order
import co.nehood.app.data.OrderRepository
import co.nehood.app.data.OrderStatus
import co.nehood.app.data.PaymentStatus
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.data.Role
import co.nehood.app.data.SalesStats
import co.nehood.app.data.User
import co.nehood.app.data.UserRepository
import co.nehood.app.databinding.FragmentAdminDashboardBinding
import co.nehood.app.seller.OrderAdapter

/** Administrador · Dashboard con indicadores que se actualizan solos con cada cambio en la base de datos. */
class AdminDashboardFragment :
    BindingFragment<FragmentAdminDashboardBinding>(FragmentAdminDashboardBinding::inflate) {

    private val recentAdapter = OrderAdapter(showAction = false)
    private var orders: List<Order> = emptyList()
    private var users: List<User> = emptyList()
    private var products: List<Product> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = Session.user?.name?.substringBefore(" ").orEmpty()
        b.tvGreeting.text = getString(R.string.admin_greeting, name)

        b.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        b.rvRecent.adapter = recentAdapter
        b.btnNewUser.setOnClickListener { startActivity(UserFormActivity.intent(requireContext(), "")) }
        b.btnReport.setOnClickListener { host()?.selectTab(R.id.nav_report) }
        render()

        keep(OrderRepository.listenAll({ orders = it; if (hasView) render() }, ::showError))
        keep(UserRepository.listenAll({ users = it; if (hasView) render() }, ::showError))
        keep(ProductRepository.listen(false, { products = it; if (hasView) render() }, ::showError))
    }

    private fun render() {
        val pending = orders.count { it.paymentStatus == PaymentStatus.APPROVED && it.status == OrderStatus.PENDING }
        val sellers = users.count { it.role == Role.SELLER }
        val published = products.count { it.published }
        val approved = SalesStats.approved(orders).size

        b.kpiSales.fill(
            getString(R.string.kpi_sales_month),
            SalesStats.salesThisMonth(orders).toShortCop(),
            resources.getQuantityString(R.plurals.kpi_paid_orders, approved, approved)
        )
        b.kpiOrders.fill(getString(R.string.kpi_orders), orders.size.toString(), getString(R.string.kpi_pending, pending))
        b.kpiUsers.fill(getString(R.string.kpi_users), users.size.toString(), getString(R.string.kpi_sellers, sellers))
        b.kpiLockers.fill(getString(R.string.kpi_products), products.size.toString(), getString(R.string.kpi_published, published))
        b.chart.setData(SalesStats.lastMonths(orders))
        recentAdapter.submit(orders.take(3))
    }
}
