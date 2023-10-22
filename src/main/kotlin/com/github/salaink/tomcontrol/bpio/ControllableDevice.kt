package com.github.salaink.tomcontrol.bpio

import kotlinx.coroutines.flow.SharedFlow

interface ControllableDevice {
  val device: Message.DeviceList.Device
  val sensorReadings: SharedFlow<Message.SensorReading>

  suspend fun stop() {
    throw UnsupportedOperationException()
  }

  suspend fun scalar(scalars: List<Message.ScalarCmd.Value>) {
    throw UnsupportedOperationException()
  }

  suspend fun linear(scalars: List<Message.LinearCmd.Value>) {
    throw UnsupportedOperationException()
  }

  suspend fun rotate(scalars: List<Message.RotateCmd.Value>) {
    throw UnsupportedOperationException()
  }

  suspend fun sensorRead(sensorIndex: Int, sensorType: String): Message.SensorReading {
    throw UnsupportedOperationException()
  }

  suspend fun sensorSubscribe(sensorIndex: Int, sensorType: String) {
    throw UnsupportedOperationException()
  }

  suspend fun sensorUnsubscribe(sensorIndex: Int, sensorType: String) {
    throw UnsupportedOperationException()
  }

  suspend fun rawWrite(endpoint: String, data: List<Int>, writeWithResponse: Boolean) {
    throw UnsupportedOperationException()
  }
}