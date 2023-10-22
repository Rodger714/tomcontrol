package com.github.salaink.tomcontrol

import com.github.salaink.tomcontrol.bpio.*
import com.github.salaink.tomcontrol.dlab.DlabModule
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.net.URI

private val log = LoggerFactory.getLogger(TomControl::class.java)

class TomControl {
  private val vertx = Vertx.vertx(
    VertxOptions()
      .setUseDaemonThread(true)
      .setEventLoopPoolSize(1)
  )

  private val bpioServer: MutableStateFlow<HttpServer?> = MutableStateFlow(null)
  val serverListening = bpioServer.map { it != null }
  private val serverConnectedClientCountMutable = MutableStateFlow(0)
  val serverConnectedClientCount: StateFlow<Int>
    get() = serverConnectedClientCountMutable

  val bpioClient: MutableStateFlow<BpioClient?> = MutableStateFlow(null)
  val clientConnected = bpioClient.map { it != null }

  val configuration = MutableStateFlow(Configuration())

  val dlabModule = DlabModule()

  @OptIn(ExperimentalCoroutinesApi::class)
  val allDevices: Flow<List<ControllableDevice>> = combine(
    bpioClient.flatMapLatest { cl -> cl?.devices ?: emptyFlow() },
    dlabModule.devices
  ) { arr ->
    arr.flatMap { it }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun launch(block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(vertx.dispatcher(), block = block)
  }

  suspend fun startServer() {
    require(bpioServer.value == null)
    val server = vertx.createHttpServer(
      HttpServerOptions()
        .setHost("127.0.0.1")
        .setPort(configuration.value.listenPort)
    )
      .webSocketHandler {
        val session = BpioServerSession(vertx, it)
        launch {
          proxy(allDevices).collect(session.devices)
        }
        launch {
          session.stopAll.collectLatest {
            stopAll()
          }
        }
        serverConnectedClientCountMutable.update { i -> i + 1 }
        it.closeHandler {
          serverConnectedClientCountMutable.update { i -> i - 1 }
        }
      }
      .listen().await()
    if (!bpioServer.compareAndSet(null, server)) {
      server.close().await()
      throw IllegalStateException("Already started")
    }
  }

  suspend fun stopServer() {
    bpioServer.getAndUpdate { null }?.close()?.await()
  }

  suspend fun connectClient() {
    require(bpioClient.value == null)
    val clientUri = URI(configuration.value.clientUri)
    val client = BpioClient(
      vertx, vertx.createHttpClient()
        .webSocket(clientUri.port, clientUri.host, clientUri.toString())
        .await()
    )
    if (!bpioClient.compareAndSet(null, client)) {
      client.close()
      throw IllegalStateException("Already connected")
    }
  }

  fun disconnectClient() {
    bpioClient.getAndUpdate { null }?.close()
  }

  private suspend fun stopAll() {
    for (dlabDevice in dlabModule.devices.value) {
      try {
        dlabDevice.stop()
      } catch (e: Exception) {
        log.warn("Failed to stop dlab device", e)
      }
    }
    bpioClient.value?.stopAll()
  }
}