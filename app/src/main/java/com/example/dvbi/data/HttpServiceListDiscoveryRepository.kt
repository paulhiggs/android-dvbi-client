package com.example.dvbi.data

import com.example.dvbi.domain.DvbServiceListOffer
import com.example.dvbi.domain.RegistryQuery
import com.example.dvbi.domain.ServiceListDiscoveryRepository
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class HttpServiceListDiscoveryRepository(
    private val httpClient: HttpXmlClient = HttpXmlClient(),
    private val parser: DvbRegistryResponseParser = DvbRegistryResponseParser {
        android.util.Xml.newPullParser()
    },
) : ServiceListDiscoveryRepository {
    override suspend fun discover(
        registryUri: String,
        query: RegistryQuery,
    ): List<DvbServiceListOffer> {
        val requestUri = ServiceListRegistryQueryBuilder.append(registryUri, query)
        return try {
            parser.parse(httpClient.get(requestUri))
        } catch (error: XmlPullParserException) {
            throw IOException(
                "The registry response could not be parsed: ${error.message ?: "invalid XML"}.",
                error,
            )
        }
    }
}

object ServiceListRegistryQueryBuilder {
    fun append(baseUri: String, query: RegistryQuery): String {
        val parameters = buildList {
            query.country?.trim()?.takeIf(String::isNotEmpty)?.let {
                add("TargetCountry" to it)
            }
            query.language?.trim()?.takeIf(String::isNotEmpty)?.let {
                add("Language" to it)
            }
            query.delivery?.trim()?.takeIf(String::isNotEmpty)?.let {
                add("Delivery" to it)
            }
            if (query.regulatorListsOnly) add("regulatorListFlag" to "true")
        }
        return HttpQueryBuilder.append(baseUri, parameters)
    }
}

object HttpQueryBuilder {
    fun append(baseUri: String, parameters: List<Pair<String, String>>): String {
        if (parameters.isEmpty()) return baseUri
        val encodedParameters = parameters.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fragmentStart = baseUri.indexOf('#')
        val endpoint = if (fragmentStart < 0) baseUri else baseUri.substring(0, fragmentStart)
        val fragment = if (fragmentStart < 0) "" else baseUri.substring(fragmentStart)
        val separator = when {
            '?' !in endpoint -> "?"
            endpoint.endsWith('?') || endpoint.endsWith('&') -> ""
            else -> "&"
        }
        return endpoint + separator + encodedParameters + fragment
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
