package co.nehood.app.buyer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.core.RoleHostActivity
import co.nehood.app.core.Session
import co.nehood.app.core.launchSafe
import co.nehood.app.core.toCop
import co.nehood.app.data.AppException
import co.nehood.app.data.AuthRepository
import co.nehood.app.data.CartItem
import co.nehood.app.data.CartRepository
import co.nehood.app.data.Order
import co.nehood.app.data.OrderRepository
import co.nehood.app.data.PaymentResult
import co.nehood.app.data.PaymentStatus
import co.nehood.app.databinding.ActivityCheckoutBinding
import co.nehood.app.payment.EpaycoClient
import co.nehood.app.payment.PaymentActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Comprador · Resumen de compra y pago.
 * 1. Dirección de entrega con ubicación del GPS (RF-22) o recoger en NE.HOUSE.
 * 2. Crea la orden en la base de datos con pago pendiente.
 * 3. Abre el checkout de ePayco (PaymentActivity) y valida la transacción al volver (RF-23).
 * 4. Si el pago es aprobado, descuenta stock, vacía el carrito y guarda el historial.
 */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var b: ActivityCheckoutBinding
    private var cart: List<CartItem> = emptyList()
    private var pendingOrder: Order? = null
    private var lat: Double? = null
    private var lng: Double? = null

    private val pickup get() = b.rbPickup.isChecked
    private val subtotal get() = cart.sumOf { it.lineTotal }
    private val shipping get() = if (pickup) 0 else OrderRepository.SHIPPING
    private val total get() = subtotal + shipping

    /** Pide el permiso de ubicación solo cuando el usuario toca el botón (RF-22). */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) fetchLocation()
            else Snackbar.make(b.root, R.string.err_location_permission, Snackbar.LENGTH_LONG).show()
        }

    /** Recibe la referencia de ePayco cuando termina el checkout. */
    private val paymentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val order = pendingOrder ?: return@registerForActivityResult
            val ref = result.data?.getStringExtra(PaymentActivity.EXTRA_REF_PAYCO)
            if (result.resultCode == Activity.RESULT_OK && !ref.isNullOrBlank()) validatePayment(order, ref)
            else onPaymentCancelled(order)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.topBar.toolbar.setTitle(R.string.checkout_title)
        b.topBar.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        b.topBar.toolbar.setNavigationContentDescription(R.string.cd_back)
        b.topBar.toolbar.setNavigationOnClickListener { finish() }

        b.rgDelivery.setOnCheckedChangeListener { _, _ -> render() }
        b.btnLocation.setOnClickListener { requestLocation() }
        b.btnPay.setOnClickListener { pay() }
        b.btnPay.isEnabled = false

        val uid = Session.uid ?: return finish()
        launchSafe(b.root) {
            cart = CartRepository.getAll(uid)
            if (cart.isEmpty()) finish() else render()
        }
    }

    private fun render() {
        b.tvProducts.text = subtotal.toCop()
        b.tvShipping.text = if (shipping == 0) getString(R.string.free) else shipping.toCop()
        b.tvTotal.text = total.toCop()
        b.tilAddress.isVisible = !pickup
        b.groupLocation.isVisible = !pickup
        b.btnPay.text = getString(R.string.checkout_pay, total.toCop())
        b.btnPay.isEnabled = cart.isNotEmpty()
    }

    // ---------- Geolocalización ----------

    private fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) fetchLocation()
        else locationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    /**
     * Obtiene la ubicación actual con el Fused Location Provider de Google
     * y la convierte en una dirección con Geocoder. Las coordenadas se guardan con la orden.
     */
    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        b.btnLocation.isEnabled = false
        b.tvLocation.setText(R.string.location_searching)
        launchSafe(b.root) {
            try {
                val client = LocationServices.getFusedLocationProviderClient(this@CheckoutActivity)
                val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                    ?: client.lastLocation.await()
                    ?: throw AppException(R.string.err_location_unavailable)
                lat = location.latitude
                lng = location.longitude

                val address = withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        Geocoder(this@CheckoutActivity, Locale("es", "CO"))
                            .getFromLocation(location.latitude, location.longitude, 1)
                            ?.firstOrNull()?.getAddressLine(0)
                    }.getOrNull()
                }
                if (address != null) b.etAddress.setText(address)
                b.tvLocation.text = getString(R.string.location_captured, location.latitude, location.longitude)
            } catch (e: Exception) {
                b.tvLocation.text = ""
                throw e
            } finally {
                b.btnLocation.isEnabled = true
            }
        }
    }

    // ---------- Pago ----------

    private fun pay() {
        b.tilAddress.error = null
        val address = b.etAddress.text?.toString()?.trim().orEmpty()
        if (!pickup && address.isBlank()) {
            b.tilAddress.error = getString(R.string.err_address)
            return
        }
        if (!b.cbTerms.isChecked) {
            Snackbar.make(b.root, R.string.err_terms, Snackbar.LENGTH_SHORT).setAnchorView(b.btnPay).show()
            return
        }
        if (!EpaycoClient.isConfigured) {
            Snackbar.make(b.root, R.string.err_epayco_key, Snackbar.LENGTH_LONG).setAnchorView(b.btnPay).show()
            return
        }
        val user = Session.user ?: return

        setLoading(true)
        launchSafe(b.root) {
            try {
                val order = OrderRepository.createPending(
                    user, cart, pickup,
                    if (pickup) getString(R.string.order_pickup) else address,
                    if (pickup) null else lat, if (pickup) null else lng
                )
                pendingOrder = order
                paymentLauncher.launch(
                    PaymentActivity.intent(
                        this@CheckoutActivity, order,
                        resources.getQuantityString(R.plurals.order_items, order.itemsCount, order.itemsCount)
                    )
                )
            } catch (e: Exception) {
                setLoading(false)
                throw e
            }
        }
    }

    /** Consulta en ePayco el estado real de la transacción y lo guarda en la orden. */
    private fun validatePayment(order: Order, refPayco: String) {
        setLoading(true, R.string.checkout_validating)
        launchSafe(b.root) {
            try {
                val result = EpaycoClient.validate(refPayco, order)
                OrderRepository.applyPayment(order, result)
                if (result.status == PaymentStatus.APPROVED) AuthRepository.refreshProfile()
                showResult(order, result)
            } finally {
                setLoading(false)
            }
        }
    }

    /** Si el usuario cierra el checkout, la orden queda registrada como fallida. */
    private fun onPaymentCancelled(order: Order) {
        setLoading(false)
        launchSafe(b.root) {
            OrderRepository.applyPayment(
                order, PaymentResult(PaymentStatus.FAILED, "", "", getString(R.string.pay_cancelled))
            )
            Snackbar.make(b.root, R.string.pay_cancelled, Snackbar.LENGTH_LONG).setAnchorView(b.btnPay).show()
        }
    }

    private fun showResult(order: Order, result: PaymentResult) {
        val (title, message) = when (result.status) {
            PaymentStatus.APPROVED ->
                R.string.pay_success_title to getString(R.string.pay_success_body, order.shortId, order.total.toCop(), result.reference)
            PaymentStatus.PENDING ->
                R.string.pay_pending_title to getString(R.string.pay_pending_body, order.shortId, result.reference)
            else ->
                R.string.pay_rejected_title to getString(R.string.pay_rejected_body, result.message.ifBlank { "-" })
        }
        val approved = result.status == PaymentStatus.APPROVED
        MaterialAlertDialogBuilder(this)
            .setIcon(if (approved) R.drawable.ic_check_circle else R.drawable.ic_info)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(if (approved) R.string.pay_success_ok else R.string.action_ok) { _, _ ->
                if (result.status != PaymentStatus.REJECTED && result.status != PaymentStatus.FAILED) goHome()
            }
            .show()
    }

    private fun goHome() {
        startActivity(
            Intent(this, BuyerActivity::class.java)
                .putExtra(RoleHostActivity.EXTRA_TAB, R.id.nav_home)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun setLoading(loading: Boolean, textRes: Int = R.string.checkout_processing) {
        b.progressPay.isVisible = loading
        b.btnPay.isEnabled = !loading && cart.isNotEmpty()
        if (loading) b.btnPay.setText(textRes) else b.btnPay.text = getString(R.string.checkout_pay, total.toCop())
    }
}
