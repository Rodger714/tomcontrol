package com.github.salaink.tomcontrol.bpio

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ProxyDevice::class.java)

class ProxyDevice(
  private val clientDevice: ControllableDevice,
  val serverIndex: Int
) : BpioServerSession.ServerDevice {
  private var subscribedSensor = false

  override val deviceName = clientDevice.device.deviceName
  override val deviceMessages = clientDevice.device.deviceMessages

  override suspend fun handleCommand(
    message: Message.DeviceMessage,
    handle: BpioServerSession.ServerDeviceHandle
  ) {
    when (message) {
      is Message.LinearCmd -> {
        clientDevice.linear(message.vectors)
        handle.sendOk(message.id)
      }
      is Message.RawWriteCmd -> {
        clientDevice.rawWrite(message.endpoint, message.data, message.writeWithResponse)
        handle.sendOk(message.id)
      }
      is Message.RotateCmd -> {
        clientDevice.rotate(message.rotations)
        handle.sendOk(message.id)
      }
      is Message.ScalarCmd -> {
        clientDevice.scalar(message.scalars)
        handle.sendOk(message.id)
      }
      is Message.SensorReadCmd -> {
        val reading = clientDevice.sensorRead(message.sensorIndex, message.sensorType)
        handle.send(mapSensorReading(message.id, message.deviceIndex, reading))
      }
      is Message.SensorSubscribeCmd -> {
        if (!subscribedSensor) {
          handle.launch {
            clientDevice.sensorReadings.collect {
              handle.send(mapSensorReading(0, message.deviceIndex, it))
            }
          }
          subscribedSensor = true
        }
        clientDevice.sensorSubscribe(message.sensorIndex, message.sensorType)
        handle.sendOk(message.id)
      }
      is Message.SensorUnsubscribeCmd -> {
        clientDevice.sensorUnsubscribe(message.sensorIndex, message.sensorType)
        handle.sendOk(message.id)
      }
      is Message.StopDeviceCmd -> {
        clientDevice.stop()
        handle.sendOk(message.id)
      }
      else -> {
        log.warn("Failed to forward command {}", message)
      }
    }
  }

  private fun mapSensorReading(
    id: Int,
    deviceIndex: Int,
    reading: Message.SensorReading
  ) = Message.SensorReading(id, deviceIndex, reading.sensorIndex, reading.sensorType, reading.data)
}