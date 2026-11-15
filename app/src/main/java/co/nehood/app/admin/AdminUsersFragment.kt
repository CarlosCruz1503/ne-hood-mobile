package co.nehood.app.admin

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import co.nehood.app.R
import co.nehood.app.core.BindingFragment
import co.nehood.app.core.Session
import co.nehood.app.data.Role
import co.nehood.app.data.User
import co.nehood.app.data.UserRepository
import co.nehood.app.databinding.FragmentAdminUsersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Administrador · Gestión de usuarios (RF-08 a RF-10).
 * Lista en tiempo real con búsqueda y filtros; crear y editar en UserFormActivity.
 */
class AdminUsersFragment : BindingFragment<FragmentAdminUsersBinding>(FragmentAdminUsersBinding::inflate) {

    private lateinit var adapter: UserAdapter
    private var users: List<User> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = UserAdapter(Session.uid, onEdit = { openForm(it.id) }, onDelete = { confirmDelete(it) })
        b.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        b.rvUsers.adapter = adapter
        b.etSearch.doAfterTextChanged { render() }
        b.chipsRole.setOnCheckedStateChangeListener { _, _ -> render() }
        b.fabAdd.setOnClickListener { openForm("") }

        keep(UserRepository.listenAll({ users = it; if (hasView) render() }, ::showError))
    }

    /** Aplica la búsqueda por nombre, correo o celular y el filtro por rol o estado. */
    private fun render() {
        val query = b.etSearch.text?.toString()?.trim().orEmpty()
        val checked = b.chipsRole.checkedChipId
        val list = users.filter { u ->
            val matchesFilter = when (checked) {
                R.id.chip_buyers -> u.role == Role.BUYER
                R.id.chip_sellers -> u.role == Role.SELLER
                R.id.chip_admins -> u.role == Role.ADMIN
                R.id.chip_suspended -> !u.active
                else -> true
            }
            val matchesQuery = query.isEmpty() ||
                u.name.contains(query, true) || u.email.contains(query, true) || u.phone.contains(query)
            matchesFilter && matchesQuery
        }
        adapter.submit(list)
        b.tvCount.text = resources.getQuantityString(R.plurals.users_count, list.size, list.size)
        b.tvEmpty.isVisible = list.isEmpty()
    }

    private fun openForm(userId: String) {
        startActivity(UserFormActivity.intent(requireContext(), userId))
    }

    private fun confirmDelete(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_user_title, user.name))
            .setMessage(R.string.delete_user_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                launchSafe {
                    UserRepository.delete(user.id)
                    if (hasView) Snackbar.make(b.root, getString(R.string.msg_deleted, user.name), Snackbar.LENGTH_SHORT)
                        .setAnchorView(b.fabAdd).show()
                }
            }
            .show()
    }
}
