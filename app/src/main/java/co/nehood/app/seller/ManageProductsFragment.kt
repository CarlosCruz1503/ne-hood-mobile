package co.nehood.app.seller

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.databinding.FragmentManageProductsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Vendedor · Panel de productos (CRUD, RF-16 a RF-18). La lista se actualiza en tiempo real.
 * Con admin = true es la "Gestión de productos" del administrador (RF-12: publicar u ocultar).
 */
class ManageProductsFragment :
    BindingFragment<FragmentManageProductsBinding>(FragmentManageProductsBinding::inflate) {

    private val adminMode get() = arguments?.getBoolean(ARG_ADMIN) == true
    private lateinit var adapter: ManageProductAdapter
    private var products: List<Product> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ManageProductAdapter(
            adminMode,
            onEdit = { openForm(it.id) },
            onDelete = { confirmDelete(it) },
            onTogglePublish = { product, published -> togglePublish(product, published) }
        )
        b.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        b.rvProducts.adapter = adapter
        b.chipsStatus.setOnCheckedStateChangeListener { _, _ -> render() }
        b.fabAdd.setOnClickListener { openForm("") }
        b.tvSubtitle.setText(if (adminMode) R.string.manage_subtitle_admin else R.string.manage_subtitle_seller)

        // El botón flotante se encoge al bajar para no tapar la lista
        b.rvProducts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) b.fabAdd.shrink() else if (dy < 0) b.fabAdd.extend()
            }
        })

        keep(ProductRepository.listen(onlyPublished = false, onChange = {
            products = it
            if (hasView) render()
        }, onError = ::showError))
    }

    private fun render() {
        val list = when (b.chipsStatus.checkedChipId) {
            R.id.chip_published -> products.filter { it.published }
            R.id.chip_hidden -> products.filter { !it.published }
            R.id.chip_sold_out -> products.filter { it.stock == 0 }
            else -> products
        }
        adapter.submit(list)
        b.tvCount.text = resources.getQuantityString(R.plurals.products_count, products.size, products.size)
        b.tvEmpty.isVisible = list.isEmpty()
    }

    private fun openForm(productId: String) {
        startActivity(ProductFormActivity.intent(requireContext(), productId, adminMode))
    }

    private fun togglePublish(product: Product, published: Boolean) {
        launchSafe {
            ProductRepository.setPublished(product.id, published)
            if (hasView) Snackbar.make(
                b.root, getString(if (published) R.string.msg_published else R.string.msg_hidden, product.name),
                Snackbar.LENGTH_SHORT
            ).setAnchorView(b.fabAdd).show()
        }
    }

    /** RF-18: si el producto ya tiene ventas se oculta en lugar de borrarse. */
    private fun confirmDelete(product: Product) {
        val hasSales = product.unitsSold > 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_product_title, product.name))
            .setMessage(if (hasSales) R.string.delete_product_has_sales else R.string.delete_product_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(if (hasSales) R.string.action_hide else R.string.action_delete) { _, _ ->
                if (hasSales) togglePublish(product, false)
                else launchSafe {
                    ProductRepository.delete(product)
                    if (hasView) Snackbar.make(b.root, getString(R.string.msg_deleted, product.name), Snackbar.LENGTH_SHORT)
                        .setAnchorView(b.fabAdd).show()
                }
            }
            .show()
    }

    companion object {
        private const val ARG_ADMIN = "admin"
        fun newInstance(admin: Boolean) = ManageProductsFragment().apply {
            arguments = bundleOf(ARG_ADMIN to admin)
        }
    }
}
