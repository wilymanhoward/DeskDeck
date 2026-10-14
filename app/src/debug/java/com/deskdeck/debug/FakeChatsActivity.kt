package com.deskdeck.debug

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Debug builds only: a stand-in for WhatsApp's main window. Opened beside DeskDeck, it "taps its
 * return-to-call bar" after a moment, starting the fake call screen from inside its own half.
 */
class FakeChatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = "Fake chats (debug)"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(30, 30, 34))
            },
        )
        window.decorView.postDelayed({ startActivity(Intent(this, FakeCallActivity::class.java)) }, 1_500)
    }
}
