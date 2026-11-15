package co.nehood.app.payment

import co.nehood.app.BuildConfig
import co.nehood.app.R
import co.nehood.app.data.AppException
import co.nehood.app.data.Order
import co.nehood.app.data.PaymentResult
import co.nehood.app.data.PaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Integración con la pasarela ePayco (RF-23).
 *
 * 1. checkoutHtml() arma una página con el Checkout oficial de ePayco (checkout.js).
 * 2. Al terminar, ePayco redirige a RESPONSE_URL con el parámetro ref_payco; PaymentActivity la intercepta.
 * 3. validate() consulta el estado real de la transacción en el servicio de validación de ePayco.
 */
object EpaycoClient {

    /** URL de respuesta. No existe de verdad: el WebView la intercepta antes de cargarla. */
    const val RESPONSE_URL = "https://nehood.app/epayco/respuesta"
    private const val VALIDATION_URL = "https://secure.epayco.co/validation/v1/reference/"

    val isConfigured: Boolean get() = BuildConfig.EPAYCO_PUBLIC_KEY.isNotBlank()

    /** Página HTML que abre el Checkout de ePayco con los datos de la orden. */
    fun checkoutHtml(order: Order, description: String): String {
        val data = JSONObject().apply {
            put("name", "NE.HOOD")
            put("description", description)
            put("invoice", order.invoice)            // factura única de la orden
            put("currency", "cop")
            put("amount", order.total.toString())
            put("tax_base", "0")
            put("tax", "0")
            put("tax_ico", "0")
            put("country", "co")
            put("lang", "es")
            put("external", "false")                 // checkout dentro de la misma página
            put("response", RESPONSE_URL)
            put("name_billing", order.customer)
            put("email_billing", order.email)
            put("extra1", order.userId)
            put("methodsDisable", JSONArray())
        }
        val key = JSONObject.quote(BuildConfig.EPAYCO_PUBLIC_KEY)
        val test = BuildConfig.EPAYCO_TEST
        return """
            <!DOCTYPE html>
            <html lang="es"><head>
            <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body{font-family:sans-serif;background:#F4F0E8;color:#141414;margin:0;height:100vh;
                   display:flex;align-items:center;justify-content:center;text-align:center}
              button{background:#FF4F1A;color:#fff;border:0;border-radius:10px;padding:14px 22px;font-weight:bold;font-size:15px}
            </style></head>
            <body><div>
              <p id="msg">Abriendo ePayco…</p>
              <button id="retry" style="display:none" onclick="pay()">Abrir pago</button>
            </div>
            <script src="https://checkout.epayco.co/checkout.js"></script>
            <script>
              function pay(){
                try {
                  var handler = ePayco.checkout.configure({ key: $key, test: $test });
                  handler.open($data);
                } catch (e) {
                  document.getElementById('msg').innerText = 'No se pudo abrir ePayco. Revisa tu conexión.';
                  document.getElementById('retry').style.display = 'inline-block';
                }
              }
              window.onload = pay;
            </script></body></html>
        """.trimIndent()
    }

    /**
     * Consulta la transacción en ePayco y verifica que corresponda a la orden y al valor esperados.
     * x_cod_response: 1 aceptada, 2 rechazada, 3 pendiente, 4 fallida.
     */
    suspend fun validate(refPayco: String, order: Order): PaymentResult = withContext(Dispatchers.IO) {
        val url = URL(VALIDATION_URL + URLEncoder.encode(refPayco, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (!json.optBoolean("success")) throw AppException(R.string.err_payment_validation)
            val data = json.getJSONObject("data")

            var status = when (data.optInt("x_cod_response")) {
                1 -> PaymentStatus.APPROVED
                2 -> PaymentStatus.REJECTED
                3 -> PaymentStatus.PENDING
                else -> PaymentStatus.FAILED
            }
            // Control de seguridad: la factura y el valor deben ser los de esta orden
            val invoice = data.optString("x_id_invoice")
            val amount = data.optString("x_amount").toDoubleOrNull()?.toInt()
            if (invoice != order.invoice || amount != order.total) status = PaymentStatus.FAILED

            PaymentResult(
                status = status,
                reference = data.optString("x_ref_payco", refPayco),
                method = data.optString("x_franchise"),
                message = data.optString("x_response_reason_text", data.optString("x_response"))
            )
        } finally {
            connection.disconnect()
        }
    }
}
