package com.example.apptest.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.apptest.model.AppInfo

object AppInfoUtil {
    fun getNonSystemApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val apps = mutableListOf<AppInfo>()

        val packageInfos = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

        for (packageInfo in packageInfos) {
            // 过滤掉自己
            if (packageInfo.packageName == context.packageName) {
                continue
            }

            // 获取应用信息，添加空安全判断
            val applicationInfo = packageInfo.applicationInfo ?: continue

            val isSystemApp = isSystemApp(applicationInfo)
            if (!isSystemApp) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                val icon = applicationInfo.loadIcon(packageManager)

                apps.add(
                    AppInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = isSystemApp
                    )
                )
            }
        }

        // 按应用名称排序
        return apps.sortedBy { it.appName.lowercase() }
    }

    // 修改参数类型为ApplicationInfo，已在调用处做过空判断
    private fun isSystemApp(applicationInfo: ApplicationInfo): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }
}