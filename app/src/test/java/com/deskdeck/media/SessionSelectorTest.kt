package com.deskdeck.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSelectorTest {

    private fun session(
        id: Long,
        playing: Boolean = false,
        hasMetadata: Boolean = true,
        startedAt: Long = 0,
        order: Int = id.toInt(),
    ) = SessionCandidate(id, playing, hasMetadata, startedAt, order)

    @Test
    fun `no sessions selects nothing`() {
        assertNull(SessionSelector.select(emptyList(), currentId = null))
    }

    @Test
    fun `sessions without metadata are ignored`() {
        assertNull(SessionSelector.select(listOf(session(1, playing = true, hasMetadata = false)), currentId = null))
    }

    @Test
    fun `playing session wins over paused ones regardless of order`() {
        val sessions = listOf(session(1, order = 0), session(2, playing = true, startedAt = 50, order = 1))
        assertEquals(2L, SessionSelector.select(sessions, currentId = 1))
    }

    @Test
    fun `most recently started playing session wins when several play`() {
        val sessions = listOf(
            session(1, playing = true, startedAt = 100, order = 0),
            session(2, playing = true, startedAt = 200, order = 1),
        )
        assertEquals(2L, SessionSelector.select(sessions, currentId = 1))
    }

    @Test
    fun `playing sessions with equal start time fall back to system order`() {
        val sessions = listOf(
            session(1, playing = true, startedAt = 1, order = 1),
            session(2, playing = true, startedAt = 1, order = 0),
        )
        assertEquals(2L, SessionSelector.select(sessions, currentId = null))
    }

    @Test
    fun `when nothing plays the current session is kept`() {
        val sessions = listOf(session(1, order = 0), session(2, order = 1))
        assertEquals(2L, SessionSelector.select(sessions, currentId = 2))
    }

    @Test
    fun `when nothing plays and current is gone the system's top session is used`() {
        val sessions = listOf(session(1, order = 1), session(2, order = 0))
        assertEquals(2L, SessionSelector.select(sessions, currentId = 99))
    }

    @Test
    fun `current session that lost its metadata is replaced`() {
        val sessions = listOf(session(1, hasMetadata = false, order = 0), session(2, order = 1))
        assertEquals(2L, SessionSelector.select(sessions, currentId = 1))
    }
}
