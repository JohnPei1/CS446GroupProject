package com.example.wardrobeapp.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room TypeConverter for storing a List<String> (e.g. clothing item tags) as JSON text. */
class Converters {
    @TypeConverter
    fun fromTagList(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    @TypeConverter
    fun toTagList(raw: String): List<String> =
        if (raw.isBlank()) emptyList()
        else Json.decodeFromString(ListSerializer(String.serializer()), raw)
}
