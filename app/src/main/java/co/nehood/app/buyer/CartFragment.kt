package co.nehood.app.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.Session
import co.nehood.app.core.host
import co.nehood.app.core.toCop
import co.nehood.app.data.CartItem
import co.nehood.app.data.CartRepository
import co.nehood.app.data.OrderRepository
import co.nehood.app.databinding.FragmentCartBinding

/** Comprador · Carrito de compras: leer, cambiar cantidad y eliminar (RF-19 a RF-21). */
class CartFragment : BindingFragment<FragmentCartBinding>(FragmentCartBinding::inflate) {

    private lateinit var adapter: CartAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = Session.uid ?: return

        adapter = CartAdapter(
            onMinus = { item ->
                launchSafe {
                    if (item.qty > 1) CartRepository.setQty(uid, item.id, item.qty - 1)
                    else CartRepository.remove(uid, item.id)
                }
            },
            onPlus = { item ->
                if (item.qty < item.stock) launchSafe { CartRepository.setQty(uid, item.id, item.qty + 1) }
            },
            onDelete = { item -> launchSafe { CartRepository.remove(uid, item.id) } }
        )
        b.rvCart.layoutManager = LinearLayoutManager(requireContext())
        b.rvCart.adapter = adapter

        b.btnCheckout.setOnClickListener { startActivity(Intent(requireContext(), CheckoutActivity::class.java)) }
        b.btnGoShop.setOnClickListener { host()?.selectTab(R.id.nav_catalog) }

        keep(CartRepository.listen(uid, onChange = { items -> if (hasView) render(items) }, onError = ::showError))
    }

    private fun render(items: List<CartItem>) {
        adapter.submit(items)
        val empty = items.isEmpty()
        b.groupContent.isVisible = !empty
        b.groupEmpty.isVisible = empty

        val subtotal = items.sumOf { it.lineTotal }
        b.tvSubtotal.text = subtotal.toCop()
        b.tvShipping.text = OrderRepository.SHIPPING.toCop()
        b.tvTotal.text = (subtotal + OrderRepository.SHIPPING).toCop()
    }
}
