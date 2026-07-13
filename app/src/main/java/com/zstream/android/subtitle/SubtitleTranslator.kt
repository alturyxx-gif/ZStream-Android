package com.zstream.android.subtitle

import com.zstream.android.ui.screens.SubtitleCue
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ports p-stream's client-side subtitle translation (src/utils/translation/googletranslate.ts):
 * hits Google Translate's free, unofficial endpoints directly -- the same ones translate.google.com
 * itself uses -- so there's no API key, billing, or backend proxy involved. The batch endpoint's
 * key is lifted straight from Google's own web client; it's what p-stream ships in production.
 */
private object GoogleTranslateEndpoints {
    const val SINGLE_URL = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&dj=1&ie=UTF-8&oe=UTF-8&sl=auto"
    const val BATCH_URL = "https://translate-pa.googleapis.com/v1/translateHtml"
    const val BATCH_API_KEY = "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520"
}

class SubtitleTranslationException(message: String) : Exception(message)

class GoogleTranslateClient(private val client: OkHttpClient) {

    suspend fun translate(text: String, targetLang: String): String {
        if (text.isBlank()) return text
        val escaped = text.replace("\n", "<br />")
        val url = "${GoogleTranslateEndpoints.SINGLE_URL}&tl=$targetLang&q=${java.net.URLEncoder.encode(escaped, "UTF-8")}"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw SubtitleTranslationException("Translate request failed: HTTP ${resp.code}")
            resp.body?.string() ?: throw SubtitleTranslationException("Empty translate response")
        }
        val sentences = JSONObject(body).optJSONArray("sentences")
            ?: throw SubtitleTranslationException("Invalid translate response")
        val sb = StringBuilder()
        for (i in 0 until sentences.length()) {
            sb.append(sentences.getJSONObject(i).optString("trans"))
        }
        return sb.toString().replace("<br />", "\n")
    }

    /** Batches up to ~80 lines per call -- much faster than one request per subtitle cue. */
    suspend fun translateMulti(batch: List<String>, targetLang: String): List<String> {
        if (batch.isEmpty()) return emptyList()
        val escaped = batch.map { it.replace("\n", "<br />") }
        val payload = JSONArray().apply {
            put(JSONArray().apply {
                put(JSONArray(escaped))
                put("auto")
                put(targetLang)
            })
            put("te")
        }
        val request = Request.Builder()
            .url(GoogleTranslateEndpoints.BATCH_URL)
            .header("Content-Type", "application/json+protobuf")
            .header("X-goog-api-key", GoogleTranslateEndpoints.BATCH_API_KEY)
            .post(payload.toString().toRequestBody("application/json+protobuf".toMediaType()))
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw SubtitleTranslationException("Batch translate failed: HTTP ${resp.code}")
            resp.body?.string() ?: throw SubtitleTranslationException("Empty batch translate response")
        }
        val root = JSONArray(body)
        if (root.length() < 1) throw SubtitleTranslationException("Invalid batch translate response")
        val translated = root.getJSONArray(0)
        return (0 until translated.length()).map { translated.getString(it).replace("<br />", "\n") }
    }
}

/**
 * Translates the text of each cue via Google Translate while leaving timing untouched, mirroring
 * p-stream's Translator class: identical repeated lines (e.g. "...") are only sent once, batches
 * of BATCH_SIZE lines go out with a short delay between them, and each batch retries a few times
 * before the whole translation is considered failed.
 */
class SubtitleTranslator(client: OkHttpClient) {
    private val translateClient = GoogleTranslateClient(client)

    companion object {
        private const val BATCH_SIZE = 80
        private const val BATCH_DELAY_MS = 200L
        private const val MAX_RETRIES = 3
    }

    suspend fun translateCues(cues: List<SubtitleCue>, targetLang: String): List<SubtitleCue> {
        val cache = HashMap<String, String>()
        val distinctTexts = cues.map { it.text }.distinct().filter { it.isNotBlank() }

        var index = 0
        while (index < distinctTexts.size) {
            val batch = distinctTexts.subList(index, minOf(index + BATCH_SIZE, distinctTexts.size))
            var attempt = 0
            var result: List<String>? = null
            var lastError: Exception? = null
            while (result == null && attempt < MAX_RETRIES) {
                try {
                    result = translateClient.translateMulti(batch, targetLang)
                    if (result.size != batch.size) {
                        result = null
                        throw SubtitleTranslationException("Batch size mismatch")
                    }
                } catch (e: Exception) {
                    lastError = e
                    result = null
                    attempt++
                    if (attempt < MAX_RETRIES) delay(500)
                }
            }
            if (result == null) throw lastError ?: SubtitleTranslationException("Translation failed")
            batch.forEachIndexed { i, text -> cache[text] = result[i] }
            index += BATCH_SIZE
            if (index < distinctTexts.size) delay(BATCH_DELAY_MS)
        }

        return cues.map { cue -> cue.copy(text = cache[cue.text] ?: cue.text) }
    }
}
