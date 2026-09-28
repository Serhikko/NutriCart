package com.nutricart.app.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A number that may arrive as `12.5`, `"12.5"`, `"12,5"`, `""`, `"<0.5"`, or
 * `null`. Open Food Facts nutriments are typed by whoever filled them in; the
 * default Double decoder throws on the first malformed value, which used to
 * drop a WHOLE page of search results (and the offline cache with it) because
 * one unrelated product had `"sugars_100g": ""`.
 *
 * Anything that is not a number becomes null, which the mapper reads as
 * "the source doesn't state this" — exactly what it means.
 */
object LenientDoubleSerializer : KSerializer<Double?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val json = decoder as? JsonDecoder
            ?: return runCatching { decoder.decodeDouble() }.getOrNull()
        val element = json.decodeJsonElement()
        if (element is JsonNull || element !is JsonPrimitive) return null
        return parse(element.content)
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }

    /** "12,5" -> 12.5; "<0.5" -> null; NaN/Infinity -> null. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
