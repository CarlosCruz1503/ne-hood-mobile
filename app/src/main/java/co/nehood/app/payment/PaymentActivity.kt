package co.nehood.app.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import co.nehood.app.R
import co.nehood.app.data.Order
import co.nehood.app.databinding.ActivityPaymentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayInputStream

/**
 * Muestra el Checkout de ePayco en un WebView.
 * Cuando ePayco redirige a la URL de respuesta, se toma el parámetro ref_payco
 * y se devuelve a CheckoutActivity, que valida el pago con el servicio de ePayco.
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var b: ActivityPaymentBinding
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.topBar.toolbar.setTitle(R.string.payment_title)
        b.topBar.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        b.topBar.toolbar.setNavigationContentDescription(R.string.cd_back)
        b.topBar.toolbar.setNavigationOnClickListener { confirmCancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmCancel()
        })

        val order = Order(
            id = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty(),
            invoice = intent.getStringExtra(EXTRA_INVOICE).orEmpty(),
            userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty(),
            customer = intent.getStringExtra(EXTRA_CUSTOMER).orEmpty(),
            email = intent.getStringExtra(EXTRA_EMAIL).orEmpty(),
            total = intent.getIntExtra(EXTRA_TOTAL, 0)
        )
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()

        with(b.webView.settings) {
            javaScriptEnabled = true          // checkout.js de ePayco
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)  // las ventanas nuevas (PSE, bancos) se abren en el mismo WebView
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(b.webView, true)

        b.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleUrl(request.url.toString())

            // Respaldo: si ePayco redirige dentro de un iframe, la petición se detecta aquí
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (!url.startsWith(EpaycoClient.RESPONSE_URL)) return null
                runOnUiThread { handleUrl(url) }
                return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!handleUrl(url)) b.progress.isVisible = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                b.progress.isVisible = false
            }
        }

        b.webView.loadDataWithBaseURL(
            "https://nehood.app/", EpaycoClient.checkoutHtml(order, description),
            "text/html", "UTF-8", null
        )
    }

    /** Intercepta la URL de respuesta de ePayco y devuelve la referencia del pago. */
    private fun handleUrl(url: String): Boolean {
        if (!url.startsWith(EpaycoClient.RESPONSE_URL) || finished) return false
        finished = true
        val uri = Uri.parse(url)
        val ref = uri.getQueryParameter("ref_payco") ?: uri.getQueryParameter("x_ref_payco")
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_REF_PAYCO, ref))
        finish()
        return true
    }

    private fun confirmCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.payment_cancel_title)
            .setMessage(R.string.payment_cancel_body)
            .setNegativeButton(R.string.payment_continue, null)
            .setPositiveButton(R.string.payment_cancel) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        b.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REF_PAYCO = "ref_payco"
        private const val EXTRA_ORDER_ID = "order_id"
        private const val EXTRA_INVOICE = "invoice"
        private const val EXTRA_USER_ID = "user_id"
        private const val EXTRA_CUSTOMER = "customer"
        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_DESCRIPTION = "description"

        fun intent(context: Context, order: Order, description: String) =
            Intent(context, PaymentActivity::class.java)
                .putExtra(EXTRA_ORDER_ID, order.id)
                .putExtra(EXTRA_INVOICE, order.invoice)
                .putExtra(EXTRA_USER_ID, order.userId)
                .putExtra(EXTRA_CUSTOMER, order.customer)
                .putExtra(EXTRA_EMAIL, order.email)
                .putExtra(EXTRA_TOTAL, order.total)
                .putExtra(EXTRA_DESCRIPTION, description)
    }
}
