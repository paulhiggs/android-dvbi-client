package com.example.dvbi.data

import com.example.dvbi.domain.DvbServiceListOffer
import com.example.dvbi.domain.DvbServiceListUri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.net.URI
import java.net.URISyntaxException

class DvbRegistryResponseParser(
    private val newPullParser: () -> XmlPullParser,
) {
    @Throws(XmlPullParserException::class, java.io.IOException::class)
    fun parse(xml: String): List<DvbServiceListOffer> {
        val parser = newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(java.io.StringReader(xml))
        }

        var rootSeen = false
        val offers = mutableListOf<DvbServiceListOffer>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "ServiceListEntryPoints") {
                if (parser.namespace != REGISTRY_NAMESPACE) {
                    throw XmlPullParserException("Unsupported service-list registry namespace.")
                }
                rootSeen = true
            } else if (parser.name == "ProviderOffering") {
                offers += readProviderOffering(parser)
            }
        }

        if (!rootSeen) {
            throw XmlPullParserException("The response is not a DVB-I ServiceListEntryPoints document.")
        }
        return offers
    }

    private fun readProviderOffering(parser: XmlPullParser): List<DvbServiceListOffer> {
        val depth = parser.depth
        var providerName = ""
        val offers = mutableListOf<DvbServiceListOffer>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "Provider" -> providerName = readOrganizationName(parser)
                "ServiceListOffering" -> offers += readOffer(parser, providerName)
            }
        }

        return offers
    }

    private fun readOrganizationName(parser: XmlPullParser): String {
        val depth = parser.depth
        var name = ""

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Name" && name.isBlank()) {
                name = parser.nextText().trim()
            }
        }

        return name
    }

    private fun readOffer(parser: XmlPullParser, providerName: String): DvbServiceListOffer {
        val regulatorList = parser.getAttributeValue(null, "regulatorListFlag") == "true"
        var name = ""
        var id = ""
        val uris = mutableListOf<DvbServiceListUri>()
        val countries = mutableListOf<String>()
        val languages = mutableListOf<String>()
        val deliveryTypes = mutableSetOf<String>()
        val depth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "ServiceListName" -> if (name.isBlank()) name = parser.nextText().trim()
                "ServiceListURI" -> readServiceListUri(parser)?.let(uris::add)
                "ServiceListId" -> id = parser.nextText().trim()
                "TargetCountry" -> countries += parser.nextText().trim()
                "Language" -> languages += parser.nextText().trim()
                "Delivery" -> deliveryTypes += readDeliveryTypes(parser)
            }
        }

        if (id.isBlank()) {
            throw XmlPullParserException("A registry offering is missing its service-list ID.")
        }

        return DvbServiceListOffer(
            providerName = providerName,
            name = name,
            id = id,
            serviceListUris = uris,
            targetCountries = countries,
            languages = languages,
            deliveryTypes = deliveryTypes,
            regulatorList = regulatorList,
        )
    }

    private fun readServiceListUri(parser: XmlPullParser): DvbServiceListUri? {
        val contentType = parser.getAttributeValue(null, "contentType").orEmpty()
        val standardVersion = parser.getAttributeValue(null, "standardVersion")
        val depth = parser.depth
        var uri: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "URI") {
                val value = parser.nextText().trim()
                if (uri == null && isHttpUri(value)) uri = value
            }
        }

        return uri?.let {
            DvbServiceListUri(
                uri = it,
                contentType = contentType,
                standardVersion = standardVersion,
            )
        }
    }

    private fun readDeliveryTypes(parser: XmlPullParser): Set<String> {
        val depth = parser.depth
        val types = mutableSetOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                types += parser.name
            }
        }
        return types
    }

    private fun isHttpUri(value: String): Boolean {
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    private companion object {
        const val REGISTRY_NAMESPACE = "urn:dvb:metadata:servicelistdiscovery:2026"
    }
}
