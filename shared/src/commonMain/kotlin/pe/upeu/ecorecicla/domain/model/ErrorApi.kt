package pe.upeu.ecorecicla.domain.model

/**
 * Errores del dominio. La capa de datos traduce cada respuesta del servicio a
 * uno de estos casos en un solo lugar (data/remote/MapeoErrores.kt), y cada
 * pantalla decide su conducta segun el tipo, no segun el codigo HTTP.
 */
sealed class ErrorApi(mensaje: String) : Exception(mensaje) {

    /** 400: campo -> mensaje, para mostrarlo bajo el campo que lo origino. */
    class Validacion(
        val campos: Map<String, String>,
        mensaje: String = "Revisa los datos ingresados"
    ) : ErrorApi(mensaje)

    /** 401: token ausente, alterado o vencido. */
    class NoAutorizado(mensaje: String = "Tu sesión ya no es válida. Vuelve a iniciar sesión") : ErrorApi(mensaje)

    /** 403: autenticado pero sin el privilegio. La sesion se mantiene. */
    class SinPrivilegio(mensaje: String = "Operación no autorizada para tu usuario") : ErrorApi(mensaje)

    /** 404: el recurso no existe. */
    class NoEncontrado(mensaje: String = "El recurso ya no existe") : ErrorApi(mensaje)

    /** 409: regla de negocio incumplida; el mensaje es el motivo del servidor. */
    class Conflicto(mensaje: String) : ErrorApi(mensaje)

    /** 500 u otro codigo inesperado. */
    class Servidor(mensaje: String = "El servicio tuvo un problema. Intenta de nuevo") : ErrorApi(mensaje)

    class SinConexion : ErrorApi("Sin conexión: no se pudo contactar al servicio")

    class TiempoAgotado : ErrorApi("El servicio tardó demasiado en responder")

    /** Errores que dejan el cambio pendiente para reintentarlo al sincronizar. */
    val esDeRed: Boolean get() = this is SinConexion || this is TiempoAgotado

    /** Errores de negocio de una fila concreta: se marcan y no bloquean al resto. */
    val esRechazo: Boolean
        get() = this is Validacion || this is Conflicto || this is SinPrivilegio || this is NoEncontrado
}
