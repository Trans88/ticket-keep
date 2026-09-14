package com.ticketkeep.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ticketkeep.app.MainActivity
import com.ticketkeep.app.R

/**
 * 通知设置辅助：保修渠道创建、测试通知、应用通知设置与电池优化跳转。
 * 渠道 ID 与 [WarrantyReminderWorker.CHANNEL_ID] 保持一致。
 */
object NotificationSettingsHelper {

    const val TEST_NOTIFICATION_ID = 90001

    /** 确保保修提醒渠道存在（Android O+）。 */
    fun ensureWarrantyChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            WarrantyReminderWorker.CHANNEL_ID,
            context.getString(R.string.notification_channel_warranty),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    /** 当前是否已授予通知权限（API 33 以下视为已授予）。 */
    fun areNotificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 发送一条一次性测试通知（中文标题/正文），使用既有保修渠道。 */
    fun postTestNotification(context: Context) {
        ensureWarrantyChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, WarrantyReminderWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("票证记 · 测试通知")
            .setContentText("若你能看到这条，说明通知权限与渠道工作正常。")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("若你能看到这条，说明通知权限与渠道工作正常。保修到期提醒会走同一渠道。"),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
    }

    /** 打开系统「应用通知」设置页。 */
    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 是否已忽略电池优化。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 请求忽略电池优化；失败则回退到系统电池优化列表页。
     * @return true 表示已成功拉起某一设置页
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openBatteryOptimizationSettings(context)
        }
    }

    /** 打开系统「忽略电池优化」列表（通用回退）。 */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
