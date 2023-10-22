package com.github.salaink.tomcontrol.bpio

import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.ServerWebSocket
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BpioServerSession::class.java)

class BpioServerSession(vertx: Vertx, serverWebSocket: ServerWebSocket) : AbstractBpioSession(vertx, serverWebSocket) {
  val devices = MutableStateFlow(emptyMap<Int, ServerDevice>())
  private var sendingDevices = false

  private val stopAllMutable = MutableSharedFlow<Unit>(0)
  val stopAll: SharedFlow<Unit>
    get() = stopAllMutable

  init {
    log.info("Client at {} connected to our BPIO server", serverWebSocket.remoteAddress())
  }

  private suspend fun sendDeviceList(initial: (List<Message.DeviceList.Device>) -> Array<Message>) {
    if (sendingDevices) {
      send(*initial(emptyList()))
      return
    }
    sendingDevices = true

    val firstPromise = Promise.promise<Unit>()
    launch {
      var lastIndices: Set<Int>? = null
      try {
        devices.collectLatest { map ->
          if (lastIndices == null) {
            send(*initial(map.map { Message.DeviceList.Device(it.value.deviceName, it.key, it.value.deviceMessages) }))
            firstPromise.complete()
          } else {
            val newIndices = map.keys
            val messages = mutableListOf<Message>()
            for (removed in lastIndices!! - newIndices) {
              messages.add(Message.DeviceRemoved(id = 0, deviceIndex = removed))
            }
            for (added in newIndices - lastIndices!!) {
              val dev = map[added]!!
              messages.add(Message.DeviceAdded(
                id = 0,
                deviceName = dev.deviceName,
                deviceIndex = added,
                deviceMessages = dev.deviceMessages
              ))
            }
            send(*messages.toTypedArray())
          }
          lastIndices = map.keys
        }
      } finally {
        firstPromise.tryFail("No devices collected")
      }
    }
    return firstPromise.future().await()
  }

  override suspend fun handle(msg: Message) {
    when (msg) {
      is Message.RequestServerInfo -> {
        send(Message.ServerInfo(
          id = msg.id,
          serverName = "tomcontrol",
          majorVersion = 1,
          minorVersion = 0,
          buildVersion = 0,
          messageVersion = 3,
          maxPingTime = 0
        ))
      }
      is Message.StartScanning -> {
        sendDeviceList { devs -> arrayOf(
          Message.Ok(msg.id),
          *devs.map { Message.DeviceAdded(
            id = 0,
            deviceName = it.deviceName,
            deviceIndex = it.deviceIndex,
            deviceMessages = it.deviceMessages
          ) }.toTypedArray(),
          Message.ScanningFinished(0)
        ) }
      }
      is Message.RequestDeviceList -> {
        sendDeviceList { arrayOf(Message.DeviceList(id = msg.id, devices = it)) }
      }
      is Message.DeviceMessage -> {
        val device = devices.value[msg.deviceIndex]
        if (device == null) {
          log.warn("Received command for unknown device {}, ignoring", msg.deviceIndex)
        } else {
          device.handleCommand(msg, ServerDeviceHandle(msg.deviceIndex))
        }
      }
      is Message.StopAllDevices -> {
        log.info("Stopping all devices")
        stopAllMutable.emit(Unit)
      }
      else -> {
        log.warn("Ignoring incoming message: {}", msg)
      }
    }
  }

  interface ServerDevice {
    val deviceName: String
    val deviceMessages: DeviceMessages

    suspend fun handleCommand(message: Message.DeviceMessage, handle: ServerDeviceHandle)
  }

  inner class ServerDeviceHandle(private val deviceIndex: Int) {
    fun launch(block: suspend CoroutineScope.() -> Unit) {
      this@BpioServerSession.launch(block)
    }

    suspend fun sendOk(id: Int) {
      this@BpioServerSession.send(Message.Ok(id))
    }

    suspend fun send(deviceMessage: Message.DeviceMessage) {
      require(deviceMessage.deviceIndex == deviceIndex)
      this@BpioServerSession.send(deviceMessage)
    }
  }
}