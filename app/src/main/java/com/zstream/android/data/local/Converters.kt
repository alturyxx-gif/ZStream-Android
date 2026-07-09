package com.zstream.android.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zstream.android.data.local.entity.DownloadStatus

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String>? {
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)
}
