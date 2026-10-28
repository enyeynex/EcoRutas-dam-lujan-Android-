package pe.upeu.ecorecicla.domain.model

data class Ruta(
    val id: Long,
    val fecha: String,
    val estado: EstadoRuta
)

enum class EstadoRuta { ABIERTA, CERRADA }

/** Ultima sincronizacion correcta: cuando fue y que se movio. */
data class InfoSincronizacion(
    val instante: Long,
    val enviados: Int,
    val recibidos: Int
)

data class ResumenSincronizacion(
    val enviados: Int,
    val recibidos: Int,
    val rechazados: Int
)

/** Lo que el operario escribe en el formulario de pesaje. */
data class DatosPesaje(
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String?
)
