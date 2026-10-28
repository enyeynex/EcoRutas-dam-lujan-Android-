package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.upeu.ecorecicla.data.local.LocalEco
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.InfoSincronizacion
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.ResumenSincronizacion
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.plataforma.Reloj
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository

/**
 * Repositorio offline-first: la base local es la unica fuente de verdad de la
 * interfaz y la red solo la mantiene al dia.
 */
class EcoRepositorioOffline(
    private val local: LocalEco,
    private val api: EcoApi,
    private val reloj: Reloj
) : RutaRepository, PesajeRepository {

    /** El registro y la sincronizacion automatica pueden cruzarse: un pendiente no se envia dos veces. */
    private val candado = Mutex()

    /** Filas que la ultima subida creo en el servicio: id local -> definitivo. */
    private val creados = mutableMapOf<Long, Pesaje>()

    /** Rechazos de la ultima subida: la pantalla que origino el cambio los muestra al momento. */
    private val rechazosRecientes = mutableMapOf<Long, ErrorApi>()

    // ---------- Lectura ----------

    override fun observarRuta(): Flow<Ruta?> = local.observarRuta()
    override fun observarPuntos(): Flow<List<PuntoAcopio>> = local.observarPuntos()
    override fun observarPunto(id: Long): Flow<PuntoAcopio?> = local.observarPunto(id)
    override fun observarPesajes(puntoId: Long): Flow<List<Pesaje>> = local.observarPesajes(puntoId)
    override fun contarPendientes(): Flow<Long> = local.contarPendientes()
    override fun ultimaSincronizacion(): Flow<InfoSincronizacion?> = local.ultimaSincronizacion()

    // ---------- Escrituras: primero local, despues se intenta enviar ----------

    override suspend fun registrar(rutaId: Long, puntoId: Long, datos: DatosPesaje, operario: String): Pesaje {
        val guardado = local.guardarNuevo(rutaId, puntoId, datos, operario, reloj.fechaHoraLocal())
        intentarSincronizar()
        // Con red, un 400/403/409 se informa en el formulario y la fila no se conserva.
        rechazosRecientes.remove(guardado.id)?.let { error ->
            local.descartarPendiente(guardado.id)
            throw error
        }
        return creados.remove(guardado.id) ?: local.buscar(guardado.id) ?: guardado
    }

    override suspend fun corregir(id: Long, datos: DatosPesaje): Pesaje {
        local.guardarCorreccion(id, datos)
        intentarSincronizar()
        rechazosRecientes.remove(id)?.let { error ->
            // Correccion rechazada (RN-01, RN-02, 400): se vuelve a la version del servicio.
            if (id > 0) {
                local.descartarPendiente(id)
                intentarSincronizar()
            }
            throw error
        }
        return creados.remove(id) ?: local.buscar(id) ?: throw ErrorApi.NoEncontrado()
    }

    override suspend fun anular(id: Long, motivo: String) {
        local.guardarAnulacion(id, motivo)
        intentarSincronizar()
        rechazosRecientes.remove(id)?.let { error ->
            local.descartarPendiente(id)
            intentarSincronizar()
            throw error
        }
    }

    override suspend fun descartar(id: Long) {
        local.descartarPendiente(id)
        // Si era una correccion o anulacion rechazada, la bajada restaura la version del servicio.
        if (id > 0) intentarSincronizar()
    }

    override fun borrarDatosLocales() {
        local.borrarTodo()
    }

    // ---------- Sincronizacion: subir y despues bajar ----------

    override suspend fun sincronizar(): ResumenSincronizacion = candado.withLock {
        val (enviados, rechazados) = subirPendientes()
        val recibidos = bajarDelServidor()
        local.registrarSincronizacion(reloj.milisegundos(), enviados, recibidos)
        ResumenSincronizacion(enviados, recibidos, rechazados)
    }

    override suspend fun cerrarRuta(rutaId: Long) {
        // Lo pesado sin red debe llegar antes: RN-04 se evalua en el servicio.
        sincronizar()
        api.cerrarRuta(rutaId)
        sincronizar()
    }

    /**
     * Envia cada pendiente. Un rechazo de negocio (400, 403, 404, 409) se
     * guarda en la fila y no bloquea a las demas; la falta de red, el tiempo
     * agotado o un 401 cortan la sincronizacion y todo queda pendiente.
     */
    private suspend fun subirPendientes(): Pair<Int, Int> {
        var enviados = 0
        var rechazados = 0
        creados.clear()
        rechazosRecientes.clear()
        local.pendientes().forEach { pendiente ->
            try {
                when (local.operacionDe(pendiente.id)) {
                    LocalEco.CREAR -> {
                        val definitivo = api.registrarPesaje(pendiente.rutaId, pendiente.puntoId, pendiente.datos()).aDominio()
                        local.confirmarCreado(pendiente.id, definitivo)
                        creados[pendiente.id] = definitivo
                    }
                    LocalEco.EDITAR -> {
                        val definitivo = api.corregirPesaje(pendiente.id, pendiente.datos()).aDominio()
                        local.confirmarCorregido(definitivo)
                    }
                    LocalEco.ANULAR -> {
                        val motivo = (pendiente.estado as? EstadoPesaje.Anulado)?.motivo.orEmpty()
                        api.anularPesaje(pendiente.id, motivo)
                        local.confirmarSincronizado(pendiente.id)
                    }
                }
                enviados++
            } catch (rechazo: ErrorApi) {
                if (!rechazo.esRechazo) throw rechazo
                local.marcarRechazado(pendiente.id, motivoDe(rechazo))
                rechazosRecientes[pendiente.id] = rechazo
                rechazados++
            }
        }
        return enviados to rechazados
    }

    /** Trae la ruta del dia y los pesajes de cada punto. Devuelve cuantos pesajes recibio. */
    private suspend fun bajarDelServidor(): Int {
        val ruta = try {
            api.rutaDeHoy()
        } catch (sinRuta: ErrorApi.NoEncontrado) {
            null // el usuario no tiene ruta asignada hoy (por ejemplo, el administrador)
        }
        val pesajes = ruta?.puntos.orEmpty().flatMap { punto ->
            api.pesajesDePunto(punto.id).map { it.aDominio() }
        }
        local.reemplazarDesdeServidor(
            ruta = ruta?.aDominio(),
            puntos = ruta?.puntos.orEmpty().map { it.aDominio() to it.atendido },
            pesajes = pesajes
        )
        return pesajes.size
    }

    /** Un fallo de red no tumba la operacion local: queda pendiente. */
    private suspend fun intentarSincronizar() {
        try {
            sincronizar()
        } catch (fallo: ErrorApi) {
            // Sin red, tiempo agotado, 500 o 401: el cambio ya esta guardado y marcado.
        }
    }

    private fun Pesaje.datos() = DatosPesaje(material, pesoBruto, tara, observacion, humedadEstimada)

    private fun motivoDe(error: ErrorApi): String =
        if (error is ErrorApi.Validacion && error.campos.isNotEmpty()) error.campos.values.joinToString(". ")
        else error.message ?: "Rechazado por el servicio"
}
