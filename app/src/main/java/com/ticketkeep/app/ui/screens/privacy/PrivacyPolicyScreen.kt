package com.ticketkeep.app.ui.screens.privacy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ticketkeep.app.R

/**
 * 应用内《隐私政策》全文页：本地展示，不请求网络。
 * 正文与仓库根目录 PRIVACY_POLICY.md 保持一致；联系邮箱为占位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy)) },
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
        Text(
            text = PRIVACY_POLICY_TEXT,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}

// 普通 val：Kotlin const val 不能用多行字符串作初始化
private val PRIVACY_POLICY_TEXT = """
**生效日期：2026-09-13**

欢迎使用「票证记」（以下简称「本应用」）。我们重视你的隐私。本政策说明本应用如何处理与隐私相关的信息。

## 我们收集什么 / 不收集什么

**本应用默认在设备本地运行，不以账号登录为前提。**

我们**不会**主动收集、上传或出售：
- 姓名、电话、身份证号等个人身份信息
- 收据 / 保修卡照片或 OCR 结果（默认仅存本地）
- 可识别个人画像数据

你主动录入或拍摄的内容保存在本设备上。

## 端侧 OCR

拍照识别在**设备端**由 Google ML Kit 完成，结果默认保存在本机，不会上传到我们的服务器（默认无云端同步）。

## 本地存储 / 备份

数据默认本地。本应用不提供云同步或家庭共享。系统备份由你自行保管。

## 通知权限

到期提醒可请求通知权限，可在系统设置关闭。

## 第三方 SDK

使用 Google ML Kit 做端侧 OCR，按其隐私政策；我们不借此回传票证内容。未来若上云会另行说明。

## 权限概要

相机 / 相册、通知、存储（如适用）仅用于对应功能。

## 未成年人 / 政策更新

未成年人请在监护人指导下使用。政策更新会公示生效日期。

## 联系我们

【待填：邮箱】
"""
