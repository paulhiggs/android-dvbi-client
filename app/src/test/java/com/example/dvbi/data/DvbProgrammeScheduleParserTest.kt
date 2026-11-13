package com.example.dvbi.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.time.Instant

class DvbProgrammeScheduleParserTest {
    private val parser = DvbProgrammeScheduleParser { KXmlParser() }

    @Test
    fun parsesProgrammeDescriptionsAndScheduleEvents() {
        val programmes = parser.parse(
            """
            <TVAMain xmlns="urn:tva:metadata:2026"
                xmlns:mpeg7="urn:tva:mpeg7:2008" xml:lang="en">
                <ProgramDescription>
                    <ProgramInformationTable>
                        <ProgramInformation programId="crid://example/show/1">
                            <BasicDescription>
                                <Title type="main">Example show</Title>
                                <Synopsis length="short">An example synopsis.</Synopsis>
                                <Genre href="urn:example:genre:news" type="main"/>
                                <ParentalGuidance><mpeg7:MinimumAge>12</mpeg7:MinimumAge></ParentalGuidance>
                            </BasicDescription>
                        </ProgramInformation>
                    </ProgramInformationTable>
                    <ProgramLocationTable>
                        <Schedule serviceIDRef="urn:example:service">
                            <ScheduleEvent>
                                <Program crid="crid://example/show/1"/>
                                <PublishedStartTime>2026-11-13T12:00:00Z</PublishedStartTime>
                                <PublishedDuration>PT30M</PublishedDuration>
                            </ScheduleEvent>
                        </Schedule>
                    </ProgramLocationTable>
                </ProgramDescription>
            </TVAMain>
            """.trimIndent(),
        )

        assertEquals(1, programmes.size)
        assertEquals("Example show", programmes.single().title)
        assertEquals("An example synopsis.", programmes.single().synopsis)
        assertEquals("urn:example:genre:news", programmes.single().genreUri)
        assertEquals(12, programmes.single().minimumAge)
        assertEquals(Instant.parse("2026-11-13T12:00:00Z"), programmes.single().start)
        assertEquals(Instant.parse("2026-11-13T12:30:00Z"), programmes.single().end)
    }

    @Test(expected = XmlPullParserException::class)
    fun rejectsScheduleEventsWithoutStartTimes() {
        parser.parse(
            """
            <TVAMain xmlns="urn:tva:metadata:2026" xml:lang="en">
                <ProgramDescription>
                    <ProgramLocationTable>
                        <Schedule serviceIDRef="urn:example:service">
                            <ScheduleEvent>
                                <Program crid="crid://example/show/1"/>
                                <PublishedDuration>PT30M</PublishedDuration>
                            </ScheduleEvent>
                        </Schedule>
                    </ProgramLocationTable>
                </ProgramDescription>
            </TVAMain>
            """.trimIndent(),
        )
    }
}
