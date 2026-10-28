package pe.upeu.ecorecicla.domain.model

data class PuntoAcopio(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val secuencia: Int,                // orden de visita en la ruta
    val atendido: Boolean
)
