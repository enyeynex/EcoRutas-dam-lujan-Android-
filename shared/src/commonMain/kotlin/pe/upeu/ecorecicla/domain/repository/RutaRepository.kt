package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.InfoSincronizacion
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.model.Ruta

/** Ruta del dia: todo se lee de la base local mediante flujos observables. */
interface RutaRepository {
    fun observarRuta(): Flow<Ruta?>
    fun observarPuntos(): Flow<List<PuntoAcopio>>
    fun observarPunto(id: Long): Flow<PuntoAcopio?>
    fun contarPendientes(): Flow<Long>
    fun ultimaSincronizacion(): Flow<InfoSincronizacion?>

    /** Primero envia lo pendiente y despues trae la ruta y sus pesajes. */
    suspend fun sincronizar(): ResumenSincronizacion

    /** Solo con conexion: aplica RN-04 en el servicio. */
    suspend fun cerrarRuta(rutaId: Long)

    /** Turno compartido: deja el equipo limpio al cerrar sesion. */
    fun borrarDatosLocales()
}
