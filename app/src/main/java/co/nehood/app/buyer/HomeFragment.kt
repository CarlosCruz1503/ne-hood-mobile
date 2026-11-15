package co.nehood.app.buyer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.Session
import co.nehood.app.core.host
import co.nehood.app.core.openProduct
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.databinding.FragmentHomeBinding
import com.google.android.material.snackbar.Snackbar

/** Comprador · Inicio (RF-01, RF-03). Los destacados se actualizan solos cuando cambia el catálogo. */
class HomeFragment : BindingFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    private val adapter = ProductCardAdapter(fixedWidth = true) { openProduct(it) }
    private var products: List<Product> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val firstName = Session.user?.name?.substringBefore(" ").orEmpty()
        b.tvGreeting.text = getString(R.string.home_greeting, firstName)

        b.rvFeatured.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.rvFeatured.adapter = adapter

        b.chipsCategory.setOnCheckedStateChangeListener { _, _ -> render() }
        b.searchBar.setOnClickListener { host()?.selectTab(R.id.nav_catalog) }
        b.btnSeeAll.setOnClickListener { host()?.selectTab(R.id.nav_catalog) }
        b.btnHero.setOnClickListener { host()?.selectTab(R.id.nav_catalog) }
        b.cardPickup.setOnClickListener { openPickupMap() }

        keep(ProductRepository.listen(onlyPublished = true, onChange = {
            products = it
            if (hasView) render()
        }, onError = ::showError))
    }

    /** Filtra por la categoría elegida y muestra primero los más vendidos. */
    private fun render() {
        val category = when (b.chipsCategory.checkedChipId) {
            R.id.chip_cat_tees -> getString(R.string.cat_tees)
            R.id.chip_cat_hoodies -> getString(R.string.cat_hoodies)
            R.id.chip_cat_caps -> getString(R.string.cat_caps)
            else -> null
        }
        adapter.submit(
            products.filter { category == null || it.category == category }
                .sortedByDescending { it.unitsSold }
                .take(10)
        )
    }

    /** RF-03: punto de recogida en NE.HOUSE. Abre Google Maps. */
    private fun openPickupMap() {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(getString(R.string.pickup_map_query)))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(b.root, R.string.msg_no_maps, Snackbar.LENGTH_SHORT).show()
        }
    }
}
