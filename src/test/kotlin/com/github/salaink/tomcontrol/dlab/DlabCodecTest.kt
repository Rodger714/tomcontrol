package com.github.salaink.tomcontrol.dlab

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DlabCodecTest {
  @Test
  fun `encodePower test data`() {
    for ((power, expected) in listOf(
      (1 to 2) to byteArrayOf(0x02, 0x08, 0x00),
      (100 to 200) to byteArrayOf(0xc8.toByte(), 0x20, 0x03),
      (2047 to 2047) to byteArrayOf(0xff.toByte(), 0xff.toByte(), 0x3f),
    )) {
      val (powerA, powerB) = power
      Assertions.assertArrayEquals(
        expected,
        DlabCodec.encodePower(powerA, powerB)
      )
    }
  }

  @Test
  fun `encodePattern test data`() {
    for ((pattern, expected) in listOf(
      DlabCodec.Pattern(1, 2, 3) to byteArrayOf(0x41, 0x80.toByte(), 0x01),
      DlabCodec.Pattern(10, 20, 30) to byteArrayOf(0x8a.toByte(), 0x02, 0x0f),
      DlabCodec.Pattern(31, 1023, 31) to byteArrayOf(0xff.toByte(), 0xff.toByte(), 0x0f),
    )) {
      Assertions.assertArrayEquals(
        expected,
        DlabCodec.encodePattern(pattern)
      )
    }
  }
}