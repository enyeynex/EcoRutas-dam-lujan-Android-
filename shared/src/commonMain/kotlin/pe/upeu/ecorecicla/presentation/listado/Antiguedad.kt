package pe.upeu.ecorecicla.presentation.listado

/** Antiguedad de los datos en lenguaje natural: «Actualizado hace 2 horas». */
fun textoAntiguedad(instante: Long?, ahora: Long): String {
    if (instante == null) return NUNCA_SINCRONIZADO
    val minutos = (ahora - instante).coerceAtLeast(0L) / 60_000L
    val horas = minutos / 60
    val dias = horas / 24
    val hace = when {
        minutos < 1 -> return "Actualizado hace un momento"
        minutos < 60 -> plural(minutos, "minuto", "minutos")
        horas < 24 -> plural(horas, "hora", "horas")
        else -> plural(dias, "día", "días")
    }
    return "Actualizado hace $hace"
}

const val NUNCA_SINCRONIZADO = "Nunca sincronizado: conéctate para descargar la ruta"

private fun plural(n: Long, uno: String, varios: String) = if (n == 1L) "1 $uno" else "$n $varios"
