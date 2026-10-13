package com.deskdeck.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSelectorTest {

    private fun session(
        id: Long,
        pkg: String = "app$id",
        playing: Boolean = false,
        hasMetadata: Boolean = true,
        startedAt: Long = 0,
        order: Int = id.toInt(),
    ) = SessionCandidate(id, pkg, playing, hasMetadata, startedAt, order)

    private fun pick(candidates: List<SessionCandidate>, currentId: Long?, selection: SourceSelection? = null) =
        SessionSelector.select(candidates, currentId, selection)

    // region Without a selection (unchanged behavior)

    @Test
    fun `no sessions selects nothing`() {
        assertNull(pick(emptyList(), currentId = null).sessionId)
    }

    @Test
    fun `sessions without metadata are ignored`() {
        assertNull(pick(listOf(session(1, playing = true, hasMetadata = false)), currentId = null).sessionId)
    }

    @Test
    fun `playing session wins over paused ones regardless of order`() {
        val sessions = listOf(session(1, order = 0), session(2, playing = true, startedAt = 50, order = 1))
        assertEquals(2L, pick(sessions, currentId = 1).sessionId)
    }

    @Test
    fun `most recently started playing session wins when several play`() {
        val sessions = listOf(
            session(1, playing = true, startedAt = 100, order = 0),
            session(2, playing = true, startedAt = 200, order = 1),
        )
        assertEquals(2L, pick(sessions, currentId = 1).sessionId)
    }

    @Test
    fun `playing sessions with equal start time fall back to system order`() {
        val sessions = listOf(
            session(1, playing = true, startedAt = 1, order = 1),
            session(2, playing = true, startedAt = 1, order = 0),
        )
        assertEquals(2L, pick(sessions, currentId = null).sessionId)
    }

    @Test
    fun `when nothing plays the current session is kept`() {
        val sessions = listOf(session(1, order = 0), session(2, order = 1))
        assertEquals(2L, pick(sessions, currentId = 2).sessionId)
    }

    @Test
    fun `when nothing plays and current is gone the system's top session is used`() {
        val sessions = listOf(session(1, order = 1), session(2, order = 0))
        assertEquals(2L, pick(sessions, currentId = 99).sessionId)
    }

    @Test
    fun `current session that lost its metadata is replaced`() {
        val sessions = listOf(session(1, hasMetadata = false, order = 0), session(2, order = 1))
        assertEquals(2L, pick(sessions, currentId = 1).sessionId)
    }

    // endregion

    // region Source selection

    private val spotify = "com.spotify.music"
    private val ytMusic = "com.google.android.apps.youtube.music"

    @Test
    fun `selected app is shown even while another app plays`() {
        val sessions = listOf(
            session(1, pkg = ytMusic, playing = true, startedAt = 50),
            session(2, pkg = spotify, playing = false),
        )
        val decision = pick(sessions, currentId = 1, selection = SourceSelection(spotify, selectedAtMs = 100, pinned = false))
        assertEquals(2L, decision.sessionId)
        assertEquals(spotify, decision.selection?.packageName)
    }

    @Test
    fun `selected app without a session shows nothing and keeps the selection`() {
        val sessions = listOf(session(1, pkg = ytMusic, playing = true, startedAt = 50))
        val decision = pick(sessions, currentId = 1, selection = SourceSelection(spotify, selectedAtMs = 100, pinned = false))
        assertNull(decision.sessionId)
        assertEquals(spotify, decision.selection?.packageName)
    }

    @Test
    fun `auto-follow switches to an app that starts playing after the selection`() {
        val sessions = listOf(
            session(1, pkg = spotify, playing = false),
            session(2, pkg = ytMusic, playing = true, startedAt = 200),
        )
        val decision = pick(sessions, currentId = 1, selection = SourceSelection(spotify, selectedAtMs = 100, pinned = false))
        assertEquals(2L, decision.sessionId)
        assertNull(decision.selection)
    }

    @Test
    fun `pinned selection stays when another app starts playing`() {
        val sessions = listOf(
            session(1, pkg = spotify, playing = false),
            session(2, pkg = ytMusic, playing = true, startedAt = 200),
        )
        val decision = pick(sessions, currentId = 1, selection = SourceSelection(spotify, selectedAtMs = 100, pinned = true))
        assertEquals(1L, decision.sessionId)
        assertEquals(spotify, decision.selection?.packageName)
    }

    @Test
    fun `selected app's own sessions follow the usual preference`() {
        val sessions = listOf(
            session(1, pkg = spotify, playing = false, order = 0),
            session(2, pkg = spotify, playing = true, startedAt = 50, order = 1),
        )
        assertEquals(2L, pick(sessions, currentId = 1, selection = SourceSelection(spotify, 100, pinned = true)).sessionId)
    }

    // endregion
}
