package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.AuthRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository

class IniciarSesionUseCase(
    private val auth: AuthRepository,
    private val rutas: RutaRepository
) {
    suspend operator fun invoke(usuario: String, clave: String): Result<Sesion> {
        val campos = buildMap {
            if (usuario.isBlank()) put("usuario", "Ingresa tu usuario")
            if (clave.isBlank()) put("clave", "Ingresa tu contraseña")
        }
        if (campos.isNotEmpty()) return Result.failure(ErrorApi.Validacion(campos))

        return resultadoDe {
            val anterior = auth.ultimoUsuario()
            val sesion = auth.iniciarSesion(usuario.trim(), clave)
            // Equipo compartido: los datos de otro operario no se mezclan con los nuevos.
            if (anterior != null && anterior != sesion.usuario) rutas.borrarDatosLocales()
            sesion
        }
    }
}

class SesionGuardadaUseCase(private val auth: AuthRepository) {
    operator fun invoke(): Sesion? = auth.sesionGuardada()
}

/**
 * Cierre de sesion del turno: token fuera y base local limpia (equipos
 * compartidos). Ante un 401 solo se borra el token: si vuelve el mismo
 * usuario, sus pesajes pendientes siguen ahi.
 */
class CerrarSesionUseCase(
    private val auth: AuthRepository,
    private val rutas: RutaRepository
) {
    operator fun invoke(borrarDatosLocales: Boolean = true) {
        auth.cerrarSesion()
        if (borrarDatosLocales) rutas.borrarDatosLocales()
    }
}
