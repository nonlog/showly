package com.michaldrabik.ui_base.floppy

import com.michaldrabik.data_local.database.model.BridgeSyncState
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import com.michaldrabik.data_remote.trakt.AuthorizedTraktRemoteDataSource
import com.michaldrabik.repository.bridge.BridgeRetryRepository
import com.michaldrabik.repository.bridge.BridgeSyncStateRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloppyBridgeIdentityGuard @Inject constructor(
  private val traktSource: AuthorizedTraktRemoteDataSource,
  private val floppySource: FloppyRemoteDataSource,
  private val stateRepository: BridgeSyncStateRepository,
  private val retryRepository: BridgeRetryRepository,
) {

  companion object {
    private const val META_DOMAIN = "__bridge_meta__"
    private const val IDENTITY_KEY = "remote_identity"
  }

  private var cachedFingerprint: String? = null

  suspend fun ensureCurrent() {
    val config = floppySource.getConfig()
    val traktUser = traktSource.fetchMyProfile().username
    val fingerprint = sha256(
      listOf(
        traktUser,
        config.baseUrl.trim().trimEnd('/'),
        config.apiKey,
      ).joinToString("\u0000"),
    )
    if (cachedFingerprint == fingerprint) return

    val stored = stateRepository.get(META_DOMAIN, IDENTITY_KEY)?.resolvedValue
    if (stored != null && stored != fingerprint) {
      stateRepository.clearAll()
      retryRepository.clearAll()
    }
    stateRepository.save(
      BridgeSyncState(
        domain = META_DOMAIN,
        entityKey = IDENTITY_KEY,
        traktValue = fingerprint,
        traktChangedAt = 0,
        traktObserved = true,
        floppyValue = fingerprint,
        floppyChangedAt = 0,
        floppyObserved = true,
        resolvedValue = fingerprint,
        resolvedChangedAt = 0,
      ),
    )
    cachedFingerprint = fingerprint
  }

  private fun sha256(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .joinToString("") { byte -> "%02x".format(byte) }
}
