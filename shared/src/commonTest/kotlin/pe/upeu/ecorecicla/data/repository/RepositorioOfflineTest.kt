package pe.upeu.ecorecicla.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.upeu.ecorecicla.apoyo.FakeAlmacenSeguro
import pe.upeu.ecorecicla.apoyo.RelojFijo
import pe.upeu.ecorecicla.apoyo.ServicioSimulado
import pe.upeu.ecorecicla.apoyo.baseEnMemoria
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.LocalEco
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.crearHttpClient
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repositorio offline-first real: base SQLite en memoria (una por prueba) y
 * el HttpClient de la app con MockEngine en lugar de EcoAPI.
 */
class RepositorioOfflineTest {

    private class Escenario {
        val servicio = ServicioSimulado()
        val base: EcoDb = baseEnMemoria()
        val local = LocalEco(base)
        private val almacen = FakeAlmacenSeguro().apply { guardar(AlmacenSeguro.TOKEN, "token") }
        private val api = EcoApi(crearHttpClient(servicio.motor, almacen, esDepuracion = false), AvisosDeSesion())
        val repo = EcoRepositorioOffline(local, api, RelojFijo())

        fun filas() = base.ecoQueries.pesajesDePunto(10).executeAsList() + base.ecoQueries.pesajesDePunto(11).executeAsList()
    }

    private val carton = DatosPesaje("Carton", 50.0, 5.0, null)

    @Test
    fun sincronizarConRedLlenaLaRutaYSusPuntosEnOrden() = runTest {
        val e = Escenario()

        val resumen = e.repo.sincronizar()

        assertEquals(listOf(1, 2), e.repo.observarPuntos().first().map { it.secuencia })
        assertEquals("2026-10-28", e.repo.observarRuta().first()?.fecha)
        assertEquals(0, resumen.enviados)
        assertTrue(e.repo.ultimaSincronizacion().first() != null)
    }

    @Test
    fun registrarSinConexionGuardaPendienteConIdNegativoYElFlowLoEmite() = runTest {
        val e = Escenario()
        e.repo.sincronizar()
        e.servicio.conRed = false

        val guardado = e.repo.registrar(1, 10, carton, "operario1")

        assertTrue(guardado.id < 0, "id local provisional negativo")
        assertTrue(guardado.pendiente)
        assertEquals(45.0, guardado.pesoNeto, "neto provisional hasta que responda el servicio")
        assertEquals(listOf(guardado.id), e.repo.observarPesajes(10).first().map { it.id })
        assertEquals(1L, e.repo.contarPendientes().first())
        assertTrue(e.repo.observarPuntos().first().first { it.id == 10L }.atendido, "el punto ya cuenta como pesado")
    }

    @Test
    fun sincronizarSubeLoPendienteAntesDeBajarYNoDuplica() = runTest {
        val e = Escenario()
        e.servicio.conRed = false
        e.repo.registrar(1, 10, carton, "operario1")
        e.servicio.conRed = true
        e.servicio.eventos.clear()

        e.repo.sincronizar()
        e.repo.sincronizar()

        val primeraSubida = e.servicio.eventos.indexOf("POST pesajes")
        val primeraBajada = e.servicio.eventos.indexOf("GET rutas/hoy")
        assertTrue(primeraSubida in 0 until primeraBajada, "orden: ${e.servicio.eventos}")
        assertEquals(1, e.servicio.pesajes.size, "el servicio recibio el pesaje una sola vez")
        val fila = e.filas().single()
        assertEquals(100L, fila.id, "la fila provisional se sustituyo por la definitiva")
        assertEquals(0L, fila.pendiente)
        assertEquals(0L, e.repo.contarPendientes().first())
    }

    @Test
    fun unPendienteRechazadoNoBloqueaALosDemasNiLaBajada() = runTest {
        val e = Escenario()
        e.servicio.conRed = false
        e.servicio.materialesRechazados += "Vidrio"
        e.repo.registrar(1, 10, DatosPesaje("Vidrio", 30.0, 1.0, null), "operario1")
        e.repo.registrar(1, 11, carton, "operario1")
        e.servicio.conRed = true

        val resumen = e.repo.sincronizar()

        assertEquals(1, resumen.enviados)
        assertEquals(1, resumen.rechazados)
        val rechazado = e.repo.observarPesajes(10).first().single()
        assertEquals("La ruta está cerrada: no se pueden registrar pesajes", rechazado.rechazo)
        assertEquals(0L, e.repo.contarPendientes().first(), "un rechazado ya no cuenta como pendiente de envio")
        assertTrue(e.repo.ultimaSincronizacion().first() != null)
    }

    @Test
    fun conRedUn400SeDevuelveAlFormularioYNoQuedaLaFila() = runTest {
        val e = Escenario()
        e.repo.sincronizar()
        // La validacion local se salta a proposito para probar el 400 del servicio.
        val fallo = assertFailsWith<ErrorApi.Validacion> {
            e.repo.registrar(1, 10, DatosPesaje("Carton", 5.0, 5.0, null), "operario1")
        }

        assertEquals("El peso neto debe ser mayor que cero", fallo.campos["tara"])
        assertTrue(e.filas().isEmpty())
    }

    @Test
    fun laBajadaNoPisaUnaCorreccionPendiente() = runTest {
        val e = Escenario()
        e.servicio.pesajes += ServicioSimulado.P(7, 10, "Carton", 40.0, 4.0, "operario1")
        e.repo.sincronizar()
        e.servicio.conRed = false

        e.repo.corregir(7, DatosPesaje("Carton", 60.0, 4.0, null))
        e.servicio.conRed = true
        e.local.reemplazarDesdeServidor(null, emptyList(), listOf()) // bajada sin la correccion
        val fila = e.filas().single { it.id == 7L }

        assertEquals(60.0, fila.pesoBruto, "la correccion pendiente sigue en la base")
        assertEquals(1L, fila.pendiente)
    }

    @Test
    fun corregirElPesajeDeOtroOperarioDevuelve403YRestauraLaVersionDelServicio() = runTest {
        val e = Escenario()
        e.servicio.pesajes += ServicioSimulado.P(8, 10, "Carton", 40.0, 4.0, "operario2")
        e.repo.sincronizar()

        assertFailsWith<ErrorApi.SinPrivilegio> { e.repo.corregir(8, DatosPesaje("Carton", 99.0, 4.0, null)) }

        val fila = e.filas().single { it.id == 8L }
        assertEquals(40.0, fila.pesoBruto)
        assertNull(fila.rechazo)
    }

    @Test
    fun anularConRedEnviaDeleteYLaFilaQuedaAnuladaSinBorrarse() = runTest {
        val e = Escenario()
        e.servicio.pesajes += ServicioSimulado.P(9, 11, "Plastico", 20.0, 1.0, "operario1")
        e.repo.sincronizar()

        e.repo.anular(9, "Pesaje duplicado")

        assertTrue("DELETE pesajes/9" in e.servicio.eventos)
        val pesaje = e.repo.observarPesajes(11).first().single()
        assertEquals(EstadoPesaje.Anulado("Pesaje duplicado"), pesaje.estado)
        assertFalse(pesaje.pendiente)
    }

    @Test
    fun sinRutaAsignadaLaBaseQuedaSinRuta() = runTest {
        val e = Escenario()
        e.servicio.sinRuta = true

        e.repo.sincronizar()

        assertNull(e.repo.observarRuta().first())
        assertTrue(e.repo.observarPuntos().first().isEmpty())
    }

    @Test
    fun cerrarSesionDejaElEquipoLimpio() = runTest {
        val e = Escenario()
        e.repo.sincronizar()
        e.servicio.conRed = false
        e.repo.registrar(1, 10, carton, "operario1")

        e.repo.borrarDatosLocales()

        assertNull(e.repo.observarRuta().first())
        assertEquals(0L, e.repo.contarPendientes().first())
        assertNull(e.repo.ultimaSincronizacion().first())
    }
}
