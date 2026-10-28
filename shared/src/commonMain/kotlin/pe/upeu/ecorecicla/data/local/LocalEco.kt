package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.EstadoRuta
import pe.upeu.ecorecicla.domain.model.InfoSincronizacion
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.db.Pesaje as FilaPesaje

/**
 * Capa local: concentra las consultas de Eco.sq y traduce filas <-> dominio.
 * No conoce Ktor ni los DTO del servicio.
 */
class LocalEco(private val base: EcoDb) {

    private val q = base.ecoQueries

    // ---------- Lectura observable ----------

    fun observarRuta(): Flow<Ruta?> =
        q.rutaActual().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { fila -> fila?.let { Ruta(it.id, it.fecha, EstadoRuta.valueOf(it.estado)) } }

    fun rutaActual(): Ruta? =
        q.rutaActual().executeAsOneOrNull()?.let { Ruta(it.id, it.fecha, EstadoRuta.valueOf(it.estado)) }

    fun observarPuntos(): Flow<List<PuntoAcopio>> =
        q.puntosDeRuta { id, razon, direccion, zona, material, secuencia, atendido ->
            PuntoAcopio(id, razon, direccion, zona, material, secuencia.toInt(), atendido == 1L)
        }.asFlow().mapToList(Dispatchers.Default)

    /** SC-A: puntos que contienen [texto] y, si [soloPendientes], solo los aun no pesados. */
    fun observarPuntos(texto: String, soloPendientes: Boolean): Flow<List<PuntoAcopio>> =
        q.puntosFiltrados(texto.trim(), if (soloPendientes) 1L else 0L) { id, razon, direccion, zona, material, secuencia, atendido ->
            PuntoAcopio(id, razon, direccion, zona, material, secuencia.toInt(), atendido == 1L)
        }.asFlow().mapToList(Dispatchers.Default)

    fun observarPunto(id: Long): Flow<PuntoAcopio?> =
        q.puntoPorId(id) { pid, razon, direccion, zona, material, secuencia, atendido ->
            PuntoAcopio(pid, razon, direccion, zona, material, secuencia.toInt(), atendido == 1L)
        }.asFlow().mapToOneOrNull(Dispatchers.Default)

    fun observarPesajes(puntoId: Long): Flow<List<Pesaje>> =
        q.pesajesDePunto(puntoId).asFlow().mapToList(Dispatchers.Default)
            .map { filas -> filas.map { it.aDominio() } }

    fun contarPendientes(): Flow<Long> =
        q.contarPendientes().asFlow().mapToOne(Dispatchers.Default)

    fun ultimaSincronizacion(): Flow<InfoSincronizacion?> =
        q.ultimaSincronizacion().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.let { s -> InfoSincronizacion(s.instante, s.enviados.toInt(), s.recibidos.toInt()) } }

    fun buscar(id: Long): Pesaje? = q.pesajePorId(id).executeAsOneOrNull()?.aDominio()

    fun pendientes(): List<Pesaje> = q.listarPendientes().executeAsList().map { it.aDominio() }

    // ---------- Escrituras de la interfaz ----------

    /** Guarda lo creado en el dispositivo con un id negativo y la marca de pendiente. */
    fun guardarNuevo(rutaId: Long, puntoId: Long, datos: DatosPesaje, operario: String, fechaHora: String): Pesaje {
        val menor = q.siguienteIdLocal().executeAsOne()
        val id = minOf(menor, 0L) - 1L
        q.insertarPesaje(
            id, rutaId, puntoId, fechaHora, datos.material, datos.pesoBruto, datos.tara,
            datos.pesoBruto - datos.tara, datos.observacion, operario, 0L, null, 1L, CREAR, null
        )
        return buscar(id)!!
    }

    /** Una correccion de algo aun no creado en el servicio sigue siendo un CREAR. */
    fun guardarCorreccion(id: Long, datos: DatosPesaje) {
        val operacion = if (id < 0) CREAR else EDITAR
        q.actualizarPesajeLocal(
            datos.material, datos.pesoBruto, datos.tara, datos.pesoBruto - datos.tara,
            datos.observacion, operacion, id
        )
    }

    fun guardarAnulacion(id: Long, motivo: String) {
        if (id < 0) q.borrarPesaje(id) else q.marcarAnulado(motivo, id)
    }

    fun descartarPendiente(id: Long) = q.descartarPendiente(id)

    // ---------- Escrituras de la sincronizacion ----------

    /** La fila provisional se sustituye por la definitiva en la misma transaccion. */
    fun confirmarCreado(idLocal: Long, definitivo: Pesaje) {
        q.transaction {
            q.borrarPesaje(idLocal)
            insertarSincronizado(definitivo)
        }
    }

    fun confirmarCorregido(definitivo: Pesaje) = insertarSincronizado(definitivo)

    fun confirmarSincronizado(id: Long) = q.marcarSincronizado(id)

    fun marcarRechazado(id: Long, motivo: String) = q.marcarRechazado(motivo, id)

    private fun insertarSincronizado(p: Pesaje) {
        val anulado = p.estado as? EstadoPesaje.Anulado
        q.insertarPesaje(
            p.id, p.rutaId, p.puntoId, p.fechaHora, p.material, p.pesoBruto, p.tara, p.pesoNeto,
            p.observacion, p.operario, if (anulado != null) 1L else 0L, anulado?.motivo, 0L, null, null
        )
    }

    /**
     * Recibe la ruta y sus pesajes del servicio. Solo borra lo sincronizado
     * (pendiente = 0) y usa INSERT OR IGNORE: nunca pisa un cambio pendiente.
     * Una sola transaccion: el Flow emite una vez y la lista no parpadea.
     */
    fun reemplazarDesdeServidor(ruta: Ruta?, puntos: List<Pair<PuntoAcopio, Boolean>>, pesajes: List<Pesaje>) {
        q.transaction {
            q.borrarRutas()
            q.borrarPuntos()
            if (ruta != null) {
                q.insertarRuta(ruta.id, ruta.fecha, ruta.estado.name)
                puntos.forEach { (p, atendidoServidor) ->
                    q.insertarPunto(
                        p.id, ruta.id, p.razonSocial, p.direccion, p.zona, p.materialHabitual,
                        p.secuencia.toLong(), if (atendidoServidor) 1L else 0L
                    )
                }
            }
            q.borrarPesajesSincronizados()
            pesajes.forEach { p ->
                val anulado = p.estado as? EstadoPesaje.Anulado
                q.insertarPesajeSiNoExiste(
                    p.id, p.rutaId, p.puntoId, p.fechaHora, p.material, p.pesoBruto, p.tara, p.pesoNeto,
                    p.observacion, p.operario, if (anulado != null) 1L else 0L, anulado?.motivo
                )
            }
        }
    }

    fun registrarSincronizacion(instante: Long, enviados: Int, recibidos: Int) =
        q.registrarSincronizacion(instante, enviados.toLong(), recibidos.toLong())

    fun borrarTodo() = q.borrarTodo()

    /** Operacion pendiente de una fila (CREAR, EDITAR o ANULAR). */
    fun operacionDe(id: Long): String? = q.pesajePorId(id).executeAsOneOrNull()?.operacion

    private fun FilaPesaje.aDominio() = Pesaje(
        id = id,
        puntoId = puntoId,
        fechaHora = fechaHora,
        material = material,
        pesoBruto = pesoBruto,
        tara = tara,
        pesoNeto = pesoNeto,
        operario = operario,
        estado = if (anulado == 1L) EstadoPesaje.Anulado(motivoAnulacion.orEmpty()) else EstadoPesaje.Registrado,
        pendiente = pendiente == 1L,
        rutaId = rutaId,
        observacion = observacion,
        rechazo = rechazo
    )

    companion object {
        const val CREAR = "CREAR"
        const val EDITAR = "EDITAR"
        const val ANULAR = "ANULAR"
    }
}
