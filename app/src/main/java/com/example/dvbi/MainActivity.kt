package com.example.dvbi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dvbi.data.HttpProgrammeGuideRepository
import com.example.dvbi.data.HttpServiceListDiscoveryRepository
import com.example.dvbi.data.HttpServiceListRepository
import com.example.dvbi.ui.ClientScreen
import com.example.dvbi.ui.DvbClientViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HttpServiceListRepository()
        val discoveryRepository = HttpServiceListDiscoveryRepository()
        val programmeGuideRepository = HttpProgrammeGuideRepository()

        setContent {
            val clientViewModel: DvbClientViewModel = viewModel(
                factory = DvbClientViewModel.factory(
                    repository,
                    discoveryRepository,
                    programmeGuideRepository,
                ),
            )
            ClientScreen(viewModel = clientViewModel)
        }
    }
}
