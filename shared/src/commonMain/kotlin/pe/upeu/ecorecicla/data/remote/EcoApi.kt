package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import pe.upeu.ecorecicla.data.remote.dto.CorreccionPesajeDto
import pe.upeu.ecorecicla.data.remote.dto.LoginRequestDto
import pe.upeu.ecorecicla.data.remote.dto.LoginResponseDto
import pe.upeu.ecorecicla.data.remote.dto.PaginaDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeRequestDto
import pe.upeu.ecorecicla.data.remote.dto.RutaDto
import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion

/**
 * Llamadas a EcoAPI. Cada una pasa por llamadaApi, que traduce los errores.
 * El token no aparece aqui: lo agrega el plugin Auth del HttpClient.
 */
class EcoApi(
    private val cliente: HttpClient,
    private val avisos: AvisosDeSesion
) {
    suspend fun login(usuario: String, clave: String): LoginResponseDto = llamadaApi(avisos = null) {
        cliente.post("auth/login") { setBody(LoginRequestDto(usuario, clave)) }.body()
    }

    suspend fun rutaDeHoy(): RutaDto = llamadaApi(avisos) {
        cliente.get("rutas/hoy").body()
    }

    suspend fun pesajesDePunto(puntoId: Long): List<PesajeDto> = llamadaApi(avisos) {
        cliente.get("puntos/$puntoId/pesajes") {
            parameter("pagina", 0)
            parameter("tamanio", ApiConfig.TAMANIO_PAGINA)
        }.body<PaginaDto<PesajeDto>>().contenido
    }

    suspend fun registrarPesaje(rutaId: Long, puntoId: Long, datos: DatosPesaje): PesajeDto = llamadaApi(avisos) {
        cliente.post("pesajes") {
            setBody(PesajeRequestDto(rutaId, puntoId, datos.material, datos.pesoBruto, datos.tara, datos.observacion))
        }.body()
    }

    suspend fun corregirPesaje(id: Long, datos: DatosPesaje): PesajeDto = llamadaApi(avisos) {
        cliente.put("pesajes/$id") {
            setBody(CorreccionPesajeDto(datos.material, datos.pesoBruto, datos.tara, datos.observacion))
        }.body()
    }

    /** 204 No Content: no se convierte el cuerpo, solo cuenta el codigo. */
    suspend fun anularPesaje(id: Long, motivo: String) {
        llamadaApi(avisos) {
            cliente.delete("pesajes/$id") { parameter("motivo", motivo) }
        }
    }

    suspend fun cerrarRuta(rutaId: Long) {
        llamadaApi(avisos) { cliente.post("rutas/$rutaId/cierre") }
    }
}
