package com.github.salaink.tomcontrol.bpio

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@OptIn(DelicateCoroutinesApi::class)
class BpioProxyTest {
  @Test
  fun test() = runTest {
    val vertx = Vertx.vertx()
    try {
      lateinit var mockServerSession: BpioServerSession
      val mockServer = vertx.createHttpServer()
        .webSocketHandler {
          mockServerSession = BpioServerSession(vertx, it)
        }
        .listen().toCompletionStage().toCompletableFuture().get()
      val proxyClient = BpioClient(
        vertx, vertx.createHttpClient()
          .webSocket(mockServer.actualPort(), "localhost", "/")
          .toCompletionStage().toCompletableFuture().get()
      )
      val proxyServer = vertx.createHttpServer()
        .webSocketHandler {
          launch {
            proxy(proxyClient.devices).collect(BpioServerSession(vertx, it).devices)
          }
        }
        .listen().toCompletionStage().toCompletableFuture().get()
      val mockClient = BpioClient(
        vertx, vertx.createHttpClient()
          .webSocket(mockServer.actualPort(), "localhost", "/")
          .toCompletionStage().toCompletableFuture().get()
      )

      Assertions.assertEquals(0, mockClient.devices.value.size)

      val deviceMessages = DeviceMessages.V3(
        mapOf(
          "ScalarCmd" to listOf(
            DeviceMessages.V3.Attributes(
              featureDescriptor = "mock vibration",
              stepCount = 100
            )
          )
        )
      )
      val mockDevice = MockDevice("foo",  deviceMessages)
      mockServerSession.devices.value = mapOf(1 to mockDevice)

      TimeUnit.SECONDS.sleep(1)
      Assertions.assertEquals(1, mockClient.devices.value.size)
      val clientDevice = mockClient.devices.value[0]
      val scalars = listOf(Message.ScalarCmd.Value(0, 0.5, "foo"))
      var called = false
      mockDevice.handler = { msg, handle ->
        mockDevice.handler = null
        Assertions.assertEquals(scalars, (msg as Message.ScalarCmd).scalars)
        called = true
        handle.sendOk(msg.id)
      }
      clientDevice.scalar(scalars)
      Assertions.assertTrue(called)
    } finally {
      vertx.close()
    }
  }

  class MockDevice(
    override val deviceName: String,
    override val deviceMessages: DeviceMessages
  ) : BpioServerSession.ServerDevice {
    var handler: (suspend (Message.DeviceMessage, BpioServerSession.ServerDeviceHandle) -> Unit)? = null

    override suspend fun handleCommand(message: Message.DeviceMessage, handle: BpioServerSession.ServerDeviceHandle) {
      handler!!(message, handle)
    }
  }
}