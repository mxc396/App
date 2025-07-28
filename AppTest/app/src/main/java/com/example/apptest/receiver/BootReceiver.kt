package com.example.apptest.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.apptest.MainActivity
import com.example.apptest.service.MyService
import com.example.apptest.service.KeepAliveService
import com.example.apptest.util.NotificationUtil
import com.example.apptest.util.PreferenceUtil

class BootReceiver : BroadcastReceiver() {  // 移除了多余的分号
    private val tag = "BootReceiver"
    private val REQUEST_POST_NOTIFICATIONS = 1003

    override fun onReceive(context: Context, intent: Intent) {
        // 只处理指定的启动事件
        val action = intent.action
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_REBOOT
        )

        if (action !in validActions) {
            return
        }

        // 检查自启开关状态
        val isAutoStartEnabled = PreferenceUtil.getAutoStartState(context)
        Log.d(tag, "收到广播: ${intent.action}, 自启开关: $isAutoStartEnabled")

        if (isAutoStartEnabled) {
            Log.d(tag, "自启开关已开启，准备启动服务")

            // 启动主服务
            startForegroundService(context)

            // 启动保活服务
            KeepAliveService.startService(context)

            // 处理Activity启动（适配Android 10+后台限制）
            startMainActivitySafely(context)
        }
    }

    /**
     * 启动前台服务（Android 8.0+ 后台启动服务必须使用前台服务）
     */
    private fun startForegroundService(context: Context) {
        val serviceIntent = Intent(context, MyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    /**
     * 安全启动主Activity（适配Android 10+后台启动限制）
     */
    private fun startMainActivitySafely(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 后台不能直接启动Activity，通过通知引导用户点击
            // 检查通知权限（Android 13+需要）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    // 有通知权限，发送通知
                    val notification = NotificationUtil.buildBackgroundStartNotification(context)
                    NotificationManagerCompat.from(context).notify(1002, notification)
                } else {
                    // 无通知权限，无法发送通知，可考虑其他方式或忽略
                    Log.w(tag, "没有通知权限，无法发送启动应用的通知")
                }
            } else {
                // Android 13以下不需要显式申请通知权限
                val notification = NotificationUtil.buildBackgroundStartNotification(context)
                NotificationManagerCompat.from(context).notify(1002, notification)
            }
        } else {
            // Android 10以下直接启动
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(activityIntent)
        }
    }
}