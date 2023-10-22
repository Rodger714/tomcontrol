package com.github.salaink.tomcontrol.bpio

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import io.vertx.core.Vertx
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.http.WebSocketBase
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.Closeable


internal val bpioMapper = JsonMapper.builder()
  .findAndAddModules()
  .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
  .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  .build()

private val messageReader = bpioMapper.readerForListOf(Message::class.java)

abstract class AbstractBpioSession(private val vertx: Vertx, private val webSocket: WebSocketBase) : Closeable {
  @OptIn(DelicateCoroutinesApi::class)
  private val scope: CoroutineScope = GlobalScope

  init {
    webSocket.textMessageHandler {
      val messages = messageReader.readValue<List<Message>>(it)
      launch {
        for (message in messages) {
          handle(message)
        }
      }
    }
  }

  protected fun launch(block: suspend CoroutineScope.() -> Unit) {
    scope.launch(vertx.dispatcher(), block = block)
  }

  protected abstract suspend fun handle(msg: Message)

  protected suspend fun send(vararg msg: Message) {
    webSocket.writeTextMessage(bpioMapper.writeValueAsString(msg)).await()
  }

  override fun close() {
    webSocket.close()
  }
}