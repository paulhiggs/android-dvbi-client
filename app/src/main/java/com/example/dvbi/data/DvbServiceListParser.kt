package com.example.dvbi.data

import android.util.Xml
import com.example.dvbi.domain.DvbGuideSource
import com.example.dvbi.domain.DvbLogicalChannelNumber
import com.example.dvbi.domain.DvbService
import com.example.dvbi.domain.DvbServiceInstance
import com.example.dvbi.domain.DvbServiceList
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.net.URI
import java.net.URISyntaxException

class DvbServiceListParser(
    private val newPullParser: () -> XmlPullParser = { Xml.newPullParser() },
) {
    @Throws(XmlPullParserException::class, java.io.IOException::class)
    fun parse(xml: String): DvbServiceList {
        val parser = newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }

        var listId: String? = null
        var version: Int? = null
        var responseStatus: String? = null
        var listName: String? = null
        var providerName: String? = null
        val services = mutableListOf<DvbService>()
        val guideSources = mutableListOf<DvbGuideSource>()
        val logicalChannelNumbers = mutableListOf<DvbLogicalChannelNumber>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "ServiceList" -> {
                    if (parser.namespace != SERVICE_LIST_NAMESPACE) {
                        throw XmlPullParserException("Unsupported DVB-I service-list namespace.")
                    }
                    listId = parser.getAttributeValue(null, "id")
                    version = parser.getAttributeValue(null, "version")?.toIntOrNull()
                    responseStatus = parser.getAttributeValue(null, "responseStatus")
                }
                "Name" -> if (listName == null) listName = parser.nextText().trim()
                "ProviderName" -> if (providerName == null) providerName = parser.nextText().trim()
                "Service", "TestService" -> services += readService(parser)
                "ContentGuideSource" -> guideSources += readGuideSource(parser)
                "LCNTableList" -> logicalChannelNumbers += readLcnTableList(parser)
            }
        }

        if (listId.isNullOrBlank() || version == null) {
            throw XmlPullParserException("The document is not a valid DVB-I service list.")
        }
        if (responseStatus != null && responseStatus != "OK") {
            throw XmlPullParserException("The service list reported status $responseStatus.")
        }

        return DvbServiceList(
            id = listId,
            version = version,
            name = listName.orEmpty(),
            providerName = providerName.orEmpty(),
            services = services,
            guideSources = guideSources,
            logicalChannelNumbers = logicalChannelNumbers,
        )
    }

    private fun readLcnTableList(parser: XmlPullParser): List<DvbLogicalChannelNumber> {
        val depth = parser.depth
        val entries = mutableListOf<DvbLogicalChannelNumber>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "LCNTable") {
                entries += readLcnTable(parser)
            }
        }

        return entries
    }

    private fun readLcnTable(parser: XmlPullParser): List<DvbLogicalChannelNumber> {
        val version = parser.getAttributeValue(null, "version")?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: throw XmlPullParserException("An LCNTable is missing a valid version.")
        val depth = parser.depth
        val regions = mutableListOf<String>()
        val entries = mutableListOf<DvbLogicalChannelNumber>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "TargetRegion" -> regions += parser.nextText().trim()
                "LCN" -> {
                    val serviceId = parser.getAttributeValue(null, "serviceRef")
                        ?.takeIf(String::isNotBlank)
                        ?: throw XmlPullParserException("An LCN entry is missing its serviceRef.")
                    val channelNumber = parser.getAttributeValue(null, "channelNumber")?.toIntOrNull()
                        ?.takeIf { it > 0 }
                        ?: throw XmlPullParserException("An LCN entry has an invalid channelNumber.")
                    entries += DvbLogicalChannelNumber(
                        serviceId = serviceId,
                        channelNumber = channelNumber,
                        targetRegions = regions.toList(),
                        tableVersion = version,
                        visible = readBooleanAttribute(parser, "visible", default = true),
                        selectable = readBooleanAttribute(parser, "selectable", default = true),
                    )
                }
            }
        }

        return entries
    }

    private fun readBooleanAttribute(parser: XmlPullParser, name: String, default: Boolean): Boolean {
        return when (parser.getAttributeValue(null, name)) {
            null -> default
            "true", "1" -> true
            "false", "0" -> false
            else -> throw XmlPullParserException("An LCN entry has an invalid $name value.")
        }
    }

    private fun readService(parser: XmlPullParser): DvbService {
        var identifier: String? = null
        var name: String? = null
        var provider: String? = null
        var guideSourceRef: String? = null
        var contentGuideServiceRef: String? = null
        var inlineGuideSource: DvbGuideSource? = null
        val instances = mutableListOf<DvbServiceInstance>()
        val depth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "UniqueIdentifier" -> identifier = parser.nextText().trim()
                "ServiceName" -> if (name == null) name = parser.nextText().trim()
                "ProviderName" -> if (provider == null) provider = parser.nextText().trim()
                "ServiceInstance" -> instances += readServiceInstance(parser)
                "ContentGuideSourceRef" -> guideSourceRef = parser.nextText().trim()
                "ContentGuideServiceRef" -> contentGuideServiceRef = parser.nextText().trim()
                "ContentGuideSource" -> inlineGuideSource = readGuideSource(parser)
            }
        }

        val serviceId = identifier?.takeIf(String::isNotBlank)
            ?: throw XmlPullParserException("A DVB-I service is missing its UniqueIdentifier.")
        val serviceName = name?.takeIf(String::isNotBlank)
            ?: throw XmlPullParserException("A DVB-I service is missing its ServiceName.")

        return DvbService(
            id = serviceId,
            name = serviceName,
            providerName = provider.orEmpty(),
            instances = instances,
            guideSourceRef = guideSourceRef,
            contentGuideServiceRef = contentGuideServiceRef,
            inlineGuideSource = inlineGuideSource,
        )
    }

    private fun readServiceInstance(parser: XmlPullParser): DvbServiceInstance {
        val priority = parser.getAttributeValue(null, "priority")?.toIntOrNull()
        var streamUri: String? = null
        var contentType: String? = null
        var uriLocationContentType: String? = null
        val instanceDepth = parser.depth
        var uriLocationDepth: Int? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG) {
                if (parser.depth == uriLocationDepth) {
                    uriLocationDepth = null
                }
                if (parser.depth == instanceDepth) break
            }
            if (parser.eventType != XmlPullParser.START_TAG) continue

            if (parser.name == "UriBasedLocation") {
                uriLocationContentType = parser.getAttributeValue(null, "contentType")
                uriLocationDepth = parser.depth
            } else if (parser.name == "URI" && uriLocationDepth != null) {
                val value = parser.nextText().trim()
                if (streamUri == null && isHttpUri(value)) {
                    streamUri = value
                    contentType = uriLocationContentType
                }
            }
        }

        return DvbServiceInstance(
            uri = streamUri,
            contentType = contentType,
            priority = priority,
        )
    }

    private fun readGuideSource(parser: XmlPullParser): DvbGuideSource {
        val id = parser.getAttributeValue(null, "CGSID").orEmpty()
        var name: String? = null
        var scheduleUri: String? = null
        var programInfoUri: String? = null
        var groupInfoUri: String? = null
        var moreEpisodesUri: String? = null
        val guideDepth = parser.depth

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == guideDepth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "Name" -> if (name == null) name = parser.nextText().trim()
                "ScheduleInfoEndpoint" -> scheduleUri = readEndpointUri(parser)
                "ProgramInfoEndpoint" -> programInfoUri = readEndpointUri(parser)
                "GroupInfoEndpoint" -> groupInfoUri = readEndpointUri(parser)
                "MoreEpisodesEndpoint" -> moreEpisodesUri = readEndpointUri(parser)
            }
        }

        return DvbGuideSource(
            id = id,
            name = name.orEmpty(),
            scheduleUri = scheduleUri,
            programInfoUri = programInfoUri,
            groupInfoUri = groupInfoUri,
            moreEpisodesUri = moreEpisodesUri,
        )
    }

    private fun readEndpointUri(parser: XmlPullParser): String? {
        val endpointDepth = parser.depth
        var endpointUri: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == endpointDepth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "URI") {
                val value = parser.nextText().trim()
                if (endpointUri == null && isHttpUri(value)) endpointUri = value
            }
        }

        return endpointUri
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
        const val SERVICE_LIST_NAMESPACE = "urn:dvb:metadata:servicediscovery:2026"
    }
}
