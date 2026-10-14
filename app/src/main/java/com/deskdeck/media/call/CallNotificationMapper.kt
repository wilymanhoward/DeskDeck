package com.deskdeck.media.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

// CallStyle's extras (Android 12+). The framework constants are API 31+, so their values are spelled out.
private const val EXTRA_CALL_TYPE = "android.callType"
private const val EXTRA_CALL_PERSON = "android.callPerson"
private const val EXTRA_CALL_IS_VIDEO = "android.callIsVideo"
private const val EXTRA_ANSWER_INTENT = "android.answerIntent"
private const val EXTRA_DECLINE_INTENT = "android.declineIntent"
private const val EXTRA_HANG_UP_INTENT = "android.hangUpIntent"

/** The plain facts [CallSource]s decide on. Reads only what call detection needs. */
fun StatusBarNotification.toCallNotification(passesDnd: Boolean): CallNotification {
    val n = notification
    val extras = n.extras
    return CallNotification(
        key = key,
        packageName = packageName,
        postTime = postTime,
        category = n.category,
        channelId = n.channelId,
        template = extras.getString(Notification.EXTRA_TEMPLATE),
        callType = extras.getInt(EXTRA_CALL_TYPE, 0).takeIf { it != 0 },
        isVideoExtra = if (extras.containsKey(EXTRA_CALL_IS_VIDEO)) extras.getBoolean(EXTRA_CALL_IS_VIDEO) else null,
        isOngoingFlag = isOngoing,
        usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
        whenMs = n.`when`,
        title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        callerName = extras.callPerson()?.name?.toString(),
        hasAnswerIntent = extras.pendingIntent(EXTRA_ANSWER_INTENT) != null,
        hasDeclineIntent = extras.pendingIntent(EXTRA_DECLINE_INTENT) != null,
        hasHangUpIntent = extras.pendingIntent(EXTRA_HANG_UP_INTENT) != null,
        actions = n.actions.orEmpty().map {
            CallNotification.ActionFacts(
                title = it.title?.toString(),
                semanticAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.semanticAction else 0,
            )
        },
        passesDnd = passesDnd,
    )
}

/** The [Call] for a detected call notification, with the app's own buttons and the caller's picture. */
fun StatusBarNotification.toCall(context: Context, detection: CallDetection, appLabel: String, passesDnd: Boolean): Call {
    val n = notification
    val extras = n.extras
    fun resolve(ref: ActionRef?): PendingIntent? = when (ref) {
        null -> null
        ActionRef.AnswerExtra -> extras.pendingIntent(EXTRA_ANSWER_INTENT)
        ActionRef.DeclineExtra -> extras.pendingIntent(EXTRA_DECLINE_INTENT)
        ActionRef.HangUpExtra -> extras.pendingIntent(EXTRA_HANG_UP_INTENT)
        is ActionRef.Action -> n.actions?.getOrNull(ref.index)?.actionIntent
    }
    return Call(
        key = key,
        packageName = packageName,
        appLabel = appLabel,
        caller = detection.caller,
        isVideo = detection.isVideo,
        ringsThroughDnd = passesDnd,
        avatar = avatar(context),
        actions = CallActions(
            answer = resolve(detection.answer),
            decline = resolve(detection.decline),
            hangUp = resolve(detection.hangUp),
            open = n.contentIntent,
        ),
    )
}

/** The caller's picture: CallStyle's person, else the notification's large icon. */
private fun StatusBarNotification.avatar(context: Context): Bitmap? {
    val icon: Icon? = notification.extras.callPerson()?.icon ?: notification.getLargeIcon()
    return runCatching { icon?.loadDrawable(context)?.toBitmap(AVATAR_PX, AVATAR_PX) }.getOrNull()
}

private const val AVATAR_PX = 192

private fun Bundle.callPerson(): Person? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelable(EXTRA_CALL_PERSON, Person::class.java)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> @Suppress("DEPRECATION") (getParcelable(EXTRA_CALL_PERSON) as? Person)
    else -> null
}

private fun Bundle.pendingIntent(key: String): PendingIntent? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelable(key, PendingIntent::class.java)
    else -> @Suppress("DEPRECATION") (getParcelable(key) as? PendingIntent)
}
