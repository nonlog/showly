package com.michaldrabik.showly2.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.michaldrabik.ui_base.trakt.TraktSyncWorker

class BridgeDebugReceiver : BroadcastReceiver() {

  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    if (intent.action != context.packageName + ACTION_SUFFIX) return
    TraktSyncWorker.scheduleOneOff(
      workManager = WorkManager.getInstance(context),
      isImport = true,
      isExport = true,
      isSilent = true,
    )
  }

  companion object {
    private const val ACTION_SUFFIX = ".BRIDGE_SYNC"
  }
}
