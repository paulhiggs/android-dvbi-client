package com.example.dvbi.data

import com.example.dvbi.domain.RegistryQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class DvbRegistryResponseParserTest {
    private val parser = DvbRegistryResponseParser { KXmlParser() }

    @Test
    fun parsesV8OfferAndDeliveryMetadata() {
        val offers = parser.parse(
            """
            <ServiceListEntryPoints xmlns="urn:dvb:metadata:servicelistdiscovery:2026"
                xmlns:dt="urn:dvb:metadata:servicediscovery-types:2026"
                version="1" xml:lang="en">
                <ServiceListRegistryEntity><Name>Registry</Name></ServiceListRegistryEntity>
                <ProviderOffering>
                    <Provider><Name>Broadcaster</Name></Provider>
                    <ServiceListOffering regulatorListFlag="true">
                        <dt:ServiceListName>Trusted channels</dt:ServiceListName>
                        <dt:ServiceListURI contentType="application/xml"
                            standardVersion="urn:dvb:metadata:dvbi:standardversion:8">
                            <dt:URI>https://example.test/services.xml</dt:URI>
                        </dt:ServiceListURI>
                        <dt:Delivery><dt:DASHDelivery/></dt:Delivery>
                        <dt:Language>en</dt:Language>
                        <dt:TargetCountry>GBR</dt:TargetCountry>
                        <dt:ServiceListId>urn:example:services</dt:ServiceListId>
                    </ServiceListOffering>
                </ProviderOffering>
            </ServiceListEntryPoints>
            """.trimIndent(),
        )

        assertEquals(1, offers.size)
        assertEquals("Broadcaster", offers.single().providerName)
        assertEquals("Trusted channels", offers.single().name)
        assertEquals("urn:example:services", offers.single().id)
        assertEquals("https://example.test/services.xml", offers.single().version8Uri)
        assertTrue(offers.single().regulatorList)
        assertEquals(setOf("DASHDelivery"), offers.single().deliveryTypes)
    }

    @Test
    fun preservesExistingQueryAndFragment() {
        val uri = ServiceListRegistryQueryBuilder.append(
            "https://registry.test/query?existing=one#results",
            RegistryQuery(
                country = "GBR",
                language = "en",
                delivery = "DASH",
                regulatorListsOnly = true,
            ),
        )

        assertEquals(
            "https://registry.test/query?existing=one&TargetCountry=GBR&Language=en&Delivery=DASH&regulatorListFlag=true#results",
            uri,
        )
    }

    @Test
    fun encodesQueryValues() {
        assertEquals(
            "https://registry.test/query?label=Service+List",
            HttpQueryBuilder.append(
                "https://registry.test/query",
                listOf("label" to "Service List"),
            ),
        )
    }
}
