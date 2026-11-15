package co.nehood.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CRUD de productos en SQLite (RF-12, RF-16 a RF-18).
 * Las fotos se guardan comprimidas en el almacenamiento interno de la app (files/products/).
 */
object ProductRepository {

    private val palette = listOf("#2E2E2E", "#C9B79C", "#4A5D3A", "#8C2F1B", "#3B4C6B", "#A05444")

    private fun fromCursor(c: Cursor) = Product(
        id = c.long("id").toString(),
        name = c.str("name"),
        description = c.str("description"),
        collection = c.str("collection"),
        category = c.str("category"),
        price = c.int("price"),
        stock = c.int("stock"),
        sizes = c.str("sizes").split(",").map { it.trim() }.filter { it.isNotEmpty() },
        colorHex = c.str("color_hex"),
        imageUrl = c.str("image_path"),
        published = c.bool("published"),
        unitsSold = c.int("units_sold")
    )

    private fun querySync(onlyPublished: Boolean): List<Product> {
        val where = if (onlyPublished) "WHERE published = 1" else ""
        return Database.db.rawQuery("SELECT * FROM products $where ORDER BY name COLLATE NOCASE", null)
            .mapAll(::fromCursor)
    }

    /** Lista que se actualiza sola. El comprador solo recibe los productos publicados. */
    fun listen(onlyPublished: Boolean, onChange: (List<Product>) -> Unit, onError: (Exception) -> Unit): Registration =
        observe(setOf(Table.PRODUCTS), { querySync(onlyPublished) }, onChange, onError)

    suspend fun get(id: String): Product? = withContext(Dispatchers.IO) {
        Database.db.rawQuery("SELECT * FROM products WHERE id = ?", arrayOf(id)).mapAll(::fromCursor).firstOrNull()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { querySync(false).size }

    /**
     * Crea o actualiza un producto. Si llega una foto nueva (JPEG ya comprimido),
     * la guarda en files/products/ y borra la foto anterior.
     */
    suspend fun save(context: Context, product: Product, newImage: ByteArray?): Product = withContext(Dispatchers.IO) {
        var imagePath = product.imageUrl
        if (newImage != null) {
            val dir = File(context.filesDir, "products").apply { mkdirs() }
            val file = File(dir, "producto_${System.currentTimeMillis()}.jpg")
            file.writeBytes(newImage)
            if (imagePath.isNotBlank()) File(imagePath).delete()
            imagePath = file.absolutePath
        }
        val values = ContentValues().apply {
            put("name", product.name); put("description", product.description)
            put("collection", product.collection); put("category", product.category)
            put("price", product.price); put("stock", product.stock)
            put("sizes", product.sizes.joinToString(",")); put("image_path", imagePath)
            put("published", if (product.published) 1 else 0)
        }
        val saved = if (product.id.isBlank()) {
            val color = palette[(System.currentTimeMillis() % palette.size).toInt()]
            values.put("color_hex", color)
            values.put("created_at", System.currentTimeMillis())
            val id = Database.db.insertOrThrow("products", null, values)
            product.copy(id = id.toString(), imageUrl = imagePath, colorHex = color)
        } else {
            Database.db.update("products", values, "id = ?", arrayOf(product.id))
            product.copy(imageUrl = imagePath)
        }
        ChangeBus.notify(Table.PRODUCTS, Table.CART)
        saved
    }

    /** Publicar u ocultar en la tienda (RF-12). */
    suspend fun setPublished(id: String, published: Boolean) = withContext(Dispatchers.IO) {
        Database.db.update("products", ContentValues().apply { put("published", if (published) 1 else 0) },
            "id = ?", arrayOf(id))
        ChangeBus.notify(Table.PRODUCTS)
    }

    /** Borra el producto, su foto y los carritos que lo tenían. Con ventas, la pantalla lo oculta (RF-18). */
    suspend fun delete(product: Product) = withContext(Dispatchers.IO) {
        Database.db.delete("products", "id = ?", arrayOf(product.id))
        if (product.imageUrl.isNotBlank()) File(product.imageUrl).delete()
        ChangeBus.notify(Table.PRODUCTS, Table.CART)
    }
}
