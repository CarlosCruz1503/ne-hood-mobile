package co.nehood.app.admin

import android.os.Bundle
import android.view.View
import android.widget.TextView
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.fill
import co.nehood.app.core.loadProductImage
import co.nehood.app.core.tintSwatch
import co.nehood.app.core.toCop
import co.nehood.app.core.toShortCop
import co.nehood.app.data.Order
import co.nehood.app.data.OrderRepository
import co.nehood.app.data.PaymentStatus
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.data.SalesStats
import co.nehood.app.databinding.FragmentSalesReportBinding
import co.nehood.app.databinding.ItemTopProductBinding
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Administrador · Reporte de ventas por periodo, estado de pago y producto, con datos de la base de datos. */
class SalesReportFragment : BindingFragment<FragmentSalesReportBinding>(FragmentSalesReportBinding::inflate) {

    private var orders: List<Order> = emptyList()
    private var products: List<Product> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.chipsPeriod.setOnCheckedStateChangeListener { _, _ -> renderPeriod() }
        renderPeriod()

        keep(OrderRepository.listenAll({
            orders = it
            if (hasView) { renderPeriod(); renderPaymentStatus() }
        }, ::showError))
        keep(ProductRepository.listen(false, {
            products = it
            if (hasView) renderTopProducts()
        }, ::showError))
    }

    /** Total, gráfica y ticket promedio del periodo elegido (semana, 6 meses o años). */
    private fun renderPeriod() {
        val (data, label) = when (b.chipsPeriod.checkedChipId) {
            R.id.chip_week -> SalesStats.lastDays(orders) to R.string.report_total_week
            R.id.chip_year -> SalesStats.lastYears(orders) to R.string.report_total_year
            else -> SalesStats.lastMonths(orders) to R.string.report_total_month
        }
        val total = data.sumOf { it.second }
        val last = data.last().second
        val previous = data[data.size - 2].second

        b.tvTotalLabel.setText(label)
        b.tvTotal.text = total.toCop()
        b.tvCompare.text = if (previous > 0) {
            val change = (last - previous) * 100 / previous
            getString(R.string.report_compare, if (change >= 0) "+$change" else "$change")
        } else getString(R.string.report_no_compare)
        b.chart.setData(data)

        // Órdenes pagadas dentro del periodo mostrado
        val paidOrders = when (b.chipsPeriod.checkedChipId) {
            R.id.chip_week -> paidSince(days = 7)
            R.id.chip_year -> SalesStats.approved(orders).size
            else -> paidSince(days = 183)
        }
        b.kpiOrders.fill(getString(R.string.kpi_paid), paidOrders.toString())
        b.kpiTicket.fill(getString(R.string.kpi_ticket), if (paidOrders > 0) (total / paidOrders).toCop() else "$0")
    }

    private fun paidSince(days: Int): Int {
        val from = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        return SalesStats.approved(orders).count { (it.createdAt?.time ?: 0) >= from }
    }

    /** Porcentaje de órdenes aprobadas, pendientes y rechazadas o fallidas. */
    private fun renderPaymentStatus() {
        val total = orders.size.coerceAtLeast(1)
        val approved = orders.count { it.paymentStatus == PaymentStatus.APPROVED }
        val pending = orders.count { it.paymentStatus == PaymentStatus.PENDING }
        val rejected = orders.size - approved - pending
        channel(b.tvChannelApp, b.progressApp, approved * 100 / total)
        channel(b.tvChannelWeb, b.progressWeb, pending * 100 / total)
        channel(b.tvChannelHouse, b.progressHouse, rejected * 100 / total)
    }

    private fun channel(text: TextView, bar: LinearProgressIndicator, percent: Int) {
        text.text = getString(R.string.percent, percent)
        bar.setProgressCompat(percent, true)
    }

    /** Los 5 productos con más unidades vendidas. */
    private fun renderTopProducts() {
        b.containerTop.removeAllViews()
        products.filter { it.unitsSold > 0 }.sortedByDescending { it.unitsSold }.take(5).forEachIndexed { i, p ->
            val row = ItemTopProductBinding.inflate(layoutInflater, b.containerTop, true)
            row.tvRank.text = (i + 1).toString()
            row.swatch.tintSwatch(p.colorHex)
            row.ivImage.loadProductImage(p.imageUrl)
            row.tvName.text = p.name
            row.tvUnits.text = resources.getQuantityString(R.plurals.units_sold, p.unitsSold, p.unitsSold)
            row.tvRevenue.text = (p.unitsSold * p.price).toShortCop()
        }
        if (b.containerTop.childCount == 0) {
            val empty = TextView(requireContext())
            empty.setText(R.string.report_no_sales)
            empty.setPadding(24, 24, 24, 24)
            b.containerTop.addView(empty)
        }
    }
}
