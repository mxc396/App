package com.example.apptest.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.apptest.util.NotificationUtil

class MyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 8.0+ 必须调用startForeground
        val notification = NotificationUtil.buildForegroundNotification(this)
        startForeground(NotificationUtil.NOTIFICATION_ID, notification)

        // 在这里处理服务逻辑（例如定时任务等）

        return START_STICKY // 服务被杀死后尝试重启
    }

    override fun onDestroy() {
        super.onDestroy()
        // 处理不同API级别的stopForeground调用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24及以上，使用stopForeground并指定移除通知
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            // API 24以下，使用stopForeground的旧版本方法
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
    