package pe.upeu.ecorecicla.domain.repository

import pe.upeu.ecorecicla.domain.model.Sesion

interface AuthRepository {
    /** Valida credenciales en el servicio y guarda el token en el almacen seguro. */
    suspend fun iniciarSesion(usuario: String, clave: String): Sesion

    /** Sesion del token guardado si sigue vigente; null si no hay o vencio. */
    fun sesionGuardada(): Sesion?

    /** Usuario del ultimo inicio de sesion (aunque el token ya no exista). */
    fun ultimoUsuario(): String?

    /** Borra el token del almacen seguro y de la cache del cliente HTTP. */
    fun cerrarSesion()
}
