package com.example.dvbi.domain

import java.time.Instant

data class DvbServiceList(
    val id: String,
    val version: Int,
    val name: String,
    val providerName: String,
    val services: List<DvbService>,
    val guideSources: List<DvbGuideSource>,
    val logicalChannelNumbers: List<DvbLogicalChannelNumber> = emptyList(),
)

data class DvbLogicalChannelNumber(
    val serviceId: String,
    val channelNumber: Int,
    val targetRegions: List<String>,
    val tableVersion: Int,
    val visible: Boolean,
    val selectable: Boolean,
)

data class DvbServiceListOffer(
    val providerName: String,
    val name: String,
    val id: String,
    val serviceListUris: List<DvbServiceListUri>,
    val targetCountries: List<String>,
    val languages: List<String>,
    val deliveryTypes: Set<String>,
    val regulatorList: Boolean,
) {
    val version8Uri: String?
        get() = serviceListUris.firstOrNull {
            it.standardVersion == "urn:dvb:metadata:dvbi:standardversion:8"
        }?.uri
}

data class DvbServiceListUri(
    val uri: String,
    val contentType: String,
    val standardVersion: String?,
)

data class DvbService(
    val id: String,
    val name: String,
    val providerName: String,
    val instances: List<DvbServiceInstance>,
    val guideSourceRef: String? = null,
    val contentGuideServiceRef: String? = null,
    val inlineGuideSource: DvbGuideSource? = null,
) {
    val preferredInstance: DvbServiceInstance?
        get() = instances
            .filter { it.uri != null }
            .minByOrNull { it.priority ?: Int.MAX_VALUE }
}

data class DvbServiceInstance(
    val uri: String?,
    val contentType: String?,
    val priority: Int?,
)

data class DvbGuideSource(
    val id: String,
    val name: String,
    val scheduleUri: String?,
    val programInfoUri: String? = null,
    val groupInfoUri: String? = null,
    val moreEpisodesUri: String? = null,
)

data class DvbProgramme(
    val id: String,
    val title: String,
    val synopsis: String?,
    val start: Instant,
    val end: Instant,
    val genreUri: String?,
    val minimumAge: Int?,
    val imageUri: String?,
)

interface ServiceListRepository {
    suspend fun load(uri: String): DvbServiceList
}

data class RegistryQuery(
    val country: String? = null,
    val language: String? = null,
    val delivery: String? = null,
    val regulatorListsOnly: Boolean = false,
)

interface ServiceListDiscoveryRepository {
    suspend fun discover(registryUri: String, query: RegistryQuery): List<DvbServiceListOffer>
}

interface ProgrammeGuideRepository {
    suspend fun loadSchedule(
        endpoint: String,
        serviceId: String,
        start: Instant,
        end: Instant,
    ): List<DvbProgramme>
}
