package com.github.salaink.tomcontrol.bpio
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT
)
sealed interface Message {
    val id: Int

    sealed interface DeviceMessage : Message {
        val deviceIndex: Int
    }

    @JsonTypeName("Ok")
    data class Ok(
        override val id: Int
    ) : Message

    @JsonTypeName("Ok")
    data class Error(
        override val id: Int,
        val errorMessage: String,
        val errorCode: Int
    ) : Message

    @JsonTypeName("Ping")
    data class Ping(
        override val id: Int
    ) : Message

    @JsonTypeName("RequestServerInfo")
    data class RequestServerInfo(
        override val id: Int,
        val clientName: String,
        val messageVersion: Int
    ) : Message

    @JsonTypeName("ServerInfo")
    data class ServerInfo(
        override val id: Int,
        val serverName: String,
        val majorVersion: Int = 0,
        val minorVersion: Int = 0,
        val buildVersion: Int = 0,
        val messageVersion: Int,
        val maxPingTime: Int,
    ) : Message

    @JsonTypeName("StartScanning")
    data class StartScanning(
        override val id: Int
    ) : Message

    @JsonTypeName("StopScanning")
    data class StopScanning(
        override val id: Int
    ) : Message

    @JsonTypeName("ScanningFinished")
    data class ScanningFinished(
        override val id: Int
    ) : Message

    @JsonTypeName("RequestDeviceList")
    data class RequestDeviceList(
        override val id: Int
    ) : Message

    @JsonTypeName("DeviceList")
    data class DeviceList(
        override val id: Int,
        val devices: List<Device>
    ) : Message {
        data class Device(
            val deviceName: String,
            val deviceIndex: Int,
            val deviceMessages: DeviceMessages
        )
    }

    @JsonTypeName("DeviceAdded")
    data class DeviceAdded(
        override val id: Int,
        val deviceName: String,
        override val deviceIndex: Int,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val deviceMessageGap: Int? = null,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val deviceDisplayName: String? = null,
        val deviceMessages: DeviceMessages
    ) : Message, DeviceMessage

    @JsonTypeName("DeviceRemoved")
    data class DeviceRemoved(
        override val id: Int,
        override val deviceIndex: Int
    ) : Message, DeviceMessage

    @JsonTypeName("StopDeviceCmd")
    data class StopDeviceCmd(
        override val id: Int,
        override val deviceIndex: Int
    ) : Message, DeviceMessage

    @JsonTypeName("StopAllDevices")
    data class StopAllDevices(
        override val id: Int
    ) : Message

    @JsonTypeName("ScalarCmd")
    data class ScalarCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val scalars: List<Value>
    ) : Message, DeviceMessage {
        data class Value(
            val index: Int,
            val scalar: Double,
            val actuatorType: String
        )
    }

    @JsonTypeName("VibrateCmd")
    data class VibrateCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val speeds: List<Value>
    ) : Message, DeviceMessage {
        data class Value(
            val index: Int,
            val speed: Double
        )
    }

    @JsonTypeName("LinearCmd")
    data class LinearCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val vectors: List<Value>
    ) : Message, DeviceMessage {
        data class Value(
            val index: Int,
            val duration: Int, // in ms
            val position: Double
        )
    }

    @JsonTypeName("RotateCmd")
    data class RotateCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val rotations: List<Value>
    ) : Message, DeviceMessage {
        data class Value(
            val index: Int,
            val speed: Double,
            val clockwise: Boolean
        )
    }

    @JsonTypeName("SensorReadCmd")
    data class SensorReadCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val sensorIndex: Int,
        val sensorType: String
    ) : Message, DeviceMessage

    @JsonTypeName("SensorReading")
    data class SensorReading(
        override val id: Int,
        override val deviceIndex: Int,
        val sensorIndex: Int,
        val sensorType: String,
        val data: List<Int>
    ) : Message, DeviceMessage

    @JsonTypeName("SensorSubscribeCmd")
    data class SensorSubscribeCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val sensorIndex: Int,
        val sensorType: String
    ) : Message, DeviceMessage

    @JsonTypeName("SensorUnsubscribeCmd")
    data class SensorUnsubscribeCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val sensorIndex: Int,
        val sensorType: String
    ) : Message, DeviceMessage

    @JsonTypeName("RawWriteCmd")
    data class RawWriteCmd(
        override val id: Int,
        override val deviceIndex: Int,
        val endpoint: String,
        val data: List<Int>,
        val writeWithResponse: Boolean
    ) : Message, DeviceMessage
}
