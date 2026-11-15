package co.nehood.app.seller

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.nehood.app.R
import co.nehood.app.core.loadProductImage
import co.nehood.app.core.setBadge
import co.nehood.app.core.tintSwatch
import co.nehood.app.core.toCop
import co.nehood.app.data.Product
import co.nehood.app.databinding.ItemManageProductBinding

/** Fila de producto con editar y eliminar. En modo administrador muestra además el switch de publicar. */
class ManageProductAdapter(
    private val adminMode: Boolean,
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit,
    private val onTogglePublish: (Product, Boolean) -> Unit
) : RecyclerView.Adapter<ManageProductAdapter.VH>() {

    private val items = mutableListOf<Product>()

    class VH(val b: ItemManageProductBinding) : RecyclerView.ViewHolder(b.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Product>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemManageProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            swatch.tintSwatch(p.colorHex)
            ivImage.loadProductImage(p.imageUrl)
            tvName.text = p.name
            tvMeta.text = ctx.getString(R.string.manage_meta, p.price.toCop(), p.stock)
            when {
                p.stock == 0 -> tvStatus.setBadge(ctx.getString(R.string.badge_sold_out), R.color.nh_red)
                !p.published -> tvStatus.setBadge(ctx.getString(R.string.badge_hidden), R.color.nh_gray)
                p.stock <= 5 -> tvStatus.setBadge(ctx.getString(R.string.badge_low_stock), R.color.nh_amber)
                else -> tvStatus.setBadge(ctx.getString(R.string.badge_published), R.color.nh_green)
            }

            // Se quita el listener antes de cambiar el estado para no disparar el evento al reciclar la fila
            switchPublished.isVisible = adminMode
            switchPublished.setOnCheckedChangeListener(null)
            switchPublished.isChecked = p.published
            switchPublished.setOnCheckedChangeListener { _, checked -> onTogglePublish(p, checked) }

            root.setOnClickListener { onEdit(p) }
            btnEdit.setOnClickListener { onEdit(p) }
            btnDelete.setOnClickListener { onDelete(p) }
        }
    }
}
