package com.deskdeck.media

/** What the selector needs to know about one active media session. */
data class SessionCandidate(
    val id: Long,
    val packageName: String,
    val isPlaying: Boolean,
    val hasMetadata: Boolean,
    /** `elapsedRealtime` of the moment this session last started playing; 0 if never seen playing. */
    val lastStartedPlayingAt: Long,
    /** Position in `getActiveSessions()`, which the system orders by priority (most recent first). */
    val systemOrder: Int,
)

/**
 * The app the user chose in the rail.
 * @param pinned stay on this app even when another one starts playing; otherwise (auto-follow) a
 * different app that *starts* playing after [selectedAtMs] takes over and the selection ends.
 */
data class SourceSelection(
    val packageName: String,
    val selectedAtMs: Long,
    val pinned: Boolean,
)

/** [sessionId] to show (null: nothing, e.g. the selected app has no session yet) and the selection after this decision. */
data class SelectionDecision(val sessionId: Long?, val selection: SourceSelection?)

/**
 * Picks the session to show.
 *
 * With a [SourceSelection]: that app's session (playing first, then the current one, then system
 * priority), or none if it has no session yet. Unless pinned, an app that starts playing after the
 * selection was made takes over and the selection is released (auto-follow).
 *
 * Without a selection:
 * 1. a playing session, the one that started playing most recently;
 * 2. otherwise keep the currently shown one, so two paused players don't make the card jump;
 * 3. otherwise the system's highest-priority session.
 *
 * Sessions without metadata (nothing to show) are ignored throughout.
 */
object SessionSelector {

    fun select(
        candidates: List<SessionCandidate>,
        currentId: Long?,
        selection: SourceSelection? = null,
    ): SelectionDecision {
        val usable = candidates.filter { it.hasMetadata }

        if (selection != null) {
            if (!selection.pinned) {
                val newcomer = usable
                    .filter { it.isPlaying && it.packageName != selection.packageName && it.lastStartedPlayingAt > selection.selectedAtMs }
                    .maxByOrNull { it.lastStartedPlayingAt }
                if (newcomer != null) return SelectionDecision(newcomer.id, selection = null)
            }
            val own = usable.filter { it.packageName == selection.packageName }
            return SelectionDecision(pick(own, currentId), selection)
        }

        return SelectionDecision(pick(usable, currentId), selection = null)
    }

    private fun pick(usable: List<SessionCandidate>, currentId: Long?): Long? {
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
