package app.gamenative.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BoundedProcessTest {
    /** Ein Prozess, der erst endet, wenn [finish] gerufen wird; [ignoresKill] = hängt auch nach destroyForcibly weiter. */
    private class FakeProcess(private val ignoresKill: Boolean = false) : Process() {
        private val done = CountDownLatch(1)

        @Volatile
        var destroyed = false

        fun finish() = done.countDown()

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int {
            done.await()
            return 0
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = done.await(timeout, unit)

        override fun exitValue(): Int {
            if (done.count > 0) throw IllegalThreadStateException()
            return 0
        }

        override fun destroy() {
            destroyForcibly()
        }

        override fun destroyForcibly(): Process {
            destroyed = true
            if (!ignoresKill) done.countDown()
            return this
        }
    }

    @Test
    fun aProcessThatFinishesInTimeIsLeftAlone() {
        val p = FakeProcess()
        p.finish()
        assertTrue(BoundedProcess.awaitOrKill(p, 1_000))
        assertFalse(p.destroyed)
    }

    @Test
    fun aHangingProcessIsDestroyedAfterTheTimeLimit() {
        val p = FakeProcess()
        val start = System.nanoTime()
        val finished = BoundedProcess.awaitOrKill(p, timeoutMs = 150, graceMs = 150)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertFalse(finished)
        assertTrue(p.destroyed)
        assertTrue("zu lange gewartet: $ms ms", ms < 2_000)
    }

    @Test
    fun aProcessThatSurvivesTheKillStillOnlyCostsTimeoutPlusGrace() {
        val p = FakeProcess(ignoresKill = true)
        val start = System.nanoTime()
        assertFalse(BoundedProcess.awaitOrKill(p, timeoutMs = 150, graceMs = 150))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(p.destroyed)
        assertTrue("zu lange gewartet: $ms ms", ms < 2_000)
    }

    @Test
    fun theWineserverLimitIsThreeSecondsAndNotMore() {
        assertEquals(3_000L, BoundedProcess.WINESERVER_KILL_TIMEOUT_MS)
        assertTrue(BoundedProcess.KILL_GRACE_MS <= 1_000L)
    }

    @Test
    fun onTheMainThreadBlockingWorkRunsInTheBackgroundAndTheCallerIsNotHeld() {
        val release = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val start = System.nanoTime()
        val thread = BoundedProcess.runOffMain(true, "test-kill") {
            release.await(5, TimeUnit.SECONDS)
            finished.set(true)
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertNotNull(thread)
        assertTrue("Aufrufer wurde aufgehalten: $ms ms", ms < 1_000)
        assertFalse(finished.get())
        assertTrue(thread!!.isDaemon)
        release.countDown()
        thread.join(2_000)
        assertTrue(finished.get())
    }

    @Test
    fun offTheMainThreadTheWorkRunsRightAway() {
        var ran = false
        assertNull(BoundedProcess.runOffMain(false, "test-kill") { ran = true })
        assertTrue(ran)
    }

    @Test
    fun hangingKillOnTheMainThreadNeverBlocksButStillEndsTheProcess() {
        val p = FakeProcess()
        val start = System.nanoTime()
        val thread = BoundedProcess.runOffMain(true, "test-kill") { BoundedProcess.awaitOrKill(p, 200, 200) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("Aufrufer wurde aufgehalten: $ms ms", ms < 150)
        thread!!.join(3_000)
        assertTrue(p.destroyed)
    }
}
