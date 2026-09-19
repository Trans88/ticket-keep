package com.ticketkeep.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 轻量触发临期小组件刷新：票证增删改成功后调用。
 * 使用独立 IO scope，不阻塞仓库协程。
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshExpiringWidget() {
        scope.launch {
            runCatching {
                ExpiringTicketsWidget().updateAll(context)
            }
        }
    }
}
