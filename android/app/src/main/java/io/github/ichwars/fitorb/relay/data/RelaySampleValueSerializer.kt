package io.github.ichwars.fitorb.relay.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object RelaySampleValueSerializer : KSerializer<RelaySampleValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RelaySampleValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RelaySampleValue) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.jsonElement)
    }

    override fun deserialize(decoder: Decoder): RelaySampleValue {
        require(decoder is JsonDecoder)
        val primitive = decoder.decodeJsonElement().jsonPrimitive
        primitive.booleanOrNull?.let { return RelaySampleValue.BoolValue(it) }
        primitive.intOrNull?.let { return RelaySampleValue.IntValue(it) }
        primitive.doubleOrNull?.let { return RelaySampleValue.DoubleValue(it) }
        return RelaySampleValue.StringValue(primitive.content)
    }
}
