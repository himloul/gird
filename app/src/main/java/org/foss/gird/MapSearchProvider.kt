package org.foss.gird

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object MapSearchProvider {
    private const val TAG = "MapSearchProvider"

    /**
     * Searches for a location using OSM Nominatim API.
     * Requires User-Agent header as per OSM policy.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1")
            
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Gird-Android-App") // Required by OSM
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val results = mutableListOf<SearchResult>()
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    results.add(SearchResult(
                        name = obj.getString("display_name"),
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon")
                    ))
                }
                return@withContext results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        } finally {
            connection?.disconnect()
        }
        
        emptyList()
    }
}
