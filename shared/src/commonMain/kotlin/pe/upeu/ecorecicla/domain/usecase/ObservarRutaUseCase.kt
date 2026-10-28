package pe.upeu.ecorecicla.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.InfoSincronizacion
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/** Suscripciones de la interfaz: todas salen de la base local. */
class ObservarRutaUseCase(private val rutas: RutaRepository) {
    fun ruta(): Flow<Ruta?> = rutas.observarRuta()
    fun puntos(): Flow<List<PuntoAcopio>> = rutas.observarPuntos()
    fun punto(id: Long): Flow<PuntoAcopio?> = rutas.observarPunto(id)
    fun pendientes(): Flow<Long> = rutas.contarPendientes()
    fun ultimaSincronizacion(): Flow<InfoSincronizacion?> = rutas.ultimaSincronizacion()
}

class ObservarPesajesUseCase(private val pesajes: PesajeRepository) {
    operator fun invoke(puntoId: Long): Flow<List<Pesaje>> = pesajes.observarPesajes(puntoId)
}
