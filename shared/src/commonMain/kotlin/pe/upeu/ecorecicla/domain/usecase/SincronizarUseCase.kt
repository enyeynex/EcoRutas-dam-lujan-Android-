package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/** Sube lo pendiente y despues baja la ruta del dia. El orden lo garantiza el repositorio. */
class SincronizarUseCase(private val rutas: RutaRepository) {
    suspend operator fun invoke(): Result<ResumenSincronizacion> = resultadoDe { rutas.sincronizar() }
}

class CerrarRutaUseCase(private val rutas: RutaRepository) {
    suspend operator fun invoke(rutaId: Long): Result<Unit> = resultadoDe { rutas.cerrarRuta(rutaId) }
}
