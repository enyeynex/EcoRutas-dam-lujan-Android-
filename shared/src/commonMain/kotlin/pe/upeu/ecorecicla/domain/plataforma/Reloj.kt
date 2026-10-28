package pe.upeu.ecorecicla.domain.plataforma

/** Reloj inyectable: las pruebas fijan la hora sin esperar. */
interface Reloj {
    fun milisegundos(): Long
    /** Fecha y hora local en ISO-8601 (yyyy-MM-ddTHH:mm:ss). */
    fun fechaHoraLocal(): String
}
