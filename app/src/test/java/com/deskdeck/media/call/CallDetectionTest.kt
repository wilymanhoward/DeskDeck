package com.deskdeck.media.call

import com.deskdeck.media.call.CallDetection.Kind
import com.deskdeck.media.call.CallNotification.ActionFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake `StatusBarNotification` data for WhatsApp's call notifications. */
private fun whatsApp(
    packageName: String = WhatsAppCallSource.WHATSAPP,
    category: String? = "call",
    channelId: String? = "voip_notification_4",
    template: String? = null,
    callType: Int? = null,
    isVideoExtra: Boolean? = null,
    ongoing: Boolean = true,
    chronometer: Boolean = false,
    whenMs: Long = 0,
    title: String? = "Aisyah",
    text: String? = null,
    callerName: String? = null,
    answerIntent: Boolean = false,
    declineIntent: Boolean = false,
    hangUpIntent: Boolean = false,
    actions: List<String> = emptyList(),
) = CallNotification(
    key = "0|$packageName|1|null|10123",
    packageName = packageName,
    postTime = 1_000,
    category = category,
    channelId = channelId,
    template = template,
    callType = callType,
    isVideoExtra = isVideoExtra,
    isOngoingFlag = ongoing,
    usesChronometer = chronometer,
    whenMs = whenMs,
    title = title,
    text = text,
    subText = null,
    callerName = callerName,
    hasAnswerIntent = answerIntent,
    hasDeclineIntent = declineIntent,
    hasHangUpIntent = hangUpIntent,
    actions = actions.map { ActionFacts(it, 0) },
    passesDnd = true,
)

private const val CALL_STYLE = "android.app.Notification\$CallStyle"

class CallDetectionTest {
    private val source = WhatsAppCallSource()

    @Test
    fun `callstyle incoming video call uses structured signals only`() {
        val d = source.detect(
            whatsApp(template = CALL_STYLE, callType = 1, isVideoExtra = true, callerName = "Aisyah", title = "Incoming video call", answerIntent = true, declineIntent = true),
        )!!
        assertEquals(Kind.INCOMING, d.kind)
        assertTrue(d.isVideo)
        assertEquals("Aisyah", d.caller)
        assertEquals(ActionRef.AnswerExtra, d.answer)
        assertEquals(ActionRef.DeclineExtra, d.decline)
        assertFalse(d.usedText)
    }

    @Test
    fun `callstyle ongoing call takes the app's timer and hang-up`() {
        val d = source.detect(whatsApp(template = CALL_STYLE, callType = 2, isVideoExtra = false, chronometer = true, whenMs = 5_000, hangUpIntent = true))!!
        assertEquals(Kind.ONGOING, d.kind)
        assertFalse(d.isVideo)
        assertEquals(5_000L, d.startedAtMs)
        assertEquals(ActionRef.HangUpExtra, d.hangUp)
        assertNull(d.answer)
    }

    @Test
    fun `without callstyle, english wording and action labels`() {
        val d = source.detect(whatsApp(title = "Aisyah", text = "Incoming voice call", actions = listOf("Decline", "Answer")))!!
        assertEquals(Kind.INCOMING, d.kind)
        assertFalse(d.isVideo)
        assertEquals("Aisyah", d.caller)
        assertEquals(ActionRef.Action(1), d.answer)
        assertEquals(ActionRef.Action(0), d.decline)
        assertTrue(d.usedText)
    }

    @Test
    fun `without callstyle, malay wording and action labels`() {
        val d = source.detect(whatsApp(title = "Aisyah", text = "Panggilan video masuk", actions = listOf("Tolak", "Jawab")))!!
        assertEquals(Kind.INCOMING, d.kind)
        assertTrue(d.isVideo)
        assertEquals(ActionRef.Action(1), d.answer)
        assertEquals(ActionRef.Action(0), d.decline)
    }

    @Test
    fun `ongoing call in malay with a hang-up button`() {
        val d = source.detect(whatsApp(text = "Panggilan suara sedang berlangsung", chronometer = true, whenMs = 9_000, actions = listOf("Letak")))!!
        assertEquals(Kind.ONGOING, d.kind)
        assertEquals(ActionRef.Action(0), d.hangUp)
        assertEquals(9_000L, d.startedAtMs)
    }

    @Test
    fun `calling out has no timer yet`() {
        val d = source.detect(whatsApp(text = "Calling…", actions = listOf("Hang up")))!!
        assertEquals(Kind.ONGOING, d.kind)
        assertTrue(d.connecting)
        assertNull(d.startedAtMs)
    }

    @Test
    fun `the title is not taken as the caller when it is the call line itself`() {
        val d = source.detect(whatsApp(title = "Incoming video call", text = "Aisyah"))!!
        assertNull(d.caller)
        assertTrue(d.isVideo)
    }

    @Test
    fun `whatsapp business is followed too`() {
        assertEquals(Kind.INCOMING, source.detect(whatsApp(packageName = WhatsAppCallSource.WHATSAPP_BUSINESS, callType = 1, template = CALL_STYLE))!!.kind)
    }

    @Test
    fun `messages, missed calls and other apps are not calls`() {
        assertNull(source.detect(whatsApp(category = "msg", channelId = "individual_chat_defaults_2", text = "Incoming video call?")))
        assertNull(source.detect(whatsApp(category = "missed_call", channelId = "voip_notification_4", text = "Missed voice call")))
        assertNull(source.detect(whatsApp(packageName = "org.telegram.messenger", callType = 1)))
    }

    @Test
    fun `the call channel alone is enough`() {
        val d = source.detect(whatsApp(category = null, text = "Incoming voice call", actions = listOf("Decline", "Answer")))
        assertEquals(Kind.INCOMING, d!!.kind)
    }
}

class CallTextTest {
    @Test
    fun `incoming, ongoing and video in english and malay`() {
        assertTrue(CallText.saysIncoming("Incoming voice call"))
        assertTrue(CallText.saysIncoming("Panggilan suara masuk"))
        assertTrue(CallText.saysOngoing("Ongoing video call"))
        assertTrue(CallText.saysOngoing("Panggilan video sedang berlangsung"))
        assertTrue(CallText.saysVideo("Panggilan video masuk"))
        assertFalse(CallText.saysVideo("Incoming voice call"))
        assertTrue(CallText.saysCallingOut("Ringing…"))
        assertTrue(CallText.saysCallingOut("Memanggil…"))
    }

    @Test
    fun `button labels`() {
        assertTrue(CallText.isAnswer("Answer"))
        assertTrue(CallText.isAnswer("Jawab"))
        assertTrue(CallText.isDecline("Decline"))
        assertTrue(CallText.isDecline("Tolak"))
        assertTrue(CallText.isHangUp("Hang up"))
        assertTrue(CallText.isHangUp("Letak"))
        assertFalse(CallText.isHangUp("Send"))
        assertFalse(CallText.isAnswer(null))
    }
}

class CallStateTest {
    private fun call(key: String = "k") = Call(key = key, packageName = WhatsAppCallSource.WHATSAPP, appLabel = "WhatsApp", caller = "Aisyah", isVideo = true)

    private fun detection(kind: Kind, startedAt: Long? = null, connecting: Boolean = false) =
        CallDetection(kind, "Aisyah", true, startedAt, connecting, null, null, null, usedText = false)

    @Test
    fun `incoming, answered, ended`() {
        var s: CallState = CallState.Idle
        s = reduceCall(s, CallEvent.Posted(call(), detection(Kind.INCOMING)), nowMs = 100)
        assertTrue(s is CallState.Incoming)
        s = reduceCall(s, CallEvent.Posted(call(), detection(Kind.ONGOING)), nowMs = 200)
        assertEquals(CallState.Ongoing(call(), startedAtMs = 200), s)
        // An update of the same call keeps its start time.
        s = reduceCall(s, CallEvent.Posted(call(), detection(Kind.ONGOING)), nowMs = 900)
        assertEquals(200L, (s as CallState.Ongoing).startedAtMs)
        s = reduceCall(s, CallEvent.Removed("k"), nowMs = 1_000)
        assertEquals(CallState.Ended(call(), answered = true, endedAtMs = 1_000), s)
        assertEquals(CallState.Idle, reduceCall(s, CallEvent.Settled, nowMs = 4_000))
    }

    @Test
    fun `incoming that goes away was missed or declined`() {
        val s = reduceCall(CallState.Incoming(call()), CallEvent.Removed("k"), nowMs = 50)
        assertEquals(CallState.Ended(call(), answered = false, endedAtMs = 50), s)
    }

    @Test
    fun `the app's own timer wins, and calling out has none until answered`() {
        assertEquals(42L, (reduceCall(CallState.Idle, CallEvent.Posted(call(), detection(Kind.ONGOING, startedAt = 42)), 100) as CallState.Ongoing).startedAtMs)
        var s = reduceCall(CallState.Idle, CallEvent.Posted(call(), detection(Kind.ONGOING, connecting = true)), 100)
        assertNull((s as CallState.Ongoing).startedAtMs)
        s = reduceCall(s, CallEvent.Posted(call(), detection(Kind.ONGOING)), 300)
        assertEquals(300L, (s as CallState.Ongoing).startedAtMs)
    }

    @Test
    fun `other notifications going away don't end the call`() {
        val s = CallState.Ongoing(call(), 10)
        assertEquals(s, reduceCall(s, CallEvent.Removed("other"), 20))
        assertEquals(s, reduceCall(s, CallEvent.Settled, 20))
    }
}
