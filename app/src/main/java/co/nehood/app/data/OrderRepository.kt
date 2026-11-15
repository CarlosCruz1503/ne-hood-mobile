package co.nehood.app.data

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.max

/** Órdenes de compra e historial en SQLite (RF-23 a RF-26). */
object OrderRepository {

    const val SHIPPING = 12_000

    private fun fromCursor(c: Cursor) = Order(
        id = c.long("id").toString(),
        invoice = c.str("invoice"),
        userId = c.long("user_id").toString(),
        customer = c.str("customer"),
        email = c.str("email"),
        subtotal = c.int("subtotal"),
        shipping = c.int("shipping"),
        total = c.int("total"),
        status = enumOr(c.str("status"), OrderStatus.PENDING),
        paymentStatus = enumOr(c.str("payment_status"), PaymentStatus.PENDING),
        paymentRef = c.str("payment_ref"),
        paymentMethod = c.str("payment_method"),
        pickup = c.bool("pickup"),
        address = c.str("address"),
        lat = c.doubleOrNull("lat"),
        lng = c.doubleOrNull("lng"),
        createdAt = Date(c.long("created_at"))
    )

    /** Lee las órdenes y les agrega sus líneas (order_items). */
    private fun querySync(where: String, args: Array<String>?): List<Order> {
        val orders = Database.db.rawQuery("SELECT * FROM orders $where ORDER BY created_at DESC", args)
            .mapAll(::fromCursor)
        if (orders.isEmpty()) return orders
        val items = Database.db.rawQuery(
            "SELECT order_id, product_id, name, size, qty, price FROM order_items", null
        ).mapAll { c ->
            c.long("order_id").toString() to OrderItem(
                c.long("product_id").toString(), c.str("name"), c.str("size"), c.int("qty"), c.int("price")
            )
        }.groupBy({ it.first }, { it.second })
        return orders.map { it.copy(items = items[it.id].orEmpty()) }
    }

    /** Crea la orden con pago pendiente. invoice es único y es la factura que se envía a ePayco. */
    suspend fun createPending(
        user: User, cart: List<CartItem>, pickup: Boolean,
        address: String, lat: Double?, lng: Double?
    ): Order = withContext(Dispatchers.IO) {
        val subtotal = cart.sumOf { it.lineTotal }
        val shipping = if (pickup) 0 else SHIPPING
        val now = System.currentTimeMillis()
        val invoice = "NH${now}"
        val db = Database.db
        db.beginTransaction()
        val id = try {
            val newId = db.insertOrThrow("orders", null, ContentValues().apply {
                put("invoice", invoice); put("user_id", user.id.toLong())
                put("customer", user.name); put("email", user.email)
                put("subtotal", subtotal); put("shipping", shipping); put("total", subtotal + shipping)
                put("pickup", if (pickup) 1 else 0); put("address", address)
                if (lat != null) put("lat", lat)
                if (lng != null) put("lng", lng)
                put("created_at", now)
            })
            cart.forEach { item ->
                db.insertOrThrow("order_items", null, ContentValues().apply {
                    put("order_id", newId); put("product_id", item.productId.toLong()); put("name", item.name)
                    put("size", item.size); put("qty", item.qty); put("price", item.price)
                })
            }
            db.setTransactionSuccessful()
            newId
        } finally {
            db.endTransaction()
        }
        ChangeBus.notify(Table.ORDERS)
        Order(
            id = id.toString(), invoice = invoice, userId = user.id, customer = user.name, email = user.email,
            items = cart.map { OrderItem(it.productId, it.name, it.size, it.qty, it.price) },
            subtotal = subtotal, shipping = shipping, total = subtotal + shipping,
            pickup = pickup, address = address, lat = lat, lng = lng, createdAt = Date(now)
        )
    }

    /** Todas las órdenes, de la más reciente a la más antigua (vendedor y administrador). */
    fun listenAll(onChange: (List<Order>) -> Unit, onError: (Exception) -> Unit): Registration =
        observe(setOf(Table.ORDERS), { querySync("", null) }, onChange, onError)

    /** Historial del comprador (RF-25). */
    fun listenForUser(uid: String, onChange: (List<Order>) -> Unit, onError: (Exception) -> Unit): Registration =
        observe(setOf(Table.ORDERS), { querySync("WHERE user_id = ?", arrayOf(uid)) }, onChange, onError)

    suspend fun getAll(): List<Order> = withContext(Dispatchers.IO) { querySync("", null) }

    suspend fun countForUser(uid: String): Int = withContext(Dispatchers.IO) {
        Database.db.rawQuery("SELECT COUNT(*) FROM orders WHERE user_id = ?", arrayOf(uid)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /** Cambio de estado de despacho por el vendedor (RF-26). */
    suspend fun updateStatus(orderId: String, status: OrderStatus) = withContext(Dispatchers.IO) {
        Database.db.update("orders", ContentValues().apply { put("status", status.name) }, "id = ?", arrayOf(orderId))
        ChangeBus.notify(Table.ORDERS)
    }

    /**
     * Registra el resultado del pago. Si fue aprobado, en una sola transacción:
     * descuenta el stock, suma unidades vendidas, suma la compra al cliente (nivel de comunidad),
     * marca la orden como pagada y vacía el carrito. Si ya estaba pagada no repite nada.
     */
    suspend fun applyPayment(order: Order, result: PaymentResult) = withContext(Dispatchers.IO) {
        val db = Database.db
        db.beginTransaction()
        try {
            val alreadyPaid = db.rawQuery("SELECT payment_status FROM orders WHERE id = ?", arrayOf(order.id))
                .use { it.moveToFirst() && it.getString(0) == PaymentStatus.APPROVED.name }
            if (!alreadyPaid) {
                db.update("orders", ContentValues().apply {
                    put("payment_status", result.status.name); put("payment_ref", result.reference)
                    put("payment_method", result.method); put("payment_message", result.message)
                }, "id = ?", arrayOf(order.id))

                if (result.status == PaymentStatus.APPROVED) {
                    order.items.groupBy { it.productId }.forEach { (productId, lines) ->
                        val qty = lines.sumOf { it.qty }
                        val stock = db.rawQuery("SELECT stock FROM products WHERE id = ?", arrayOf(productId))
                            .use { if (it.moveToFirst()) it.getInt(0) else null }
                        if (stock != null) {
                            db.execSQL(
                                "UPDATE products SET stock = ?, units_sold = units_sold + ? WHERE id = ?",
                                arrayOf<Any>(max(0, stock - qty), qty, productId)
                            )
                        }
                    }
                    val purchases = db.rawQuery("SELECT purchases FROM users WHERE id = ?", arrayOf(order.userId))
                        .use { if (it.moveToFirst()) it.getInt(0) + 1 else null }
                    if (purchases != null) {
                        db.update("users", ContentValues().apply {
                            put("purchases", purchases); put("level", Level.forPurchases(purchases).name)
                        }, "id = ?", arrayOf(order.userId))
                    }
                    db.delete("cart_items", "user_id = ?", arrayOf(order.userId))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ChangeBus.notify(Table.ORDERS, Table.PRODUCTS, Table.USERS, Table.CART)
    }
}
