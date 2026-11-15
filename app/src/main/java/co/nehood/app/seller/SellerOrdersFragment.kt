package co.nehood.app.seller

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.Session
import co.nehood.app.data.Order
import co.nehood.app.data.OrderRepository
import co.nehood.app.data.OrderStatus
import co.nehood.app.databinding.FragmentSellerOrdersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Historial de pedidos en tiempo real.
 * - Vendedor: todos los pedidos y cambio de estado de despacho (RF-26).
 * - Comprador (buyerMode): solo sus pedidos, sin acciones ("Mis pedidos", RF-25).
 */
class SellerOrdersFragment :
    BindingFragment<FragmentSellerOrdersBinding>(FragmentSellerOrdersBinding::inflate) {

    private val buyerMode get() = arguments?.getBoolean(ARG_BUYER) == true
    private lateinit var adapter: OrderAdapter
    private var orders: List<Order> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = OrderAdapter(showAction = !buyerMode) { changeStatus(it) }
        b.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        b.rvOrders.adapter = adapter
        b.chipsStatus.setOnCheckedStateChangeListener { _, _ -> render() }
        b.tvSubtitle.setText(if (buyerMode) R.string.orders_subtitle_buyer else R.string.orders_subtitle)

        val onChange: (List<Order>) -> Unit = {
            orders = it
            if (hasView) render()
        }
        val uid = Session.uid ?: return
        keep(
            if (buyerMode) OrderRepository.listenForUser(uid, onChange, ::showError)
            else OrderRepository.listenAll(onChange, ::showError)
        )
    }

    private fun render() {
        val status = when (b.chipsStatus.checkedChipId) {
            R.id.chip_pending -> OrderStatus.PENDING
            R.id.chip_confirmed -> OrderStatus.CONFIRMED
            R.id.chip_shipped -> OrderStatus.SHIPPED
            R.id.chip_delivered -> OrderStatus.DELIVERED
            else -> null
        }
        val list = orders.filter { status == null || it.status == status }
        adapter.submit(list)
        b.tvCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        b.tvEmpty.isVisible = list.isEmpty()
    }

    /** El vendedor elige el nuevo estado; se guarda en la base de datos y la lista se actualiza sola. */
    private fun changeStatus(order: Order) {
        val statuses = OrderStatus.values()
        val labels = statuses.map { getString(it.label) }.toTypedArray()
        var selected = order.status.ordinal
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.orders_change_title, order.shortId))
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                launchSafe {
                    OrderRepository.updateStatus(order.id, statuses[selected])
                    if (hasView) Snackbar.make(
                        b.root, getString(R.string.msg_status_changed, order.shortId, labels[selected]),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    companion object {
        private const val ARG_BUYER = "buyer"
        fun newInstance(buyerMode: Boolean) = SellerOrdersFragment().apply {
            arguments = bundleOf(ARG_BUYER to buyerMode)
        }
    }
}
