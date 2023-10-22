package com.github.salaink.tomcontrol.dlab

import fr.stardustenterprises.yanl.NativeLoader
import java.util.UUID
import java.util.concurrent.TimeUnit

object Native {
  private val loader = NativeLoader.Builder()
    .build()

  init {
    loader.loadLibrary("tomcontrol-rust")
  }

  @JvmStatic
  private external fun createConnector(): Long
  @JvmStatic
  private external fun scan(connector: Long)
  @JvmStatic
  private external fun listen(connector: Long)
  @JvmStatic
  private external fun write(connector: Long, peripheralId: Long, serviceIdHi: Long, serviceIdLo: Long, characteristicIdHi: Long, characteristicIdLo: Long, data: ByteArray)
  @JvmStatic
  private external fun read(connector: Long, peripheralId: Long, serviceIdHi: Long, serviceIdLo: Long, characteristicIdHi: Long, characteristicIdLo: Long): ByteArray
  @JvmStatic
  private external fun listDevices(connector: Long): LongArray

  class Connector {
    private val connector = createConnector()

    fun scan() {
      scan(connector)
    }

    fun listen() {
      listen(connector)
    }

    fun write(peripheralId: Long, serviceId: UUID, characteristicId: UUID, data: ByteArray) {
      write(connector, peripheralId, serviceId.mostSignificantBits, serviceId.leastSignificantBits, characteristicId.mostSignificantBits, characteristicId.leastSignificantBits, data)
    }

    fun read(peripheralId: Long, serviceId: UUID, characteristicId: UUID): ByteArray {
      return read(connector, peripheralId, serviceId.mostSignificantBits, serviceId.leastSignificantBits, characteristicId.mostSignificantBits, characteristicId.leastSignificantBits)
    }

    fun listDevices(): LongArray {
      return listDevices(connector)
    }
  }
}