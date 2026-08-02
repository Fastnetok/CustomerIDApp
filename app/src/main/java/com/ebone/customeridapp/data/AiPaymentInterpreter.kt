package com.ebone.customeridapp.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Step 3 (final automatic fallback) of the Payment Verification Engine:
 *   SMS Reader -> fails -> OCR -> fails -> AI text interpretation -> fails -> Manual T-ID entry (always available).
 *
 * NOTE: Store the API key in local.properties / a secure remote config,
 * never hard-code it in source control.
 */
object AiPaymentInterpreter {

    private val client = OkHttpClient()
    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

    fun interpret(
        rawText: String,
        apiKey: String,
        onResult: (SmsPaymentParser.ParsedResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val prompt = """
            Extract the payment amount and transaction/reference ID from this text.
            Respond ONLY as JSON: {"amount": <number or null>, "transactionId": <string or null>}
            Text: $rawText
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", listOf(mapOf("role" to "user", "content" to prompt)))
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = onError(e)

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val json = JSONObject(response.body?.string() ?: "")
                    val content = json.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                    val parsed = JSONObject(content)
                    val amount = parsed.optDouble("amount", Double.NaN).takeIf { !it.isNaN() }
                    val tid = parsed.optString("transactionId", null)
                    onResult(SmsPaymentParser.ParsedResult(amount, tid, rawText))
                } catch (e: Exception) {
                    onError(e)
                }
            }
        })
    }
}
