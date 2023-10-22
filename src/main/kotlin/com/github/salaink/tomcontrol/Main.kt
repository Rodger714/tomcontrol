package com.github.salaink.tomcontrol

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.salaink.tomcontrol.dlab.DlabModule
import com.github.salaink.tomcontrol.dlab.SAFETY_LIMIT_POWER
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class Main {
  val tomControl = TomControl()

  @Composable
  @Preview
  fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
      Column {
        Card {
          Column {
            Text("BPIO Server")
            val listenPort by tomControl.configuration.map { it.listenPort }.collectAsState(Configuration().listenPort)
            TextField(
              "$listenPort",
              { text -> tomControl.configuration.update { it.copy(listenPort = text.toInt()) } },
              label = { Text("Listen Port") }
            )
            val listening by tomControl.serverListening.collectAsState(false)
            Button(onClick = {
              GlobalScope.launch {
                if (listening) {
                  tomControl.stopServer()
                } else {
                  tomControl.startServer()
                }
              }
            }, colors = ButtonDefaults.buttonColors(if (listening) Color.Red else Color.Green)) {
              Text(if (listening) "Stop Server" else "Start Server")
            }
            val connectedClients by tomControl.serverConnectedClientCount.collectAsState()
            Text("Connected clients: $connectedClients")
            val availableDevices by tomControl.allDevices.map { it.size }.collectAsState(0)
            Text("Available devices: $availableDevices")
          }
        }
        Card {
          Column {
            Text("BPIO Client")
            val clientUri by tomControl.configuration.map { it.clientUri }.collectAsState(Configuration().clientUri)
            TextField(
              clientUri,
              { text -> tomControl.configuration.update { it.copy(clientUri = text) } },
              label = { Text("Intiface URI") }
            )
            val connected by tomControl.clientConnected.collectAsState(false)
            Button(onClick = {
              GlobalScope.launch {
                if (connected) {
                  tomControl.disconnectClient()
                } else {
                  tomControl.connectClient()
                }
              }
            }, colors = ButtonDefaults.buttonColors(if (connected) Color.Red else Color.Green)) {
              Text(if (connected) "Disconnect from Intiface" else "Connect to Intiface")
            }
            val availableDevices by tomControl.bpioClient
              .flatMapLatest { it?.devices ?: flowOf(null) }
              .map { it?.size }
              .collectAsState(null)
            Text("Intiface devices: ${availableDevices ?: "Not connected"}")
          }
        }
        Card {
          Column {
            Text("DG-Lab E-Stim")
            Button(onClick = {
              GlobalScope.launch {
                tomControl.dlabModule.scan()
              }
            }) {
              Text("Scan")
            }
            val availableDevices by tomControl.dlabModule.devices.collectAsState()
            for (device in availableDevices) {
              Row {
                val minPower by device.minPower.collectAsState()
                val maxPower by device.maxPower.collectAsState()
                val calibrating = remember { mutableStateOf(false) }
                Text("Device range: $minPower to $maxPower")
                Button(onClick = {
                  calibrating.value = true
                }) {
                  Text("Calibrate")
                }
                DlabCalibrate(device, calibrating)
              }
            }
          }
        }
      }
    }
  }

  @Composable
  @Preview
  fun DlabCalibrate(device: DlabModule.Device, visible: MutableState<Boolean>) {
    Dialog(onCloseRequest = { visible.value = false }, visible = visible.value) {
      Column {
        for (min in listOf(true, false)) {
          Row {
            val stateFlow = if (min) device.minPower else device.maxPower
            val power by stateFlow.collectAsState()
            Text(if (min) "Min Power:" else "Max Power:")
            val canDecrease = power > 0 && (min || power > device.minPower.value)
            val canIncrease = power < SAFETY_LIMIT_POWER - 49 && (!min || power < device.maxPower.value)
            Button(onClick = {
              if (canDecrease) {
                stateFlow.value = power - 50
              }
            }, enabled = canDecrease) {
              Text("-50")
            }
            Button(onClick = {
              if (canDecrease) {
                val newPower = power - 50
                GlobalScope.launch {
                  device.shockWithAbsolutePower(newPower)
                  stateFlow.value = newPower
                }
              }
            }, enabled = canDecrease) {
              Text("-50 ⚡")
            }
            Text("$power")
            Button(onClick = {
              GlobalScope.launch {
                device.shockWithAbsolutePower(power)
              }
            }) {
              Text("⚡")
            }
            Button(onClick = {
              if (canIncrease) {
                val newPower = power + 50
                GlobalScope.launch {
                  device.shockWithAbsolutePower(newPower)
                  stateFlow.value = newPower
                }
              }
            }, enabled = canIncrease) {
              Text("+50 ⚡")
            }
          }
        }
      }
    }
  }
}

fun main() = application {
  Window(onCloseRequest = ::exitApplication) {
    Main().App()
  }
}
