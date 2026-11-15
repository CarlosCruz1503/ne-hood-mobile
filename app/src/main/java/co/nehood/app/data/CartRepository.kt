package co.nehood.app.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/** CRUD del carrito en la tabla cart_items (RF-19 a RF-21). */
object CartRepository {

    /** Une el carrito con products para traer el precio, el stock y la foto actuales. */
    private const val QUERY = """
        SELECT c.id, c.product_id, c.size, c.qty, p.name, p.price, p.stock, p.color_hex, p.image_path
        FROM cart_items c JOIN products p ON p.id = c.product_id
        WHERE c.user_id = ? ORDER BY p.name COLLATE NOCASE"""

    private fun fromCursor(c: Cursor) = CartItem(
        id = c.long("id").toString(),
        productId = c.long("product_id").toString(),
        name = c.str("name"),
        price = c.int("price"),
        size = c.str("size"),
        qty = c.int("qty"),
        stock = c.int("stock"),
        colorHex = c.str("color_hex"),
        imageUrl = c.str("image_path")
    )

    private fun getAllSync(uid: String) = Database.db.rawQuery(QUERY, arrayOf(uid)).mapAll(::fromCursor)

    fun listen(uid: String, onChange: (List<CartItem>) -> Unit, onError: (Exception) -> Unit): Registration =
        observe(setOf(Table.CART, Table.PRODUCTS), { getAllSync(uid) }, onChange, onError)

    suspend fun getAll(uid: String): List<CartItem> = withContext(Dispatchers.IO) { getAllSync(uid) }

    /** Agrega el producto con su talla. Si ya estaba, suma la cantidad sin pasar del stock. */
    suspend fun add(uid: String, product: Product, size: String, qty: Int) = withContext(Dispatchers.IO) {
        val db = Database.db
        db.beginTransaction()
        try {
            val current = db.rawQuery(
                "SELECT qty FROM cart_items WHERE user_id = ? AND product_id = ? AND size = ?",
                arrayOf(uid, product.id, size)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val newQty = min(current + qty, product.stock)
            if (current == 0) {
                db.insertOrThrow("cart_items", null, ContentValues().apply {
                    put("user_id", uid.toLong()); put("product_id", product.id.toLong())
                    put("size", size); put("qty", newQty)
                })
            } else {
                db.update("cart_items", ContentValues().apply { put("qty", newQty) },
                    "user_id = ? AND product_id = ? AND size = ?", arrayOf(uid, product.id, size))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ChangeBus.notify(Table.CART)
    }

    suspend fun setQty(uid: String, itemId: String, qty: Int) = withContext(Dispatchers.IO) {
        Database.db.update("cart_items", ContentValues().apply { put("qty", qty) },
            "id = ? AND user_id = ?", arrayOf(itemId, uid))
        ChangeBus.notify(Table.CART)
    }

    suspend fun remove(uid: String, itemId: String) = withContext(Dispatchers.IO) {
        Database.db.delete("cart_items", "id = ? AND user_id = ?", arrayOf(itemId, uid))
        ChangeBus.notify(Table.CART)
    }
}
