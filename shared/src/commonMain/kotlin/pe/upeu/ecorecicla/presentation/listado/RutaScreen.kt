package pe.upeu.ecorecicla.presentation.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pe.upeu.ecorecicla.domain.model.EstadoRuta
import pe.upeu.ecorecicla.domain.model.Privilegios
import pe.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.presentation.components.EstadoVacio
import pe.upeu.ecorecicla.presentation.sesion.Mensaje

@Composable
fun RutaScreen(
    viewModel: RutaViewModel,
    sesion: Sesion,
    onVerPunto: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    // «Hace 5 minutos» envejece aunque nada cambie: se recalcula cada minuto.
    val ahora by produceState(viewModel.reloj.milisegundos()) {
        while (true) {
            delay(60_000L)
            value = viewModel.reloj.milisegundos()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Encabezado(estado, textoAntiguedad(estado.ultima?.instante, ahora), viewModel::sincronizar)

        estado.resumen?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        estado.aviso?.let { Mensaje(it, error = true) }
        estado.mensajeCierre?.let { Mensaje(it, error = estado.cierreConError) }

        val ruta = estado.ruta
        if (ruta != null && ruta.estado == EstadoRuta.ABIERTA && sesion.puede(Privilegios.RUTA_CERRAR)) {
            Button(onClick = viewModel::cerrarRuta, enabled = !estado.cerrandoRuta, modifier = Modifier.fillMaxWidth()) {
                Text(if (estado.cerrandoRuta) "Cerrando ruta…" else "Cerrar ruta del día")
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                // Primera carga: base vacia mientras baja la ruta.
                ruta == null && estado.sincronizando && estado.ultima == null ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Descargando la ruta del día…", modifier = Modifier.padding(top = 12.dp))
                    }

                ruta == null && estado.errorServidor ->
                    EstadoVacio(
                        icono = Icons.Default.CloudDownload, titulo = "El servicio tuvo un problema",
                        descripcion = "No se pudo descargar la ruta.", modifier = Modifier.align(Alignment.Center),
                        accion = { FilledTonalButton(onClick = { viewModel.sincronizar() }) { Text("Reintentar") } }
                    )

                ruta == null && estado.ultima == null ->
                    EstadoVacio(
                        icono = Icons.Default.CloudDownload, titulo = "Aún no hay datos guardados",
                        descripcion = "Conéctate y pulsa Sincronizar para descargar la ruta del día.",
                        modifier = Modifier.align(Alignment.Center),
                        accion = { FilledTonalButton(onClick = { viewModel.sincronizar() }) { Text("Sincronizar") } }
                    )

                ruta == null ->
                    EstadoVacio(
                        icono = Icons.Default.EventBusy, titulo = "No tienes una ruta asignada hoy",
                        descripcion = "Cuando el supervisor te asigne una ruta aparecerá aquí.",
                        modifier = Modifier.align(Alignment.Center)
                    )

                estado.puntos.isEmpty() ->
                    EstadoVacio(
                        icono = Icons.Default.EventBusy, titulo = "La ruta no tiene puntos",
                        descripcion = "No hay puntos de acopio planificados.", modifier = Modifier.align(Alignment.Center)
                    )

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(estado.puntos, key = { it.id }) { punto ->
                        PuntoItem(punto, onClick = { onVerPunto(punto.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Encabezado(estado: RutaUiState, antiguedad: String, onSincronizar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val ruta = estado.ruta
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ruta?.fecha ?: "Ruta del día", style = MaterialTheme.typography.titleMedium)
                ruta?.let { Etiqueta(it.estado.name, alerta = it.estado == EstadoRuta.CERRADA) }
            }
            if (ruta != null) {
                Text("${estado.atendidos} de ${estado.puntos.size} puntos pesados", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                antiguedad, style = MaterialTheme.typography.labelMedium,
                color = if (estado.ultima == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (estado.sincronizando) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        } else {
            // El numero sale de una consulta observable de la base local (contarPendientes).
            BadgedBox(badge = { if (estado.pendientes > 0) Badge { Text("${estado.pendientes}") } }) {
                IconButton(onClick = onSincronizar) { Icon(Icons.Default.Sync, contentDescription = "Sincronizar") }
            }
        }
    }
}

@Composable
private fun PuntoItem(punto: PuntoAcopio, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text("${punto.secuencia}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(punto.razonSocial, style = MaterialTheme.typography.titleSmall)
                Text("${punto.zona} · ${punto.direccion}", style = MaterialTheme.typography.bodySmall)
                Text("Material habitual: ${punto.materialHabitual}", style = MaterialTheme.typography.bodySmall)
            }
            Etiqueta(if (punto.atendido) "Pesado" else "Por pesar", alerta = !punto.atendido)
        }
    }
}

@Composable
fun Etiqueta(texto: String, alerta: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (alerta) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(texto, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
