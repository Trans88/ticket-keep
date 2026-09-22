package com.ticketkeep.app.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.backup.BackupAuthStore
import com.ticketkeep.app.backup.BackupListItem
import com.ticketkeep.app.backup.CloudBackupRepository
import com.ticketkeep.app.data.repository.TicketRepository
import com.ticketkeep.app.entitlement.EntitlementRepository
import com.ticketkeep.app.entitlement.EntitlementSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 云备份页状态。
 *
 * 云上传/恢复门禁看 [canUseCloud]，**不是**本地高级版。
 * 退出备份账号不得清除本地买断（本 VM 只调 [CloudBackupRepository.logout]）。
 */
data class CloudBackupUiState(
    /** @deprecated 兼容旧 UI；请用 [canUseCloud] / [hasLocalPremium]。 */
    val isPro: Boolean? = null,
    val hasLocalPremium: Boolean? = null,
    val canUseCloud: Boolean? = null,
    val email: String = "",
    val isLoggedIn: Boolean = false,
    val baseUrl: String = BackupAuthStore.DEFAULT_BASE_URL,
    val backups: List<BackupListItem> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val restorePreview: CloudBackupRepository.RestorePreview? = null,
    val pendingRestoreId: String? = null,
)

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val repository: CloudBackupRepository,
    private val authStore: BackupAuthStore,
    private val ticketRepository: TicketRepository,
    private val entitlementRepository: EntitlementRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        CloudBackupUiState(baseUrl = authStore.currentBaseUrl()),
    )
    val uiState: StateFlow<CloudBackupUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            authStore.session.collect { s ->
                _ui.value = _ui.value.copy(
                    email = s.email.orEmpty(),
                    isLoggedIn = s.isLoggedIn,
                    baseUrl = s.baseUrl,
                )
                if (s.isLoggedIn) refreshListQuiet()
                else _ui.value = _ui.value.copy(backups = emptyList())
            }
        }
        viewModelScope.launch {
            entitlementRepository.observe().collect { snap: EntitlementSnapshot ->
                _ui.value = _ui.value.copy(
                    isPro = snap.canUploadCloud, // 旧字段：云门禁语义，避免误用本地 Pro 踢出
                    hasLocalPremium = snap.hasLocalPremium,
                    canUseCloud = snap.canUploadCloud,
                )
            }
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun showMessage(msg: String) {
        _ui.value = _ui.value.copy(message = msg)
    }

    fun setBaseUrl(url: String) {
        authStore.setBaseUrl(url)
        _ui.value = _ui.value.copy(baseUrl = authStore.currentBaseUrl(), message = "已更新服务器地址")
    }

    fun register(email: String, password: String) = launchBusy {
        when (val r = repository.register(email, password)) {
            is CloudBackupRepository.Result.Ok -> {
                _ui.value = _ui.value.copy(message = "注册成功")
                refreshListQuiet()
            }
            is CloudBackupRepository.Result.Err -> _ui.value = _ui.value.copy(message = r.message)
        }
    }

    fun login(email: String, password: String) = launchBusy {
        when (val r = repository.login(email, password)) {
            is CloudBackupRepository.Result.Ok -> {
                _ui.value = _ui.value.copy(message = "登录成功")
                refreshListQuiet()
            }
            is CloudBackupRepository.Result.Err -> _ui.value = _ui.value.copy(message = r.message)
        }
    }

    /** 登出备份账号：只清会话，**不**清本地买断。 */
    fun logout() {
        repository.logout()
        _ui.value = _ui.value.copy(backups = emptyList(), message = "已登出")
    }

    fun refreshList() = launchBusy {
        when (val r = repository.listBackups()) {
            is CloudBackupRepository.Result.Ok ->
                _ui.value = _ui.value.copy(backups = r.value, message = "已刷新列表")
            is CloudBackupRepository.Result.Err ->
                _ui.value = _ui.value.copy(message = r.message)
        }
    }

    private suspend fun refreshListQuiet() {
        when (val r = repository.listBackups()) {
            is CloudBackupRepository.Result.Ok ->
                _ui.value = _ui.value.copy(backups = r.value)
            is CloudBackupRepository.Result.Err -> { /* 静默 */ }
        }
    }

    fun upload(passphrase: String) = launchBusy {
        if (_ui.value.canUseCloud != true) {
            _ui.value = _ui.value.copy(
                message = "云备份服务未开通或已到期，无法上传。本地票证与本地高级版不受影响。",
            )
            return@launchBusy
        }
        when (val r = repository.uploadEncryptedBackup(passphrase.toCharArray())) {
            is CloudBackupRepository.Result.Ok -> {
                _ui.value = _ui.value.copy(message = "上传成功")
                refreshListQuiet()
            }
            is CloudBackupRepository.Result.Err ->
                _ui.value = _ui.value.copy(message = r.message)
        }
    }

    fun prepareRestore(id: String, passphrase: String) = launchBusy {
        if (_ui.value.canUseCloud != true) {
            _ui.value = _ui.value.copy(
                message = "云备份服务未开通或已到期，暂不可恢复。本地数据不会被删除。",
            )
            return@launchBusy
        }
        when (val r = repository.prepareRestore(id, passphrase.toCharArray())) {
            is CloudBackupRepository.Result.Ok ->
                _ui.value = _ui.value.copy(
                    restorePreview = r.value,
                    pendingRestoreId = id,
                    message = null,
                )
            is CloudBackupRepository.Result.Err ->
                _ui.value = _ui.value.copy(message = r.message)
        }
    }

    fun dismissRestorePreview() {
        _ui.value = _ui.value.copy(restorePreview = null, pendingRestoreId = null)
    }

    fun confirmRestore() = launchBusy {
        val preview = _ui.value.restorePreview ?: return@launchBusy
        when (val r = repository.confirmRestore(preview)) {
            is CloudBackupRepository.Result.Ok ->
                _ui.value = _ui.value.copy(
                    restorePreview = null,
                    pendingRestoreId = null,
                    message = "已导入 ${r.value} 条票证（未覆盖本地原有数据）",
                )
            is CloudBackupRepository.Result.Err ->
                _ui.value = _ui.value.copy(message = r.message)
        }
    }

    fun deleteBackup(id: String) = launchBusy {
        when (val r = repository.deleteBackup(id)) {
            is CloudBackupRepository.Result.Ok -> {
                _ui.value = _ui.value.copy(message = "已删除")
                refreshListQuiet()
            }
            is CloudBackupRepository.Result.Err ->
                _ui.value = _ui.value.copy(message = r.message)
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, message = null)
            try {
                block()
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }
}
