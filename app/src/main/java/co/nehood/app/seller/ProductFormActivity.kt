package co.nehood.app.seller

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.ImageCompressor
import co.nehood.app.core.launchSafe
import co.nehood.app.data.Product
import co.nehood.app.data.ProductRepository
import co.nehood.app.databinding.ActivityProductFormBinding
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Vendedor · Crear y editar producto (RF-16 a RF-18).
 * La foto se toma con la cámara o se elige de la galería, se comprime y se guarda en el celular.
 */
class ProductFormActivity : AppCompatActivity() {

    private lateinit var b: ActivityProductFormBinding
    private var product: Product? = null
    private var pendingImage: Uri? = null       // foto nueva aún no subida
    private var cameraUri: Uri? = null          // archivo temporal donde la cámara guarda la foto

    private val sizeChips: Map<String, Chip> by lazy {
        mapOf(
            "S" to b.chipFormS, "M" to b.chipFormM, "L" to b.chipFormL,
            "XL" to b.chipFormXl, "Única" to b.chipFormUnique
        )
    }

    /** Cámara a resolución completa: guarda la foto en el archivo temporal (RF-17). */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) showNewPhoto(uri)
    }

    /** Selector de fotos del sistema (no necesita permisos de almacenamiento). */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) showNewPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // El administrador entra con su color (morado); el vendedor con el azul del manifest.
        if (intent.getBooleanExtra(EXTRA_ADMIN, false)) setTheme(R.style.Theme_NeHood_Admin)
        super.onCreate(savedInstanceState)
        b = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(b.root)

        val productId = intent.getStringExtra(EXTRA_ID).orEmpty()
        setupToolbar(isNew = productId.isBlank())
        setupDropdowns()

        b.btnCamera.setOnClickListener { openCamera() }
        b.btnGallery.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        b.btnSave.setOnClickListener { save() }

        if (productId.isBlank()) {
            fillDefaults()
        } else {
            setLoading(true)
            launchSafe(b.root) {
                try {
                    product = ProductRepository.get(productId)
                    product?.let(::fillForm) ?: finish()
                } finally {
                    setLoading(false)
                }
            }
        }
    }

    private fun setupToolbar(isNew: Boolean) {
        val toolbar = b.topBar.toolbar
        toolbar.setTitle(if (isNew) R.string.form_new_title else R.string.form_edit_title)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationContentDescription(R.string.cd_back)
        toolbar.setNavigationOnClickListener { finish() }
        if (!isNew) {
            toolbar.inflateMenu(R.menu.menu_form)
            toolbar.setOnMenuItemClickListener {
                if (it.itemId == R.id.action_delete) confirmDelete()
                true
            }
        }
    }

    private fun setupDropdowns() {
        b.actCollection.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.collections))
        )
        b.actCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.categories))
        )
    }

    private fun fillDefaults() {
        b.actCollection.setText(resources.getStringArray(R.array.collections).first(), false)
        b.actCategory.setText(resources.getStringArray(R.array.categories).first(), false)
        b.chipFormM.isChecked = true
    }

    private fun fillForm(p: Product) {
        b.etName.setText(p.name)
        b.etDescription.setText(p.description)
        b.etPrice.setText(p.price.toString())
        b.etStock.setText(p.stock.toString())
        b.actCollection.setText(p.collection, false)
        b.actCategory.setText(p.category, false)
        sizeChips.forEach { (size, chip) -> chip.isChecked = size in p.sizes }
        b.switchPublished.isChecked = p.published
        if (p.imageUrl.isNotBlank()) {
            b.ivPhoto.load(File(p.imageUrl)) { crossfade(true) }
            b.ivPhoto.isVisible = true
            b.groupPhotoEmpty.isVisible = false
        }
    }

    /** Crea un archivo temporal compartido con FileProvider y abre la cámara. */
    private fun openCamera() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "producto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(b.root, R.string.msg_no_camera, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showNewPhoto(uri: Uri) {
        pendingImage = uri
        b.ivPhoto.setImageURI(null)       // fuerza a recargar si es el mismo archivo
        b.ivPhoto.setImageURI(uri)
        b.ivPhoto.isVisible = true
        b.groupPhotoEmpty.isVisible = false
    }

    /** Valida el formulario, comprime la foto y guarda el producto con su foto. */
    private fun save() {
        b.tilName.error = null
        b.tilPrice.error = null
        b.tilStock.error = null

        val name = b.etName.text?.toString()?.trim().orEmpty()
        val description = b.etDescription.text?.toString()?.trim().orEmpty()
        val price = b.etPrice.text?.toString()?.toIntOrNull()
        val stock = b.etStock.text?.toString()?.toIntOrNull()
        val sizes = sizeChips.filterValues { it.isChecked }.keys.toList()

        var valid = true
        if (name.length < 3) { b.tilName.error = getString(R.string.err_name); valid = false }
        // RF-16: precio y stock no pueden ser negativos
        if (price == null || price <= 0) { b.tilPrice.error = getString(R.string.err_price); valid = false }
        if (stock == null || stock < 0) { b.tilStock.error = getString(R.string.err_stock); valid = false }
        if (sizes.isEmpty()) {
            Snackbar.make(b.root, R.string.err_sizes, Snackbar.LENGTH_SHORT).show()
            valid = false
        }
        if (product == null && pendingImage == null) {
            Snackbar.make(b.root, R.string.err_photo, Snackbar.LENGTH_SHORT).show()
            valid = false
        }
        if (!valid || price == null || stock == null) return

        val base = product ?: Product()
        val edited = base.copy(
            name = name, description = description,
            collection = b.actCollection.text.toString(), category = b.actCategory.text.toString(),
            price = price, stock = stock, sizes = sizes, published = b.switchPublished.isChecked
        )

        setLoading(true)
        launchSafe(b.root) {
            try {
                val imageBytes = pendingImage?.let { uri ->
                    withContext(Dispatchers.IO) { ImageCompressor.compress(this@ProductFormActivity, uri) }
                }
                ProductRepository.save(this@ProductFormActivity, edited, imageBytes)
                val message = if (product == null) R.string.msg_product_created else R.string.msg_product_updated
                Toast.makeText(this@ProductFormActivity, getString(message, name), Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun confirmDelete() {
        val p = product ?: return
        val hasSales = p.unitsSold > 0
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_product_title, p.name))
            .setMessage(if (hasSales) R.string.delete_product_has_sales else R.string.delete_product_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(if (hasSales) R.string.action_hide else R.string.action_delete) { _, _ ->
                launchSafe(b.root) {
                    if (hasSales) ProductRepository.setPublished(p.id, false) else ProductRepository.delete(p)
                    finish()
                }
            }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        b.progressSave.isVisible = loading
        b.btnSave.isEnabled = !loading
        b.btnCamera.isEnabled = !loading
        b.btnGallery.isEnabled = !loading
    }

    companion object {
        private const val EXTRA_ID = "product_id"
        private const val EXTRA_ADMIN = "admin"

        /** productId vacío = producto nuevo. */
        fun intent(context: Context, productId: String, admin: Boolean) =
            Intent(context, ProductFormActivity::class.java)
                .putExtra(EXTRA_ID, productId)
                .putExtra(EXTRA_ADMIN, admin)
    }
}
