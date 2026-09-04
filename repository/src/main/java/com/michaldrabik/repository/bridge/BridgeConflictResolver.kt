package com.michaldrabik.repository.bridge

enum class BridgeSide {
  TRAKT,
  FLOPPY,
}

data class BridgeObservedState(
  val value: String?,
  val changedAt: Long,
  val observed: Boolean,
)

data class BridgeResolution(
  val value: String?,
  val changedAt: Long,
  val winner: BridgeSide?,
)

object BridgeConflictResolver {

  fun observe(
    previous: BridgeObservedState,
    currentValue: String?,
    remoteChangedAt: Long?,
    observedAt: Long,
  ): BridgeObservedState {
    require(observedAt > 0) { "Observation timestamp must be positive" }
    val validRemoteChangedAt = remoteChangedAt?.takeIf { it > 0 }

    if (!previous.observed) {
      return BridgeObservedState(
        value = currentValue,
        changedAt = if (currentValue == null) 0 else validRemoteChangedAt ?: observedAt,
        observed = true,
      )
    }

    if (previous.value == currentValue) {
      return previous.copy(
        changedAt = maxOf(previous.changedAt, validRemoteChangedAt ?: 0),
      )
    }

    return BridgeObservedState(
      value = currentValue,
      changedAt = validRemoteChangedAt ?: observedAt,
      observed = true,
    )
  }

  fun resolve(
    trakt: BridgeObservedState,
    floppy: BridgeObservedState,
    previousResolvedValue: String? = null,
    previousResolvedAt: Long = 0,
  ): BridgeResolution {
    if (!trakt.observed && !floppy.observed) {
      return BridgeResolution(previousResolvedValue, previousResolvedAt, null)
    }
    if (!trakt.observed) return BridgeResolution(floppy.value, floppy.changedAt, BridgeSide.FLOPPY)
    if (!floppy.observed) return BridgeResolution(trakt.value, trakt.changedAt, BridgeSide.TRAKT)

    if (trakt.value == floppy.value) {
      return BridgeResolution(
        value = trakt.value,
        changedAt = maxOf(trakt.changedAt, floppy.changedAt, previousResolvedAt),
        winner = null,
      )
    }

    if (trakt.changedAt > floppy.changedAt) {
      return BridgeResolution(trakt.value, trakt.changedAt, BridgeSide.TRAKT)
    }
    if (floppy.changedAt > trakt.changedAt) {
      return BridgeResolution(floppy.value, floppy.changedAt, BridgeSide.FLOPPY)
    }

    if (previousResolvedAt == trakt.changedAt) {
      if (previousResolvedValue == trakt.value) {
        return BridgeResolution(trakt.value, trakt.changedAt, BridgeSide.TRAKT)
      }
      if (previousResolvedValue == floppy.value) {
        return BridgeResolution(floppy.value, floppy.changedAt, BridgeSide.FLOPPY)
      }
    }

    // Exact timestamp ties are deterministic. Trakt wins only when there is no
    // earlier resolved value that can preserve convergence.
    return BridgeResolution(trakt.value, trakt.changedAt, BridgeSide.TRAKT)
  }
}
