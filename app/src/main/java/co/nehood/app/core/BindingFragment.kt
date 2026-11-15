package co.nehood.app.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import co.nehood.app.data.Registration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fragment base con View Binding.
 * - Infla el layout y libera la vista en onDestroyView.
 * - Guarda las suscripciones a la base de datos y las quita al destruir la vista (evita fugas de memoria).
 * - launchSafe() ejecuta tareas en segundo plano y muestra un mensaje si fallan.
 */
abstract class BindingFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : Fragment() {

    private var _b: VB? = null
    protected val b: VB get() = _b!!
    protected val hasView: Boolean get() = _b != null

    private val registrations = mutableListOf<Registration>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = inflate(inflater, container, false)
        return b.root
    }

    /** Registra una suscripción que se cancela sola cuando la vista se destruye. */
    protected fun keep(registration: Registration) {
        registrations += registration
    }

    protected fun launchSafe(block: suspend CoroutineScope.() -> Unit): Job =
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            }
        }

    protected fun showError(e: Throwable) {
        if (hasView) Snackbar.make(b.root, e.friendlyMessage(requireContext()), Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        registrations.forEach { it.remove() }
        registrations.clear()
        super.onDestroyView()
        _b = null
    }
}
