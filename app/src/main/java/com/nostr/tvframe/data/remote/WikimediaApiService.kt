package com.nostr.tvframe.data.remote

import android.text.Html
import com.nostr.tvframe.data.local.PhotoEntity
import com.nostr.tvframe.domain.model.PhotoSourceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Retrofit interface for the Wikimedia Commons API (`https://commons.wikimedia.org/w/api.php`).
 * Queries imageinfo with `extmetadata` to extract high-resolution image URLs,
 * Photographer/Artist name, Location, and the Commons page URL (encoded into the QR code).
 */
interface WikimediaApiService {

    @GET("w/api.php")
    suspend fun searchCommonsImages(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("generator") generator: String = "search",
        @Query("gsrsearch") searchQuery: String,
        @Query("gsrnamespace") namespace: Int = 6, // File: namespace
        @Query("gsrlimit") limit: Int = 35,
        @Query("prop") prop: String = "imageinfo|categories",
        @Query("cllimit") categoryLimit: Int = 15,
        @Query("iiprop") iiProp: String = "url|size|extmetadata",
        @Query("iiurlwidth") thumbnailWidth: Int = 3840
    ): WikimediaQueryResponse
}

@Serializable
data class WikimediaQueryResponse(
    val query: WikimediaPagesContainer? = null
)

@Serializable
data class WikimediaPagesContainer(
    val pages: Map<String, WikimediaPageDto> = emptyMap()
)

@Serializable
data class WikimediaPageDto(
    val pageid: Long = 0L,
    val title: String = "",
    val categories: List<WikimediaCategoryTag>? = null,
    val imageinfo: List<WikimediaImageInfoDto>? = null
)

@Serializable
data class WikimediaCategoryTag(
    val title: String = ""
)

@Serializable
data class WikimediaImageInfoDto(
    val url: String = "",
    val descriptionurl: String = "",
    val thumburl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0L,
    val extmetadata: WikimediaExtMetadataDto? = null
)

@Serializable
data class WikimediaExtMetadataDto(
    @SerialName("Artist") val artist: ExtMetaValue? = null,
    @SerialName("Credit") val credit: ExtMetaValue? = null,
    @SerialName("ObjectName") val objectName: ExtMetaValue? = null,
    @SerialName("LocationShown") val locationShown: ExtMetaValue? = null,
    @SerialName("City") val city: ExtMetaValue? = null,
    @SerialName("Country") val country: ExtMetaValue? = null,
    @SerialName("ImageDescription") val imageDescription: ExtMetaValue? = null,
    @SerialName("GPSLatitude") val gpsLat: ExtMetaValue? = null,
    @SerialName("GPSLongitude") val gpsLon: ExtMetaValue? = null
)

@Serializable
data class ExtMetaValue(
    val value: String? = null
)

object WikimediaClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "TVFrame/1.0 (Android TV; https://primal.net/s; s@primal.net)"
                    )
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    val apiService: WikimediaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://commons.wikimedia.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WikimediaApiService::class.java)
    }

    /**
     * Converts a Wikimedia API page into a [PhotoEntity], extracting ONLY the Photographer Name
     * and the Location for the TV overlay, while storing `descriptionurl` for the QR Code.
     */
    fun mapDtoToEntity(
        page: WikimediaPageDto,
        categoryKey: String
    ): PhotoEntity? {
        val info = page.imageinfo?.firstOrNull() ?: return null
        val bestImageUrl = info.thumburl?.takeIf { it.isNotBlank() } ?: info.url
        if (bestImageUrl.isBlank()) return null

        // Skip SVGs, PDFs, or audio/video files
        val lowerUrl = bestImageUrl.lowercase()
        if (lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".pdf") || lowerUrl.endsWith(".ogv") || lowerUrl.endsWith(".webm")) {
            return null
        }

        val photographer = extractCleanPhotographer(info.extmetadata)
        val location = extractCleanLocation(page, info.extmetadata)

        return PhotoEntity(
            id = "wikimedia_${page.pageid}",
            sourceType = PhotoSourceType.WIKIMEDIA.name,
            imageUrlOrUri = bestImageUrl,
            localCachedFilePath = null,
            photographerName = photographer,
            locationName = location,
            commonsPageUrl = info.descriptionurl.ifBlank {
                "https://commons.wikimedia.org/wiki/${page.title.replace(" ", "_")}"
            },
            categoryKey = categoryKey,
            width = info.width,
            height = info.height,
            isFavorite = false,
            fileSizeBytes = info.size
        )
    }

    private fun extractCleanPhotographer(meta: WikimediaExtMetadataDto?): String {
        val rawArtist = meta?.artist?.value?.takeIf { it.isNotBlank() }
            ?: meta?.credit?.value?.takeIf { it.isNotBlank() }
            ?: return "Wikimedia Commons Contributor"

        val plainText = stripHtml(rawArtist)
            .replace(Regex("(?i)^own work\\s*(by)?\\s*"), "")
            .replace(Regex("\\(talk\\)|\\(contributions\\)"), "")
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: "Wikimedia Commons Contributor"

        return plainText.take(65).ifBlank { "Wikimedia Commons Contributor" }
    }

    /**
     * Extracts a concise geographical location from structured Commons fields,
     * categories (e.g., "Category:Mountains of Switzerland"), or title/description tokens.
     */
    private fun extractCleanLocation(
        page: WikimediaPageDto,
        meta: WikimediaExtMetadataDto?
    ): String {
        // 1. Check explicit LocationShown / City + Country fields
        val explicitLocation = listOfNotNull(
            meta?.locationShown?.value?.let { stripHtml(it) }?.takeIf { it.isNotBlank() },
            meta?.city?.value?.let { stripHtml(it) }?.takeIf { it.isNotBlank() },
            meta?.country?.value?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
        ).distinct().joinToString(", ")

        if (explicitLocation.isNotBlank()) {
            return explicitLocation.take(70)
        }

        // 2. Check categories for geographical prepositions ("in <Place>" or "of <Place>")
        val geoCategory = page.categories
            ?.map { it.title.removePrefix("Category:").trim() }
            ?.firstOrNull { cat ->
                !cat.contains("Featured", ignoreCase = true) &&
                    !cat.contains("Quality", ignoreCase = true) &&
                    !cat.contains("Pictures of the day", ignoreCase = true) &&
                    !cat.contains("CC-BY", ignoreCase = true) &&
                    (cat.contains(" in ") || cat.contains(" of "))
            }

        if (!geoCategory.isNullOrBlank()) {
            val extractedPlace = geoCategory
                .substringAfter(" in ", geoCategory.substringAfter(" of ", geoCategory))
                .trim()
            if (extractedPlace.isNotBlank() && extractedPlace.length <= 65) {
                return extractedPlace
            }
        }

        // 3. Fallback to cleaned ObjectName or File Title (without File: prefix or extension)
        val rawObject = meta?.objectName?.value?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
            ?: page.title.removePrefix("File:").substringBeforeLast(".")

        return rawObject
            .replace("_", " ")
            .replace(Regex("\\s*-\\s*\\d{4}.*$"), "")
            .trim()
            .take(70)
    }

    private fun stripHtml(raw: String): String {
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
