package pe.upeu.ecorecicla.domain.sesion

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Canal por el que la capa de datos avisa que el servicio respondio 401 fuera
 * del login. Solo el SesionViewModel lo escucha y devuelve al inicio de sesion.
 */
class AvisosDeSesion {
    private val _sesionTerminada = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sesionTerminada: SharedFlow<String> = _sesionTerminada.asSharedFlow()

    fun notificar(motivo: String) {
        _sesionTerminada.tryEmit(motivo)
    }
}
