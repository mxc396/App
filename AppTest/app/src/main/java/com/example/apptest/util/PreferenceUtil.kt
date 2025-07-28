package com.example.apptest.util

import android.content.Context
import android.content.SharedPreferences

object PreferenceUtil {
    private const val PREFERENCE_NAME = "keep_alive_prefs"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_SELECTED_APP = "selected_app"
    private const val KEY_MONITOR_INTERVAL = "monitor_interval"
    private const val KEY_WHITE_LIST = "white_list"

    // 默认监控间隔10秒
    private const val DEFAULT_INTERVAL = 10 * 1000L

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    }

    // 自启设置
    fun saveAutoStartState(context: Context, state: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_AUTO_START, state).apply()
    }

    fun getAutoStartState(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_START, false)
    }

    // 选中的应用
    fun saveSelectedAppPackage(context: Context, packageName: String) {
        getPreferences(context).edit().putString(KEY_SELECTED_APP, packageName).apply()
    }

    fun getSelectedAppPackage(context: Context): String? {
        return getPreferences(context).getString(KEY_SELECTED_APP, null)
    }

    // 监控间隔
    fun saveMonitorInterval(context: Context, interval: Long) {
        getPreferences(context).edit().putLong(KEY_MONITOR_INTERVAL, interval).apply()
    }

    fun getMonitorInterval(context: Context): Long {
        return getPreferences(context).getLong(KEY_MONITOR_INTERVAL, DEFAULT_INTERVAL)
    }

    // 白名单管理
    fun addAppToWhiteList(context: Context, packageName: String) {
        val whiteList = getWhiteList(context).toMutableSet()
        whiteList.add(packageName)
        getPreferences(context).edit().putStringSet(KEY_WHITE_LIST, whiteList).apply()
    }

    fun removeAppFromWhiteList(context: Context, packageName: String) {
        val whiteList = getWhiteList(context).toMutableSet()
        whiteList.remove(packageName)
        getPreferences(context).edit().putStringSet(KEY_WHITE_LIST, whiteList).apply()
    }

    fun getWhiteList(context: Context): Set<String> {
        return getPreferences(context).getStringSet(KEY_WHITE_LIST, emptySet()) ?: emptySet()
    }

    fun isAppInWhiteList(context: Context, packageName: String): Boolean {
        return getWhiteList(context).contains(packageName)
    }
}