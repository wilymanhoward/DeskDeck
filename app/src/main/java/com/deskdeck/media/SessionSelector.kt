package com.deskdeck.media

/** What the selector needs to know about one active media session. */
data class SessionCandidate(
    val id: Long,
    val isPlaying: Boolean,
    val hasMetadata: Boolean,
    /** `elapsedRealtime` of the moment this session last started playing; 0 if never seen playing. */
    val lastStartedPlayingAt: Long,
    /** Position in `getActiveSessions()`, which the system orders by priority (most recent first). */
    val systemOrder: Int,
)

/**
 * Picks the session to show:
 * 1. a playing session, the one that started playing most recently;
 * 2. otherwise keep the currently shown one, so two paused players don't make the card jump;
 * 3. otherwise the system's highest-priority session.
 * Sessions without metadata (nothing to show) are ignored.
 */
object SessionSelector {

    fun select(candidates: List<SessionCandidate>, currentId: Long?): Long? {
        val usable = candidates.filter { it.hasMetadata }
        if (usable.isEmpty()) return null

        val playing = usable.filter { it.isPlaying }
        if (playing.isNotEmpty()) {
            return playing
                .sortedWith(compareByDescending<SessionCandidate> { it.lastStartedPlayingAt }.thenBy { it.systemOrder })
                .first()
                .id
        }

        usable.firstOrNull { it.id == currentId }?.let { return it.id }
        return usable.minBy { it.systemOrder }.id
    }
}
