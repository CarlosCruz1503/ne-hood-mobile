package co.nehood.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Cálculos del dashboard y del reporte de ventas a partir de las órdenes pagadas. */
object SalesStats {

    private val locale = Locale("es", "CO")

    fun approved(orders: List<Order>) =
        orders.filter { it.paymentStatus == PaymentStatus.APPROVED && it.createdAt != null }

    /** Ventas de los últimos 7 días, un valor por día. */
    fun lastDays(orders: List<Order>, days: Int = 7): List<Pair<String, Int>> {
        val format = SimpleDateFormat("EEE", locale)
        return (days - 1 downTo 0).map { offset ->
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val total = approved(orders).filter { sameDay(it, day) }.sumOf { it.total }
            format.format(day.time).replaceFirstChar { it.uppercase() }.take(3) to total
        }
    }

    /** Ventas de los últimos 6 meses, un valor por mes. */
    fun lastMonths(orders: List<Order>, months: Int = 6): List<Pair<String, Int>> {
        val format = SimpleDateFormat("MMM", locale)
        return (months - 1 downTo 0).map { offset ->
            val month = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            val total = approved(orders).filter { sameMonth(it, month) }.sumOf { it.total }
            format.format(month.time).replace(".", "").replaceFirstChar { it.uppercase() } to total
        }
    }

    /** Ventas por año de los últimos 5 años. */
    fun lastYears(orders: List<Order>, years: Int = 5): List<Pair<String, Int>> {
        val current = Calendar.getInstance().get(Calendar.YEAR)
        return (years - 1 downTo 0).map { offset ->
            val year = current - offset
            val total = approved(orders).filter { yearOf(it) == year }.sumOf { it.total }
            year.toString() to total
        }
    }

    fun salesThisMonth(orders: List<Order>) = lastMonths(orders, 1).first().second

    private fun cal(order: Order) = Calendar.getInstance().apply { time = order.createdAt!! }
    private fun yearOf(order: Order) = cal(order).get(Calendar.YEAR)
    private fun sameDay(order: Order, day: Calendar) = cal(order).let {
        it.get(Calendar.YEAR) == day.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    }
    private fun sameMonth(order: Order, month: Calendar) = cal(order).let {
        it.get(Calendar.YEAR) == month.get(Calendar.YEAR) && it.get(Calendar.MONTH) == month.get(Calendar.MONTH)
    }
}
