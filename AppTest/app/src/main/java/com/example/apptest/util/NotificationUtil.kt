package com.example.apptest.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.apptest.MainActivity
import com.example.apptest.R

object NotificationUtil {
    // 通知渠道ID（必须唯一）
    private const val CHANNEL_ID = "foreground_service_channel"
    // 通知渠道名称
    private const val CHANNEL_NAME = "前台服务通知"
    // 通知ID（必须唯一）
    const val NOTIFICATION_ID = 1001
    // 通知权限请求码
    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 10086

    /**
     * 初始化通知渠道（Android 8.0+ 必需）
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "用于保持服务在后台运行"
                setSound(null, null) // 无提示音
            }
            // 注册渠道
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建前台服务所需的通知
     */
    fun buildForegroundNotification(context: Context): Notification {
        // 点击通知打开主界面
        val pendingIntent = context.pendingIntentForActivity()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("应用运行中")
            .setContentText("点击返回应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true) // 不可滑动取消
            .build()
    }

    /**
     * 构建用于后台启动Activity的通知（Android 10+）
     */
    fun buildBackgroundStartNotification(context: Context): Notification {
        val pendingIntent = context.pendingIntentForActivity()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("应用需要启动")
            .setContentText("点击打开应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    /**
     * 创建启动主Activity的PendingIntent，适配不同Android版本
     */
    private fun Context.pendingIntentForActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // 根据Android版本设置合适的标志位
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 支持IMMUTABLE标志
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            // Android 5.1及以下使用旧版标志
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            flags
        )
    }

    /**
     * 检查是否有发送通知的权限
     * Android 13+ 需要显式请求POST_NOTIFICATIONS权限
     * 低版本默认拥有此权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需检查POST_NOTIFICATIONS权限
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            // 低版本只需检查通知是否启用
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * 检查是否需要请求通知权限（未授予且未被永久拒绝）
     */
    fun shouldRequestNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        return !hasNotificationPermission(context) &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    context as? android.app.Activity ?: return false,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
    }

    /**
     * 请求通知权限（需在Activity中调用）
     */
    fun requestNotificationPermission(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }
}
