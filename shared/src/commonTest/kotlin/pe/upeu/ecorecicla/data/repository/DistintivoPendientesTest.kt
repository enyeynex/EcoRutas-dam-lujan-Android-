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
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SC-D: el numero del distintivo sale de la consulta observable
 * contarPendientes y cambia solo al registrar o al sincronizar.
 */
class DistintivoPendientesTest {

    private class Escenario {
        val servicio = ServicioSimulado()
        private val almacen = FakeAlmacenSeguro().apply { guardar(AlmacenSeguro.TOKEN, "token") }
        val repo = EcoRepositorioOffline(
            LocalEco(baseEnMemoria()),
            EcoApi(crearHttpClient(servicio.motor, almacen, esDepuracion = false), AvisosDeSesion()),
            RelojFijo()
        )
        /** Lo mismo que observa RutaViewModel.pendientes. */
        val distintivo = ObservarRutaUseCase(repo).pendientes()
    }

    private val carton = DatosPesaje("Carton", 40.0, 4.0, null)

    @Test
    fun elDistintivoPasaDeCeroAUnoAlRegistrarSinRedYVuelveACeroAlSincronizar() = runTest {
        val e = Escenario()
        e.repo.sincronizar()
        assertEquals(0L, e.distintivo.first())

        e.servicio.conRed = false
        e.repo.registrar(1, 10, carton, "operario1")
        assertEquals(1L, e.distintivo.first())

        e.repo.registrar(1, 11, carton, "operario1")
        assertEquals(2L, e.distintivo.first())

        e.servicio.conRed = true
        e.repo.sincronizar()
        assertEquals(0L, e.distintivo.first())
    }

    @Test
    fun unaSincronizacionFallidaNoCambiaElNumero() = runTest {
        val e = Escenario()
        e.servicio.conRed = false
        e.repo.registrar(1, 10, carton, "operario1")

        runCatching { e.repo.sincronizar() }

        assertEquals(1L, e.distintivo.first(), "sin red el pendiente sigue contando")
    }

    @Test
    fun unRechazadoDelServicioNoSeCuentaComoPendiente() = runTest {
        val e = Escenario()
        e.servicio.materialesRechazados += "Vidrio"
        e.servicio.conRed = false
        e.repo.registrar(1, 10, DatosPesaje("Vidrio", 20.0, 2.0, null), "operario1")
        assertEquals(1L, e.distintivo.first())

        e.servicio.conRed = true
        e.repo.sincronizar()

        assertEquals(0L, e.distintivo.first(), "el rechazado ya no se reintenta: se muestra aparte")
    }
}
