package com.example.apptest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.apptest.adapter.AppListAdapter
import com.example.apptest.databinding.ActivityMainBinding
import com.example.apptest.model.AppInfo
import com.example.apptest.service.KeepAliveService
import com.example.apptest.util.AppInfoUtil
import com.example.apptest.util.PreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    // 视图绑定
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    // 通知相关常量
    private val CHANNEL_ID_APP = "app_test_channel" // 应用通用通知通道
    private val CHANNEL_ID_KEEP_ALIVE = "keep_alive_channel" // 保活服务通知通道
    private val NOTIFICATION_ID_LAUNCH = 1001 // 启动应用的通知ID

    // 监控间隔选项（毫秒）
    private val intervalOptions = listOf(10000L, 60000L, 300000L, 1800000L, 7200000L)

    // 适配器
    private lateinit var appListAdapter: AppListAdapter
    private lateinit var whiteListAdapter: AppListAdapter

    // 权限相关常量
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.PACKAGE_USAGE_STATS, // 应用使用情况权限
        android.Manifest.permission.POST_NOTIFICATIONS    // 通知权限（Android 13+）
    )

    // 权限请求Launcher（针对需要跳转设置页的权限）
    private val overlayPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionSwitches()
            showPermissionResultToast(checkOverlayPermission(), "悬浮窗")
        }

    private val usageStatsPermissionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionSwitches()
            showPermissionResultToast(checkUsageStatsPermission(), "应用使用情况")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化通知通道
        initNotificationChannels()

        // 处理窗口边距
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化界面与权限检查
        initViews()
        loadAppList()
        checkAndRequestPermissions() // 合并批量权限检查
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null // 防止内存泄漏
    }

    /**
     * 初始化所有视图组件和状态
     */
    private fun initViews() {
        // 初始化应用列表适配器
        appListAdapter = AppListAdapter(this, emptyList())
        binding.appRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appListAdapter
        }

        // 初始化白名单适配器
        whiteListAdapter = AppListAdapter(this, emptyList(), true)
        binding.whiteListRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = whiteListAdapter
        }

        // 初始化监控间隔选择Spinner
        val intervalLabels = resources.getStringArray(R.array.monitor_intervals)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalLabels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.intervalSpinner.adapter = spinnerAdapter
        // 设置当前选中的间隔
        val currentInterval = PreferenceUtil.getMonitorInterval(this)
        val intervalIndex = intervalOptions.indexOf(currentInterval).coerceAtLeast(0)
        binding.intervalSpinner.setSelection(intervalIndex)
        // 间隔选择监听
        binding.intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedInterval = intervalOptions[position]
                PreferenceUtil.saveMonitorInterval(this@MainActivity, selectedInterval)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 加载保存的状态
        loadAutoStartState()
        updateServiceSwitchState()
        updatePermissionSwitches()

        // 设置所有交互监听
        setListeners()
    }

    /**
     * 设置所有视图的交互监听
     */
    private fun setListeners() {
        // 自启开关监听
        binding.autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            PreferenceUtil.saveAutoStartState(this, isChecked)
            val message = if (isChecked) "已开启开机自启" else "已关闭开机自启"
            showToast(message)
            if (isChecked) checkAndRequestPermissions() // 开启自启时检查权限
        }

        // 服务开关监听（启动/停止保活服务）
        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val selectedApp = PreferenceUtil.getSelectedAppPackage(this)
                if (selectedApp.isNullOrEmpty()) {
                    showToast("请先选择需要保活的应用")
                    binding.serviceSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (checkRequiredPermissions()) {
                    KeepAliveService.startService(this)
                } else {
                    showToast("请先授予所有必要的权限")
                    binding.serviceSwitch.isChecked = false
                }
            } else {
                KeepAliveService.stopService(this)
            }
        }

        // 悬浮窗权限布局点击
        binding.overlayPermissionLayout.setOnClickListener {
            if (!checkOverlayPermission()) requestOverlayPermission()
        }

        // 通知权限布局点击
        binding.notificationPermissionLayout.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !checkNotificationPermission()) {
                // 通知权限属于危险权限，使用批量请求或单独请求
                checkAndRequestPermissions()
            }
        }

        // 应用使用情况权限布局点击
        binding.usageStatsPermissionLayout.setOnClickListener {
            if (!checkUsageStatsPermission()) requestUsageStatsPermission()
        }
    }

    /**
     * 加载设备上的应用列表（非系统应用）
     */
    private fun loadAppList() {
        CoroutineScope(Dispatchers.IO).launch {
            val apps = AppInfoUtil.getNonSystemApps(this@MainActivity)
            withContext(Dispatchers.Main) {
                appListAdapter.updateList(apps)
                whiteListAdapter.updateList(apps)
            }
        }
    }

    /**
     * 初始化所有通知通道（适配Android 8.0+）
     */
    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 应用通用通知通道
            val appChannel = NotificationChannel(
                CHANNEL_ID_APP,
                "应用通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "用于显示应用启动引导等通知" }

            // 保活服务通知通道
            val keepAliveChannel = NotificationChannel(
                CHANNEL_ID_KEEP_ALIVE,
                "保活服务通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "显示保活服务的运行状态" }

            // 注册通道
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(appChannel)
            notificationManager.createNotificationChannel(keepAliveChannel)
        }
    }

    // ------------------------------ 权限检查与请求 ------------------------------

    /**
     * 批量检查并请求所有必要权限（合并两个版本的权限逻辑）
     */
    private fun checkAndRequestPermissions() {
        // 收集未授予的权限（仅包含可通过requestPermissions请求的权限）
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions,
                PERMISSION_REQUEST_CODE
            )
        } else {
            // 检查需要跳转设置页的权限（悬浮窗、应用使用情况）
            checkSpecialPermissions()
        }
    }

    /**
     * 检查需要跳转设置页的特殊权限
     */
    private fun checkSpecialPermissions() {
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
        } else if (!checkUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            onAllPermissionsGranted()
        }
    }

    /**
     * 检查所有必要权限是否已授予（包括特殊权限）
     */
    private fun checkRequiredPermissions(): Boolean {
        // 检查常规权限
        val normalPermissionsGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        // 检查特殊权限
        return normalPermissionsGranted &&
                checkOverlayPermission() &&
                checkUsageStatsPermission()
    }

    /**
     * 处理权限请求结果（批量请求的回调）
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                checkSpecialPermissions() // 常规权限通过后检查特殊权限
            } else {
                showPermissionDeniedMessage()
            }
            updatePermissionSwitches() // 更新权限开关状态
        }
    }

    /**
     * 悬浮窗权限检查（Android 6.0+）
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // 6.0以下默认有权限
        }
    }

    /**
     * 请求悬浮窗权限（需要跳转设置页）
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    /**
     * 通知权限检查（Android 13+）
     */
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

    /**
     * 应用使用情况权限检查（Android 5.1+）
     */
    private fun checkUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            return mode == android.app.AppOpsManager.MODE_ALLOWED
        }
        return true // 5.1以下无需此权限
    }

    /**
     * 请求应用使用情况权限（需要跳转设置页）
     */
    private fun requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsPermissionLauncher.launch(intent)
        }
    }

    // ------------------------------ 权限回调与状态更新 ------------------------------

    /**
     * 所有权限都已授予时的回调
     */
    private fun onAllPermissionsGranted() {
        showToast("所有必要权限已授予，应用可正常使用")
        // 可在此处初始化需要权限的服务或功能
    }

    /**
     * 权限被拒绝时的提示
     */
    private fun showPermissionDeniedMessage() {
        showToast("部分权限被拒绝，应用可能无法正常工作")
    }

    /**
     * 更新所有权限开关的状态
     */
    private fun updatePermissionSwitches() {
        binding.overlayPermissionSwitch.isChecked = checkOverlayPermission()
        binding.notificationPermissionSwitch.isChecked = checkNotificationPermission()
        binding.usageStatsPermissionSwitch.isChecked = checkUsageStatsPermission()
    }

    // ------------------------------ 其他状态更新与工具方法 ------------------------------

    /**
     * 更新服务开关状态（反映保活服务是否运行）
     */
    private fun updateServiceSwitchState() {
        binding.serviceSwitch.isChecked = false // 实际项目中需通过ServiceConnection判断
    }

    /**
     * 加载保存的自启状态
     */
    private fun loadAutoStartState() {
        binding.autoStartSwitch.isChecked = PreferenceUtil.getAutoStartState(this)
    }

    /**
     * 显示权限请求结果的Toast提示
     */
    private fun showPermissionResultToast(granted: Boolean, permissionName: String) {
        val message = if (granted) {
            "$permissionName 权限已开启"
        } else {
            "$permissionName 权限未开启，部分功能可能受限"
        }
        showToast(message)
    }

    /**
     * 安全显示Toast
     */
    private fun showToast(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 发送通知引导用户启动Activity（适配Android 10+后台限制）
     */
    fun startMainActivityViaNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_APP)
            .setContentTitle("应用已准备就绪")
            .setContentText("点击启动应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(NOTIFICATION_ID_LAUNCH, notification)
        }
    }
}