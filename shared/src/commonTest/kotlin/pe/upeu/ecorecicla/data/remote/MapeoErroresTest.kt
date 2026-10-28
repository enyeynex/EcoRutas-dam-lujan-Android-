package pe.upeu.ecorecicla.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import pe.upeu.ecorecicla.apoyo.FakeAlmacenSeguro
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Traduccion de cada respuesta del servicio a un error tipado del dominio. */
class MapeoErroresTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val avisos = AvisosDeSesion()

    private fun api(estado: HttpStatusCode, cuerpo: String) = EcoApi(
        crearHttpClient(MockEngine { respond(cuerpo, estado, json) }, FakeAlmacenSeguro(), esDepuracion = false),
        avisos
    )

    private val datos = DatosPesaje("Carton", 10.0, 2.0, null)

    @Test
    fun un400ConValidationErrorsLlegaComoValidacionPorCampo() = runTest {
        val api = api(HttpStatusCode.BadRequest,
            """{"status":400,"message":"Datos invalidos","validationErrors":{"tara":"El peso neto debe ser mayor que cero"}}""")

        val fallo = assertFailsWith<ErrorApi.Validacion> { api.registrarPesaje(1, 10, datos) }

        assertEquals("El peso neto debe ser mayor que cero", fallo.campos["tara"])
    }

    @Test
    fun un403EsSinPrivilegioYNoAvisaCierreDeSesion() = runTest {
        val api = api(HttpStatusCode.Forbidden, """{"status":403,"message":"Acceso denegado"}""")
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { avisos.sesionTerminada.first() }

        assertFailsWith<ErrorApi.SinPrivilegio> { api.anularPesaje(5, "x") }

        assertTrue(aviso.isActive, "un 403 no cierra la sesion")
        aviso.cancel()
    }

    @Test
    fun un409TraeElMotivoDelServidor() = runTest {
        val api = api(HttpStatusCode.Conflict, """{"status":409,"message":"No se puede cerrar la ruta: faltan 3 puntos"}""")

        val fallo = assertFailsWith<ErrorApi.Conflicto> { api.cerrarRuta(1) }

        assertEquals("No se puede cerrar la ruta: faltan 3 puntos", fallo.message)
    }

    @Test
    fun un401FueraDelLoginAvisaQueLaSesionTermino() = runTest {
        val api = api(HttpStatusCode.Unauthorized, """{"status":401,"message":"Token vencido"}""")
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { avisos.sesionTerminada.first() }

        assertFailsWith<ErrorApi.NoAutorizado> { api.rutaDeHoy() }

        assertEquals("Token vencido", aviso.await())
    }

    @Test
    fun los404Y500TienenSuPropioTipo() = runTest {
        assertFailsWith<ErrorApi.NoEncontrado> { api(HttpStatusCode.NotFound, "{}").rutaDeHoy() }
        assertFailsWith<ErrorApi.Servidor> { api(HttpStatusCode.InternalServerError, "x").rutaDeHoy() }
    }

    @Test
    fun unaFallaDeRedEsSinConexion() = runTest {
        val api = EcoApi(
            crearHttpClient(MockEngine { throw IOException("modo avion") }, FakeAlmacenSeguro(), esDepuracion = false),
            avisos
        )

        assertFailsWith<ErrorApi.SinConexion> { api.rutaDeHoy() }
    }
}
