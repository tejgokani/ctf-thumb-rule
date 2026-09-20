package com.tejgokani.strata.di

import android.content.Context
import com.tejgokani.strata.auth.AccountAuthorizer
import com.tejgokani.strata.auth.TokenProvider
import com.tejgokani.strata.core.AndroidStrataClock
import com.tejgokani.strata.core.StrataClock
import com.tejgokani.strata.crypto.KeyVault
import com.tejgokani.strata.data.db.RoomAccountRepository
import com.tejgokani.strata.data.db.RoomChunkRepository
import com.tejgokani.strata.data.db.RoomFileRepository
import com.tejgokani.strata.data.db.RoomLedgerStore
import com.tejgokani.strata.data.db.RoomPlacementRepository
import com.tejgokani.strata.data.db.RoomReservationRepository
import com.tejgokani.strata.data.db.StrataDatabase
import com.tejgokani.strata.data.prefs.SettingsStore
import com.tejgokani.strata.drive.DriveBackend
import com.tejgokani.strata.drive.StorageBackend
import com.tejgokani.strata.engine.AccountRepository
import com.tejgokani.strata.engine.ChunkRepository
import com.tejgokani.strata.engine.FileRepository
import com.tejgokani.strata.engine.ManifestReplicator
import com.tejgokani.strata.engine.ManifestSeqAllocator
import com.tejgokani.strata.engine.PersistedManifestSeqAllocator
import com.tejgokani.strata.engine.PlacementRepository
import com.tejgokani.strata.engine.RecoveryEngine
import com.tejgokani.strata.engine.ReservationRepository
import com.tejgokani.strata.engine.ScrubEngine
import com.tejgokani.strata.engine.TransferCoordinator
import com.tejgokani.strata.ledger.Ledger

/**
 * Hand-written composition root (plan §3: no Hilt — `@HiltWorker`/`WorkerFactory` wiring is a
 * classic one-pass build breaker; a manual container removes an entire class of build failure
 * for a graph this size). Every dependency below is a real, production-wired implementation —
 * the only thing this app substitutes in tests is the whole graph, via TestHarness, not
 * individual pieces of this container.
 */
class AppContainer(private val context: Context) {
    val settings = SettingsStore(context)
    val db: StrataDatabase by lazy { StrataDatabase.get(context) }

    val accountRepository: AccountRepository by lazy { RoomAccountRepository(db.accountDao()) }
    val fileRepository: FileRepository by lazy { RoomFileRepository(db.fileDao()) }
    val chunkRepository: ChunkRepository by lazy { RoomChunkRepository(db.chunkDao()) }
    val placementRepository: PlacementRepository by lazy { RoomPlacementRepository(db.placementDao()) }
    val reservationRepository: ReservationRepository by lazy { RoomReservationRepository(db.reservationDao()) }
    val ledgerStore by lazy { RoomLedgerStore(db.ledgerDao()) }

    val seqAllocator: ManifestSeqAllocator by lazy { PersistedManifestSeqAllocator(settings) }

    lateinit var deviceId: String private set
    lateinit var poolId: String private set
    lateinit var clock: StrataClock private set
    lateinit var ledger: Ledger private set

    val accountAuthorizer: AccountAuthorizer by lazy { AccountAuthorizer(context) }
    val tokenProvider: TokenProvider by lazy { TokenProvider(accountAuthorizer, clock) }

    val driveBackend: StorageBackend by lazy {
        DriveBackend(tokenProvider) { accountId ->
            // Every AccountRecord this app ever creates uses the Google account's own email as
            // its id (see NodesViewModel.finishAddingNode), so accountId IS the email whether or
            // not a repository row exists for it yet. The repository lookup is preferred when
            // available (keeps the door open for id != email later), but falling back to
            // accountId itself — rather than throwing — is what makes adding a brand-new node
            // work: its very first getQuota() call necessarily happens before that node's own
            // AccountRecord has been persisted.
            accountRepository.get(accountId)?.email ?: accountId
        }
    }

    /** The passphrase-derived vault. Null until [unlock] is called from the BOOT screen. */
    var vault: KeyVault? = null
        private set

    private var initialized = false

    /** Must be called once, before anything else on this container, from a coroutine (e.g. app startup). */
    suspend fun initialize() {
        if (initialized) return
        deviceId = settings.getOrCreateDeviceId()
        poolId = settings.getOrCreatePoolId()
        clock = AndroidStrataClock(settings.getOrCreateBootId())
        ledger = Ledger(ledgerStore, clock, deviceId)
        initialized = true
    }

    fun unlock(v: KeyVault) { vault = v }
    fun lock() { vault = null }
    fun isUnlocked(): Boolean = vault != null

    private fun requireVault(): KeyVault = vault ?: error("STRATA vault is locked")

    fun manifestReplicator(): ManifestReplicator = ManifestReplicator(driveBackend, requireVault(), deviceId)

    fun transferCoordinator(): TransferCoordinator = TransferCoordinator(
        backend = driveBackend, vault = requireVault(), ledger = ledger,
        accounts = accountRepository, files = fileRepository, chunks = chunkRepository,
        placements = placementRepository, reservations = reservationRepository,
        manifestReplicator = manifestReplicator(), seqAllocator = seqAllocator,
        clock = clock, deviceId = deviceId, poolId = poolId,
    )

    fun recoveryEngine(): RecoveryEngine = RecoveryEngine(driveBackend, requireVault(), manifestReplicator(), poolId)

    fun scrubEngine(): ScrubEngine = ScrubEngine(driveBackend, requireVault(), clock, poolId)
}
