package com.example.apptest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apptest.databinding.ActivityMainBinding
import com.example.apptest.util.PreferenceUtil

class MainActivity : ComponentActivity() {
    // 使用视图绑定
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    // 权限请求Launcher
    private val overlayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionSwitches()
            showPermissionResultToast(checkOverlayPermission(), "悬浮窗")
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
            updatePermissionSwitches()
            showPermissionResultToast(granted, "通知")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化视图绑定
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 处理窗口边距
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化界面
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null // 防止内存泄漏
    }

    private fun initViews() {
        // 加载保存的状态
        loadAutoStartState()
        updatePermissionSwitches()

        // 设置监听事件
        setListeners()
    }

    private fun setListeners() {
        // 自启开关监听
        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceUtil.saveAutoStartState(this, isChecked)
            val message = if (isChecked) "已开启开机自启" else "已关闭开机自启"
            // 显示Toast前添加Activity状态判断
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            if (isChecked) checkAndRequestPermissions()
        }

        // 悬浮窗权限布局点击
        binding.overlayPermissionLayout.setOnClickListener {
            if (!checkOverlayPermission()) requestOverlayPermission()
        }

        // 通知权限布局点击
        binding.notificationPermissionLayout.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !checkNotificationPermission()) {
                requestNotificationPermission()
            }
        }
    }

    // 检查并请求必要权限
    private fun checkAndRequestPermissions() {
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !checkNotificationPermission()) {
            requestNotificationPermission()
        }
    }

    // 检查悬浮悬浮窗权限
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // 6.0以下默认有权限
        }
    }

    // 请求悬浮窗权限
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    // 检查通知权限
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // 13以下无需显式权限
        }
    }

    // 请求通知权限
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 更新权限开关状态
    private fun updatePermissionSwitches() {
        binding.overlayPermissionSwitch.isChecked = checkOverlayPermission()
        binding.notificationPermissionSwitch.isChecked = checkNotificationPermission()
    }

    // 加载自启状态
    private fun loadAutoStartState() {
        binding.autoStartSwitch.isChecked = PreferenceUtil.getAutoStartState(this)
    }

    // 显示权限请求结果提示
    private fun showPermissionResultToast(granted: Boolean, permissionName: String) {
        val message = if (granted) {
            "$permissionName 权限已开启"
        } else {
            "$permissionName 权限未开启，部分功能可能受限"
        }
        // 显示Toast前添加Activity状态判断
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 启动MainActivity的方法，增加了FLAG以增强启动稳定性
    private fun startMainActivity(context: Context) {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(activityIntent)
    }
}
