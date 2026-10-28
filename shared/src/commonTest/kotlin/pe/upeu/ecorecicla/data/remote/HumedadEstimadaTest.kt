package pe.upeu.ecorecicla.data.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.upeu.ecorecicla.apoyo.FakeAlmacenSeguro
import pe.upeu.ecorecicla.apoyo.RelojFijo
import pe.upeu.ecorecicla.apoyo.ServicioSimulado
import pe.upeu.ecorecicla.apoyo.baseEnMemoria
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.LocalEco
import pe.upeu.ecorecicla.data.remote.dto.CorreccionPesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.data.repository.EcoRepositorioOffline
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import pe.upeu.ecorecicla.domain.usecase.MENSAJE_HUMEDAD
import pe.upeu.ecorecicla.domain.usecase.validarPesaje
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SC-C: la humedad estimada recorre DTO, dominio, validacion y base local. */
class HumedadEstimadaTest {

    @Test
    fun elDtoConHumedadSeConvierteAlDominioYSinElCampoQuedaEnNull() {
        val base = """{"id":7,"rutaId":1,"puntoId":10,"fechaHora":"2026-10-28T09:00:00","material":"Carton",
            "pesoBruto":50.0,"tara":5.0,"pesoNeto":45.0,"operario":"operario1""""

        val conHumedad = JsonEco.decodeFromString(PesajeDto.serializer(), "$base,\"humedadEstimada\":12.5}")
        val sinHumedad = JsonEco.decodeFromString(PesajeDto.serializer(), "$base}")

        assertEquals(12.5, conHumedad.aDominio().humedadEstimada)
        assertNull(sinHumedad.aDominio().humedadEstimada, "un servicio sin el campo no rompe la app")
    }

    @Test
    fun lasPeticionesDeRegistroYCorreccionEnvianLaHumedad() {
        val alta = JsonEco.encodeToString(PesajeRequestDto.serializer(), PesajeRequestDto(1, 10, "Papel", 20.0, 2.0, null, 8.0))
        val correccion = JsonEco.encodeToString(CorreccionPesajeDto.serializer(), CorreccionPesajeDto("Papel", 20.0, 2.0, null, 9.5))

        assertTrue("\"humedadEstimada\":8.0" in alta, alta)
        assertTrue("\"humedadEstimada\":9.5" in correccion, correccion)
    }

    @Test
    fun laHumedadEsOpcionalYDebeEstarEntreCeroYCien() {
        assertNull(validarPesaje("Papel", "20", "2", "", "").getOrThrow().humedadEstimada)
        assertEquals(12.5, validarPesaje("Papel", "20", "2", "", "12,5").getOrThrow().humedadEstimada)

        val fueraDeRango = assertIs<ErrorApi.Validacion>(validarPesaje("Papel", "20", "2", "", "150").exceptionOrNull())
        val noNumerica = assertIs<ErrorApi.Validacion>(validarPesaje("Papel", "20", "2", "", "mucha").exceptionOrNull())

        assertEquals(MENSAJE_HUMEDAD, fueraDeRango.campos["humedadEstimada"])
        assertEquals(MENSAJE_HUMEDAD, noNumerica.campos["humedadEstimada"])
    }

    @Test
    fun sinRedLaHumedadQuedaGuardadaEnElPendienteYSeConservaAlCorregir() = runTest {
        val servicio = ServicioSimulado()
        val almacen = FakeAlmacenSeguro().apply { guardar(AlmacenSeguro.TOKEN, "token") }
        val repo = EcoRepositorioOffline(
            LocalEco(baseEnMemoria()),
            EcoApi(crearHttpClient(servicio.motor, almacen, esDepuracion = false), AvisosDeSesion()),
            RelojFijo()
        )
        repo.sincronizar()
        servicio.conRed = false

        val guardado = repo.registrar(1, 10, DatosPesaje("Carton", 40.0, 4.0, null, 15.0), "operario1")
        assertEquals(15.0, repo.observarPesajes(10).first().single().humedadEstimada)

        repo.corregir(guardado.id, DatosPesaje("Carton", 40.0, 4.0, null, 18.0))
        assertEquals(18.0, repo.observarPesajes(10).first().single().humedadEstimada)
    }
}
