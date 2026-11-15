package co.nehood.app.admin

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.nehood.app.R
import co.nehood.app.core.initials
import co.nehood.app.core.setBadge
import co.nehood.app.core.tintColorRes
import co.nehood.app.data.User
import co.nehood.app.databinding.ItemUserBinding

/** Fila de usuario con rol, nivel y estado. El administrador no puede eliminarse a sí mismo. */
class UserAdapter(
    private val currentUid: String?,
    private val onEdit: (User) -> Unit,
    private val onDelete: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    private val items = mutableListOf<User>()

    class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<User>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            tvAvatar.text = user.name.initials()
            tvAvatar.tintColorRes(user.role.color)
            tvName.text = user.name
            tvMeta.text = ctx.getString(R.string.user_meta, user.email, ctx.getString(user.level.label))
            tvRole.setBadge(ctx.getString(user.role.label), user.role.color)
            tvSuspended.isVisible = !user.active
            if (!user.active) tvSuspended.setBadge(ctx.getString(R.string.badge_suspended), R.color.nh_red)
            btnDelete.isVisible = user.id != currentUid

            root.setOnClickListener { onEdit(user) }
            btnEdit.setOnClickListener { onEdit(user) }
            btnDelete.setOnClickListener { onDelete(user) }
        }
    }
}
