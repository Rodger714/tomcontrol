package com.github.salaink.tomcontrol

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonIncludeProperties

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class Configuration(
  val listenPort: Int = 12350,
  val clientUri: String = "ws://127.0.0.1:12345"
)