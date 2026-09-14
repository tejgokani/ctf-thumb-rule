package com.tejgokani.strata.ui.screens.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tejgokani.strata.ui.components.BarMeter
import com.tejgokani.strata.ui.components.SlabCard
import com.tejgokani.strata.ui.theme.StrataColors
import com.tejgokani.strata.ui.theme.StrataType
import com.tejgokani.strata.ui.vm.AppPhase
import com.tejgokani.strata.ui.vm.BootViewModel
import com.tejgokani.strata.ui.vm.passphraseStrength

@Composable
fun BootScreen(vm: BootViewModel, onReady: () -> Unit) {
    val state by vm.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(StrataColors.Bg).padding(24.dp)) {
        when (state.phase) {
            AppPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = StrataColors.Signal)
            }
            AppPhase.NEEDS_SETUP -> {
                if (state.recoveryKeyToShow != null && !state.recoveryKeyAcknowledged) {
                    RecoveryKeyScreen(recoveryKey = state.recoveryKeyToShow!!, onAcknowledge = {
                        vm.acknowledgeRecoveryKey()
                        onReady()
                    })
                } else {
                    SetupScreen(vm)
                }
            }
            AppPhase.LOCKED -> UnlockScreen(vm)
            AppPhase.READY -> onReady()
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            "STRATA",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = StrataType.Mono,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 28.sp,
                color = StrataColors.Fg,
                letterSpacing = 0.05.em,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text("ONE VAULT. MANY DRIVES.", style = StrataType.LabelDim)
    }
}

@Composable
private fun SetupScreen(vm: BootViewModel) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Header()
        Spacer(Modifier.height(32.dp))
        SlabCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("CREATE VAULT", style = StrataType.Label)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This passphrase encrypts every file before it ever leaves your device. " +
                        "STRATA does not store it anywhere — losing it means losing access unless " +
                        "you keep the recovery key shown on the next screen.",
                    style = StrataType.BodyDim,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.passphrase, onValueChange = vm::setPassphrase,
                    label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), colors = strataFieldColors(),
                )
                Spacer(Modifier.height(4.dp))
                BarMeter(fraction = passphraseStrength(state.passphrase), modifier = Modifier.height(6.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.passphraseConfirm, onValueChange = vm::setPassphraseConfirm,
                    label = { Text("Confirm passphrase") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), colors = strataFieldColors(),
                )
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error!!, style = StrataType.Data.copy(color = StrataColors.Alert))
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = vm::createVault, enabled = !state.unlocking, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StrataColors.Signal, contentColor = StrataColors.Bg),
                ) {
                    if (state.unlocking) CircularProgressIndicator(modifier = Modifier.height(16.dp), color = StrataColors.Bg)
                    else Text("CREATE VAULT")
                }
            }
        }
    }
}

@Composable
private fun UnlockScreen(vm: BootViewModel) {
    val state by vm.state.collectAsState()
    var showRecoveryInput by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Header()
        Spacer(Modifier.height(32.dp))
        SlabCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(if (showRecoveryInput) "UNLOCK WITH RECOVERY KEY" else "UNLOCK VAULT", style = StrataType.Label)
                Spacer(Modifier.height(16.dp))
                if (showRecoveryInput) {
                    OutlinedTextField(
                        value = recoveryInput, onValueChange = { recoveryInput = it },
                        label = { Text("Recovery key") }, modifier = Modifier.fillMaxWidth(), colors = strataFieldColors(),
                    )
                } else {
                    OutlinedTextField(
                        value = state.passphrase, onValueChange = vm::setPassphrase,
                        label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), colors = strataFieldColors(),
                    )
                }
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error!!, style = StrataType.Data.copy(color = StrataColors.Alert))
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { if (showRecoveryInput) vm.unlockWithRecoveryKey(recoveryInput) else vm.unlockWithPassphrase() },
                    enabled = !state.unlocking, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StrataColors.Signal, contentColor = StrataColors.Bg),
                ) {
                    if (state.unlocking) CircularProgressIndicator(modifier = Modifier.height(16.dp), color = StrataColors.Bg)
                    else Text("UNLOCK")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showRecoveryInput = !showRecoveryInput }) {
                    Text(if (showRecoveryInput) "Use passphrase instead" else "Use recovery key instead", style = StrataType.BodyDim)
                }
            }
        }
    }
}

@Composable
private fun RecoveryKeyScreen(recoveryKey: String, onAcknowledge: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("SAVE YOUR RECOVERY KEY", style = StrataType.Label.copy(color = StrataColors.Alert))
        Spacer(Modifier.height(8.dp))
        Text(
            "If you forget your passphrase, this key is the ONLY other way to recover your " +
                "vault. STRATA cannot reset it for you. Write it down or save it somewhere safe, offline.",
            style = StrataType.BodyDim,
        )
        Spacer(Modifier.height(20.dp))
        SlabCard(modifier = Modifier.fillMaxWidth(), corner = false) {
            Text(recoveryKey, style = StrataType.DataLarge.copy(color = StrataColors.Ok))
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = acknowledged, onCheckedChange = { acknowledged = it },
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = StrataColors.Signal),
            )
            Text("I have saved this recovery key somewhere safe", style = StrataType.Body)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAcknowledge, enabled = acknowledged, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = StrataColors.Signal, contentColor = StrataColors.Bg),
        ) { Text("CONTINUE") }
    }
}

@Composable
private fun strataFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = StrataColors.Fg, unfocusedTextColor = StrataColors.Fg,
    focusedBorderColor = StrataColors.Signal, unfocusedBorderColor = StrataColors.Hairline,
    focusedLabelColor = StrataColors.Signal, unfocusedLabelColor = StrataColors.Dim,
    cursorColor = StrataColors.Signal,
)
