package com.tejgokani.strata.engine

import com.tejgokani.strata.data.prefs.SettingsStore

/** Backs [ManifestSeqAllocator] with [SettingsStore] so the per-device WAL sequence survives process death. */
class PersistedManifestSeqAllocator(private val settings: SettingsStore) : ManifestSeqAllocator {
    override suspend fun next(): Long = settings.nextManifestSeq()
}
