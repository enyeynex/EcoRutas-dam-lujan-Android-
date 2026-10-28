package pe.upeu.ecorecicla.domain.model

data class Pesaje(
    val id: Long,                      // negativo mientras este pendiente de envio
    val puntoId: Long,
    val fechaHora: String,             // ISO-8601
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,              // lo calcula el servicio
    val operario: String,
    val estado: EstadoPesaje,
    val pendiente: Boolean,
    // Propiedades agregadas (el enunciado permite agregar, no quitar):
    val rutaId: Long = 0,
    val observacion: String? = null,
    /** Motivo si el servicio rechazo el cambio al sincronizar; null si no. */
    val rechazo: String? = null
) {
    val anulado: Boolean get() = estado is EstadoPesaje.Anulado
}
