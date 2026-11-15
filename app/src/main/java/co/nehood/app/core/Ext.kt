package co.nehood.app.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.nehood.app.R
import co.nehood.app.buyer.ProductDetailActivity
import co.nehood.app.data.AppException
import co.nehood.app.data.Product
import co.nehood.app.databinding.ViewKpiBinding
import coil.load
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

private val copFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CO"))

/** 189000 -> "$189.000" */
fun Int.toCop(): String = "$" + copFormat.format(this)

/** 6130000 -> "$6,1M" · 320000 -> "$320k" (para tarjetas y gráficas). */
fun Int.toShortCop(): String = when {
    this >= 1_000_000 -> "$" + String.format(Locale("es", "CO"), "%.1fM", this / 1_000_000f)
    this >= 1_000 -> "$" + (this / 1_000) + "k"
    else -> "$$this"
}

/** "Laura Gómez" -> "LG" */
fun String.initials(): String =
    split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/** Tiñe el bloque de color que se ve mientras no hay foto del producto. */
fun View.tintSwatch(hex: String) {
    val color = runCatching { Color.parseColor(hex) }.getOrDefault(Color.DKGRAY)
    ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(color))
}

fun View.tintColorRes(@ColorRes color: Int) {
    ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, color)))
}

/** Muestra la foto del producto guardada en el celular; si no tiene, deja visible el color de muestra. */
fun ImageView.loadProductImage(path: String) {
    val file = if (path.isNotBlank()) File(path) else null
    isVisible = file?.exists() == true
    if (file != null && file.exists()) load(file) { crossfade(true) }
}

/** Etiqueta con borde y texto del mismo color (drawable bg_badge). */
fun TextView.setBadge(text: String, @ColorRes colorRes: Int) {
    this.text = text
    val color = ContextCompat.getColor(context, colorRes)
    setTextColor(color)
    (background?.mutate() as? GradientDrawable)?.setStroke(
        (1.5f * resources.displayMetrics.density).toInt(), color
    )
}

/** Etiqueta de inventario en las tarjetas del catálogo. */
fun TextView.setStockBadge(product: Product) {
    when {
        product.stock == 0 -> {
            isVisible = true
            setBadge(context.getString(R.string.badge_sold_out), R.color.nh_red)
        }
        product.stock <= 5 -> {
            isVisible = true
            setBadge(context.getString(R.string.badge_last_units, product.stock), R.color.nh_amber)
        }
        else -> isVisible = false
    }
}

fun ViewKpiBinding.fill(label: String, value: String, note: String? = null) {
    tvKpiLabel.text = label
    tvKpiValue.text = value
    tvKpiNote.isVisible = note != null
    tvKpiNote.text = note
}

/**
 * Traduce los errores a mensajes claros en español (RNF-08).
 * Nunca se muestran códigos técnicos al usuario.
 */
fun Throwable.friendlyMessage(context: Context): String = context.getString(
    when (this) {
        is AppException -> messageRes
        is SQLiteConstraintException -> R.string.err_constraint
        is SQLiteException -> R.string.err_database
        is IOException -> R.string.err_network
        else -> R.string.err_generic
    }
)

/** Ejecuta una tarea de Firebase en una corrutina de la actividad y muestra el error si falla. */
fun AppCompatActivity.launchSafe(anchor: View, block: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Snackbar.make(anchor, e.friendlyMessage(this@launchSafe), Snackbar.LENGTH_LONG).show()
        }
    }

/** Acceso a la actividad contenedora del rol desde un fragment. */
fun Fragment.host(): RoleHostActivity? = activity as? RoleHostActivity

fun Fragment.openProduct(product: Product) {
    startActivity(ProductDetailActivity.intent(requireContext(), product.id))
}

/** Abre la ubicación en Google Maps u otra app de mapas. */
fun Context.openMap(lat: Double, lng: Double, label: String): Boolean = try {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(" + Uri.encode(label) + ")")
    startActivity(Intent(Intent.ACTION_VIEW, uri))
    true
} catch (e: ActivityNotFoundException) {
    false
}
