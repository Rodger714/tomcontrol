package com.github.salaink.tomcontrol.bpio

import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DeviceMessagesTest {
  @Test
  fun `deserialization test`() {
    Assertions.assertEquals(
      DeviceMessages.V3(mapOf("foo" to listOf(DeviceMessages.V3.Attributes("bar")))),
      bpioMapper.readValue<DeviceMessages>("""{"foo":[{"FeatureDescriptor":"bar"}]}""")
    )
    Assertions.assertEquals(
      DeviceMessages.V3(mapOf("foo" to listOf(DeviceMessages.V3.Attributes("bar")))),
      bpioMapper.readValue<DeviceMessages>("""{"foo":{"FeatureDescriptor":"bar"}}""")
    )
    Assertions.assertEquals(
      DeviceMessages.V3(mapOf("foo" to null)),
      bpioMapper.readValue<DeviceMessages>("""{"foo":{}}""")
    )
    Assertions.assertEquals(
      DeviceMessages.V3(mapOf("foo" to emptyList())),
      bpioMapper.readValue<DeviceMessages>("""{"foo":[]}""")
    )
  }
}