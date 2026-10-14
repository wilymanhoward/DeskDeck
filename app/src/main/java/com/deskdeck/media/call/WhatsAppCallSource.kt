package com.deskdeck.media.call

import javax.inject.Inject

/**
 * WhatsApp and WhatsApp Business calls. Signals, most reliable first:
 * 1. `category == "call"`, CallStyle (Android 12+), or WhatsApp's call channel ("voip_notification_N",
 *    seen on WhatsApp 2.26). A missed-call notification is never a live call.
 * 2. Incoming vs ongoing: CallStyle's call type; else CallStyle's answer/hang-up intents; else an
 *    action button labelled Answer/Hang up; else the wording ([CallText]); else a running timer.
 * 3. Video: CallStyle's video flag, else the word "video" in the title or text.
 */
class WhatsAppCallSource @Inject constructor() : CallSource {
    override val packages = setOf(WHATSAPP, WHATSAPP_BUSINESS)

    override fun detect(n: CallNotification): CallDetection? {
        if (n.packageName !in packages) return null
        if (n.category == CATEGORY_MISSED_CALL) return null
        val callStyle = n.template?.endsWith(CALL_STYLE) == true
        val isCall = n.category == CATEGORY_CALL || callStyle || n.callType != null ||
            n.channelId?.startsWith(VOIP_CHANNEL) == true
        if (!isCall) return null

        var usedText = false
        // Buttons: CallStyle's own intents first, then labelled actions.
        val answerIndex = n.actions.indexOfFirst { CallText.isAnswer(it.title) }.takeIf { it >= 0 }
        val declineIndex = n.actions.indexOfFirst { CallText.isDecline(it.title) }.takeIf { it >= 0 }
        val hangUpIndex = n.actions.indexOfFirst { CallText.isHangUp(it.title) && !CallText.isDecline(it.title) }.takeIf { it >= 0 }
        val answer = if (n.hasAnswerIntent) ActionRef.AnswerExtra else answerIndex?.let { usedText = true; ActionRef.Action(it) }
        val decline = if (n.hasDeclineIntent) ActionRef.DeclineExtra else declineIndex?.let { usedText = true; ActionRef.Action(it) }
        val hangUp = if (n.hasHangUpIntent) ActionRef.HangUpExtra else hangUpIndex?.let { usedText = true; ActionRef.Action(it) }

        val kind = when (n.callType) {
            CALL_TYPE_INCOMING, CALL_TYPE_SCREENING -> CallDetection.Kind.INCOMING
            CALL_TYPE_ONGOING -> CallDetection.Kind.ONGOING
            else -> when {
                n.hasAnswerIntent -> CallDetection.Kind.INCOMING
                n.hasHangUpIntent -> CallDetection.Kind.ONGOING
                answer != null -> CallDetection.Kind.INCOMING
                CallText.saysIncoming(n.title, n.text, n.subText) -> { usedText = true; CallDetection.Kind.INCOMING }
                // Any other live call notification: in a call, or calling out.
                else -> CallDetection.Kind.ONGOING
            }
        }
        val isVideo = n.isVideoExtra ?: CallText.saysVideo(n.title, n.text, n.subText).also { if (it) usedText = true }
        // CallStyle names the caller; otherwise WhatsApp puts the name in the title. The title is
        // only a name when it isn't the "Incoming voice call" line itself.
        val caller = n.callerName?.takeIf { it.isNotBlank() }
            ?: n.title?.takeIf { it.isNotBlank() && !CallText.saysIncoming(it) && !CallText.saysOngoing(it) && !CallText.saysCallingOut(it) }
        return CallDetection(
            kind = kind,
            caller = caller?.trim(),
            isVideo = isVideo,
            startedAtMs = n.whenMs.takeIf { kind == CallDetection.Kind.ONGOING && n.usesChronometer && it > 0 },
            connecting = kind == CallDetection.Kind.ONGOING && !n.usesChronometer && CallText.saysCallingOut(n.title, n.text, n.subText),
            answer = answer.takeIf { kind == CallDetection.Kind.INCOMING },
            decline = decline.takeIf { kind == CallDetection.Kind.INCOMING },
            hangUp = hangUp ?: decline.takeIf { kind == CallDetection.Kind.ONGOING },
            usedText = usedText,
        )
    }

    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val CATEGORY_CALL = "call"
        private const val CATEGORY_MISSED_CALL = "missed_call"
        private const val CALL_STYLE = "\$CallStyle"
        private const val VOIP_CHANNEL = "voip_notification"
        private const val CALL_TYPE_INCOMING = 1
        private const val CALL_TYPE_ONGOING = 2
        private const val CALL_TYPE_SCREENING = 3
    }
}
