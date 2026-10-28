package pe.upeu.ecorecicla.presentation.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.EstadoRuta
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.model.Privilegios
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.presentation.components.EstadoVacio
import pe.upeu.ecorecicla.presentation.components.ValidatedTextField
import pe.upeu.ecorecicla.presentation.listado.Etiqueta
import pe.upeu.ecorecicla.presentation.sesion.Mensaje

@Composable
fun DetallePuntoScreen(
    puntoId: Long,
    viewModel: DetallePuntoViewModel,
    sesion: Sesion,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(puntoId) { viewModel.cargar(puntoId) }
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val punto = estado.punto
    val rutaAbierta = estado.ruta?.estado == EstadoRuta.ABIERTA

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            estado.cargando -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            punto == null -> Mensaje("El punto ya no forma parte de la ruta", error = true)
            else -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${punto.secuencia}. ${punto.razonSocial}", style = MaterialTheme.typography.titleMedium)
                        Text(punto.direccion, style = MaterialTheme.typography.bodyMedium)
                        Text("Zona ${punto.zona} · Material habitual: ${punto.materialHabitual}", style = MaterialTheme.typography.bodySmall)
                        Etiqueta(if (punto.atendido) "Pesado" else "Por pesar", alerta = !punto.atendido)
                    }
                }

                estado.mensaje?.let { Mensaje(it, error = estado.mensajeEsError) }
                if (!rutaAbierta) Mensaje("La ruta está cerrada: no se registran ni corrigen pesajes", error = false)

                // La interfaz solo ofrece lo que el privilegio permite; el servicio igual lo verifica.
                if (sesion.puede(Privilegios.PESAJE_CREAR) && rutaAbierta) {
                    Button(onClick = viewModel::nuevoPesaje, modifier = Modifier.fillMaxWidth()) { Text("Registrar pesaje") }
                }

                Text("Pesajes", style = MaterialTheme.typography.titleSmall)
                if (estado.pesajes.isEmpty()) {
                    EstadoVacio(icono = Icons.Default.Scale, titulo = "Sin pesajes", descripcion = "Este punto aún no tiene pesajes.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(estado.pesajes, key = { it.id }) { pesaje ->
                            PesajeItem(
                                pesaje = pesaje,
                                puedeCorregir = rutaAbierta && !pesaje.anulado && sesion.puede(Privilegios.PESAJE_EDITAR) &&
                                    (sesion.puede(Privilegios.PESAJE_EDITAR_TODOS) || pesaje.operario == sesion.usuario),
                                puedeAnular = !pesaje.anulado && sesion.puede(Privilegios.PESAJE_ANULAR),
                                onCorregir = { viewModel.corregir(pesaje) },
                                onAnular = { viewModel.pedirAnulacion(pesaje) },
                                onDescartar = { viewModel.descartar(pesaje) }
                            )
                        }
                    }
                }
            }
        }
    }

    estado.formulario?.let { f ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarFormulario,
            title = { Text(if (f.idEditando == null) "Registrar pesaje" else "Corregir pesaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ValidatedTextField(f.material, viewModel::onMaterial, "Material", f.errores["material"])
                    ValidatedTextField(f.pesoBruto, viewModel::onPesoBruto, "Peso bruto (kg)", f.errores["pesoBruto"],
                        keyboardType = KeyboardType.Decimal)
                    ValidatedTextField(f.tara, viewModel::onTara, "Tara del contenedor (kg)", f.errores["tara"],
                        keyboardType = KeyboardType.Decimal, ayuda = "El peso neto lo calcula el servicio")
                    ValidatedTextField(f.observacion, viewModel::onObservacion, "Observación", f.errores["observacion"])
                    f.error?.let { Mensaje(it, error = true) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::guardar, enabled = !f.guardando) { Text(if (f.guardando) "Guardando…" else "Guardar") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelarFormulario) { Text("Cancelar") } }
        )
    }

    estado.anulacion?.let { a ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarAnulacion,
            title = { Text("Anular pesaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El pesaje no se borra: queda marcado como anulado.")
                    ValidatedTextField(a.motivo, viewModel::onMotivo, "Motivo", a.error)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmarAnulacion, enabled = !a.guardando) { Text("Anular") } },
            dismissButton = { TextButton(onClick = viewModel::cancelarAnulacion) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PesajeItem(
    pesaje: Pesaje,
    puedeCorregir: Boolean,
    puedeAnular: Boolean,
    onCorregir: () -> Unit,
    onAnular: () -> Unit,
    onDescartar: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${pesaje.material} · neto ${pesaje.pesoNeto} kg", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                if (pesaje.pendiente && pesaje.rechazo == null) Etiqueta("Pendiente", alerta = true)
                (pesaje.estado as? EstadoPesaje.Anulado)?.let { Etiqueta("Anulado", alerta = true) }
            }
            Text("Bruto ${pesaje.pesoBruto} kg · Tara ${pesaje.tara} kg", style = MaterialTheme.typography.bodySmall)
            Text("${pesaje.operario} · ${pesaje.fechaHora.replace('T', ' ').take(16)}" + if (pesaje.id < 0) " · id local ${pesaje.id}" else "",
                style = MaterialTheme.typography.bodySmall)
            (pesaje.estado as? EstadoPesaje.Anulado)?.let {
                Text("Motivo de anulación: ${it.motivo}", style = MaterialTheme.typography.bodySmall)
            }
            pesaje.rechazo?.let {
                Text("Rechazado por el servicio: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pesaje.rechazo != null) TextButton(onClick = onDescartar) { Text("Descartar") }
                if (puedeCorregir) TextButton(onClick = onCorregir) { Text("Corregir") }
                if (puedeAnular) TextButton(onClick = onAnular) { Text("Anular") }
            }
        }
    }
}
