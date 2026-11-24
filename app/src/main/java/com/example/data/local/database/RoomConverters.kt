package com.example.data.local.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class RoomConverters {
  private val json = Json { ignoreUnknownKeys = true }
  private val stringListSerializer = ListSerializer(String.serializer())

  @TypeConverter
  fun fromStringList(value: List<String>?): String {
    return json.encodeToString(stringListSerializer, value ?: emptyList())
  }

  @TypeConverter
  fun toStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString(stringListSerializer, raw) }.getOrDefault(emptyList())
  }
}
