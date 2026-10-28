package pe.upeu.ecorecicla.domain.model

data class Sesion(
    val token: String,
    val usuario: String,
    val rol: String,
    val privilegios: Set<String>,
    val nombreCompleto: String = usuario
) {
    /** La interfaz pregunta por privilegio, nunca por el nombre del rol. */
    fun puede(privilegio: String): Boolean = privilegio in privilegios
}
