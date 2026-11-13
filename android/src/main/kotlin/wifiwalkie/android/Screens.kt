package wifiwalkie.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Shows the Connect screen, or the Signal screen while connected. */
@Composable
fun WalkieApp(viewModel: WalkieViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.connected) {
        SignalScreen(state, onSignal = viewModel::setLocalSignal, onDisconnect = viewModel::disconnect)
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
fun SignalScreen(state: WalkieViewModel.State, onSignal: (Boolean) -> Unit, onDisconnect: () -> Unit) {
    // The whole screen turns white while the other person sends.
    val background = if (state.peerOn) Color.White else Color(0xFF121212)
    val foreground = if (state.peerOn) Color.Black else Color.White
    val currentOnSignal by rememberUpdatedState(onSignal)

    BackHandler(onBack = onDisconnect)
    KeepScreenOn()

    Column(
        modifier = Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (state.peerConnected) "Connected" else "Waiting for the other device…",
            color = foreground,
            style = MaterialTheme.typography.titleLarge,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(if (state.localOn) Color(0xFFE0943A) else Color(0xFF3F89CA))
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        currentOnSignal(true)
                        tryAwaitRelease()
                        currentOnSignal(false)
                    })
                },
        ) {
            Text(
                if (state.localOn) "SENDING…" else "HOLD TO SEND",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect", color = foreground)
        }
    }
}

/** Keeps the display on while this screen is showing, so a long conversation isn't interrupted. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
