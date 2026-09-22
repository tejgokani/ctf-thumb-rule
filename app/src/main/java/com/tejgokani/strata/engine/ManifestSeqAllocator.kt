package com.tejgokani.strata.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Per-device monotonic sequence numbers for manifest WAL ops (plan §6.6). */
interface ManifestSeqAllocator {
    suspend fun next(): Long
}

class InMemoryManifestSeqAllocator : ManifestSeqAllocator {
    private var seq = 0L
    private val mutex = Mutex()
    override suspend fun next(): Long = mutex.withLock { seq++ }
}
