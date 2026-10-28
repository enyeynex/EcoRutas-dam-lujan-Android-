package pe.upeu.ecorecicla.data.remote

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.io.IOException
import pe.upeu.ecorecicla.data.remote.dto.ErrorDto
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import kotlin.coroutines.cancellation.CancellationException

/** Unico lugar donde un codigo HTTP se convierte en un error del dominio. */
fun traducirError(estado: Int, error: ErrorDto?): ErrorApi {
    val mensaje = error?.message?.takeIf { it.isNotBlank() }
    return when (estado) {
        400 -> ErrorApi.Validacion(error?.validationErrors.orEmpty(), mensaje ?: "Datos inválidos")
        401 -> ErrorApi.NoAutorizado(mensaje ?: "Tu sesión ya no es válida. Vuelve a iniciar sesión")
        403 -> ErrorApi.SinPrivilegio("Operación no autorizada: tu usuario no tiene el privilegio necesario")
        404 -> ErrorApi.NoEncontrado(mensaje ?: "El recurso ya no existe")
        409 -> ErrorApi.Conflicto(mensaje ?: "La operación no cumple una regla del negocio")
        else -> ErrorApi.Servidor("El servicio respondió $estado. Intenta de nuevo")
    }
}

/**
 * Ejecuta una llamada al servicio y traduce cualquier fallo a [ErrorApi].
 * Un 401 fuera del login ademas se avisa por [AvisosDeSesion] para que la
 * app borre el token y vuelva al inicio de sesion.
 */
internal suspend fun <T> llamadaApi(
    avisos: AvisosDeSesion?,
    bloque: suspend () -> T
): T {
    try {
        return bloque()
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (fallo: ResponseException) {
        val traducido = traducirError(fallo.response.status.value, leerError(fallo))
        if (traducido is ErrorApi.NoAutorizado) avisos?.notificar(traducido.message.orEmpty())
        throw traducido
    } catch (fallo: HttpRequestTimeoutException) {
        throw ErrorApi.TiempoAgotado()
    } catch (fallo: ConnectTimeoutException) {
        throw ErrorApi.TiempoAgotado()
    } catch (fallo: SocketTimeoutException) {
        throw ErrorApi.TiempoAgotado()
    } catch (fallo: IOException) {
        throw ErrorApi.SinConexion()
    }
}

private suspend fun leerError(fallo: ResponseException): ErrorDto? =
    try {
        JsonEco.decodeFromString(ErrorDto.serializer(), fallo.response.bodyAsText())
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (otro: Exception) {
        null
    }
