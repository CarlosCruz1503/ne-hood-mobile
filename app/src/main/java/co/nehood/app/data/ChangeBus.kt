package co.nehood.app.data

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet

/** Tablas que las pantallas pueden observar. */
enum class Table { USERS, PRODUCTS, CART, ORDERS }

/** Suscripción a cambios; se cancela con remove() cuando la pantalla se destruye. */
fun interface Registration {
    fun remove()
}

/**
 * Avisa a las pantallas abiertas cuando una tabla cambia, para que se actualicen solas
 * (por ejemplo, el dashboard cuando entra un pedido o el contador del carrito).
 */
object ChangeBus {

    private class Subscriber(val tables: Set<Table>, val callback: () -> Unit)

    private val subscribers = CopyOnWriteArraySet<Subscriber>()
    private val main = Handler(Looper.getMainLooper())

    fun subscribe(tables: Set<Table>, callback: () -> Unit): Registration {
        val subscriber = Subscriber(tables, callback)
        subscribers += subscriber
        return Registration { subscribers -= subscriber }
    }

    /** Lo llaman los repositorios después de escribir en la base de datos. */
    fun notify(vararg tables: Table) {
        val changed = tables.toSet()
        main.post {
            subscribers.filter { s -> s.tables.any { it in changed } }.forEach { it.callback() }
        }
    }
}

/**
 * Ejecuta [query] en segundo plano y entrega el resultado en el hilo principal;
 * lo repite cada vez que cambia alguna de las [tables]. Es el equivalente local
 * de un listener en tiempo real.
 */
fun <T> observe(
    tables: Set<Table>,
    query: () -> T,
    onChange: (T) -> Unit,
    onError: (Exception) -> Unit
): Registration {
    val scope = MainScope()
    val load = {
        scope.launch {
            try {
                onChange(withContext(Dispatchers.IO) { query() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
        Unit
    }
    val subscription = ChangeBus.subscribe(tables, load)
    load()
    return Registration {
        subscription.remove()
        scope.cancel()
    }
}
