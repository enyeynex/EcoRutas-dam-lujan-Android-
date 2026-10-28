package pe.upeu.ecorecicla.presentation.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase

class PerfilViewModel(observar: ObservarRutaUseCase) : ViewModel() {
    /** Pendientes sin enviar: se avisa antes de cerrar sesion porque se borrarian. */
    val pendientes: StateFlow<Long> =
        observar.pendientes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
}
