package com.yujian.ai.ai.subject

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList

class SlimSamNativeRunLifecycleTest {
    @Test
    fun coroutineCancellationTerminatesRunOptionsOnIndependentControlThread() {
        val terminationExecutor = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        try {
            val callerThread = Thread.currentThread()
            val terminateThread = AtomicReference<Thread?>()
            val terminateCalls = AtomicInteger()
            val terminateReturned = CountDownLatch(1)
            val events = CopyOnWriteArrayList<String>()
            val controller = SlimSamNativeRunController(
                runOptionsId = "run-options-test-1",
                terminationExecutor = terminationExecutor,
                watchdogScheduler = watchdog,
                terminationWaitMs = 500,
                setTerminate = {
                    terminateThread.set(Thread.currentThread())
                    terminateCalls.incrementAndGet()
                    terminateReturned.countDown()
                },
                onEvent = { event, _ -> events += event },
            )
            controller.nativeStarted()
            val job = Job()

            val cancellationHandle = installSlimSamCancellationHandler(job, controller)
            job.cancel(CancellationException("recognition attempt replaced"))

            assertTrue("setTerminate must be called", terminateReturned.await(2, TimeUnit.SECONDS))
            assertEquals(1, terminateCalls.get())
            assertFalse("RunOptions control must not run on the cancelled inference caller", callerThread === terminateThread.get())
            assertTrue(controller.snapshot().terminateRequested)
            assertFalse(controller.snapshot().nativeReturned)

            cancellationHandle.dispose()
            controller.nativeReturned()
            controller.cleanupCompleted()
            assertEquals(SlimSamNativeRunState.CLEANUP_COMPLETED, controller.snapshot().state)
            assertTrue(events.indexOf("NATIVE_RUN_STARTED") < events.indexOf("NATIVE_CANCEL_REQUESTED"))
            assertTrue(events.indexOf("NATIVE_CANCEL_REQUESTED") < events.indexOf("NATIVE_TERMINATE_REQUESTED"))
            assertTrue(events.indexOf("NATIVE_TERMINATE_REQUESTED") < events.indexOf("NATIVE_TERMINATE_CALL_RETURNED"))
            assertTrue(events.indexOf("NATIVE_TERMINATE_CALL_RETURNED") < events.indexOf("NATIVE_RUN_RETURNED"))
            assertTrue(events.indexOf("NATIVE_RUN_RETURNED") < events.indexOf("RESOURCE_CLEANUP_END"))
        } finally {
            terminationExecutor.shutdownNow()
            watchdog.shutdownNow()
        }
    }

    @Test
    fun duplicateCancellationRequestsIssueOnlyOneNativeTerminateCall() {
        val terminationExecutor = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        try {
            val calls = AtomicInteger()
            val completed = CountDownLatch(1)
            val controller = SlimSamNativeRunController(
                runOptionsId = "run-options-test-2",
                terminationExecutor = terminationExecutor,
                watchdogScheduler = watchdog,
                terminationWaitMs = 500,
                setTerminate = {
                    calls.incrementAndGet()
                    completed.countDown()
                },
            )

            assertTrue(controller.requestTermination())
            assertFalse(controller.requestTermination())
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
            controller.nativeReturned()
            controller.cleanupCompleted()
        } finally {
            terminationExecutor.shutdownNow()
            watchdog.shutdownNow()
        }
    }

    @Test
    fun closeDefersSessionAndSessionOptionsUntilNativeRunLeaseIsReleased() {
        val closeCalls = AtomicInteger()
        val gate = SlimSamNativeSessionGate { closeCalls.incrementAndGet() }
        val lease = gate.acquire()

        assertTrue(lease != null)
        gate.requestClose()

        assertEquals(0, closeCalls.get())
        assertNull(gate.acquire())
        assertEquals(1, gate.snapshot().activeRunCount)

        lease!!.close()

        assertEquals(1, closeCalls.get())
        assertEquals(0, gate.snapshot().activeRunCount)
        assertTrue(gate.snapshot().resourcesClosed)
        gate.requestClose()
        assertEquals("close callback runs once", 1, closeCalls.get())
    }

    @Test
    fun unconfirmedNativeTerminationPoisonsSessionAndRetainsResourcesUntilReturn() {
        val terminationExecutor = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        try {
            val closeCalls = AtomicInteger()
            val unconfirmed = CountDownLatch(1)
            val gate = SlimSamNativeSessionGate { closeCalls.incrementAndGet() }
            val lease = gate.acquire()!!
            val controller = SlimSamNativeRunController(
                runOptionsId = "run-options-test-3",
                terminationExecutor = terminationExecutor,
                watchdogScheduler = watchdog,
                terminationWaitMs = 30,
                setTerminate = {},
                onTerminationUnconfirmed = { gate.poison(); unconfirmed.countDown() },
            )

            assertTrue(controller.requestTermination())
            assertTrue("bounded wait must report a still-running native call", unconfirmed.await(2, TimeUnit.SECONDS))
            assertTrue(controller.snapshot().terminationUnconfirmed)
            assertTrue(gate.snapshot().poisoned)
            assertNull("poisoned session cannot accept another run", gate.acquire())
            assertEquals("live Native call keeps Session and RunOptions owners alive", 0, closeCalls.get())

            controller.nativeReturned()
            controller.cleanupCompleted()
            lease.close()

            assertEquals(1, closeCalls.get())
            assertTrue(gate.snapshot().resourcesClosed)
        } finally {
            terminationExecutor.shutdownNow()
            watchdog.shutdownNow()
        }
    }

    @Test
    fun runOptionsAndSessionLeaseStayAliveUntilExternalTerminationCallReturns() {
        val terminationExecutor = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        try {
            val terminateStarted = CountDownLatch(1)
            val allowTerminateReturn = CountDownLatch(1)
            val cleanupFinished = CountDownLatch(1)
            val terminationUnconfirmed = CountDownLatch(1)
            val closeCalls = AtomicInteger()
            val gate = SlimSamNativeSessionGate { closeCalls.incrementAndGet() }
            val lease = gate.acquire()!!
            val controller = SlimSamNativeRunController(
                runOptionsId = "run-options-test-5",
                terminationExecutor = terminationExecutor,
                watchdogScheduler = watchdog,
                terminationWaitMs = 40,
                setTerminate = {
                    terminateStarted.countDown()
                    allowTerminateReturn.await(2, TimeUnit.SECONDS)
                },
                onTerminationUnconfirmed = { gate.poison(); terminationUnconfirmed.countDown() },
            )
            controller.nativeStarted()

            assertTrue(controller.requestTermination())
            assertTrue(terminateStarted.await(2, TimeUnit.SECONDS))
            controller.nativeReturned()
            controller.finishResourceCleanup {
                lease.close()
                controller.cleanupCompleted()
                cleanupFinished.countDown()
            }

            assertFalse("RunOptions cleanup must wait for the control call", cleanupFinished.await(20, TimeUnit.MILLISECONDS))
            assertTrue("stuck termination control poisons the owning Session", terminationUnconfirmed.await(2, TimeUnit.SECONDS))
            assertEquals(1, gate.snapshot().activeRunCount)
            assertTrue(gate.snapshot().poisoned)
            assertEquals(0, closeCalls.get())

            allowTerminateReturn.countDown()
            assertTrue(cleanupFinished.await(2, TimeUnit.SECONDS))
            assertEquals(0, gate.snapshot().activeRunCount)
        } finally {
            terminationExecutor.shutdownNow()
            watchdog.shutdownNow()
        }
    }

    @Test
    fun terminationAfterNativeReturnIsIgnored() {
        val terminationExecutor = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        try {
            val calls = AtomicInteger()
            val controller = SlimSamNativeRunController(
                runOptionsId = "run-options-test-4",
                terminationExecutor = terminationExecutor,
                watchdogScheduler = watchdog,
                terminationWaitMs = 30,
                setTerminate = { calls.incrementAndGet() },
            )

            controller.nativeReturned()

            assertFalse(controller.requestTermination())
            assertEquals(0, calls.get())
            controller.cleanupCompleted()
            assertSame(SlimSamNativeRunState.CLEANUP_COMPLETED, controller.snapshot().state)
        } finally {
            terminationExecutor.shutdownNow()
            watchdog.shutdownNow()
        }
    }

    @Test
    fun cancelledNativeRunReleasesItsLeaseAndAllowsANewRun() {
        val terminationExecutor = Executors.newSingleThreadExecutor()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        try {
            val terminateReturned = CountDownLatch(1)
            val closeRunOptionsCalls = AtomicInteger()
            val closeSessionCalls = AtomicInteger()
            val gate = SlimSamNativeSessionGate { closeSessionCalls.incrementAndGet() }
            val firstLease = gate.acquire()!!
            val controller = SlimSamNativeRunController(
                runOptionsId = "run-options-cancel-and-retry",
                terminationExecutor = terminationExecutor,
                watchdogScheduler = watchdog,
                terminationWaitMs = 500,
                setTerminate = { terminateReturned.countDown() },
            )
            controller.nativeStarted()

            assertTrue(controller.requestTermination())
            assertTrue(terminateReturned.await(2, TimeUnit.SECONDS))
            controller.nativeReturned()
            controller.finishResourceCleanup {
                closeRunOptionsCalls.incrementAndGet()
                firstLease.close()
                controller.cleanupCompleted()
            }

            assertTrue(controller.awaitCleanup(2_000))
            val retryLease = gate.acquire()
            assertTrue("a completed cancellation must leave the Session reusable", retryLease != null)
            retryLease!!.close()
            assertEquals(1, closeRunOptionsCalls.get())
            assertEquals(0, closeSessionCalls.get())
            assertEquals(0, gate.snapshot().activeRunCount)
        } finally {
            terminationExecutor.shutdownNow()
            watchdog.shutdownNow()
        }
    }
}
