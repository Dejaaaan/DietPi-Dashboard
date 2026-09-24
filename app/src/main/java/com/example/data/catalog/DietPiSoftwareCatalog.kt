package com.example.data.catalog

import android.content.Context
import com.example.data.model.SoftwareItem
import org.json.JSONArray

object DietPiSoftwareCatalog {

    @Volatile
    private var cachedList: List<SoftwareItem>? = null

    fun getSoftwareList(context: Context): List<SoftwareItem> {
        cachedList?.let { return it }
        synchronized(this) {
            cachedList?.let { return it }
            val loaded = loadFromAssets(context)
            cachedList = loaded
            return loaded
        }
    }

    private fun loadFromAssets(context: Context): List<SoftwareItem> {
        return try {
            val jsonString = context.assets.open("dietpi_software.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            val list = mutableListOf<SoftwareItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawUrl = obj.optString("imageUrl", "").trim()
                val normalizedImageUrl = when {
                    rawUrl.isEmpty() -> ""
                    rawUrl.contains("docs/assets/images/") -> {
                        val fileName = rawUrl.substringAfterLast("/")
                        "https://dietpi.com/docs/assets/images/$fileName"
                    }
                    rawUrl.startsWith("dietpi-software-") -> {
                        "https://dietpi.com/docs/assets/images/$rawUrl"
                    }
                    else -> rawUrl
                }
                list.add(
                    SoftwareItem(
                        id = obj.optInt("id"),
                        name = obj.optString("name"),
                        desc = obj.optString("desc"),
                        category = obj.optString("category", "General Software").ifEmpty { "General Software" },
                        homepage = obj.optString("homepage", ""),
                        imageUrl = normalizedImageUrl,
                        docs = obj.optString("docs", ""),
                        deps = obj.optString("deps", ""),
                        isInstalled = false
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
