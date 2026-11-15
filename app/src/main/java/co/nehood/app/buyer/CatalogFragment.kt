package co.nehood.app.buyer

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.openProduct
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.databinding.FragmentCatalogBinding

/** Comprador · Catálogo con búsqueda y filtros (RF-02). */
class CatalogFragment : BindingFragment<FragmentCatalogBinding>(FragmentCatalogBinding::inflate) {

    private val adapter = ProductCardAdapter { openProduct(it) }
    private var products: List<Product> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvCatalog.layoutManager = GridLayoutManager(requireContext(), 2)
        b.rvCatalog.adapter = adapter
        b.etSearch.doAfterTextChanged { applyFilters() }
        b.chipsFilter.setOnCheckedStateChangeListener { _, _ -> applyFilters() }

        keep(ProductRepository.listen(onlyPublished = true, onChange = {
            products = it
            if (hasView) applyFilters()
        }, onError = ::showError))
    }

    /** Aplica el texto de búsqueda y el filtro de categoría o disponibilidad. */
    private fun applyFilters() {
        val query = b.etSearch.text?.toString()?.trim().orEmpty()
        val checked = b.chipsFilter.checkedChipId
        val category = when (checked) {
            R.id.chip_tees -> getString(R.string.cat_tees)
            R.id.chip_hoodies -> getString(R.string.cat_hoodies)
            R.id.chip_caps -> getString(R.string.cat_caps)
            R.id.chip_accessories -> getString(R.string.cat_accessories)
            else -> null
        }
        val list = products.filter { p ->
            (query.isEmpty() || p.name.contains(query, true) || p.collection.contains(query, true)) &&
                (category == null || p.category == category) &&
                (checked != R.id.chip_available || p.stock > 0)
        }
        adapter.submit(list)
        b.tvResults.text = resources.getQuantityString(R.plurals.results_count, list.size, list.size)
        b.tvEmpty.isVisible = list.isEmpty()
    }
}
