package com.ticketkeep.app.ui.screens.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ticketkeep.app.billing.BillingConfig
import com.ticketkeep.app.channel.ChannelConfig
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.ui.components.PaperCard
import com.ticketkeep.app.ui.theme.MintPrimaryContainer

/**
 * Pro 升级页：展示普通与会员差异、价格，并按渠道提供购买/恢复或 Play 引导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    onOpenPrivacy: (() -> Unit)? = null,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val priceFormatted by viewModel.priceFormatted.collectAsStateWithLifecycle()
    val productDetails by viewModel.productDetails.collectAsStateWithLifecycle()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("升级 Pro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("票证记 Pro", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "突破 10 条上限，导出与索赔包一次备齐",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FreeVsProCompareCard(priceFormatted = priceFormatted)
            Spacer(Modifier.height(24.dp))

            if (!ChannelConfig.showProPurchase) {
                Text(
                    "当前为国内渠道包，应用内无法通过 Google Play 开通会员。完整会员与导出请使用 Google Play 版。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "免费额度仍为 " + TicketRepository.FREE_TICKET_LIMIT + " 条，本地功能可用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            if (isPro) {
                Text(
                    "你已是 Pro 会员",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (ChannelConfig.showProPurchase && productDetails == null && !purchaseInProgress && !isPro) {
                Text(
                    "暂未从 Google Play 获取到商品，请确认应用已上架内测且已创建订阅 ${BillingConfig.PRODUCT_ID}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (purchaseInProgress) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
            }

            if (ChannelConfig.showProPurchase && !isPro) {
                Button(
                    onClick = { viewModel.purchase(activity) },
                    enabled = !purchaseInProgress && productDetails != null && activity != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (!priceFormatted.isNullOrBlank()) {
                            "开通 Pro · $priceFormatted"
                        } else {
                            "开通 Pro"
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
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("恢复购买") }
            }

            if (ChannelConfig.showProPurchase && viewModel.isDebug) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "调试",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (isPro) {
                    OutlinedButton(
                        onClick = { viewModel.debugSetPro(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("关闭 Pro（调试）") }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.debugSetPro(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("模拟开通 Pro（调试）") }
                }
            }

            if (onOpenPrivacy != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("隐私政策")
                }
            }
        }
    }
}


/**
 * 普通 vs Pro 权益对比卡（设计桥规范）：纸感白卡、Pro 列 `#CCFBF1` 通栏圆角、分组疏排。
 * ✓：普通列 onSurfaceVariant；Pro 列 primary。—：outline，不用 error 红。
 */
@Composable
private fun FreeVsProCompareCard(priceFormatted: String?) {
    val limit = TicketRepository.FREE_TICKET_LIMIT
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "权益对比",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // 功能列
                Column(modifier = Modifier.weight(1.6f)) {
                    CompareSideHeader("功能", emphasize = false)
                    Spacer(Modifier.height(16.dp))
                    CompareGroupTitle("基础能力")
                    CompareLabelCell("拍照 OCR")
                    CompareLabelCell("保修提醒")
                    CompareLabelCell("本地存储")
                    Spacer(Modifier.height(16.dp))
                    CompareGroupTitle("Pro 解锁")
                    CompareLabelCell("票证数量")
                    CompareLabelCell("单张导出 PDF")
                    CompareLabelCell("全量导出 CSV")
                    CompareLabelCell("CSV 导入/恢复")
                    CompareLabelCell("保修索赔包 PDF")
                }
                // 普通列
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompareSideHeader("普通", emphasize = false)
                    Spacer(Modifier.height(16.dp))
                    CompareGroupTitle(" ") // 占位对齐
                    CompareIconCell(ok = true, emphasize = false)
                    CompareIconCell(ok = true, emphasize = false)
                    CompareIconCell(ok = true, emphasize = false)
                    Spacer(Modifier.height(16.dp))
                    CompareGroupTitle(" ")
                    CompareTextCell("最多 $limit 条", emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                    CompareIconCell(ok = false, emphasize = false)
                }
                // Pro 列通栏薄荷底
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MintPrimaryContainer)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompareSideHeader("Pro", emphasize = true)
                    Spacer(Modifier.height(16.dp))
                    CompareGroupTitle(" ")
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    Spacer(Modifier.height(16.dp))
                    CompareGroupTitle(" ")
                    CompareTextCell("无限", emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                    CompareIconCell(ok = true, emphasize = true)
                }
            }

            if (!priceFormatted.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "年订阅 · $priceFormatted",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
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
private fun CompareGroupTitle(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompareRowHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isNotBlank()) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.width(20.dp).height(20.dp),
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

/** 从 Compose Context 向上查找 Activity（Billing 购买流需要）。 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? Activity
}
