package com.example.dvbi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dvbi.domain.DvbService
import com.example.dvbi.domain.DvbServiceList
import com.example.dvbi.domain.DvbServiceListOffer
import com.example.dvbi.domain.DvbProgramme
import com.example.dvbi.domain.ProgrammeGuideRepository
import com.example.dvbi.domain.RegistryQuery
import com.example.dvbi.domain.ServiceListDiscoveryRepository
import com.example.dvbi.domain.ServiceListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

data class ClientUiState(
    val serviceListUrl: String = "",
    val serviceList: DvbServiceList? = null,
    val expectedServiceListId: String? = null,
    val registryUrl: String = "",
    val registryCountry: String = "",
    val registryLanguage: String = "",
    val registryDelivery: String = "DASH",
    val regulatorListsOnly: Boolean = false,
    val registryOffers: List<DvbServiceListOffer> = emptyList(),
    val isDiscovering: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val playingService: DvbService? = null,
    val guideService: DvbService? = null,
    val programmes: List<DvbProgramme> = emptyList(),
    val isLoadingGuide: Boolean = false,
)

class DvbClientViewModel(
    private val repository: ServiceListRepository,
    private val discoveryRepository: ServiceListDiscoveryRepository,
    private val programmeGuideRepository: ProgrammeGuideRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClientUiState())
    val uiState: StateFlow<ClientUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var discoveryJob: Job? = null
    private var guideJob: Job? = null

    fun updateServiceListUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            serviceListUrl = value,
            expectedServiceListId = null,
            errorMessage = null,
        )
    }

    fun updateRegistryUrl(value: String) {
        _uiState.value = _uiState.value.copy(registryUrl = value, errorMessage = null)
    }

    fun updateRegistryCountry(value: String) {
        _uiState.value = _uiState.value.copy(registryCountry = value, errorMessage = null)
    }

    fun updateRegistryLanguage(value: String) {
        _uiState.value = _uiState.value.copy(registryLanguage = value, errorMessage = null)
    }

    fun updateRegistryDelivery(value: String) {
        _uiState.value = _uiState.value.copy(registryDelivery = value, errorMessage = null)
    }

    fun setRegulatorListsOnly(value: Boolean) {
        _uiState.value = _uiState.value.copy(regulatorListsOnly = value, errorMessage = null)
    }

    fun discoverServiceLists() {
        val state = _uiState.value
        val registryUrl = state.registryUrl.trim()
        if (registryUrl.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Enter a service-list registry URL.")
            return
        }

        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDiscovering = true, errorMessage = null)
            try {
                val offers = discoveryRepository.discover(
                    registryUrl,
                    RegistryQuery(
                        country = state.registryCountry,
                        language = state.registryLanguage,
                        delivery = state.registryDelivery,
                        regulatorListsOnly = state.regulatorListsOnly,
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    registryOffers = offers,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    errorMessage = error.message ?: "Unable to query the service-list registry.",
                )
            } catch (error: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    errorMessage = error.message ?: "The registry query is invalid.",
                )
            }
        }
    }

    fun loadOfferedServiceList(offer: DvbServiceListOffer) {
        val uri = offer.version8Uri
        if (uri == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "This registry entry does not offer a DVB-I v8 service list.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            serviceListUrl = uri,
            expectedServiceListId = offer.id,
        )
        loadServiceList()
    }

    fun loadServiceList() {
        val state = _uiState.value
        val uri = state.serviceListUrl.trim()
        val expectedListId = state.expectedServiceListId
        if (uri.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a service-list URL.")
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val list = repository.load(uri)
                if (expectedListId != null && list.id != expectedListId) {
                    throw IOException("The service list ID does not match the registry offering.")
                }
                _uiState.value = _uiState.value.copy(
                    serviceList = list,
                    isLoading = false,
                    playingService = null,
                    guideService = null,
                    programmes = emptyList(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unable to load the service list.",
                )
            } catch (error: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "The service-list URL is invalid.",
                )
            }
        }
    }

    fun play(service: DvbService) {
        if (service.preferredInstance == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "${service.name} has no supported broadband stream.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(playingService = service, errorMessage = null)
    }

    fun stopPlayback() {
        _uiState.value = _uiState.value.copy(playingService = null)
    }

    fun loadGuide(service: DvbService) {
        guideJob?.cancel()
        _uiState.value = _uiState.value.copy(
            guideService = service,
            programmes = emptyList(),
            isLoadingGuide = false,
            errorMessage = null,
        )
        val serviceList = _uiState.value.serviceList
        if (serviceList == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Load a service list first.")
            return
        }

        val guideSource = service.inlineGuideSource
            ?: service.guideSourceRef?.let { sourceId ->
                serviceList.guideSources.firstOrNull { it.id == sourceId }
            }
            ?: serviceList.guideSources.firstOrNull()
        val endpoint = guideSource?.scheduleUri
        if (endpoint == null) {
            _uiState.value = _uiState.value.copy(
                guideService = service,
                programmes = emptyList(),
                errorMessage = "${service.name} has no schedule endpoint.",
            )
            return
        }

        guideJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                guideService = service,
                programmes = emptyList(),
                isLoadingGuide = true,
                errorMessage = null,
            )
            try {
                val now = Instant.now()
                val programmes = programmeGuideRepository.loadSchedule(
                    endpoint = endpoint,
                    serviceId = service.contentGuideServiceRef ?: service.id,
                    start = now.minusSeconds(SCHEDULE_LOOKBACK_SECONDS),
                    end = now.plusSeconds(SCHEDULE_LOOKAHEAD_SECONDS),
                )
                _uiState.value = _uiState.value.copy(
                    programmes = programmes,
                    isLoadingGuide = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                _uiState.value = _uiState.value.copy(
                    isLoadingGuide = false,
                    errorMessage = error.message ?: "Unable to load the programme guide.",
                )
            } catch (error: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    isLoadingGuide = false,
                    errorMessage = error.message ?: "The programme-guide request is invalid.",
                )
            }
        }
    }

    companion object {
        private const val SCHEDULE_LOOKBACK_SECONDS = 6 * 60 * 60L
        private const val SCHEDULE_LOOKAHEAD_SECONDS = 24 * 60 * 60L

        fun factory(
            repository: ServiceListRepository,
            discoveryRepository: ServiceListDiscoveryRepository,
            programmeGuideRepository: ProgrammeGuideRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(DvbClientViewModel::class.java))
                    return DvbClientViewModel(
                        repository,
                        discoveryRepository,
                        programmeGuideRepository,
                    ) as T
                }
            }
    }
}
