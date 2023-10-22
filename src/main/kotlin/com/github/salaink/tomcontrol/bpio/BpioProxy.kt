package com.github.salaink.tomcontrol.bpio

import kotlinx.coroutines.flow.*

fun proxy(flow: Flow<List<ControllableDevice>>): Flow<Map<Int, BpioServerSession.ServerDevice>> {
  var nextDeviceId = 0
  val assigned = mutableMapOf<ControllableDevice, ProxyDevice>()
  return flow.map { clientDevices ->
      for (clientDevice in clientDevices) {
        if (clientDevice !in assigned) {
          val deviceId = nextDeviceId++
          assigned[clientDevice] = ProxyDevice(clientDevice, deviceId)
        }
      }
      clientDevices.associate { clientDevice ->
        val proxyDevice = assigned[clientDevice]!!
        proxyDevice.serverIndex to proxyDevice
      }
    }
}