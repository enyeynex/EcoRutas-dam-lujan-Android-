package pe.upeu.ecorecicla.presentation.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Ruta
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.DescartarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase

data class FormularioPesaje(
    /** null = registrar; un id = corregir ese pesaje. */
    val idEditando: Long? = null,
    val material: String = "",
    val pesoBruto: String = "",
    val tara: String = "",
    val observacion: String = "",
    /** Errores por campo: locales o los validationErrors del servicio (400). */
    val errores: Map<String, String> = emptyMap(),
    val guardando: Boolean = false,
    val error: String? = null
)

data class Anulacion(
    val pesaje: Pesaje,
    val motivo: String = "",
    val error: String? = null,
    val guardando: Boolean = false
)

data class DetalleUiState(
    val cargando: Boolean = true,
    val punto: PuntoAcopio? = null,
    val ruta: Ruta? = null,
    val pesajes: List<Pesaje> = emptyList(),
    val formulario: FormularioPesaje? = null,
    val anulacion: Anulacion? = null,
    val mensaje: String? = null,
    val mensajeEsError: Boolean = false
)

class DetallePuntoViewModel(
    private val observarRuta: ObservarRutaUseCase,
    private val observarPesajes: ObservarPesajesUseCase,
    private val registrarPesaje: RegistrarPesajeUseCase,
    private val corregirPesaje: CorregirPesajeUseCase,
    private val anularPesaje: AnularPesajeUseCase,
    private val descartarPesaje: DescartarPesajeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleUiState())
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    private var suscripciones: List<Job> = emptyList()

    fun cargar(puntoId: Long) {
        suscripciones.forEach { it.cancel() }
        _uiState.value = DetalleUiState()
        suscripciones = listOf(
            viewModelScope.launch {
                observarRuta.punto(puntoId).collect { p -> _uiState.update { it.copy(punto = p, cargando = false) } }
            },
            viewModelScope.launch { observarRuta.ruta().collect { r -> _uiState.update { it.copy(ruta = r) } } },
            viewModelScope.launch { observarPesajes(puntoId).collect { l -> _uiState.update { it.copy(pesajes = l) } } }
        )
    }

    // ---------- Formulario ----------

    fun nuevoPesaje() {
        val punto = _uiState.value.punto ?: return
        _uiState.update { it.copy(formulario = FormularioPesaje(material = punto.materialHabitual), mensaje = null) }
    }

    fun corregir(pesaje: Pesaje) {
        _uiState.update {
            it.copy(
                mensaje = null,
                formulario = FormularioPesaje(
                    idEditando = pesaje.id, material = pesaje.material,
                    pesoBruto = numero(pesaje.pesoBruto), tara = numero(pesaje.tara),
                    observacion = pesaje.observacion.orEmpty()
                )
            )
        }
    }

    fun cancelarFormulario() = _uiState.update { it.copy(formulario = null) }

    fun onMaterial(v: String) = editar { it.copy(material = v, errores = it.errores - "material") }
    fun onPesoBruto(v: String) = editar { it.copy(pesoBruto = v, errores = it.errores - "pesoBruto") }
    fun onTara(v: String) = editar { it.copy(tara = v, errores = it.errores - "tara") }
    fun onObservacion(v: String) = editar { it.copy(observacion = v) }

    fun guardar() {
        val estado = _uiState.value
        val f = estado.formulario ?: return
        val punto = estado.punto ?: return
        val ruta = estado.ruta ?: return
        if (f.guardando) return
        editar { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val resultado = if (f.idEditando == null) {
                registrarPesaje(ruta.id, punto.id, f.material, f.pesoBruto, f.tara, f.observacion)
            } else {
                corregirPesaje(f.idEditando, f.material, f.pesoBruto, f.tara, f.observacion)
            }
            resultado.fold(
                onSuccess = { p ->
                    _uiState.update {
                        it.copy(
                            formulario = null, mensajeEsError = false,
                            mensaje = when {
                                p.pendiente -> "Guardado sin conexión: se enviará al sincronizar"
                                f.idEditando == null -> "Pesaje registrado. Neto calculado por el servicio: ${numero(p.pesoNeto)} kg"
                                else -> "Pesaje corregido. Neto: ${numero(p.pesoNeto)} kg"
                            }
                        )
                    }
                },
                onFailure = { fallo ->
                    when (fallo) {
                        // 400: cada mensaje bajo el campo que lo origino.
                        is ErrorApi.Validacion -> editar {
                            it.copy(guardando = false, errores = fallo.campos,
                                error = if (fallo.campos.isEmpty()) fallo.message else null)
                        }
                        // 403 y 409: el motivo del servicio; la sesion se mantiene.
                        else -> editar { it.copy(guardando = false, error = fallo.message) }
                    }
                }
            )
        }
    }

    // ---------- Anulacion ----------

    fun pedirAnulacion(pesaje: Pesaje) = _uiState.update { it.copy(anulacion = Anulacion(pesaje), mensaje = null) }
    fun onMotivo(v: String) = _uiState.update { it.copy(anulacion = it.anulacion?.copy(motivo = v, error = null)) }
    fun cancelarAnulacion() = _uiState.update { it.copy(anulacion = null) }

    fun confirmarAnulacion() {
        val a = _uiState.value.anulacion ?: return
        _uiState.update { it.copy(anulacion = a.copy(guardando = true)) }
        viewModelScope.launch {
            anularPesaje(a.pesaje.id, a.motivo).fold(
                onSuccess = {
                    _uiState.update { it.copy(anulacion = null, mensaje = "Pesaje anulado", mensajeEsError = false) }
                },
                onFailure = { fallo ->
                    val texto = (fallo as? ErrorApi.Validacion)?.campos?.get("motivo") ?: fallo.message
                    if (fallo is ErrorApi.NoEncontrado) {
                        // 404: se informa y la lista ya se refresco con la bajada.
                        _uiState.update { it.copy(anulacion = null, mensaje = texto, mensajeEsError = true) }
                    } else {
                        _uiState.update { it.copy(anulacion = a.copy(guardando = false, error = texto)) }
                    }
                }
            )
        }
    }

    fun descartar(pesaje: Pesaje) {
        viewModelScope.launch { descartarPesaje(pesaje.id) }
    }

    private fun editar(cambio: (FormularioPesaje) -> FormularioPesaje) =
        _uiState.update { it.copy(formulario = it.formulario?.let(cambio)) }

    private fun numero(valor: Double): String =
        if (valor % 1.0 == 0.0) valor.toLong().toString() else ((valor * 100).toLong() / 100.0).toString()
}
