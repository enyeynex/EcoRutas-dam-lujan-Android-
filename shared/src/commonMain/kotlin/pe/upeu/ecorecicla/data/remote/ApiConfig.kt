package pe.upeu.ecorecicla.data.remote

/**
 * Configuracion del servicio en un solo lugar. 10.0.2.2 es el «localhost»
 * del PC visto desde el emulador de Android.
 */
object ApiConfig {
    const val URL_BASE = "http://10.0.2.2:8080/api/v1/"
    const val TIEMPO_CONEXION_MS = 10_000L
    const val TIEMPO_PETICION_MS = 15_000L
    const val TAMANIO_PAGINA = 50
}
