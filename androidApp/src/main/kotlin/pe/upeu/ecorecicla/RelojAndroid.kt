package pe.upeu.ecorecicla

import pe.upeu.ecorecicla.domain.plataforma.Reloj
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class RelojAndroid : Reloj {
    override fun milisegundos(): Long = System.currentTimeMillis()
    override fun fechaHoraLocal(): String = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
}
