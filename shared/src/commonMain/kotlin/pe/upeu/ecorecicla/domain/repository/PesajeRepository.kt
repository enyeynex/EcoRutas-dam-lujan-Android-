package pe.upeu.ecorecicla.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.Pesaje

interface PesajeRepository {
    fun observarPesajes(puntoId: Long): Flow<List<Pesaje>>

    /** Guarda en local (id negativo, pendiente) e intenta enviar. Devuelve como quedo. */
    suspend fun registrar(rutaId: Long, puntoId: Long, datos: DatosPesaje, operario: String): Pesaje

    suspend fun corregir(id: Long, datos: DatosPesaje): Pesaje

    suspend fun anular(id: Long, motivo: String)

    /** Quita una fila rechazada por el servicio; la siguiente bajada restaura la version del servidor. */
    suspend fun descartar(id: Long)
}
