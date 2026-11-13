package wifiwalkie.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Shows the Connect screen, or the Signal screen while connected. */
@Composable
fun WalkieApp(viewModel: WalkieViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Only an Activity can ask for a permission, so this one lives here. After it's granted, the next hold talks.
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val onTalk: (Boolean) -> Unit = { on ->
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (on && !allowed) askForMicrophone.launch(Manifest.permission.RECORD_AUDIO) else viewModel.setTalking(on)
    }
    if (state.connected) {
        SignalScreen(state, onTalk = onTalk, onSignal = viewModel::setLocalSignal, onDisconnect = viewModel::disconnect)
    } else {
        ConnectScreen(localIp = viewModel.localIp(), error = state.error, onConnect = viewModel::connect)
    }
}

@Composable
fun ConnectScreen(localIp: String?, error: String?, onConnect: (peerIp: String, useTcp: Boolean) -> Unit) {
    var peerIp by rememberSaveable { mutableStateOf("") }
    var useTcp by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Wi-fi Walkie", style = MaterialTheme.typography.headlineMedium)
        Text("This phone's IP: ${localIp ?: "not on a network"}")
        OutlinedTextField(
            value = peerIp,
            onValueChange = { peerIp = it },
            label = { Text("Other device's IP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !useTcp, onClick = { useTcp = false })
            Text("UDP")
            Spacer(Modifier.padding(horizontal = 12.dp))
            RadioButton(selected = useTcp, onClick = { useTcp = true })
            Text("TCP")
        }
        Text("Both devices must choose the same one. Port 5000.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onConnect(peerIp, useTcp) }, enabled = peerIp.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Connect")
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SignalScreen(
    state: WalkieViewModel.State,
    onTalk: (Boolean) -> Unit,
    onSignal: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    // The whole screen turns white while the other person sends Morse.
    val background = if (state.peerOn) Color.White else Color(0xFF121212)
    val foreground = if (state.peerOn) Color.Black else Color.White

    BackHandler(onBack = onDisconnect)
    KeepScreenOn()

    Column(
        modifier = Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            !state.peerConnected -> Text("Waiting for the other device…", color = foreground, style = MaterialTheme.typography.titleLarge)
            state.peerTalking -> Text(
                "THEY'RE TALKING",
                color = Color.Black,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PEER_TALKING_COLOR).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            else -> Text("Connected", color = foreground, style = MaterialTheme.typography.titleLarge)
        }
        HoldArea(
            text = if (state.talking) "TALKING…" else "HOLD TO TALK",
            color = if (state.talking) Color(0xFF2E7D32) else Color(0xFF43A047),
            onHold = onTalk,
            modifier = Modifier.weight(2f),
        )
        HoldArea(
            text = if (state.localOn) "SENDING…" else "HOLD FOR MORSE",
            color = if (state.localOn) Color(0xFFE0943A) else Color(0xFF3F89CA),
            onHold = onSignal,
            modifier = Modifier.weight(1f),
        )
        if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect", color = foreground)
        }
    }
}

/** A big button that reports true when a finger goes down on it and false when it lifts. */
@Composable
private fun HoldArea(text: String, color: Color, onHold: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val currentOnHold by rememberUpdatedState(onHold)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    currentOnHold(true)
                    tryAwaitRelease()
                    currentOnHold(false)
                })
            },
    ) {
        Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

private val PEER_TALKING_COLOR = Color(0xFFA5D6A7)

/** Keeps the display on while this screen is showing, so a long conversation isn't interrupted. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
