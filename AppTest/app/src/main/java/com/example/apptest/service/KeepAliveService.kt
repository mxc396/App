package com.example.apptest.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.app.NotificationChannel
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.apptest.MainActivity
import com.example.apptest.R
import com.example.apptest.util.PreferenceUtil

class KeepAliveService : Service() {
    private val CHANNEL_ID = "keep_alive_channel"
    private val NOTIFICATION_ID = 1002
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val name = "保活服务"
        val descriptionText = "监控应用运行状态"
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = descriptionText

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 修复：统一使用带通道ID的构造函数，不再区分版本
        // 即使在低版本系统上，传入通道ID也不会有问题（系统会忽略）
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("保活服务运行中")
            .setContentText("正在监控指定应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun startMonitoring() {
        stopMonitoring()
        val interval = PreferenceUtil.getMonitorInterval(this)

        checkRunnable = Runnable {
            checkAndKeepAliveApp()
            handler.postDelayed(checkRunnable!!, interval)
        }
        handler.postDelayed(checkRunnable!!, interval)
    }

    private fun stopMonitoring() {
        checkRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun checkAndKeepAliveApp() {
        val targetPackage = PreferenceUtil.getSelectedAppPackage(this) ?: return

        if (!isAppRunning(targetPackage)) {
            startApp(targetPackage)
        } else if (!isAppForeground(targetPackage)) {
            bringAppToForeground(targetPackage)
        }
    }

    private fun isAppRunning(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isAppForeground(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                currentTime - 10000,
                currentTime
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.let {
                return it.packageName == packageName
            }
        }
        return false
    }

    private fun startApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { startActivity(it) }
    }

    private fun bringAppToForeground(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent?.let { startActivity(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }
}
