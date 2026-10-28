package pe.upeu.ecorecicla.presentation.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.SesionGuardadaUseCase

data class SesionUiState(
    val verificando: Boolean = true,
    val sesion: Sesion? = null,
    val usuario: String = "",
    val clave: String = "",
    val errorUsuario: String? = null,
    val errorClave: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val aviso: String? = null,
    /** Cambia en cada inicio de sesion: los ViewModel de la sesion anterior no se reutilizan. */
    val idSesion: Int = 0
)

/**
 * Dueno de la sesion en la interfaz: decide si se ve el login o la app y es
 * el unico que reacciona a un 401 (borra el token y vuelve al login).
 */
class SesionViewModel(
    private val iniciarSesion: IniciarSesionUseCase,
    private val sesionGuardada: SesionGuardadaUseCase,
    private val cerrarSesion: CerrarSesionUseCase,
    private val avisos: AvisosDeSesion
) : ViewModel() {

    private val _uiState = MutableStateFlow(SesionUiState())
    val uiState: StateFlow<SesionUiState> = _uiState.asStateFlow()

    init {
        val guardada = sesionGuardada()
        _uiState.update {
            it.copy(verificando = false, sesion = guardada, idSesion = if (guardada != null) 1 else 0)
        }
        viewModelScope.launch {
            avisos.sesionTerminada.collect { motivo ->
                if (_uiState.value.sesion != null) {
                    // 401: solo se borra el token; los pendientes quedan para el mismo usuario.
                    cerrarSesion.invoke(borrarDatosLocales = false)
                    _uiState.update {
                        SesionUiState(verificando = false, idSesion = it.idSesion,
                            aviso = "Tu sesión terminó (401): $motivo")
                    }
                }
            }
        }
    }

    fun onUsuarioChange(valor: String) = _uiState.update { it.copy(usuario = valor, errorUsuario = null, error = null) }
    fun onClaveChange(valor: String) = _uiState.update { it.copy(clave = valor, errorClave = null, error = null) }

    fun iniciarSesion() {
        if (_uiState.value.cargando) return
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null, aviso = null) }
            val estado = _uiState.value
            iniciarSesion(estado.usuario, estado.clave).fold(
                onSuccess = { sesion ->
                    _uiState.update {
                        it.copy(cargando = false, clave = "", sesion = sesion, idSesion = it.idSesion + 1)
                    }
                },
                onFailure = { fallo ->
                    _uiState.update {
                        when (fallo) {
                            is ErrorApi.Validacion -> it.copy(
                                cargando = false,
                                errorUsuario = fallo.campos["usuario"],
                                errorClave = fallo.campos["clave"]
                            )
                            // Mensajes distintos para credenciales y para falta de conexion.
                            is ErrorApi.NoAutorizado -> it.copy(cargando = false, error = "Usuario o contraseña incorrectos")
                            is ErrorApi.SinConexion -> it.copy(cargando = false, error = fallo.message)
                            else -> it.copy(cargando = false, error = fallo.message ?: "No se pudo iniciar sesión")
                        }
                    }
                }
            )
        }
    }

    /** Cierre de sesion del turno: borra token y datos locales. */
    fun cerrarSesion() {
        cerrarSesion.invoke()
        _uiState.update { SesionUiState(verificando = false, idSesion = it.idSesion) }
    }
}
