package co.nehood.app.seller

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.nehood.app.R
import co.nehood.app.core.openMap
import co.nehood.app.core.setBadge
import co.nehood.app.core.toCop
import co.nehood.app.data.Order
import co.nehood.app.data.PaymentStatus
import java.text.SimpleDateFormat
import java.util.Locale
import co.nehood.app.databinding.ItemOrderBinding

/**
 * Tarjeta de pedido con estado de pago y de despacho.
 * showAction = true muestra "Cambiar estado" (vendedor). Si la orden tiene coordenadas,
 * al tocar la dirección se abre en el mapa.
 */
class OrderAdapter(
    private val showAction: Boolean,
    private val onChangeStatus: (Order) -> Unit = {}
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    private val items = mutableListOf<Order>()
    private val dateFormat = SimpleDateFormat("d MMM, h:mm a", Locale("es", "CO"))

    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Order>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val order = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            tvOrderId.text = order.shortId
            tvPayment.setBadge(ctx.getString(order.paymentStatus.label), order.paymentStatus.color)
            tvStatus.setBadge(ctx.getString(order.status.label), order.status.color)
            tvCustomer.text = order.customer
            tvMeta.text = ctx.getString(
                R.string.order_meta,
                ctx.resources.getQuantityString(R.plurals.order_items, order.itemsCount, order.itemsCount),
                order.createdAt?.let { dateFormat.format(it) } ?: ctx.getString(R.string.order_now),
                ctx.getString(if (order.pickup) R.string.order_pickup else R.string.order_home)
            )
            tvTotal.text = order.total.toCop()

            // Dirección de entrega; con GPS se puede abrir en el mapa
            tvAddress.isVisible = !order.pickup && order.address.isNotBlank()
            tvAddress.text = order.address
            val lat = order.lat
            val lng = order.lng
            if (lat != null && lng != null) tvAddress.setOnClickListener { ctx.openMap(lat, lng, order.shortId) }
            else tvAddress.setOnClickListener(null)

            // Solo se despachan pedidos pagados
            btnChangeStatus.isVisible = showAction && order.paymentStatus == PaymentStatus.APPROVED
            btnChangeStatus.setOnClickListener { onChangeStatus(order) }
        }
    }
}
