package com.github.salaink.tomcontrol.dlab

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.salaink.tomcontrol.bpio.ControllableDevice
import com.github.salaink.tomcontrol.bpio.DeviceMessages
import com.github.salaink.tomcontrol.bpio.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val SERVICE_BATTERY = UUID.fromString("955a180a-0fe2-f5aa-a094-84b8d4f3e8ad")
private val CHARACTERISTIC_BATTERY = UUID.fromString("955a1500-0fe2-f5aa-a094-84b8d4f3e8ad")
private val SERVICE_PWM = UUID.fromString("955a180b-0fe2-f5aa-a094-84b8d4f3e8ad")
private val CHARACTERISTIC_POWER = UUID.fromString("955a1504-0fe2-f5aa-a094-84b8d4f3e8ad")
private val CHARACTERISTIC_PATTERN_A = UUID.fromString("955a1505-0fe2-f5aa-a094-84b8d4f3e8ad")
private val CHARACTERISTIC_PATTERN_B = UUID.fromString("955a1506-0fe2-f5aa-a094-84b8d4f3e8ad")
private val CHARACTERISTIC_CONFIG = UUID.fromString("955a1507-0fe2-f5aa-a094-84b8d4f3e8ad")

const val SAFETY_LIMIT_POWER = 768
private const val SAFETY_LIMIT_AMPLITUDE = 10
private const val SAFETY_LIMIT_LENGTH = 768

class DlabModule {
  private val shockMapper = JsonMapper.builder().findAndAddModules().build()

  private val devicesMutable = MutableStateFlow(emptyList<Device>())
  val devices: StateFlow<List<Device>>
    get() = devicesMutable

  private val connector by lazy {
    val con = Native.Connector()
    Thread { con.listen() }.also {
      it.name = "BLE event listener"
      it.isDaemon = true
    }.start()
    Thread {
      val devicesById = mutableMapOf<Long, Device>()
      while (true) {
        TimeUnit.SECONDS.sleep(2)
        val macs = con.listDevices()
        devicesMutable.value = macs.map { id -> devicesById.computeIfAbsent(id) { Device(it) } }
      }
    }.also {
      it.name = "BLE device polling"
      it.isDaemon = true
    }.start()
    con
  }

  fun scan() {
    connector.scan()
  }

  inner class Device(private val mac: Long) : ControllableDevice {
    override val device: Message.DeviceList.Device
      get() = Message.DeviceList.Device(
        "DG-Lab E-Stim",
        0,
        deviceMessages = DeviceMessages.V3(types = mapOf("RawWriteCmd" to listOf(DeviceMessages.V3.Attributes(
          featureDescriptor = "E-Stim",
          endpoints = listOf("shock")
        ))))
      )

    override val sensorReadings: SharedFlow<Message.SensorReading>
      get() = MutableSharedFlow()

    @Volatile
    private var stopGeneration = 0

    val minPower = MutableStateFlow(100)
    val maxPower = MutableStateFlow(200)

    suspend fun getBatteryPercent() {
      withContext(Dispatchers.IO) {
        connector.read(mac, SERVICE_BATTERY, CHARACTERISTIC_BATTERY)[0]
      }
    }

    override suspend fun stop() {
      stopGeneration++
      withContext(Dispatchers.IO) {
        setPowerBlocking(0, 0)
      }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun rawWrite(endpoint: String, data: List<Int>, writeWithResponse: Boolean) {
      if (endpoint == "shock") {
        val bytes = data.map { it.toByte() }.toByteArray()
        val patternSet = shockMapper.readValue<RelativePatternSet>(bytes)
        stopGeneration++ // cancel any previous in-progress shocks
        GlobalScope.launch {
          shock(patternSet)
        }
      }
    }

    private suspend fun shock(rps: RelativePatternSet) {
      withContext(Dispatchers.IO) {
        shockBlocking(rps)
      }
    }

    private fun shockBlocking(rps: RelativePatternSet) {
      val generation = stopGeneration
      val minPower = this.minPower.value
      val maxPower = this.maxPower.value
      val absolutePower = min(maxPower, max(minPower, minPower + ((maxPower - minPower) * rps.power).roundToInt()))
      setPowerBlocking(absolutePower, 0)
      try {
        for (pattern in rps.patterns) {
          if (this.stopGeneration != generation) {
            // force stop
            break
          }
          writePatternBlocking(pattern)
          TimeUnit.MILLISECONDS.sleep((pattern.pulseDurationMs + pattern.pauseDurationMs).toLong())
        }
      } finally {
        setPowerBlocking(0, 0)
      }
    }

    suspend fun shockWithAbsolutePower(power: Int) {
      withContext(Dispatchers.IO) {
        setPowerBlocking(power, 0)
        try {
          writePatternBlocking(DlabCodec.Pattern(10, 90, 10))
          TimeUnit.MILLISECONDS.sleep(100)
        } finally {
          setPowerBlocking(0, 0)
        }
      }
    }

    private fun writePatternBlocking(pattern: DlabCodec.Pattern) {
      connector.write(mac, SERVICE_PWM, CHARACTERISTIC_PATTERN_B, DlabCodec.encodePattern(DlabCodec.Pattern(
        pulseDurationMs = max(0, min(SAFETY_LIMIT_LENGTH, pattern.pulseDurationMs)),
        pauseDurationMs = max(0, pattern.pauseDurationMs),
        amplitude = max(0, min(SAFETY_LIMIT_AMPLITUDE, pattern.amplitude))
      )))
    }

    private fun setPowerBlocking(powerA: Int, powerB: Int) {
      connector.write(mac, SERVICE_PWM, CHARACTERISTIC_POWER, DlabCodec.encodePower(
        max(0, min(SAFETY_LIMIT_POWER, powerA)),
        max(0, min(SAFETY_LIMIT_POWER, powerB))
      ))
    }
  }
}