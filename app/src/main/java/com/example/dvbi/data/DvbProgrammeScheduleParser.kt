package com.example.dvbi.data

import com.example.dvbi.domain.DvbProgramme
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

class DvbProgrammeScheduleParser(
    private val newPullParser: () -> XmlPullParser,
) {
    @Throws(XmlPullParserException::class, java.io.IOException::class)
    fun parse(xml: String): List<DvbProgramme> {
        val parser = newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        val programmes = mutableMapOf<String, ProgrammeDetails>()
        val events = mutableListOf<ScheduleEvent>()
        var rootSeen = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "TVAMain" -> {
                    if (!parser.namespace.startsWith(TVA_NAMESPACE_PREFIX)) {
                        throw XmlPullParserException("Unsupported DVB-I programme metadata namespace.")
                    }
                    rootSeen = true
                }
                "ProgramInformation" -> readProgramInformation(parser).let {
                    programmes[it.id] = it
                }
                "ScheduleEvent" -> events += readScheduleEvent(parser)
            }
        }

        if (!rootSeen) {
            throw XmlPullParserException("The response is not a TVAMain programme metadata document.")
        }

        return events.map { event ->
            val details = programmes[event.programId]
            DvbProgramme(
                id = event.programId,
                title = details?.title?.takeIf(String::isNotBlank) ?: event.programId,
                synopsis = details?.synopsis,
                start = event.start,
                end = event.start.plus(event.duration),
                genreUri = details?.genreUri,
                minimumAge = details?.minimumAge,
                imageUri = details?.imageUri,
            )
        }.sortedBy(DvbProgramme::start)
    }

    private fun readProgramInformation(parser: XmlPullParser): ProgrammeDetails {
        val id = parser.getAttributeValue(null, "programId")?.takeIf(String::isNotBlank)
            ?: throw XmlPullParserException("A ProgramInformation element is missing its programId.")
        val depth = parser.depth
        var title: String? = null
        var synopsis: String? = null
        var genreUri: String? = null
        var minimumAge: Int? = null
        var imageUri: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "Title" -> if (title == null) title = parser.nextText().trim()
                "Synopsis" -> if (synopsis == null) synopsis = parser.nextText().trim()
                "Genre" -> if (genreUri == null) genreUri = parser.getAttributeValue(null, "href")
                "MinimumAge" -> if (minimumAge == null) {
                    minimumAge = parser.nextText().trim().toIntOrNull()
                }
                "MediaUri" -> {
                    val contentType = parser.getAttributeValue(null, "contentType").orEmpty()
                    val value = parser.nextText().trim()
                    if (imageUri == null && contentType.startsWith("image/") && isHttpUri(value)) {
                        imageUri = value
                    }
                }
            }
        }

        return ProgrammeDetails(id, title.orEmpty(), synopsis, genreUri, minimumAge, imageUri)
    }

    private fun readScheduleEvent(parser: XmlPullParser): ScheduleEvent {
        val depth = parser.depth
        var programId: String? = null
        var start: Instant? = null
        var duration: Duration? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "Program" -> programId = parser.getAttributeValue(null, "crid")
                "PublishedStartTime" -> start = parseInstant(parser.nextText().trim())
                "PublishedDuration" -> duration = parseDuration(parser.nextText().trim())
            }
        }

        val id = programId?.takeIf(String::isNotBlank)
            ?: throw XmlPullParserException("A ScheduleEvent is missing its Program CRID.")
        val startTime = start
            ?: throw XmlPullParserException("A ScheduleEvent is missing PublishedStartTime.")
        val runningTime = duration
            ?: throw XmlPullParserException("A ScheduleEvent is missing PublishedDuration.")
        if (runningTime.isNegative || runningTime.isZero) {
            throw XmlPullParserException("A ScheduleEvent has an invalid PublishedDuration.")
        }

        return ScheduleEvent(id, startTime, runningTime)
    }

    private fun parseInstant(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw XmlPullParserException("Invalid PublishedStartTime: $value")
        }

    private fun parseDuration(value: String): Duration =
        try {
            Duration.parse(value)
        } catch (_: DateTimeParseException) {
            throw XmlPullParserException("Invalid PublishedDuration: $value")
        }

    private fun isHttpUri(value: String): Boolean {
        val uri = try {
            java.net.URI(value)
        } catch (_: java.net.URISyntaxException) {
            return false
        }
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    private data class ProgrammeDetails(
        val id: String,
        val title: String,
        val synopsis: String?,
        val genreUri: String?,
        val minimumAge: Int?,
        val imageUri: String?,
    )

    private data class ScheduleEvent(
        val programId: String,
        val start: Instant,
        val duration: Duration,
    )

    private companion object {
        const val TVA_NAMESPACE_PREFIX = "urn:tva:metadata:"
    }
}
