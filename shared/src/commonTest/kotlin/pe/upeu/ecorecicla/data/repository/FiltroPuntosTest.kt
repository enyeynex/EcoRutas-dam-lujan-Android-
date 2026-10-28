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
 * SC-A: el filtro «Solo pendientes de pesar» y el buscador se resuelven en la
 * consulta local, asi que funcionan igual sin conexion.
 */
class FiltroPuntosTest {

    private class Escenario {
        val servicio = ServicioSimulado()
        private val almacen = FakeAlmacenSeguro().apply { guardar(AlmacenSeguro.TOKEN, "token") }
        val repo = EcoRepositorioOffline(
            LocalEco(baseEnMemoria()),
            EcoApi(crearHttpClient(servicio.motor, almacen, esDepuracion = false), AvisosDeSesion()),
            RelojFijo()
        )
        val observar = ObservarRutaUseCase(repo)

        /** Descarga la ruta (puntos 10 y 11, ambos sin pesar) y corta la red. */
        suspend fun sinRedConRutaDescargada(): Escenario {
            repo.sincronizar()
            servicio.conRed = false
            return this
        }

        suspend fun ids(texto: String, soloPendientes: Boolean) =
            observar.puntos(texto, soloPendientes).first().map { it.id }
    }

    @Test
    fun sinFiltroDevuelveTodosLosPuntosEnOrdenDeVisita() = runTest {
        val e = Escenario().sinRedConRutaDescargada()
        assertEquals(listOf(10L, 11L), e.ids("", false))
    }

    @Test
    fun elBuscadorFiltraPorRazonSocialSinDistinguirMayusculas() = runTest {
        val e = Escenario().sinRedConRutaDescargada()
        assertEquals(listOf(11L), e.ids("mega", false))
        assertEquals(emptyList(), e.ids("no existe", false))
    }

    @Test
    fun alRegistrarSinRedElPuntoSaleDeLosPendientes() = runTest {
        val e = Escenario().sinRedConRutaDescargada()
        assertEquals(listOf(10L, 11L), e.ids("", true))

        e.repo.registrar(1, 10, DatosPesaje("Carton", 40.0, 4.0, null), "operario1")

        assertEquals(listOf(11L), e.ids("", true), "el punto 10 ya tiene un pesaje local")
    }

    @Test
    fun buscadorYFiltroSeCombinan() = runTest {
        val e = Escenario().sinRedConRutaDescargada()
        e.repo.registrar(1, 11, DatosPesaje("Plastico", 30.0, 3.0, null), "operario1")

        assertEquals(emptyList(), e.ids("mega", true), "Mega Centro ya fue pesado")
        assertEquals(listOf(10L), e.ids("plaza", true))
    }
}
