package com.deskdeck.media.sources

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeChainTest {

    private class FakeSource(
        override val name: String,
        private val canHandle: Boolean = true,
        private val sends: Boolean = true,
        private val throws: Boolean = false,
    ) : PlaybackSource {
        var requests = 0
        override suspend fun canHandle(packageName: String) = canHandle
        override suspend fun requestPlay(packageName: String): Boolean {
            requests++
            if (throws) error("boom")
            return sends
        }
    }

    private val pkg = "com.spotify.music"

    @Test
    fun `stops at the first source after which a session appears`() = runTest {
        val first = FakeSource("first")
        val second = FakeSource("second")
        val third = FakeSource("third")
        var checks = 0
        val worked = ResumeChain(listOf(first, second, third)).run(pkg) { checks++; checks == 2 }

        assertEquals(second, worked)
        assertEquals(0, third.requests)
    }

    @Test
    fun `skips sources that can't handle the app or couldn't send`() = runTest {
        val disabled = FakeSource("disabled", canHandle = false)
        val failsToSend = FakeSource("fails", sends = false)
        val works = FakeSource("works")
        var waits = 0
        val worked = ResumeChain(listOf(disabled, failsToSend, works)).run(pkg) { waits++; true }

        assertEquals(works, worked)
        assertEquals(0, disabled.requests)
        // Only waited for a session after a request was actually sent.
        assertEquals(1, waits)
    }

    @Test
    fun `a throwing source doesn't stop the chain`() = runTest {
        val broken = FakeSource("broken", throws = true)
        val works = FakeSource("works")
        assertEquals(works, ResumeChain(listOf(broken, works)).run(pkg) { true })
    }

    @Test
    fun `nothing works means null so the UI can offer to open the app`() = runTest {
        val chain = ResumeChain(listOf(FakeSource("a"), FakeSource("b")))
        assertNull(chain.run(pkg) { false })
    }

    @Test
    fun `each wait uses the step timeout`() = runTest {
        var seenTimeout = 0L
        ResumeChain(listOf(FakeSource("a")), stepTimeoutMs = 1_234).run(pkg) { seenTimeout = it; true }
        assertEquals(1_234L, seenTimeout)
    }

    @Test
    fun `a source can ask for its own session wait`() = runTest {
        val quick = object : PlaybackSource {
            override val name = "quick"
            override val sessionTimeoutMs = 500L
            override suspend fun requestPlay(packageName: String) = true
        }
        val waits = mutableListOf<Long>()
        ResumeChain(listOf(quick, FakeSource("default")), stepTimeoutMs = 3_000).run(pkg) { waits += it; false }
        assertEquals(listOf(500L, 3_000L), waits)
    }
}
