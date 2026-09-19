package com.ticketkeep.app.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ticketkeep.app.backup.BackupAuthStore
import com.ticketkeep.app.backup.BackupListItem
import com.ticketkeep.app.backup.CloudBackupRepository
import com.ticketkeep.app.data.repository.TicketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 云备份页状态（配合 Screen 状态机分段）：
 * - isLoggedIn=false：UI 仅账号；不暴露列表/恢复主路径。
 * - isLoggedIn=true：备份状态 + 上传 + 列表；恢复由 Screen 二次确认后再调 [prepareRestore]。
 * Base URL 变更仍走 [setBaseUrl]；是否展示由 Screen 的 BuildConfig.DEBUG +「高级」折叠控制。
 */
data class CloudBackupUiState(
    val isPro: Boolean? = null,
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
                // 仅登录后拉列表；未登录不预取，避免误展示恢复入口所需数据
                if (s.isLoggedIn) refreshListQuiet()
                else _ui.value = _ui.value.copy(backups = emptyList())
            }
        }
        viewModelScope.launch {
            ticketRepository.observeIsPro().collect { pro ->
                _ui.value = _ui.value.copy(isPro = pro)
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
        when (val r = repository.uploadEncryptedBackup(passphrase.toCharArray())) {
            is CloudBackupRepository.Result.Ok -> {
                _ui.value = _ui.value.copy(message = "上传成功")
                refreshListQuiet()
            }
            is CloudBackupRepository.Result.Err ->
                _ui.value = _ui.value.copy(message = r.message)
        }
    }

    /** 须在 Screen 二次确认 + 口令校验通过后再调用；下载解密并给出导入预览。 */
    fun prepareRestore(id: String, passphrase: String) = launchBusy {
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
