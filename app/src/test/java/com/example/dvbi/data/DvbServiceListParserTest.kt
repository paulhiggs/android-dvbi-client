package com.example.dvbi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException

class DvbServiceListParserTest {
    private val parser = DvbServiceListParser { KXmlParser() }

    @Test
    fun parsesNamespacedServiceAndDashUri() {
        val list = parser.parse(
            """
            <ServiceList xmlns="urn:dvb:metadata:servicediscovery:2026"
                xmlns:dt="urn:dvb:metadata:servicediscovery-types:2026"
                version="2" xml:lang="en" id="urn:example:list">
                <Name>Example list</Name>
                <ProviderName>Example provider</ProviderName>
                <Service version="1">
                    <UniqueIdentifier>urn:example:channel:1</UniqueIdentifier>
                    <ServiceInstance priority="1">
                        <DASHDeliveryParameters>
                            <UriBasedLocation contentType="application/dash+xml">
                                <dt:URI>https://media.example/channel.mpd</dt:URI>
                            </UriBasedLocation>
                        </DASHDeliveryParameters>
                    </ServiceInstance>
                    <ServiceName>Example channel</ServiceName>
                    <ProviderName>Example provider</ProviderName>
                </Service>
            </ServiceList>
            """.trimIndent(),
        )

        assertEquals("urn:example:list", list.id)
        assertEquals(2, list.version)
        assertEquals("Example channel", list.services.single().name)
        assertEquals("https://media.example/channel.mpd", list.services.single().instances.single().uri)
    }

    @Test
    fun ignoresNonHttpStreamUris() {
        val list = parser.parse(
            """
            <ServiceList xmlns="urn:dvb:metadata:servicediscovery:2026"
                xmlns:dt="urn:dvb:metadata:servicediscovery-types:2026"
                version="1" xml:lang="en" id="urn:example:list">
                <Name>Example list</Name>
                <ProviderName>Example provider</ProviderName>
                <Service version="1">
                    <UniqueIdentifier>urn:example:channel:1</UniqueIdentifier>
                    <ServiceInstance>
                        <DASHDeliveryParameters>
                            <UriBasedLocation contentType="application/dash+xml">
                                <dt:URI>file:///private/stream.mpd</dt:URI>
                            </UriBasedLocation>
                        </DASHDeliveryParameters>
                    </ServiceInstance>
                    <ServiceName>Example channel</ServiceName>
                    <ProviderName>Example provider</ProviderName>
                </Service>
            </ServiceList>
            """.trimIndent(),
        )

        assertNull(list.services.single().instances.single().uri)
    }

    @Test
    fun parsesContentGuideSourceFromList() {
        val list = parser.parse(
            """
            <ServiceList xmlns="urn:dvb:metadata:servicediscovery:2026"
                xmlns:dt="urn:dvb:metadata:servicediscovery-types:2026"
                version="1" xml:lang="en" id="urn:example:list">
                <Name>Example list</Name>
                <ProviderName>Example provider</ProviderName>
                <ContentGuideSourceList>
                    <ContentGuideSource CGSID="guide-1">
                        <Name>Programme guide</Name>
                        <ProviderName>Guide provider</ProviderName>
                        <ScheduleInfoEndpoint contentType="application/xml">
                            <dt:URI>https://guide.example/schedule</dt:URI>
                        </ScheduleInfoEndpoint>
                    </ContentGuideSource>
                </ContentGuideSourceList>
            </ServiceList>
            """.trimIndent(),
        )

        assertEquals("guide-1", list.guideSources.single().id)
        assertEquals("https://guide.example/schedule", list.guideSources.single().scheduleUri)
    }

    @Test
    fun parsesGlobalAndRegionalLogicalChannelNumbers() {
        val list = parser.parse(
            """
            <ServiceList xmlns="urn:dvb:metadata:servicediscovery:2026"
                version="1" xml:lang="en" id="urn:example:list">
                <Name>Example list</Name>
                <ProviderName>Example provider</ProviderName>
                <LCNTableList>
                    <LCNTable version="2">
                        <LCN serviceRef="urn:example:channel:1" channelNumber="7"
                            visible="1" selectable="0"/>
                    </LCNTable>
                    <LCNTable version="1">
                        <TargetRegion>R1</TargetRegion>
                        <LCN serviceRef="urn:example:channel:1" channelNumber="12"/>
                    </LCNTable>
                </LCNTableList>
            </ServiceList>
            """.trimIndent(),
        )

        assertEquals(2, list.logicalChannelNumbers.size)
        assertEquals(7, list.logicalChannelNumbers[0].channelNumber)
        assertEquals(emptyList<String>(), list.logicalChannelNumbers[0].targetRegions)
        assertEquals(false, list.logicalChannelNumbers[0].selectable)
        assertEquals(listOf("R1"), list.logicalChannelNumbers[1].targetRegions)
        assertEquals(true, list.logicalChannelNumbers[1].visible)
    }

    @Test(expected = XmlPullParserException::class)
    fun rejectsServiceListErrorStatus() {
        parser.parse(
            """
            <ServiceList xmlns="urn:dvb:metadata:servicediscovery:2026"
                version="1" xml:lang="en" id="urn:example:list"
                responseStatus="ERROR_INVALID_REQUEST">
                <Name>Example list</Name>
                <ProviderName>Example provider</ProviderName>
            </ServiceList>
            """.trimIndent(),
        )
    }

    @Test(expected = XmlPullParserException::class)
    fun rejectsServiceWithoutIdentifier() {
        parser.parse(
            """
            <ServiceList xmlns="urn:dvb:metadata:servicediscovery:2026"
                version="1" xml:lang="en" id="urn:example:list">
                <Name>Example list</Name>
                <ProviderName>Example provider</ProviderName>
                <Service version="1">
                    <ServiceName>Example channel</ServiceName>
                    <ProviderName>Example provider</ProviderName>
                </Service>
            </ServiceList>
            """.trimIndent(),
        )
    }
}
