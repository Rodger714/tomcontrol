package com.github.salaink.tomcontrol.bpio

import io.vertx.core.Vertx
import io.vertx.core.http.WebSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BpioClient::class.java)

class BpioClient(vertx: Vertx, private val webSocket: WebSocket) : AbstractBpioSession(vertx, webSocket) {

  private val expectingResponse = mutableMapOf<Int, SendChannel<Message>>()
  private var nextId = 1

  private val mutableDevices = MutableStateFlow(emptyList<ClientDevice>())
  val devices: StateFlow<List<ClientDevice>>
    get() = mutableDevices

  init {
    webSocket.closeHandler {
      for (channel in expectingResponse.values) {
        channel.close()
      }
      log.info("Disconnected from BPIO client {}", webSocket.remoteAddress())
    }
    launch {
      val serverInfo = sendExpectingResponse<Message.ServerInfo> {
        Message.RequestServerInfo(
          id = it,
          clientName = "tomcontrol",
          messageVersion = 3
        )
      }
      val initialDeviceList = sendExpectingResponse<Message.DeviceList> { Message.RequestDeviceList(id = it) }
      addDevices(initialDeviceList.devices)
      log.info("Successfully connected to BPIO client at {}. Server info: {}", webSocket.remoteAddress(), serverInfo)
    }
  }

  private fun addDevices(devices: List<Message.DeviceList.Device>) {
    mutableDevices.update { oldDevices ->
      val newDevices = oldDevices.toMutableList()
      for (dev in devices) {
        if (newDevices.none { it.deviceIndex == dev.deviceIndex }) {
          log.info("Added device {}", dev)
          newDevices.add(ClientDevice(dev))
        }
      }
      newDevices
    }
  }

  private suspend inline fun <reified M : Message> sendExpectingResponse(msgFactory: (Int) -> Message): M {
    val id = nextId++
    val msg = msgFactory(id)
    val ch = Channel<Message>(1)
    expectingResponse[id] = ch
    send(msg)
    val response = ch.receive()
    expectingResponse.remove(id)
    return response as M
  }

  override suspend fun handle(msg: Message) {
    expectingResponse[msg.id]?.let {
      it.send(msg)
      return
    }
    when (msg) {
      is Message.SensorReading -> {
        devices.value.singleOrNull { it.deviceIndex == msg.deviceIndex }?.sensorReadings?.emit(msg)
      }
      is Message.DeviceAdded -> {
        addDevices(listOf(Message.DeviceList.Device(msg.deviceName, msg.deviceIndex, msg.deviceMessages)))
      }
      is Message.DeviceRemoved -> {
        mutableDevices.update { it.filter { dev -> dev.deviceIndex != msg.deviceIndex } }
      }
      else -> {
        log.warn("Ignoring incoming message: {}", msg)
      }
    }
  }

  suspend fun stopAll() {
    sendExpectingResponse<Message.Ok> { Message.StopAllDevices(it) }
  }

  inner class ClientDevice(override val device: Message.DeviceList.Device) : ControllableDevice {
    override val sensorReadings = MutableSharedFlow<Message.SensorReading>()

    val deviceIndex: Int by device::deviceIndex

    override suspend fun stop() {
      sendExpectingResponse<Message.Ok> { Message.StopDeviceCmd(it, deviceIndex) }
    }

    override suspend fun scalar(scalars: List<Message.ScalarCmd.Value>) {
      sendExpectingResponse<Message.Ok> { Message.ScalarCmd(it, deviceIndex, scalars) }
    }

    override suspend fun linear(scalars: List<Message.LinearCmd.Value>) {
      sendExpectingResponse<Message.Ok> { Message.LinearCmd(it, deviceIndex, scalars) }
    }

    override suspend fun rotate(scalars: List<Message.RotateCmd.Value>) {
      sendExpectingResponse<Message.Ok> { Message.RotateCmd(it, deviceIndex, scalars) }
    }

    override suspend fun sensorRead(sensorIndex: Int, sensorType: String): Message.SensorReading {
      return sendExpectingResponse<Message.SensorReading> { Message.SensorReadCmd(it, deviceIndex, sensorIndex, sensorType) }
    }

    override suspend fun sensorSubscribe(sensorIndex: Int, sensorType: String) {
      sendExpectingResponse<Message.Ok> { Message.SensorSubscribeCmd(it, deviceIndex, sensorIndex, sensorType) }
    }

    override suspend fun sensorUnsubscribe(sensorIndex: Int, sensorType: String) {
      sendExpectingResponse<Message.Ok> { Message.SensorUnsubscribeCmd(it, deviceIndex, sensorIndex, sensorType) }
    }

    override suspend fun rawWrite(endpoint: String, data: List<Int>, writeWithResponse: Boolean) {
      sendExpectingResponse<Message.Ok> { Message.RawWriteCmd(it, deviceIndex, endpoint, data, writeWithResponse) }
    }
  }
}