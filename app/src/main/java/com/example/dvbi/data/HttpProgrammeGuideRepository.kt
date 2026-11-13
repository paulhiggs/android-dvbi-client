package com.example.dvbi.data

import com.example.dvbi.domain.DvbProgramme
import com.example.dvbi.domain.ProgrammeGuideRepository
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.time.Instant

class HttpProgrammeGuideRepository(
    private val httpClient: HttpXmlClient = HttpXmlClient(),
    private val parser: DvbProgrammeScheduleParser = DvbProgrammeScheduleParser {
        android.util.Xml.newPullParser()
    },
) : ProgrammeGuideRepository {
    override suspend fun loadSchedule(
        endpoint: String,
        serviceId: String,
        start: Instant,
        end: Instant,
    ): List<DvbProgramme> {
        require(end.isAfter(start)) { "The programme-guide end time must be after its start time." }

        val requestUri = HttpQueryBuilder.append(
            endpoint,
            listOf(
                "sid" to serviceId,
                "start" to start.epochSecond.toString(),
                "end" to end.epochSecond.toString(),
                "inclusive" to "true",
            ),
        )
        return try {
            parser.parse(httpClient.get(requestUri))
        } catch (error: XmlPullParserException) {
            throw IOException(
                "The programme-guide XML could not be parsed: ${error.message ?: "invalid XML"}.",
                error,
            )
        }
    }
}
