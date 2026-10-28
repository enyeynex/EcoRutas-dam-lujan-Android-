package pe.upeu.ecorecicla.domain.model

sealed interface EstadoPesaje {
    data object Registrado : EstadoPesaje
    data class Anulado(val motivo: String) : EstadoPesaje
}
