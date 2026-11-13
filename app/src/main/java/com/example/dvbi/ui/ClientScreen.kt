package com.example.dvbi.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.dvbi.domain.DvbProgramme
import com.example.dvbi.domain.DvbService
import com.example.dvbi.domain.DvbServiceInstance
import com.example.dvbi.domain.DvbServiceList
import com.example.dvbi.domain.DvbServiceListOffer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ClientTab(val label: String, val marker: String) {
    CHANNELS("Channels", "TV"),
    GUIDE("Guide", "EPG"),
    SETTINGS("Settings", "SET"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(viewModel: DvbClientViewModel) {
    val state by viewModel.uiState.collectAsState()
    val selectedTab = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(ClientTab.CHANNELS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.serviceList?.name?.ifBlank { "DVB-I Client" } ?: "DVB-I Client")
                },
            )
        },
        bottomBar = {
            NavigationBar {
                ClientTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab.value == tab,
                        onClick = { selectedTab.value = tab },
                        icon = { Text(tab.marker) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            state.playingService?.let { service ->
                val instance = service.preferredInstance
                if (instance != null) {
                    PlaybackPanel(
                        service = service,
                        instance = instance,
                        onClose = viewModel::stopPlayback,
                    )
                }
            }

            when (selectedTab.value) {
                ClientTab.CHANNELS -> ChannelList(
                    serviceList = state.serviceList,
                    onPlay = viewModel::play,
                    onGuide = { service ->
                        viewModel.loadGuide(service)
                        selectedTab.value = ClientTab.GUIDE
                    },
                    isLoading = state.isLoading,
                )
                ClientTab.GUIDE -> GuideList(
                    serviceList = state.serviceList,
                    service = state.guideService,
                    programmes = state.programmes,
                    isLoading = state.isLoadingGuide,
                    onRefresh = { state.guideService?.let(viewModel::loadGuide) },
                )
                ClientTab.SETTINGS -> SettingsPanel(
                    url = state.serviceListUrl,
                    registryUrl = state.registryUrl,
                    registryCountry = state.registryCountry,
                    registryLanguage = state.registryLanguage,
                    registryDelivery = state.registryDelivery,
                    regulatorListsOnly = state.regulatorListsOnly,
                    registryOffers = state.registryOffers,
                    isLoading = state.isLoading,
                    isDiscovering = state.isDiscovering,
                    onUrlChange = viewModel::updateServiceListUrl,
                    onRegistryUrlChange = viewModel::updateRegistryUrl,
                    onRegistryCountryChange = viewModel::updateRegistryCountry,
                    onRegistryLanguageChange = viewModel::updateRegistryLanguage,
                    onRegistryDeliveryChange = viewModel::updateRegistryDelivery,
                    onRegulatorListsOnlyChange = viewModel::setRegulatorListsOnly,
                    onDiscover = viewModel::discoverServiceLists,
                    onLoadOffer = { offer ->
                        viewModel.loadOfferedServiceList(offer)
                        selectedTab.value = ClientTab.CHANNELS
                    },
                    onLoad = {
                        viewModel.loadServiceList()
                        selectedTab.value = ClientTab.CHANNELS
                    },
                )
            }
        }
    }
}

@Composable
private fun ChannelList(
    serviceList: DvbServiceList?,
    onPlay: (DvbService) -> Unit,
    onGuide: (DvbService) -> Unit,
    isLoading: Boolean,
) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        return
    }

    if (serviceList == null) {
        Text(
            text = "Add a DVB-I service-list URL in Settings to discover channels.",
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }

    val defaultLcnByService = serviceList.logicalChannelNumbers
        .filter { it.targetRegions.isEmpty() }
        .groupBy { it.serviceId }
        .mapValues { (_, entries) -> entries.maxBy { it.tableVersion } }
    val services = if (defaultLcnByService.isEmpty()) {
        serviceList.services
    } else {
        serviceList.services
            .filter { defaultLcnByService[it.id]?.visible != false }
            .sortedWith(
                compareBy<DvbService> { defaultLcnByService[it.id]?.channelNumber ?: Int.MAX_VALUE }
                    .thenBy { it.name },
            )
    }

    Text(
        text = "${serviceList.providerName} · ${services.size} services",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(services, key = { it.id }) { service ->
            Card(modifier = Modifier.fillMaxWidth()) {
                val lcn = defaultLcnByService[service.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lcn?.let { "${it.channelNumber} · ${service.name}" } ?: service.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            service.providerName.ifBlank { serviceList.providerName },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Column {
                        TextButton(
                            onClick = { onPlay(service) },
                            enabled = service.preferredInstance != null && lcn?.selectable != false,
                        ) {
                            Text("Play")
                        }
                        TextButton(onClick = { onGuide(service) }) {
                            Text("Guide")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideList(
    serviceList: DvbServiceList?,
    service: DvbService?,
    programmes: List<DvbProgramme>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    if (serviceList == null) {
        Text("Load a service list to see its programme-guide sources.")
        return
    }

    if (service == null) {
        Text("Choose Guide on a channel to load its programme schedule.")
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = service.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        TextButton(onClick = onRefresh, enabled = !isLoading) {
            Text("Refresh")
        }
    }

    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        return
    }
    if (programmes.isEmpty()) {
        Text("No programme information was returned for this time window.")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(programmes, key = { "${it.id}-${it.start}" }) { programme ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${formatProgrammeTime(programme.start)} – ${formatProgrammeTime(programme.end)}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(programme.title, style = MaterialTheme.typography.titleMedium)
                    programme.synopsis?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    programme.minimumAge?.let {
                        Text("Minimum age: $it", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    url: String,
    registryUrl: String,
    registryCountry: String,
    registryLanguage: String,
    registryDelivery: String,
    regulatorListsOnly: Boolean,
    registryOffers: List<DvbServiceListOffer>,
    isLoading: Boolean,
    isDiscovering: Boolean,
    onUrlChange: (String) -> Unit,
    onRegistryUrlChange: (String) -> Unit,
    onRegistryCountryChange: (String) -> Unit,
    onRegistryLanguageChange: (String) -> Unit,
    onRegistryDeliveryChange: (String) -> Unit,
    onRegulatorListsOnlyChange: (Boolean) -> Unit,
    onDiscover: () -> Unit,
    onLoadOffer: (DvbServiceListOffer) -> Unit,
    onLoad: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Service discovery", style = MaterialTheme.typography.titleLarge)
        Text(
            "Query a Service List Registry, or enter a ServiceList URL directly.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = registryUrl,
            onValueChange = onRegistryUrlChange,
            label = { Text("Registry query endpoint") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = registryCountry,
                onValueChange = onRegistryCountryChange,
                label = { Text("Country (e.g. IT)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = registryLanguage,
                onValueChange = onRegistryLanguageChange,
                label = { Text("Language") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = registryDelivery,
            onValueChange = onRegistryDeliveryChange,
            label = { Text("Delivery filter (e.g. DASH)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = regulatorListsOnly,
                onCheckedChange = onRegulatorListsOnlyChange,
            )
            Text("Filter to regulator lists")
        }
        Button(onClick = onDiscover, enabled = !isDiscovering) {
            Text(if (isDiscovering) "Searching…" else "Find service lists")
        }
        registryOffers.forEach { offer ->
            RegistryOfferCard(offer = offer, onLoad = { onLoadOffer(offer) })
        }

        Text("Direct service-list URL", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Service-list URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onLoad, enabled = !isLoading) {
            Text(if (isLoading) "Loading…" else "Load service list")
        }
    }
}

@Composable
private fun RegistryOfferCard(
    offer: DvbServiceListOffer,
    onLoad: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(offer.name, style = MaterialTheme.typography.titleMedium)
            Text(offer.providerName, style = MaterialTheme.typography.bodyMedium)
            if (offer.targetCountries.isNotEmpty()) {
                Text("Countries: ${offer.targetCountries.joinToString()}")
            }
            if (offer.deliveryTypes.isNotEmpty()) {
                Text("Delivery: ${offer.deliveryTypes.joinToString()}")
            }
            Button(onClick = onLoad, enabled = offer.version8Uri != null) {
                Text(if (offer.version8Uri != null) "Load DVB-I v8 list" else "No v8 list available")
            }
        }
    }
}

@Composable
private fun PlaybackPanel(
    service: DvbService,
    instance: DvbServiceInstance,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val streamUri = instance.uri ?: return

    key(streamUri) {
        val player = androidx.compose.runtime.remember(context, streamUri, instance.contentType) {
            createPlayer(context, instance)
        }
        androidx.compose.runtime.DisposableEffect(player) {
            onDispose { player.release() }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(service.name, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose) { Text("Close") }
                }
                AndroidView(
                    factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
        }
    }
}

private val programmeTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, HH:mm").withZone(ZoneId.systemDefault())

private fun formatProgrammeTime(time: Instant): String = programmeTimeFormatter.format(time)

private fun createPlayer(context: Context, instance: DvbServiceInstance): ExoPlayer {
    val uri = requireNotNull(instance.uri)
    val mediaItemBuilder = MediaItem.Builder().setUri(uri)
    when (instance.contentType?.lowercase()) {
        "application/dash+xml" -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl" -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }

    return ExoPlayer.Builder(context).build().apply {
        setMediaItem(mediaItemBuilder.build())
        prepare()
        playWhenReady = true
    }
}
