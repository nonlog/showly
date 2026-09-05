package com.michaldrabik.ui_base.floppy

import kotlinx.coroutines.sync.Mutex

internal object BridgeSyncExecutionGate {
  val mutex = Mutex()
}
