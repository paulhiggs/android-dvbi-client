package com.example.dvbi.data

import com.example.dvbi.domain.DvbServiceList
import com.example.dvbi.domain.ServiceListRepository
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class HttpServiceListRepository(
    private val parser: DvbServiceListParser = DvbServiceListParser(),
    private val httpClient: HttpXmlClient = HttpXmlClient(),
) : ServiceListRepository {
    override suspend fun load(uri: String): DvbServiceList {
        return try {
            parser.parse(httpClient.get(uri))
        } catch (error: XmlPullParserException) {
            throw IOException(
                "The service-list XML could not be parsed: ${error.message ?: "invalid XML"}.",
                error,
            )
        }
    }
}
