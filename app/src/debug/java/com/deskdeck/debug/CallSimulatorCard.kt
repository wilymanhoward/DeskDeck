package com.deskdeck.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.deskdeck.ui.theme.CappedWhite
import com.deskdeck.ui.theme.Dimens
import com.deskdeck.ui.theme.MutedText
import com.deskdeck.ui.theme.glass

/** Settings › Calls in debug builds: buttons that post simulated calls. Not in release builds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallSimulatorCard() {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke()
    }
    fun run(action: () -> Unit) {
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pending = action
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(Dimens.cardCorner))
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text("Simulate call (debug build)", style = MaterialTheme.typography.titleMedium, color = CappedWhite)
        Text(
            "Posts a made-up call notification shaped like WhatsApp's. Its Answer, Decline and Hang up buttons work.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
        )
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SimButton("Incoming video call") { run { CallSimulator.incoming(context, video = true, mode = CallSimulator.Mode.STRUCTURED) } }
            SimButton("Incoming voice call") { run { CallSimulator.incoming(context, video = false, mode = CallSimulator.Mode.STRUCTURED, caller = "Daniel") } }
            SimButton("Incoming, Malay wording only") { run { CallSimulator.incoming(context, video = true, mode = CallSimulator.Mode.WORDING_MALAY) } }
            SimButton("Incoming, no call buttons") { run { CallSimulator.incoming(context, video = false, mode = CallSimulator.Mode.NO_BUTTONS) } }
            SimButton("Ongoing call") { run { CallSimulator.ongoing(context) } }
            SimButton("End call") { CallSimulator.end(context) }
        }
    }
}

@Composable
private fun SimButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = Dimens.minTouch)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
