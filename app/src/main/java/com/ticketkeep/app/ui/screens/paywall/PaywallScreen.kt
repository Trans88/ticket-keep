package com.ticketkeep.app.ui.screens.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.billing.BillingConnectionState
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.theme.DarkHeroBackground
import com.ticketkeep.app.ui.theme.DarkOnHero
import com.ticketkeep.app.ui.theme.DarkOnHeroSecondary
import com.ticketkeep.app.ui.theme.LightAccent
import com.ticketkeep.app.ui.theme.LightHeroBackground
import com.ticketkeep.app.ui.theme.LightOnHero
import com.ticketkeep.app.ui.theme.LightOnHeroSecondary
import com.ticketkeep.app.ui.theme.TicketKeepRadius
import com.ticketkeep.app.ui.theme.TicketKeepSpacing
import kotlinx.coroutines.launch

/**
 * Pro 页 SoT：状态行 + 深绿 Hero + 三列对比 + 普通版也有 + Billing 价格区。
 * China（[ChannelConfig.showProPurchase] 为 false）不展示购买/恢复，仅提示渠道未开放。
 * Debug 底部可模拟 Pro/普通版，走 [PaywallViewModel.debugSetPro]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    onOpenPrivacy: (() -> Unit)? = null,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val ticketCount by viewModel.ticketCount.collectAsStateWithLifecycle()
    val priceFormatted by viewModel.priceFormatted.collectAsStateWithLifecycle()
    val productDetails by viewModel.productDetails.collectAsStateWithLifecycle()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val freeLimit = TicketRepository.FREE_TICKET_LIMIT

    val priceLoading = ChannelConfig.showProPurchase &&
        !isPro &&
        productDetails == null &&
        (
            purchaseInProgress ||
                connectionState == BillingConnectionState.CONNECTING ||
                connectionState == BillingConnectionState.DISCONNECTED
            )
    val priceFailed = ChannelConfig.showProPurchase &&
        !isPro &&
        productDetails == null &&
        !purchaseInProgress &&
        (
            connectionState == BillingConnectionState.CONNECTED ||
                connectionState == BillingConnectionState.UNAVAILABLE
            )

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
            // 顶栏：statusBarsPadding 一次 + 紧顶，对齐详情/我的
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
                    "票证记 Pro",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(TicketKeepSpacing.sm))

            // 状态行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPro) "当前：Pro" else "当前：普通版 · 免费",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "已有 $ticketCount 张票证",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(TicketKeepSpacing.lg))

            ProHeroCard(freeLimit = freeLimit)

            Spacer(Modifier.height(TicketKeepSpacing.section))

            Text(
                "升级后有什么不同？",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(TicketKeepSpacing.sm))
            FreeVsProCompareCard(freeLimit = freeLimit)

            Spacer(Modifier.height(TicketKeepSpacing.section))

            Text(
                "这些功能，普通版也有",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(TicketKeepSpacing.sm))
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TicketKeepSpacing.lg)) {
                    Text(
                        "拍照识别 · 手动录入 · 搜索筛选",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "保修提醒 · 本地保存 · 查看编辑",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(TicketKeepSpacing.section))

            if (!ChannelConfig.showProPurchase) {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(TicketKeepSpacing.lg)) {
                        Text(
                            "当前渠道暂未开放购买",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前版本最多保存 $freeLimit 张，暂未开放会员购买。" +
                                "完整会员与导出请使用 Google Play 版。" +
                                "本地查阅与编辑已有票证仍可用。",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(TicketKeepSpacing.lg))
            }

            if (isPro) {
                Text(
                    "你已开通 Pro",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (ChannelConfig.showProPurchase && !isPro) {
                when {
                    priceLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "正在获取价格…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    priceFailed || productDetails == null -> {
                        Text(
                            "暂时无法获取价格",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text("重新获取价格")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    !priceFormatted.isNullOrBlank() -> {
                        Text(
                            priceFormatted!!,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "价格与计费周期以 Google Play 为准",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    sanitizeUserError(errorMessage!!),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
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

            if (ChannelConfig.showProPurchase && !isPro) {
                Button(
                    onClick = { viewModel.purchase(activity) },
                    enabled = !purchaseInProgress && productDetails != null && activity != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(TicketKeepRadius.button),
                ) {
                    Text(
                        when {
                            productDetails == null -> "开通 Pro"
                            !priceFormatted.isNullOrBlank() -> "开通 Pro · $priceFormatted"
                            else -> "开通 Pro"
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
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
                DebugProStrip(
                    isPro = isPro,
                    onSetPro = { enabled ->
                        viewModel.debugSetPro(enabled)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (enabled) "已切换为 Pro" else "已切换为普通版",
                            )
                        }
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

/**
 * 深绿 Hero：TICKETKEEP / PRO +「从 10 张，到每一张。」+ 容量/导出/云备份说明 + 不限数量 pill。
 */
@Composable
private fun ProHeroCard(freeLimit: Int) {
    val dark = isSystemInDarkTheme()
    val heroBg = if (dark) DarkHeroBackground else LightHeroBackground
    val onHero = if (dark) DarkOnHero else LightOnHero
    val onHeroSecondary = if (dark) DarkOnHeroSecondary else LightOnHeroSecondary
    val eyebrow = if (dark) onHeroSecondary else androidx.compose.ui.graphics.Color(0xFFD0DDCF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TicketKeepRadius.card))
            .background(heroBg)
            .padding(22.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "TICKETKEEP / PRO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                ),
                color = eyebrow,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "从 $freeLimit 张，到每一张。",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                ),
                color = onHero,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "突破免费容量，导出 PDF / CSV / 送修材料，并支持加密云备份。",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = onHeroSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(TicketKeepRadius.badge),
                color = LightAccent,
            ) {
                Text(
                    "不限票证数量",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = LightHeroBackground,
                )
            }
        }
    }
}

/**
 * 三列对比：功能 | 普通版 | Pro（Pro 列浅绿底）。
 */
@Composable
private fun FreeVsProCompareCard(freeLimit: Int) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TicketKeepSpacing.md)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1.5f)) {
                    CompareSideHeader("功能", emphasize = false)
                    CompareLabelCell("票证数量")
                    CompareLabelCell("PDF · 送修材料")
                    CompareLabelCell("CSV")
                    CompareLabelCell("加密云备份")
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompareSideHeader("普通版", emphasize = false)
                    CompareTextCell("$freeLimit", emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                }
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompareSideHeader("Pro", emphasize = true)
                    CompareTextCell("不限", emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                }
            }
        }
    }
}

/**
 * Debug 专用：模拟普通用户 / 模拟 Pro 用户。
 */
@Composable
private fun DebugProStrip(
    isPro: Boolean,
    onSetPro: (Boolean) -> Unit,
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
                onClick = { onSetPro(false) },
                enabled = isPro,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(TicketKeepRadius.button),
            ) { Text("模拟普通用户") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSetPro(true) },
                enabled = !isPro,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(TicketKeepRadius.button),
            ) { Text("模拟 Pro 用户") }
        }
    }
}

private fun sanitizeUserError(raw: String): String {
    val lower = raw.lowercase()
    if ("product" in lower || "sku" in lower || "billingconfig" in lower) {
        return "暂时无法完成购买，请稍后重试"
    }
    return raw
}

private val CompareRowHeight = 40.dp

@Composable
private fun CompareSideHeader(text: String, emphasize: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompareRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (emphasize) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CompareLabelCell(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompareRowHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CompareIconCell(ok: Boolean, emphasize: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompareRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.Check else Icons.Filled.Remove,
            contentDescription = if (ok) "支持" else "不支持",
            tint = when {
                !ok -> MaterialTheme.colorScheme.outline
                emphasize -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CompareTextCell(text: String, emphasize: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompareRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (emphasize) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? Activity
}
