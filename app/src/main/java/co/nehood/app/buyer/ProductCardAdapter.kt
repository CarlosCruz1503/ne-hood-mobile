package co.nehood.app.buyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.nehood.app.R
import co.nehood.app.core.loadProductImage
import co.nehood.app.core.setStockBadge
import co.nehood.app.core.tintSwatch
import co.nehood.app.core.toCop
import co.nehood.app.data.Product
import co.nehood.app.databinding.ItemProductCardBinding

/** Tarjetas de producto del inicio y del catálogo. fixedWidth = true para el carrusel horizontal. */
class ProductCardAdapter(
    private val fixedWidth: Boolean = false,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductCardAdapter.VH>() {

    private val items = mutableListOf<Product>()

    class VH(val b: ItemProductCardBinding) : RecyclerView.ViewHolder(b.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Product>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        if (fixedWidth) {
            b.root.layoutParams.width = parent.resources.getDimensionPixelSize(R.dimen.product_card_width)
        }
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        with(holder.b) {
            tvName.text = p.name
            tvCollection.text = p.collection
            tvPrice.text = p.price.toCop()
            swatch.tintSwatch(p.colorHex)
            ivImage.loadProductImage(p.imageUrl)
            tvStockBadge.setStockBadge(p)
            root.setOnClickListener { onClick(p) }
        }
    }
}
