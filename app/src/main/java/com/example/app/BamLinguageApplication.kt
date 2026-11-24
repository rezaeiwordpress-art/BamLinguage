package com.example.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BamLinguageApplication : Application() {
  lateinit var container: AppContainer
    private set

  private val applicationScope = CoroutineScope(SupervisorJob())

  override fun onCreate() {
    super.onCreate()
    container = DefaultAppContainer(this)
    applicationScope.launch(container.dispatchers.io) {
      runCatching { container.ensureBundledSamplePackageUseCase(forceReload = false) }
    }
  }

  override fun onTerminate() {
    super.onTerminate()
    if (::container.isInitialized) {
      container.ttsEngine.shutdown()
    }
  }
}
