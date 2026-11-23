package com.lightnotes.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AiService {

    suspend fun executeAiTask(
        context: Context,
        systemInstruction: String,
        userPrompt: String,
        sourceText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = AiPreferences.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("API key is not configured. Please set your API key in Settings.")
            )
        }

        val endpoint = AiPreferences.getEndpoint(context)
        val model = AiPreferences.getModel(context)

        try {
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
                doInput = true
            }

            val fullUserContent = if (sourceText.isNotBlank()) {
                "$userPrompt\n\nContent:\n$sourceText"
            } else {
                userPrompt
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", fullUserContent)
                    })
                }
                put("messages", messagesArray)
                put("temperature", 0.5)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use {
                    it.readText()
                }
                val responseJson = JSONObject(responseText)
                val choices = responseJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val result = message?.optString("content", "")?.trim() ?: ""
                    Result.success(result)
                } else {
                    Result.failure(Exception("AI returned an empty response."))
                }
            } else {
                val errorStream = connection.errorStream
                val errorText = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream, "UTF-8")).use { it.readText() }
                } else {
                    "HTTP $responseCode ${connection.responseMessage}"
                }
                Result.failure(Exception("AI request failed (HTTP $responseCode): $errorText"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
