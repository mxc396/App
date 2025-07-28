package com.example.apptest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.apptest.MainActivity
import com.example.apptest.util.PreferenceUtil

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        // 只处理指定的启动事件
        val action = intent.action
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                Intent.ACTION_REBOOT
            )
        ) {
            return
        }

        // 检查自启开关状态，仅在开启时启动应用
        val isAutoStartEnabled = PreferenceUtil.getAutoStartState(context)
        // 添加自启开关状态日志
        Log.d(tag, "自启开关状态: $isAutoStartEnabled")
        // 新增：记录收到的广播和自启开关状态
        Log.d(tag, "收到广播: ${intent.action}, 自启开关: $isAutoStartEnabled")

        if (isAutoStartEnabled) {
            Log.d(tag, "自启开关已开启，准备启动应用")
            startMainActivity(context)
        } else {
            Log.d(tag, "自启开关已关闭，不启动应用")
        }
    }

    // 封装启动Activity的逻辑
    private fun startMainActivity(context: Context) {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // 适配Android 10+后台启动限制
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startForegroundService(activityIntent) // 更可靠的启动方式
        } else {
            context.startActivity(activityIntent)
        }
    }
}