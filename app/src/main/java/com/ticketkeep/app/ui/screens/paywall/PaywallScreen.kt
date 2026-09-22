package com.ticketkeep.app.ui.screens.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.entitlement.EntitlementSnapshot
import com.ticketkeep.app.entitlement.LocalUnlockStatus
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.theme.TicketKeepRadius
import com.ticketkeep.app.ui.theme.TicketKeepSpacing
import kotlinx.coroutines.launch

/**
 * 权益页 Phase 1：上下两张独立方案卡 —
 * 1) 票证记高级版（一次购买，不含云备份）
 * 2) 加密云备份（单独年费，不自动解锁本地高级）
 *
 * 新商品销售关闭时 CTA 显示「商品待配置」。China 渠道仍隐藏购买入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    onOpenPrivacy: (() -> Unit)? = null,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val hasLocal by viewModel.hasLocalPremium.collectAsStateWithLifecycle()
    val canCloud by viewModel.canUseCloud.collectAsStateWithLifecycle()
    val ticketCount by viewModel.ticketCount.collectAsStateWithLifecycle()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val restoreMessage by viewModel.lastRestoreMessage.collectAsStateWithLifecycle()
    val localPriceRaw by viewModel.localPriceFormatted.collectAsStateWithLifecycle()
    val localPrice = viewModel.resolveLocalPrice(localPriceRaw)
    val cloudPriceRaw by viewModel.cloudPriceFormatted.collectAsStateWithLifecycle()
    val cloudPrice = viewModel.resolveCloudPrice(cloudPriceRaw)
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val freeLimit = TicketRepository.FREE_TICKET_LIMIT
    val salesOn = viewModel.newProductSalesEnabled

    LaunchedEffect(errorMessage) {
        val msg = errorMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(sanitizeUserError(msg))
        viewModel.clearError()
    }
    LaunchedEffect(restoreMessage) {
        val msg = restoreMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearRestoreMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TicketKeepSpacing.page),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .height(48.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "升级与备份",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(TicketKeepSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = statusSummary(entitlement),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "已有 $ticketCount 张票证",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(TicketKeepSpacing.lg))

            Text(
                "普通版也有：拍照识别 · 手动录入 · 搜索筛选 · 保修提醒 · 本地保存",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "免费版最多 $freeLimit 张；以下两项权益相互独立。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(TicketKeepSpacing.section))

            // ——— 卡 1：票证记高级版 ———
            LocalPremiumCard(
                owned = hasLocal || entitlement.localStatus == LocalUnlockStatus.OWNED ||
                    entitlement.legacyYearlyActive,
                salesEnabled = ChannelConfig.showProPurchase && salesOn,
                channelAllowsPurchase = ChannelConfig.showProPurchase,
                priceFormatted = localPrice,
                purchaseInProgress = purchaseInProgress,
                onPurchase = { viewModel.purchaseLocal(activity) },
            )

            Spacer(Modifier.height(TicketKeepSpacing.lg))

            // ——— 卡 2：加密云备份 ———
            CloudBackupCard(
                active = canCloud,
                salesEnabled = ChannelConfig.showProPurchase && salesOn,
                channelAllowsPurchase = ChannelConfig.showProPurchase,
                priceFormatted = cloudPrice,
                purchaseInProgress = purchaseInProgress,
                onPurchase = { viewModel.purchaseCloud(activity) },
            )

            Spacer(Modifier.height(TicketKeepSpacing.section))

            if (!ChannelConfig.showProPurchase) {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TicketKeepSpacing.lg)) {
                        Text(
                            "当前渠道暂未开放购买",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "完整购买与导出请使用 Google Play 版。" +
                                "本地查阅与编辑已有票证仍可用。",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(TicketKeepSpacing.lg))
            }

            if (purchaseInProgress && ChannelConfig.showProPurchase) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    "正在确认购买，请稍候",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (ChannelConfig.showProPurchase) {
                OutlinedButton(
                    onClick = { viewModel.restore() },
                    enabled = !purchaseInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(TicketKeepRadius.button),
                ) { Text("恢复购买") }
            }

            if (viewModel.isDebug) {
                Spacer(Modifier.height(TicketKeepSpacing.lg))
                DebugEntitlementStrip(
                    hasLocal = hasLocal,
                    canCloud = canCloud,
                    onLocal = { on ->
                        viewModel.debugSetLocal(on)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (on) "已模拟本地高级版" else "已关闭本地高级版模拟",
                            )
                        }
                    },
                    onCloud = { on ->
                        viewModel.debugSetCloud(on)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (on) "已模拟云备份有效" else "已关闭云备份模拟",
                            )
                        }
                    },
                    onAllOff = {
                        viewModel.debugAllOff()
                        scope.launch { snackbarHostState.showSnackbar("已全部关闭模拟权益") }
                    },
                )
            }

            if (onOpenPrivacy != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("隐私政策") }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LocalPremiumCard(
    owned: Boolean,
    salesEnabled: Boolean,
    channelAllowsPurchase: Boolean,
    priceFormatted: String?,
    purchaseInProgress: Boolean,
    onPurchase: () -> Unit,
) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TicketKeepSpacing.lg)) {
            Text(
                "票证记高级版",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "一次购买，无周期续费",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "不含云备份",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FeatureLine("不限票证条数")
            FeatureLine("PDF / 送修材料导出")
            FeatureLine("CSV 导入与导出")
            Spacer(Modifier.height(14.dp))
            when {
                owned -> {
                    Text(
                        "已解锁",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                !channelAllowsPurchase -> {
                    Text(
                        "当前渠道暂未开放购买",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !salesEnabled -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(TicketKeepRadius.button),
                    ) { Text("商品待配置") }
                }
                else -> {
                    if (!priceFormatted.isNullOrBlank()) {
                        Text(
                            priceFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "价格以 Google Play 为准",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onPurchase,
                        enabled = !purchaseInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(TicketKeepRadius.button),
                    ) {
                        Text(
                            if (!priceFormatted.isNullOrBlank()) {
                                "解锁高级版 · $priceFormatted"
                            } else {
                                "解锁高级版"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBackupCard(
    active: Boolean,
    salesEnabled: Boolean,
    channelAllowsPurchase: Boolean,
    priceFormatted: String?,
    purchaseInProgress: Boolean,
    onPurchase: () -> Unit,
) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TicketKeepSpacing.lg)) {
            Text(
                "加密云备份",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "单独付费，不自动解锁本地高级功能",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            FeatureLine("手动加密备份到云端")
            FeatureLine("换机时解密恢复")
            Spacer(Modifier.height(8.dp))
            Text(
                "空间、副本数量与续费形式以正式商品为准；配置完成前不展示虚构价格或额度。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            when {
                active -> {
                    Text(
                        "云备份服务有效",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                !channelAllowsPurchase -> {
                    Text(
                        "当前渠道暂未开放购买",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !salesEnabled -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(TicketKeepRadius.button),
                    ) { Text("商品待配置") }
                }
                else -> {
                    if (!priceFormatted.isNullOrBlank()) {
                        Text(
                            priceFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "计费周期以 Google Play 为准",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = onPurchase,
                        enabled = !purchaseInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(TicketKeepRadius.button),
                    ) {
                        Text(
                            if (!priceFormatted.isNullOrBlank()) {
                                "开通云备份 · $priceFormatted"
                            } else {
                                "开通云备份"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        )
    }
}

@Composable
private fun DebugEntitlementStrip(
    hasLocal: Boolean,
    canCloud: Boolean,
    onLocal: (Boolean) -> Unit,
    onCloud: (Boolean) -> Unit,
    onAllOff: () -> Unit,
) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TicketKeepSpacing.lg)) {
            Text(
                "调试",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onLocal(!hasLocal) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(TicketKeepRadius.button),
            ) {
                Text(if (hasLocal) "关闭「模拟本地高级版」" else "模拟本地高级版")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onCloud(!canCloud) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(TicketKeepRadius.button),
            ) {
                Text(if (canCloud) "关闭「模拟云备份有效」" else "模拟云备份有效")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAllOff,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(TicketKeepRadius.button),
            ) { Text("全部关闭") }
        }
    }
}

private fun statusSummary(s: EntitlementSnapshot): String {
    val local = when {
        s.hasLocalPremium -> "本地高级版：已解锁"
        else -> "本地高级版：未解锁"
    }
    val cloud = when {
        s.canUploadCloud -> "云备份：有效"
        else -> "云备份：未开通/已到期"
    }
    return "$local · $cloud"
}

private fun sanitizeUserError(raw: String): String {
    val lower = raw.lowercase()
    if ("product" in lower || "sku" in lower || "billingconfig" in lower) {
        return "暂时无法完成购买，请稍后重试"
    }
    return raw
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? Activity
}
