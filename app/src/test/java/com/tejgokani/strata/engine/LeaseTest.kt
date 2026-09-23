package com.tejgokani.strata.engine

import com.tejgokani.strata.core.FakeStrataClock
import com.tejgokani.strata.core.StrataResult
import com.tejgokani.strata.fakes.FakeDriveBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaseTest {
    @Test fun `a single device acquires the lease as holder`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)
        val lease = Lease(backend, "shared-account", "device-A", clock)
        val result = (lease.acquireOrRenew() as StrataResult.Ok).value
        assertTrue(result is Lease.LeaseResult.Holder)
    }

    @Test fun `the device whose lease was created first on the server wins, the other goes read-only`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)

        // Device A acquires first.
        val leaseA = Lease(backend, "shared-account", "device-A", clock)
        val resultA1 = (leaseA.acquireOrRenew() as StrataResult.Ok).value
        assertTrue(resultA1 is Lease.LeaseResult.Holder)

        // Advance the fake clock so device B's candidate file gets a later createdAtMs.
        clock.advanceWall(1000)
        val leaseB = Lease(backend, "shared-account", "device-B", clock)
        val resultB = (leaseB.acquireOrRenew() as StrataResult.Ok).value
        assertTrue("second device must be read-only, got $resultB", resultB is Lease.LeaseResult.ReadOnly)
        assertEquals("device-A", (resultB as Lease.LeaseResult.ReadOnly).holderDeviceId)

        // Device A must still see itself as holder on a renew.
        val resultA2 = (leaseA.acquireOrRenew() as StrataResult.Ok).value
        assertTrue(resultA2 is Lease.LeaseResult.Holder)
    }

    @Test fun `a released lease lets a previously read only device become holder`() = runTest {
        val clock = FakeStrataClock()
        val backend = FakeDriveBackend(clock)
        val leaseA = Lease(backend, "shared-account", "device-A", clock)
        leaseA.acquireOrRenew()
        clock.advanceWall(1000)
        val leaseB = Lease(backend, "shared-account", "device-B", clock)
        assertTrue((leaseB.acquireOrRenew() as StrataResult.Ok).value is Lease.LeaseResult.ReadOnly)

        leaseA.release()
        clock.advanceWall(1000)
        val resultB2 = (leaseB.acquireOrRenew() as StrataResult.Ok).value
        assertTrue(resultB2 is Lease.LeaseResult.Holder)
    }
}
