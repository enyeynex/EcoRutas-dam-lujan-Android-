package pe.upeu.ecorecicla.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.EstadoRuta
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.model.Sesion

// DTO del contrato del servicio. Solo existen en la capa de datos; el dominio
// no conoce anotaciones de serializacion.

@Serializable
data class LoginRequestDto(val usuario: String, val clave: String)

@Serializable
data class LoginResponseDto(
    val token: String,
    val tipo: String = "Bearer",
    val expiraEnSegundos: Long = 3600,
    val usuario: String,
    val nombreCompleto: String = usuario,
    val rol: String,
    val privilegios: List<String> = emptyList()
)

@Serializable
data class UsuarioDto(
    val usuario: String,
    val nombreCompleto: String = usuario,
    val rol: String,
    val privilegios: List<String> = emptyList()
)

/** Envoltorio de paginacion de todos los listados (seccion 4.2). */
@Serializable
data class PaginaDto<T>(
    val contenido: List<T>,
    val pagina: Int = 0,
    val tamanio: Int = 0,
    val totalElementos: Long = 0,
    val totalPaginas: Int = 0,
    val ultima: Boolean = true
)

/** Formato de error comun del servicio. */
@Serializable
data class ErrorDto(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
    val validationErrors: Map<String, String>? = null
)

@Serializable
data class RutaDto(
    val id: Long,
    val fecha: String,
    val estado: String,
    val puntos: List<PuntoRutaDto> = emptyList()
)

@Serializable
data class PuntoRutaDto(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,
    val atendido: Boolean = false,
    val motivoOmision: String? = null
)

@Serializable
data class PesajeDto(
    val id: Long,
    val rutaId: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val observacion: String? = null,
    @SerialName("operario") val operarioUsuario: String,
    val anulado: Boolean = false,
    val motivoAnulacion: String? = null
)

/** El cliente nunca envia el peso neto: lo calcula el servicio (RN-03). */
@Serializable
data class PesajeRequestDto(
    val rutaId: Long,
    val puntoId: Long,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String?
)

@Serializable
data class CorreccionPesajeDto(
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String?
)

// ---------- Conversion al dominio ----------

fun LoginResponseDto.aSesion() = Sesion(
    token = token, usuario = usuario, rol = rol,
    privilegios = privilegios.toSet(), nombreCompleto = nombreCompleto
)

fun RutaDto.aDominio() = Ruta(
    id = id,
    fecha = fecha,
    estado = if (estado.equals("CERRADA", ignoreCase = true)) EstadoRuta.CERRADA else EstadoRuta.ABIERTA
)

fun PuntoRutaDto.aDominio() = PuntoAcopio(
    id = id, razonSocial = razonSocial, direccion = direccion, zona = zona,
    materialHabitual = materialHabitual, secuencia = secuencia, atendido = atendido
)

fun PesajeDto.aDominio() = Pesaje(
    id = id,
    puntoId = puntoId,
    fechaHora = fechaHora,
    material = material,
    pesoBruto = pesoBruto,
    tara = tara,
    pesoNeto = pesoNeto,
    operario = operarioUsuario,
    estado = if (anulado) EstadoPesaje.Anulado(motivoAnulacion.orEmpty()) else EstadoPesaje.Registrado,
    pendiente = false,
    rutaId = rutaId,
    observacion = observacion
)
