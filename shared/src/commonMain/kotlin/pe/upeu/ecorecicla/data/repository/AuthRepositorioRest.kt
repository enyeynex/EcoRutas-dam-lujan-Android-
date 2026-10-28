package pe.upeu.ecorecicla.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import kotlinx.serialization.Serializable
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.JsonEco
import pe.upeu.ecorecicla.data.remote.dto.aSesion
import pe.upeu.ecorecicla.data.remote.expiracionDelToken
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.plataforma.Reloj
import pe.upeu.ecorecicla.domain.repository.AuthRepository

/** Datos del usuario que se guardan cifrados junto al token. */
@Serializable
private data class PerfilGuardado(
    val usuario: String,
    val nombreCompleto: String,
    val rol: String,
    val privilegios: List<String>
)

class AuthRepositorioRest(
    private val api: EcoApi,
    private val cliente: HttpClient,
    private val almacen: AlmacenSeguro,
    private val reloj: Reloj
) : AuthRepository {

    override suspend fun iniciarSesion(usuario: String, clave: String): Sesion {
        val respuesta = try {
            api.login(usuario, clave)
        } catch (fallo: ErrorApi.NoAutorizado) {
            // En el login un 401 no es una sesion vencida: son credenciales incorrectas.
            throw ErrorApi.NoAutorizado("Usuario o contraseña incorrectos")
        }
        val sesion = respuesta.aSesion()
        almacen.guardar(AlmacenSeguro.TOKEN, sesion.token)
        almacen.guardar(
            AlmacenSeguro.PERFIL,
            JsonEco.encodeToString(
                PerfilGuardado.serializer(),
                PerfilGuardado(sesion.usuario, sesion.nombreCompleto, sesion.rol, sesion.privilegios.toList())
            )
        )
        almacen.guardar(AlmacenSeguro.ULTIMO_USUARIO, sesion.usuario)
        // loadTokens quedo en cache con el valor anterior: se fuerza a releerlo.
        cliente.authProvider<BearerAuthProvider>()?.clearToken()
        return sesion
    }

    /**
     * Sesion al reabrir la app, sin red: si el token sigue vigente (claim exp)
     * se entra directo; si vencio, se borra y se vuelve al inicio de sesion.
     */
    override fun sesionGuardada(): Sesion? {
        val token = almacen.leer(AlmacenSeguro.TOKEN) ?: return null
        val expira = expiracionDelToken(token)
        val perfil = almacen.leer(AlmacenSeguro.PERFIL)?.let {
            runCatching { JsonEco.decodeFromString(PerfilGuardado.serializer(), it) }.getOrNull()
        }
        if (expira == null || expira * 1000 <= reloj.milisegundos() || perfil == null) {
            cerrarSesion()
            return null
        }
        return Sesion(token, perfil.usuario, perfil.rol, perfil.privilegios.toSet(), perfil.nombreCompleto)
    }

    override fun ultimoUsuario(): String? = almacen.leer(AlmacenSeguro.ULTIMO_USUARIO)

    override fun cerrarSesion() {
        almacen.borrar(AlmacenSeguro.TOKEN)
        almacen.borrar(AlmacenSeguro.PERFIL)
        cliente.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
