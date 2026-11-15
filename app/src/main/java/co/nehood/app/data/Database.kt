package co.nehood.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos SQLite local de la app (nehood.db).
 *
 * Tablas:
 * - users: cuentas con rol, nivel y hash de contraseña.
 * - products: catálogo con la ruta de la foto guardada en el celular.
 * - cart_items: carrito de cada usuario (producto + talla + cantidad).
 * - orders y order_items: órdenes de compra con el resultado del pago en ePayco.
 *
 * Al crearse por primera vez inserta un administrador, un vendedor y productos de ejemplo.
 */
class Database private constructor(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                phone TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'BUYER',
                level TEXT NOT NULL DEFAULT 'CLIENTE',
                active INTEGER NOT NULL DEFAULT 1,
                purchases INTEGER NOT NULL DEFAULT 0,
                last_change TEXT,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                collection TEXT NOT NULL,
                category TEXT NOT NULL,
                price INTEGER NOT NULL CHECK (price > 0),
                stock INTEGER NOT NULL CHECK (stock >= 0),
                sizes TEXT NOT NULL,
                color_hex TEXT NOT NULL,
                image_path TEXT NOT NULL DEFAULT '',
                published INTEGER NOT NULL DEFAULT 1,
                units_sold INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """
            CREATE TABLE cart_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                size TEXT NOT NULL,
                qty INTEGER NOT NULL CHECK (qty > 0),
                UNIQUE (user_id, product_id, size)
            )"""
        )
        db.execSQL(
            """
            CREATE TABLE orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                invoice TEXT NOT NULL UNIQUE,
                user_id INTEGER NOT NULL,
                customer TEXT NOT NULL,
                email TEXT NOT NULL,
                subtotal INTEGER NOT NULL,
                shipping INTEGER NOT NULL,
                total INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                payment_status TEXT NOT NULL DEFAULT 'PENDING',
                payment_ref TEXT NOT NULL DEFAULT '',
                payment_method TEXT NOT NULL DEFAULT '',
                payment_message TEXT NOT NULL DEFAULT '',
                pickup INTEGER NOT NULL,
                address TEXT NOT NULL,
                lat REAL,
                lng REAL,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """
            CREATE TABLE order_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                size TEXT NOT NULL,
                qty INTEGER NOT NULL,
                price INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_orders_user ON orders(user_id)")
        db.execSQL("CREATE INDEX idx_cart_user ON cart_items(user_id)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Primera versión: no hay migraciones todavía.
    }

    /** Datos iniciales para poder entrar y probar la app desde la primera vez. */
    private fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        fun user(name: String, email: String, phone: String, role: Role) {
            val salt = PasswordHasher.newSalt()
            db.insert("users", null, ContentValues().apply {
                put("name", name); put("email", email); put("phone", phone)
                put("password_hash", PasswordHasher.hash(SEED_PASSWORD, salt)); put("salt", salt)
                put("role", role.name); put("level", Level.FAMILIA.name); put("created_at", now)
            })
        }
        user("Dirección NE.HOOD", "admin@nehood.co", "3000000000", Role.ADMIN)
        user("Equipo de ventas", "vendedor@nehood.co", "3000000001", Role.SELLER)

        fun product(name: String, desc: String, col: String, cat: String, price: Int, stock: Int, sizes: String, color: String) {
            db.insert("products", null, ContentValues().apply {
                put("name", name); put("description", desc); put("collection", col); put("category", cat)
                put("price", price); put("stock", stock); put("sizes", sizes); put("color_hex", color)
                put("created_at", now)
            })
        }
        product("Hoodie Hora Pico", "Hoodie oversize en algodón perchado de 380 g con estampado frontal.",
            "Hora Pico", "Hoodies", 189_000, 14, "S,M,L,XL", "#2E2E2E")
        product("Tee Engativá", "Camiseta de algodón peinado con gráfico del barrio en la espalda.",
            "Hora Pico", "Camisetas", 79_000, 4, "S,M,L,XL", "#C9B79C")
        product("Gorra NH", "Gorra de seis paneles con logo bordado y cierre metálico.",
            "Básicos NH", "Gorras", 65_000, 20, "Única", "#4A5D3A")
        product("Zip Hoodie Tercer Día", "Hoodie con cremallera completa y bolsillos laterales.",
            "Tercer Día", "Hoodies", 199_000, 8, "S,M,L", "#A05444")
    }

    companion object {
        private const val NAME = "nehood.db"
        private const val VERSION = 1
        const val SEED_PASSWORD = "nehood123"

        @Volatile private var instance: Database? = null

        fun init(context: Context) {
            if (instance == null) synchronized(this) {
                if (instance == null) instance = Database(context.applicationContext)
            }
        }

        /** Conexión de escritura (SQLite la reutiliza; es segura entre hilos). */
        val db: SQLiteDatabase
            get() = (instance ?: error("Database.init() no se llamó")).writableDatabase
    }
}

// ---------- Utilidades para leer columnas del Cursor por nombre ----------

fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
fun Cursor.strOrNull(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
fun Cursor.bool(column: String): Boolean = int(column) == 1
fun Cursor.doubleOrNull(column: String): Double? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

/** Recorre todas las filas de una consulta y las convierte con [map]; cierra el cursor al terminar. */
inline fun <T> Cursor.mapAll(map: (Cursor) -> T): List<T> = use {
    val out = ArrayList<T>(count)
    while (moveToNext()) out += map(this)
    out
}
