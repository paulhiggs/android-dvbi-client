package com.example.dvbi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpXmlClient {
    suspend fun get(uri: String): String = withContext(Dispatchers.IO) {
        val url = URL(uri)
        if (url.protocol !in setOf("http", "https")) {
            throw IOException("DVB-I endpoints must use HTTP or HTTPS.")
        }

        val connection = url.openConnection() as? HttpURLConnection
            ?: throw IOException("The DVB-I endpoint must use HTTP or HTTPS.")
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "Accept",
                "application/xml, text/xml, application/*+xml, */*;q=0.1",
            )

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IOException("DVB-I server returned HTTP $statusCode.")
            }

            connection.inputStream.use(::readLimited)
                .toString(Charsets.UTF_8.name())
                .removePrefix("\uFEFF")
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            if (totalBytes > MAX_RESPONSE_BYTES) {
                throw IOException("The DVB-I XML response exceeds 5 MB.")
            }
            output.write(buffer, 0, count)
        }

        return output.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024
    }
}
