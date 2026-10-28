package pe.upeu.ecorecicla.presentation.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.InfoSincronizacion
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.plataforma.MonitorDeRed
import pe.upeu.ecorecicla.domain.plataforma.Reloj
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase

data class RutaUiState(
    val ruta: Ruta? = null,
    val puntos: List<PuntoAcopio> = emptyList(),
    /** SC-A: texto del buscador y filtro «Solo pendientes de pesar». */
    val texto: String = "",
    val soloPendientes: Boolean = false,
    /** Puntos que devuelve la consulta filtrada (la lista que se dibuja). */
    val puntosVisibles: List<PuntoAcopio> = emptyList(),
    val pendientes: Long = 0,
    val ultima: InfoSincronizacion? = null,
    val sincronizando: Boolean = false,
    /** Ya termino al menos un intento de sincronizar en esta pantalla. */
    val intentoInicialTerminado: Boolean = false,
    val resumen: String? = null,
    val aviso: String? = null,
    /** 500: se ofrece reintentar. */
    val errorServidor: Boolean = false,
    val cerrandoRuta: Boolean = false,
    val mensajeCierre: String? = null,
    val cierreConError: Boolean = false
) {
    val atendidos: Int get() = puntos.count { it.atendido }
    val hayFiltro: Boolean get() = texto.isNotBlank() || soloPendientes
}

/**
 * La ruta se lee siempre de la base local (Flow). La red solo la actualiza
 * con [sincronizar]: al abrir, con el boton y al recuperar la conexion.
 */
class RutaViewModel(
    private val observar: ObservarRutaUseCase,
    private val sincronizarUseCase: SincronizarUseCase,
    private val cerrarRutaUseCase: CerrarRutaUseCase,
    private val monitorDeRed: MonitorDeRed,
    val reloj: Reloj
) : ViewModel() {

    private val _uiState = MutableStateFlow(RutaUiState())
    val uiState: StateFlow<RutaUiState> = _uiState.asStateFlow()

    // SC-A: el filtro vive en el ViewModel solo como criterio; la consulta la hace SQLite.
    private val texto = MutableStateFlow("")
    private val soloPendientes = MutableStateFlow(false)

    init {
        viewModelScope.launch { observar.ruta().collect { r -> _uiState.update { it.copy(ruta = r) } } }
        viewModelScope.launch { observar.puntos().collect { p -> _uiState.update { it.copy(puntos = p) } } }
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            combine(texto, soloPendientes) { t, s -> t to s }
                .flatMapLatest { (t, s) -> observar.puntos(t, s) }
                .collect { p -> _uiState.update { it.copy(puntosVisibles = p) } }
        }
        viewModelScope.launch { observar.pendientes().collect { n -> _uiState.update { it.copy(pendientes = n) } } }
        viewModelScope.launch { observar.ultimaSincronizacion().collect { u -> _uiState.update { it.copy(ultima = u) } } }
        // Sincronizacion automatica al recuperar la conexion (sin red -> con red).
        viewModelScope.launch {
            var antes: Boolean? = null
            monitorDeRed.conectado.collect { conectado ->
                if (antes == false && conectado) sincronizar(automatica = true)
                antes = conectado
            }
        }
        sincronizar()
    }

    fun sincronizar(automatica: Boolean = false) {
        if (_uiState.value.sincronizando) return
        viewModelScope.launch {
            _uiState.update { it.copy(sincronizando = true) }
            val resultado = sincronizarUseCase()
            _uiState.update { estado ->
                resultado.fold(
                    onSuccess = { r ->
                        estado.copy(
                            sincronizando = false, intentoInicialTerminado = true, aviso = null, errorServidor = false,
                            resumen = buildString {
                                if (automatica) append("Conexión recuperada · ")
                                append("Enviados ${r.enviados} · Recibidos ${r.recibidos}")
                                if (r.rechazados > 0) append(" · Rechazados ${r.rechazados}")
                            }
                        )
                    },
                    onFailure = { fallo ->
                        estado.copy(
                            sincronizando = false, intentoInicialTerminado = true,
                            errorServidor = fallo is ErrorApi.Servidor,
                            aviso = when (fallo) {
                                is ErrorApi.SinConexion, is ErrorApi.TiempoAgotado ->
                                    "${fallo.message}. Mostrando los datos guardados"
                                else -> fallo.message
                            }
                        )
                    }
                )
            }
        }
    }

    fun onTextoChange(valor: String) {
        texto.value = valor
        _uiState.update { it.copy(texto = valor) }
    }

    fun onSoloPendientesChange(valor: Boolean) {
        soloPendientes.value = valor
        _uiState.update { it.copy(soloPendientes = valor) }
    }

    fun cerrarRuta() {
        val ruta = _uiState.value.ruta ?: return
        if (_uiState.value.cerrandoRuta) return
        viewModelScope.launch {
            _uiState.update { it.copy(cerrandoRuta = true, mensajeCierre = null) }
            val resultado = cerrarRutaUseCase(ruta.id)
            _uiState.update {
                it.copy(
                    cerrandoRuta = false,
                    cierreConError = resultado.isFailure,
                    // 409 (RN-04): se muestra el motivo que devuelve el servicio.
                    mensajeCierre = resultado.exceptionOrNull()?.message ?: "Ruta cerrada correctamente"
                )
            }
        }
    }

    fun ocultarMensajeCierre() = _uiState.update { it.copy(mensajeCierre = null) }
}
