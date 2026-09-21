package com.tejgokani.strata.engine

import com.tejgokani.strata.core.StrataError
import com.tejgokani.strata.core.StrataResult
import java.util.Random

/**
 * Placement is table-driven, never hash-derived (plan §6.3). Consistent hashing is explicitly
 * rejected here: it exists to avoid a placement directory when membership churns constantly at
 * large N. STRATA has N <= ~20 accounts that change a handful of times over the app's life, and
 * CH would force moving ~1/N of all data — a re-download-and-re-upload over the user's network —
 * on every single membership change, for zero benefit at this scale.
 *
 * Layer 1 (this object's [planStripe]) plans a whole file's initial stripe with largest-remainder
 * (Hamilton) apportionment: weighted-random and power-of-two-choices both have unacceptable
 * *variance* here — P2C on a 16-chunk file across 10 accounts routinely dumps 5-6 chunks on one
 * node. Apportionment is deterministic and exactly proportional to free space.
 *
 * Layer 2 ([chooseForRepair]) uses power-of-two-choices, which is the right tool for a single,
 * cheap, stateless repair decision after a node fails mid-stripe — just the wrong tool for
 * planning an entire file up front.
 */
object Allocator {
    const val MAX_USAGE_RATIO = 0.90

    data class AccountView(
        val accountId: String,
        val effectiveFreeBytes: Long,
        val usageRatio: Double,
        val eligible: Boolean,
    )

    data class PlacementPlan(val chunkIndexToAccountId: Map<Int, String>)

    /** True if [account] may host a new chunk of size [chunkSizeBytes] right now. */
    private fun canHost(account: AccountView, chunkSizeBytes: Long): Boolean =
        account.eligible && account.usageRatio < MAX_USAGE_RATIO && account.effectiveFreeBytes >= chunkSizeBytes

    fun planStripe(
        chunkCount: Int,
        chunkSizeBytes: Long,
        accounts: List<AccountView>,
    ): StrataResult<PlacementPlan> {
        require(chunkCount > 0) { "chunkCount must be positive" }
        val eligible = accounts.filter { canHost(it, chunkSizeBytes) }
        if (eligible.isEmpty()) {
            return StrataResult.Err(StrataError.PoolFull(chunkCount * chunkSizeBytes))
        }

        val capacityByAccount = eligible.associate { it.accountId to (it.effectiveFreeBytes / chunkSizeBytes).toInt() }
        val totalCapacityChunks = capacityByAccount.values.sum()
        if (totalCapacityChunks < chunkCount) {
            return StrataResult.Err(StrataError.PoolFull(chunkCount * chunkSizeBytes))
        }

        // --- Largest-remainder apportionment, capped per-account by actual capacity ---
        val totalWeight = eligible.sumOf { it.effectiveFreeBytes.toDouble() }
        val rawQuota = eligible.associate { it.accountId to (chunkCount * it.effectiveFreeBytes / totalWeight) }
        val floors = rawQuota.mapValues { (id, q) -> minOf(q.toInt(), capacityByAccount.getValue(id)) }
        var assignedSoFar = floors.values.sum()

        val finalCounts = floors.toMutableMap()
        // Distribute the remainder to the accounts with the largest fractional remainder that
        // still have spare capacity, repeating passes until every chunk is placed or no account
        // has room left (in which case totalCapacityChunks would have already failed above).
        val byFractionDesc = eligible
            .sortedByDescending { rawQuota.getValue(it.accountId) - floors.getValue(it.accountId) }
            .map { it.accountId }
        var guardPasses = 0
        while (assignedSoFar < chunkCount && guardPasses < chunkCount + eligible.size) {
            var progressed = false
            for (id in byFractionDesc) {
                if (assignedSoFar >= chunkCount) break
                if (finalCounts.getValue(id) < capacityByAccount.getValue(id)) {
                    finalCounts[id] = finalCounts.getValue(id) + 1
                    assignedSoFar++
                    progressed = true
                }
            }
            guardPasses++
            if (!progressed) break
        }

        val pool = finalCounts.entries.filter { it.value > 0 }.flatMap { (id, count) -> List(count) { id } }
        if (pool.size < chunkCount) {
            return StrataResult.Err(StrataError.PoolFull(chunkCount * chunkSizeBytes))
        }

        // Interleave rather than block-assign, so a file's shards are spread round-robin across
        // its assigned accounts instead of "all of account A's chunks, then all of account B's".
        val interleaved = interleaveByAccount(finalCounts)
        val map = interleaved.take(chunkCount).withIndex().associate { (i, id) -> i to id }
        return StrataResult.Ok(PlacementPlan(map))
    }

    private fun interleaveByAccount(counts: Map<String, Int>): List<String> {
        val remaining = counts.toMutableMap()
        val out = ArrayList<String>(counts.values.sum())
        while (remaining.values.any { it > 0 }) {
            for (id in remaining.keys.toList()) {
                val left = remaining.getValue(id)
                if (left > 0) {
                    out.add(id)
                    remaining[id] = left - 1
                }
            }
        }
        return out
    }

    /** Layer 2: cheap stateless repair placement for a single chunk whose original node failed. */
    fun chooseForRepair(
        accounts: List<AccountView>,
        chunkSizeBytes: Long,
        excludeAccountIds: Set<String> = emptySet(),
        random: Random = Random(),
    ): StrataResult<String> {
        val eligible = accounts.filter { canHost(it, chunkSizeBytes) && it.accountId !in excludeAccountIds }
        if (eligible.isEmpty()) return StrataResult.Err(StrataError.PoolFull(chunkSizeBytes))
        if (eligible.size == 1) return StrataResult.Ok(eligible[0].accountId)
        val a = eligible[random.nextInt(eligible.size)]
        var b = eligible[random.nextInt(eligible.size)]
        var guard = 0
        while (b.accountId == a.accountId && guard < 10) {
            b = eligible[random.nextInt(eligible.size)]
            guard++
        }
        return StrataResult.Ok(if (a.usageRatio <= b.usageRatio) a.accountId else b.accountId)
    }

    /** Mirror placement must land on a node distinct from every existing placement of the chunk/file. */
    fun chooseMirror(
        accounts: List<AccountView>,
        chunkSizeBytes: Long,
        excludeAccountIds: Set<String>,
    ): StrataResult<String> {
        val eligible = accounts.filter { canHost(it, chunkSizeBytes) && it.accountId !in excludeAccountIds }
        if (eligible.isEmpty()) return StrataResult.Err(StrataError.PoolFull(chunkSizeBytes))
        return StrataResult.Ok(eligible.maxBy { it.effectiveFreeBytes }.accountId)
    }
}
