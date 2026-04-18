package com.airouter.ui.screen.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airouter.data.model.Provider
import com.airouter.data.repository.ProviderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ProviderListViewModel(
    private val providerRepository: ProviderRepository,
) : ViewModel() {

    val providers: StateFlow<List<Provider>> = providerRepository.getAllProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
