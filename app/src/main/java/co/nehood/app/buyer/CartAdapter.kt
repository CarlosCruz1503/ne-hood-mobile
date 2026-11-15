package co.nehood.app.buyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nehood.app.R
import co.nehood.app.core.loadProductImage
import co.nehood.app.core.tintSwatch
import co.nehood.app.core.toCop
import co.nehood.app.data.CartItem
import co.nehood.app.databinding.ItemCartBinding

/**
 * Ítems del carrito. Los botones no cambian la lista directamente: avisan al fragment,
 * que actualiza la base de datos, y la suscripción a cambios redibuja la lista.
 */
class CartAdapter(
    private val onMinus: (CartItem) -> Unit,
    private val onPlus: (CartItem) -> Unit,
    private val onDelete: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {

    private val items = mutableListOf<CartItem>()

    class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<CartItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            swatch.tintSwatch(item.colorHex)
            ivImage.loadProductImage(item.imageUrl)
            tvName.text = item.name
            tvDetail.text = ctx.getString(R.string.cart_item_detail, item.size, item.price.toCop())
            tvQty.text = item.qty.toString()
            tvLineTotal.text = item.lineTotal.toCop()
            btnPlus.isEnabled = item.qty < item.stock
            btnMinus.setOnClickListener { onMinus(item) }
            btnPlus.setOnClickListener { onPlus(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
