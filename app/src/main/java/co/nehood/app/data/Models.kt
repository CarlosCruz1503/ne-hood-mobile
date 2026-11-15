package co.nehood.app.data

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import co.nehood.app.R
import java.util.Date

/** Convierte el texto guardado en la base de datos al enum; si no coincide usa el valor por defecto. */
inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/** Roles definidos en el documento técnico. Cada rol tiene su color de acento. */
enum class Role(@StringRes val label: Int, @ColorRes val color: Int) {
    BUYER(R.string.role_buyer, R.color.nh_orange),
    SELLER(R.string.role_seller, R.color.nh_blue),
    ADMIN(R.string.role_admin, R.color.nh_purple)
}

/** Niveles de comunidad del CRM (RF-28). */
enum class Level(@StringRes val label: Int) {
    CLIENTE(R.string.level_cliente),
    PARCHE(R.string.level_parche),
    FAMILIA(R.string.level_familia);

    companion object {
        /** Regla de niveles: 3 compras para Parche y 8 para Familia. */
        fun forPurchases(purchases: Int) = when {
            purchases >= 8 -> FAMILIA
            purchases >= 3 -> PARCHE
            else -> CLIENTE
        }
    }
}

/** Estado de despacho del pedido (lo cambia el vendedor, RF-26). */
enum class OrderStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    PENDING(R.string.status_pending, R.color.nh_amber),
    CONFIRMED(R.string.status_confirmed, R.color.nh_blue),
    SHIPPED(R.string.status_shipped, R.color.nh_purple),
    DELIVERED(R.string.status_delivered, R.color.nh_green)
}

/** Estado del pago según la respuesta de ePayco (x_cod_response 1, 2, 3 o 4). */
enum class PaymentStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    PENDING(R.string.pay_status_pending, R.color.nh_amber),
    APPROVED(R.string.pay_status_approved, R.color.nh_green),
    REJECTED(R.string.pay_status_rejected, R.color.nh_red),
    FAILED(R.string.pay_status_failed, R.color.nh_red)
}

/** Usuario. Tabla users; la contraseña se guarda aparte como hash (nunca en texto plano). */
data class User(
    val id: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var role: Role = Role.BUYER,
    var level: Level = Level.CLIENTE,
    var active: Boolean = true,
    var purchases: Int = 0,
    var lastChange: String? = null       // RF-09: quién y cuándo cambió el rol o el estado
)

/** Producto. Tabla products. imageUrl es la ruta de la foto guardada en el almacenamiento interno de la app. */
data class Product(
    val id: String = "",
    var name: String = "",
    var description: String = "",
    var collection: String = "",
    var category: String = "",
    var price: Int = 0,
    var stock: Int = 0,
    var sizes: List<String> = emptyList(),
    var colorHex: String = "#2E2E2E",
    var imageUrl: String = "",
    var published: Boolean = true,
    var unitsSold: Int = 0
)

/** Ítem del carrito. Tabla cart_items unida con products: precio, stock y foto siempre actualizados. */
data class CartItem(
    val id: String = "",
    val productId: String = "",
    val name: String = "",
    val price: Int = 0,
    val size: String = "",
    val qty: Int = 1,
    val stock: Int = 0,
    val colorHex: String = "#2E2E2E",
    val imageUrl: String = ""
) {
    val lineTotal: Int get() = price * qty

}

/** Línea de una orden: copia del producto al momento de la compra. */
data class OrderItem(
    val productId: String,
    val name: String,
    val size: String,
    val qty: Int,
    val price: Int
)

/** Orden de compra. Tablas orders y order_items. invoice es el número de factura único enviado a ePayco. */
data class Order(
    val id: String = "",
    val invoice: String = "",
    val userId: String = "",
    val customer: String = "",
    val email: String = "",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Int = 0,
    val shipping: Int = 0,
    val total: Int = 0,
    var status: OrderStatus = OrderStatus.PENDING,
    var paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val paymentRef: String = "",
    val paymentMethod: String = "",
    val pickup: Boolean = false,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val createdAt: Date? = null
) {
    val itemsCount: Int get() = items.sumOf { it.qty }

    /** Número de pedido para mostrar al usuario: NH-0007. */
    val shortId: String get() = "NH-" + id.padStart(4, '0')

}

/** Resultado de la validación de un pago en ePayco. */
data class PaymentResult(
    val status: PaymentStatus,
    val reference: String,
    val method: String,
    val message: String
)

/** Error con un mensaje en español listo para mostrar al usuario (RNF-08). */
class AppException(@StringRes val messageRes: Int) : Exception()
