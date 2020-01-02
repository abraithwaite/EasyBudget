package com.benoitletondor.easybudgetapp.view.settings.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import com.benoitletondor.easybudgetapp.auth.Auth
import com.benoitletondor.easybudgetapp.auth.AuthState
import com.benoitletondor.easybudgetapp.auth.CurrentUser
import com.benoitletondor.easybudgetapp.cloudstorage.CloudStorage
import com.benoitletondor.easybudgetapp.db.DB
import com.benoitletondor.easybudgetapp.helper.*
import com.benoitletondor.easybudgetapp.parameters.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import org.koin.java.KoinJavaComponent.get
import java.lang.Exception
import java.lang.RuntimeException

class BackupSettingsViewModel(private val auth: Auth,
                              private val parameters: Parameters,
                              private val appContext: Context) : ViewModel() {

    val cloudBackupStateStream: MutableLiveData<BackupCloudStorageState> = MutableLiveData()
    val backupNowErrorEvent = SingleLiveEvent<Throwable>()
    val restorationErrorEvent = SingleLiveEvent<Throwable>()
    val previousBackupAvailableEvent = SingleLiveEvent<Date>()
    val appRestartEvent = SingleLiveEvent<Unit>()
    
    private var backupInProgress = false
    private var restorationInProgress = false

    private val backupJobObserver = Observer<List<WorkInfo>> {
        cloudBackupStateStream.value = computeBackupCloudStorageState(auth.state.value)
    }
    private val authStateObserver = Observer<AuthState> {
        if( it is AuthState.Authenticated ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        parameters.saveLastBackupDate(getBackupDBMetaData(get(CloudStorage::class.java), auth).lastUpdateDate)
                    } catch (e: Throwable) {
                        Log.e(
                            "BackupSettingsViewModel",
                            "Error getting last backup date",
                            e
                        )
                    }
                }

                cloudBackupStateStream.value = computeBackupCloudStorageState(it)
            }
        } else {
            cloudBackupStateStream.value = computeBackupCloudStorageState(it)
        }
    }

    init {
        auth.state.observeForever(authStateObserver)
        getBackupJobInfosLiveData(appContext).observeForever(backupJobObserver)
    }

    override fun onCleared() {
        auth.state.removeObserver(authStateObserver)
        getBackupJobInfosLiveData(appContext).removeObserver(backupJobObserver)

        super.onCleared()
    }


    fun onAuthenticateButtonPressed(activity: Activity) {
        auth.startAuthentication(activity)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        auth.handleActivityResult(requestCode, resultCode, data)
    }

    fun onLogoutButtonPressed() {
        parameters.saveLastBackupDate(null)
        parameters.setBackupEnabled(false)
        unscheduleBackup(appContext)

        auth.logout()
    }

    private fun computeBackupCloudStorageState(authState: AuthState?): BackupCloudStorageState {
        return when(authState) {
            AuthState.NotAuthenticated -> BackupCloudStorageState.NotAuthenticated
            AuthState.Authenticating -> BackupCloudStorageState.Authenticating
            is AuthState.Authenticated -> {
                if( backupInProgress ) {
                    BackupCloudStorageState.BackupInProgress(authState.currentUser)
                } else {
                    if( parameters.isBackupEnabled() ) {
                        val lastBackupDate = parameters.getLastBackupDate()
                        val backupNowAvailable = lastBackupDate == null || lastBackupDate.isOlderThanADay()

                        BackupCloudStorageState.Activated(authState.currentUser, lastBackupDate, backupNowAvailable)
                    } else {
                        BackupCloudStorageState.NotActivated(authState.currentUser)
                    }
                }

            }
            null -> BackupCloudStorageState.NotAuthenticated
        }
    }

    fun onBackupActivated() {
        parameters.setBackupEnabled(true)
        val newBackupState = computeBackupCloudStorageState(auth.state.value)
        cloudBackupStateStream.value = newBackupState

        if( newBackupState is BackupCloudStorageState.Activated ) {
            val lastBackupDate = newBackupState.lastBackupDate
            if( lastBackupDate != null ) {
                previousBackupAvailableEvent.value = lastBackupDate
            }
        }

        scheduleBackup(appContext)
    }

    fun onBackupDeactivated() {
        parameters.setBackupEnabled(false)
        cloudBackupStateStream.value = computeBackupCloudStorageState(auth.state.value)

        unscheduleBackup(appContext)
    }

    fun onBackupNowButtonPressed() {
        viewModelScope.launch {
            backupInProgress = true
            cloudBackupStateStream.value = computeBackupCloudStorageState(auth.state.value)

            try {
                withContext(Dispatchers.IO) {
                    val result = backupDB(appContext, get(DB::class.java), get(CloudStorage::class.java), auth, parameters)
                    if( result !is ListenableWorker.Result.Success ) {
                        throw RuntimeException(result.toString())
                    }
                }
            } catch (error: Throwable) {
                Log.e("BackupSettingsViewModel", "Error while backup now", error)
                backupNowErrorEvent.value = error
            } finally {
                backupInProgress = false
                cloudBackupStateStream.value = computeBackupCloudStorageState(auth.state.value)
            }
        }
    }

    fun onRestorePreviousBackupButtonPressed() {
        restoreBackup()
    }

    fun onIgnorePreviousBackupButtonPressed() {
        // No-op
    }

    private fun restoreBackup() {
        viewModelScope.launch {
            restorationInProgress = true
            cloudBackupStateStream.value = computeBackupCloudStorageState(auth.state.value)

            try {
                withContext(Dispatchers.IO) {
                    restoreLatestDBBackup(appContext, auth, get(CloudStorage::class.java))
                }

                appRestartEvent.postValue(Unit)
            } catch (error: Throwable) {
                Log.e("BackupSettingsViewModel", "Error while restoring", error)
                restorationErrorEvent.value = error
            } finally {
                restorationInProgress = false
                cloudBackupStateStream.value = computeBackupCloudStorageState(auth.state.value)
            }
        }
    }

    private fun Date.isOlderThanADay(): Boolean {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        return calendar.time.after(this)
    }
}

sealed class BackupCloudStorageState {
    object NotAuthenticated : BackupCloudStorageState()
    object Authenticating : BackupCloudStorageState()
    data class NotActivated(val currentUser: CurrentUser) : BackupCloudStorageState()
    data class Activated(val currentUser: CurrentUser, val lastBackupDate: Date?, val backupNowAvailable: Boolean): BackupCloudStorageState()
    data class BackupInProgress(val currentUser: CurrentUser): BackupCloudStorageState()
    data class RestorationInProgress(val currentUser: CurrentUser): BackupCloudStorageState()
}
