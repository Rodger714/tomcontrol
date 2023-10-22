package com.github.salaink.tomcontrol.bpio
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.TokenBuffer

@JsonDeserialize(using = DeviceMessages.V3Deserializer::class)
sealed interface DeviceMessages {
    data class V1(
        @JsonValue
        val types: Map<String, Attributes?>
    ) : DeviceMessages {
        data class Attributes(
            @JsonInclude(JsonInclude.Include.NON_DEFAULT)
            val featureCount: Int
        )
    }

    data class V2(
        @JsonValue
        val types: Map<String, Attributes?>
        ) : DeviceMessages {
        data class Attributes(
            @JsonInclude(JsonInclude.Include.NON_DEFAULT)
            val featureCount: Int,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            val stepCount: List<Int> = emptyList()
        )
    }

    @JsonSerialize(using = V3Serializer::class)
    @JsonDeserialize(using = V3Deserializer::class)
    data class V3(
        val types: Map<String, List<Attributes>?>
        ) : DeviceMessages {
        data class Attributes(
            val featureDescriptor: String,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            val stepCount: Int? = null,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            val actuatorType: String? = null,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            val sensorType: String? = null,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            val sensorRange: List<List<Int>>? = null,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            val endpoints: List<String>? = null,
        )
    }

    class V3Serializer : JsonSerializer<V3>() {
        override fun serialize(value: V3, gen: JsonGenerator, serializers: SerializerProvider?) {
            gen.writeStartObject()
            for ((k, v) in value.types.entries) {
                gen.writeFieldName(k)
                if (v == null) {
                    gen.writeStartObject()
                    gen.writeEndObject()
                } else if (k == "RawWriteCmd" && v.size == 1) {
                    gen.writeObject(v.single()) // bug in buttplug.js
                } else {
                    gen.writeObject(v)
                }
            }
            gen.writeEndObject()
        }
    }

    class V3Deserializer : JsonDeserializer<V3>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): V3 {
            if (!p.isExpectedStartObjectToken) {
                return ctxt.handleUnexpectedToken(V3::class.java, p) as V3
            }
            val types = mutableMapOf<String, List<V3.Attributes>?>()
            while (true) {
                val name = p.nextFieldName() ?: break
                p.nextToken()
                val attributes: List<V3.Attributes>? = if (p.isExpectedStartArrayToken) {
                    val items = mutableListOf<V3.Attributes>()
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        items.add(p.readValueAs(V3.Attributes::class.java))
                    }
                    items
                } else if (p.isExpectedStartObjectToken) {
                    val tokenBuffer = ctxt.bufferAsCopyOfValue(p)
                    val testParser = tokenBuffer.asParserOnFirstToken()
                    if (testParser.nextToken() == JsonToken.END_OBJECT) {
                        null
                    } else {
                        listOf(tokenBuffer.asParserOnFirstToken().readValueAs(V3.Attributes::class.java))
                    }
                } else {
                    null
                }
                types[name] = attributes
            }
            return V3(types)
        }
    }
}
