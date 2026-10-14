package com.deskdeck.debug

import android.app.PictureInPictureParams
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Debug builds only: a stand-in for WhatsApp's call screen (VoipActivityV2), which runs in its own
 * task ("…:voip"), supports picture-in-picture, and goes into a floating window when the user
 * leaves it during a video call. Used to try "Open call beside DeskDeck" without a real call.
 */
class FakeCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "Fake call screen\n(debug)"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(11, 80, 60))
            },
        )
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) {
            finishAndRemoveTask()
            return
        }
        if (intent.getBooleanExtra(EXTRA_PIP, false)) {
            window.decorView.post { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) finishAndRemoveTask()
    }

    // Like a video call app: leaving the call screen shrinks it into a floating window.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipOnLeave) enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }

    private val pipOnLeave get() = !intent.getBooleanExtra(EXTRA_NO_PIP, false)

    companion object {
        const val EXTRA_PIP = "pip"
        const val EXTRA_FINISH = "finish"
        /** A voice call: leaving the screen just sends it to the background. */
        const val EXTRA_NO_PIP = "no_pip"
    }
}
