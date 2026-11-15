package co.nehood.app.buyer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.RoleHostActivity
import co.nehood.app.core.Session
import co.nehood.app.core.launchSafe
import co.nehood.app.core.loadProductImage
import co.nehood.app.core.toCop
import co.nehood.app.data.CartRepository
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.databinding.ActivityProductDetailBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Comprador · Detalle de producto: talla, cantidad, agregar al carrito (RF-19) y Casillero. */
class ProductDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityProductDetailBinding
    private var product: Product? = null
    private var qty = 1

    private val sizeChips: Map<String, Chip> by lazy {
        mapOf(
            "S" to b.chipSizeS, "M" to b.chipSizeM, "L" to b.chipSizeL,
            "XL" to b.chipSizeXl, "Única" to b.chipSizeUnique
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.topBar.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        b.topBar.toolbar.setNavigationContentDescription(R.string.cd_back)
        b.topBar.toolbar.setNavigationOnClickListener { finish() }
        b.btnMinus.setOnClickListener { if (qty > 1) { qty--; renderQty() } }
        b.btnPlus.setOnClickListener { if (qty < (product?.stock ?: 0)) { qty++; renderQty() } }
        b.btnAddCart.setOnClickListener { addToCart() }
        b.btnCasillero.setOnClickListener { showCasillero() }
        b.btnAddCart.isEnabled = false

        // Se consulta el producto actualizado (precio y stock reales)
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        launchSafe(b.root) {
            val loaded = ProductRepository.get(id)
            if (loaded == null) {
                finish()
                return@launchSafe
            }
            product = loaded
            render(loaded)
        }
    }

    private fun render(p: Product) {
        b.topBar.toolbar.title = p.category
        b.swatch.setBackgroundColor(runCatching { Color.parseColor(p.colorHex) }.getOrDefault(Color.DKGRAY))
        b.ivImage.loadProductImage(p.imageUrl)
        b.tvCollection.text = getString(R.string.detail_collection, p.collection)
        b.tvName.text = p.name
        b.tvPrice.text = p.price.toCop()
        b.tvCasillero.text = getString(R.string.detail_casillero_from, initialPayment(p).toCop())
        b.tvDescription.text = p.description

        val soldOut = p.stock == 0
        sizeChips.forEach { (size, chip) ->
            chip.isVisible = size in p.sizes
            chip.isEnabled = !soldOut
        }
        if (p.sizes.size == 1) sizeChips[p.sizes.first()]?.isChecked = true

        b.btnAddCart.isEnabled = !soldOut
        b.btnCasillero.isEnabled = !soldOut
        if (soldOut) b.btnAddCart.setText(R.string.badge_sold_out)
        renderQty()
    }

    private fun initialPayment(p: Product) = p.price * 25 / 100

    private fun renderQty() {
        val stock = product?.stock ?: 0
        b.tvQty.text = qty.toString()
        b.btnMinus.isEnabled = qty > 1
        b.btnPlus.isEnabled = qty < stock
        b.tvStock.text = if (stock == 0) getString(R.string.detail_sold_out)
        else resources.getQuantityString(R.plurals.stock_available, stock, stock)
    }

    /** Guarda el producto con la talla y la cantidad en el carrito. */
    private fun addToCart() {
        val p = product ?: return
        val uid = Session.uid ?: return
        val size = sizeChips.entries.firstOrNull { it.value.isChecked }?.key
        if (size == null) {
            Snackbar.make(b.root, R.string.msg_choose_size, Snackbar.LENGTH_SHORT).setAnchorView(b.bottomBar).show()
            return
        }
        b.btnAddCart.isEnabled = false
        launchSafe(b.root) {
            try {
                CartRepository.add(uid, p, size, qty)
                Snackbar.make(b.root, getString(R.string.msg_added_cart, p.name), Snackbar.LENGTH_LONG)
                    .setAnchorView(b.bottomBar)
                    .setAction(R.string.action_view_cart) {
                        startActivity(
                            Intent(this@ProductDetailActivity, BuyerActivity::class.java)
                                .putExtra(RoleHostActivity.EXTRA_TAB, R.id.nav_cart)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        finish()
                    }
                    .show()
            } finally {
                b.btnAddCart.isEnabled = true
            }
        }
    }

    /** Casillero: separar la prenda con una cuota inicial del 25 % (informativo en esta versión). */
    private fun showCasillero() {
        val p = product ?: return
        val initial = initialPayment(p)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.casillero_title)
            .setMessage(getString(R.string.casillero_body, p.price.toCop(), initial.toCop(), ((p.price - initial) / 3).toCop()))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    companion object {
        private const val EXTRA_ID = "product_id"
        fun intent(context: Context, productId: String) =
            Intent(context, ProductDetailActivity::class.java).putExtra(EXTRA_ID, productId)
    }
}
