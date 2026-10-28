package pe.upeu.ecorecicla.data.repository

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import pe.upeu.ecorecicla.apoyo.FakeAlmacenSeguro
import pe.upeu.ecorecicla.apoyo.RelojFijo
import pe.upeu.ecorecicla.apoyo.jwtDePrueba
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.crearHttpClient
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositorioTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repo(almacen: FakeAlmacenSeguro, reloj: RelojFijo, estado: HttpStatusCode, cuerpo: String): AuthRepositorioRest {
        val cliente = crearHttpClient(MockEngine { respond(cuerpo, estado, json) }, almacen, esDepuracion = false)
        return AuthRepositorioRest(EcoApi(cliente, AvisosDeSesion()), cliente, almacen, reloj)
    }

    @Test
    fun elLoginGuardaElTokenYLosPrivilegios() = runTest {
        val almacen = FakeAlmacenSeguro()
        val token = jwtDePrueba("supervisor1", expSegundos = 5_000)
        val auth = repo(almacen, RelojFijo(1_000_000), HttpStatusCode.OK,
            """{"token":"$token","tipo":"Bearer","expiraEnSegundos":3600,"usuario":"supervisor1",
               "nombreCompleto":"Rosa Diaz","rol":"SUPERVISOR","privilegios":["PESAJE_ANULAR","RUTA_CERRAR"]}""")

        val sesion = auth.iniciarSesion("supervisor1", "Eco2026*")

        assertEquals(token, almacen.leer(AlmacenSeguro.TOKEN))
        assertTrue(sesion.puede("RUTA_CERRAR"))
        assertEquals("SUPERVISOR", assertNotNull(auth.sesionGuardada()).rol)
    }

    @Test
    fun credencialesIncorrectasNoGuardanNada() = runTest {
        val almacen = FakeAlmacenSeguro()
        val auth = repo(almacen, RelojFijo(), HttpStatusCode.Unauthorized, """{"status":401,"message":"Credenciales invalidas"}""")

        val fallo = assertFailsWith<ErrorApi.NoAutorizado> { auth.iniciarSesion("operario1", "mala") }

        assertEquals("Usuario o contraseña incorrectos", fallo.message)
        assertNull(almacen.leer(AlmacenSeguro.TOKEN))
    }

    @Test
    fun unTokenVencidoVuelveAlInicioDeSesionYSeBorra() = runTest {
        val almacen = FakeAlmacenSeguro()
        val token = jwtDePrueba("operario1", expSegundos = 5_000)
        val reloj = RelojFijo(1_000_000)
        val auth = repo(almacen, reloj, HttpStatusCode.OK,
            """{"token":"$token","usuario":"operario1","rol":"OPERARIO","privilegios":["PESAJE_CREAR"]}""")
        auth.iniciarSesion("operario1", "Eco2026*")

        reloj.ms = 6_000_000 // despues del exp

        assertNull(auth.sesionGuardada())
        assertNull(almacen.leer(AlmacenSeguro.TOKEN))
        assertEquals("operario1", auth.ultimoUsuario())
    }
}
