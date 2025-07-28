package com.example.apptest.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.apptest.R
import com.example.apptest.model.AppInfo
import com.example.apptest.util.PreferenceUtil

class AppListAdapter(
    private val context: android.content.Context,
    private var appList: List<AppInfo>,
    private val isWhiteListMode: Boolean = false
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private var selectedPackageName: String? = null // 改用包名存储选中状态，而非position

    init {
        // 初始化选中状态（用包名而非position）
        if (!isWhiteListMode) {
            selectedPackageName = PreferenceUtil.getSelectedAppPackage(context)
        } else {
            // 白名单模式下初始化选中状态
            appList.forEach { app ->
                app.isInWhiteList = PreferenceUtil.isAppInWhiteList(context, app.packageName)
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: android.widget.ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val packageName: TextView = itemView.findViewById(R.id.packageName)
        val radioButton: RadioButton = itemView.findViewById(R.id.radioButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        holder.appIcon.setImageDrawable(appInfo.icon)
        holder.appName.text = appInfo.appName
        holder.packageName.text = appInfo.packageName

        if (isWhiteListMode) {
            holder.radioButton.isChecked = appInfo.isInWhiteList
            holder.radioButton.setOnCheckedChangeListener { _, isChecked ->
                appInfo.isInWhiteList = isChecked
                if (isChecked) {
                    PreferenceUtil.addAppToWhiteList(context, appInfo.packageName)
                } else {
                    PreferenceUtil.removeAppFromWhiteList(context, appInfo.packageName)
                }
            }
        } else {
            // 用包名判断选中状态，而非position
            holder.radioButton.isChecked = appInfo.packageName == selectedPackageName
            holder.itemView.setOnClickListener {
                // 实时获取当前位置（解决position可能失效的问题）
                val currentPosition = holder.adapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                // 更新选中的包名
                val oldPackageName = selectedPackageName
                selectedPackageName = appInfo.packageName

                // 刷新旧位置和新位置（通过包名查找旧位置）
                if (oldPackageName != null) {
                    val oldPosition = appList.indexOfFirst { it.packageName == oldPackageName }
                    if (oldPosition != -1) {
                        notifyItemChanged(oldPosition)
                    }
                }
                notifyItemChanged(currentPosition)

                // 保存选中的应用
                PreferenceUtil.saveSelectedAppPackage(context, appInfo.packageName)
            }
        }
    }

    override fun getItemCount() = appList.size

    fun updateList(newList: List<AppInfo>) {
        appList = newList
        // 更新选中状态（避免数据刷新后选中状态丢失）
        if (!isWhiteListMode) {
            selectedPackageName = PreferenceUtil.getSelectedAppPackage(context)
        } else {
            appList.forEach { app ->
                app.isInWhiteList = PreferenceUtil.isAppInWhiteList(context, app.packageName)
            }
        }
        notifyDataSetChanged()
    }
}