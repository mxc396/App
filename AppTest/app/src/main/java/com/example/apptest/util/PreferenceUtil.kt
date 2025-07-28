package com.example.apptest.util

import android.content.Context

// 封装SharedPreferences操作，避免代码重复
object PreferenceUtil {
    private const val PREF_NAME = "auto_start_prefs"
    private const val KEY_AUTO_START = "auto_start_enabled"

    // 获取自启状态
    fun getAutoStartState(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START, false)
    }

    // 保存自启状态
    fun saveAutoStartState(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START, enabled)
            .apply() // 异步提交，性能更好
    }
}