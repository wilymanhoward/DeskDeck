package com.deskdeck.media.call

/**
 * What call detection needs from a posted `StatusBarNotification`, as plain values, so the rules
 * can be unit-tested with fake data. Built by [MediaListenerService][com.deskdeck.media.MediaListenerService];
 * lives only in memory.
 */
data class CallNotification(
    val key: String,
    val packageName: String,
    val postTime: Long,
    /** `Notification.category`, e.g. "call" or "missed_call". */
    val category: String?,
    val channelId: String?,
    /** `EXTRA_TEMPLATE`, e.g. "android.app.Notification$CallStyle". */
    val template: String?,
    /** CallStyle's `EXTRA_CALL_TYPE`: 1 incoming, 2 ongoing, 3 screening; null without CallStyle. */
    val callType: Int?,
    /** CallStyle's `EXTRA_CALL_IS_VIDEO`; null when the app doesn't say. */
    val isVideoExtra: Boolean?,
    val isOngoingFlag: Boolean,
    /** The notification shows a running timer from [whenMs]. */
    val usesChronometer: Boolean,
    val whenMs: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    /** CallStyle's caller (`EXTRA_CALL_PERSON`) name. */
    val callerName: String?,
    /** CallStyle's own answer / decline / hang-up intents are present. */
    val hasAnswerIntent: Boolean,
    val hasDeclineIntent: Boolean,
    val hasHangUpIntent: Boolean,
    /** The notification's action buttons, in order. */
    val actions: List<ActionFacts>,
    /** The system would let it through the current Do Not Disturb filter (it would ring). */
    val passesDnd: Boolean,
) {
    data class ActionFacts(val title: String?, val semanticAction: Int)
}

/** Where one of the call's buttons comes from: CallStyle's extras, or an action by index. */
sealed interface ActionRef {
    data object AnswerExtra : ActionRef
    data object DeclineExtra : ActionRef
    data object HangUpExtra : ActionRef
    data class Action(val index: Int) : ActionRef
}

/** What a [CallSource] recognized in one notification. */
data class CallDetection(
    val kind: Kind,
    val caller: String?,
    val isVideo: Boolean,
    /** Ongoing calls: when the call's timer started, if the notification shows one. */
    val startedAtMs: Long?,
    /** Calling out and not answered yet ("Calling…", "Ringing…"): no timer yet. */
    val connecting: Boolean = false,
    val answer: ActionRef?,
    val decline: ActionRef?,
    val hangUp: ActionRef?,
    /** True when the result leaned on matching words (see [CallText]) instead of structured signals. */
    val usedText: Boolean,
) {
    enum class Kind { INCOMING, ONGOING }
}

/**
 * One app's calls. Only WhatsApp is implemented; Telegram, Google Meet and others can be added as
 * further sources without touching the state machine or the UI.
 */
interface CallSource {
    val packages: Set<String>

    /** The call this notification represents, or null if it isn't a live call (a message, a missed call). */
    fun detect(n: CallNotification): CallDetection?
}
